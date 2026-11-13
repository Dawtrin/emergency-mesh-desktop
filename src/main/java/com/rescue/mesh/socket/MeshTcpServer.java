package com.rescue.mesh.socket;

import com.google.gson.Gson;
import com.rescue.mesh.db.DatabaseManager;
import com.rescue.mesh.model.MeshPacket;
import com.rescue.mesh.routing.RoutingEngine;
import com.rescue.mesh.service.C2ControllerService;
import com.rescue.mesh.service.NetworkAnalyticsService;
import com.rescue.mesh.util.PacketFactory;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskExecutor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * TCP Mesh engine của Tactical Web C2, lắng nghe gói JSON trên port 8888.
 *
 * Class này giữ nguyên boundary của Phase 1: Socket → MeshPacket → RoutingEngine.
 * RoutingEngine tiếp tục kiểm tra duplicate, SHA-256 checksum và TTL trước khi
 * gọi callback. Khi SOS hợp lệ đến Base Station, dữ liệu được ghi SQLite WAL
 * thông qua C2ControllerService và đẩy realtime tới các màn hình C2 qua STOMP.
 */
@Component
public class MeshTcpServer implements RoutingEngine.RoutingCallback {

    private static final Logger LOGGER = LoggerFactory.getLogger(MeshTcpServer.class);
    private static final int CLIENT_READ_TIMEOUT_MS = 5_000;

    private final int meshPort;
    private final String meshHost;
    private final TaskExecutor meshExecutor;
    private final SimpMessagingTemplate messagingTemplate;
    private final Gson gson = new Gson();
    private final DatabaseManager databaseManager;
    private final C2ControllerService c2Service;
    private final NetworkAnalyticsService analyticsService;
    private final RoutingEngine routingEngine;

    /**
     * Hàng đợi các lệnh điều phối đang chờ gửi tới từng node (Pending Dispatch Queue).
     * Khi node kết nối vào Base Station, các lệnh trong hàng đợi sẽ được ghi ngay trên socket.
     */
    private final Map<String, ConcurrentLinkedQueue<MeshPacket>> pendingDispatches = new ConcurrentHashMap<>();

    private final Object lifecycleLock = new Object();
    private volatile boolean running;
    private volatile ServerSocket serverSocket;

    public MeshTcpServer(
            @Value("${mesh.tcp.port:8888}") int meshPort,
            @Value("${mesh.tcp.host:0.0.0.0}") String meshHost,
            @Qualifier("meshExecutor") TaskExecutor meshExecutor,
            SimpMessagingTemplate messagingTemplate,
            DatabaseManager databaseManager,
            C2ControllerService c2Service,
            NetworkAnalyticsService analyticsService) {
        this.meshPort = meshPort;
        this.meshHost = meshHost;
        this.meshExecutor = meshExecutor;
        this.messagingTemplate = messagingTemplate;
        this.databaseManager = databaseManager;
        this.c2Service = c2Service;
        this.analyticsService = analyticsService;
        this.routingEngine = new RoutingEngine(
                MeshPacket.NODE_BASE_STATION,
                "localhost",
                -1,
                this
        );
    }

    /** Khởi động accept loop bất đồng bộ; an toàn khi được gọi lại. */
    public void start() {
        synchronized (lifecycleLock) {
            if (running) {
                LOGGER.info("[INFO] Mesh TCP server đã chạy tại port {}", meshPort);
                return;
            }
            recoverPendingDispatchesFromDatabase();
            running = true;
            meshExecutor.execute(this::acceptLoop);
        }
    }

    /**
     * Nạp lại các lệnh điều phối chưa nhận được ACK từ SQLite vào RAM queue khi khởi động lại máy chủ.
     */
    private void recoverPendingDispatchesFromDatabase() {
        try {
            if (c2Service != null && c2Service.getDispatchDao() != null) {
                List<com.rescue.mesh.db.DispatchDao.DispatchRecord> pendingList = c2Service.getDispatchDao().findPendingDispatches();
                int recoveredCount = 0;
                for (com.rescue.mesh.db.DispatchDao.DispatchRecord rec : pendingList) {
                    if (rec.targetNodeId() != null && !rec.targetNodeId().isBlank()) {
                        MeshPacket packet = PacketFactory.createDispatchCommand(
                                rec.targetNodeId(),
                                rec.commandMessage(),
                                "HIGH"
                        );
                        pendingDispatches.computeIfAbsent(rec.targetNodeId(), k -> new ConcurrentLinkedQueue<>()).add(packet);
                        recoveredCount++;
                    }
                }
                if (recoveredCount > 0) {
                    LOGGER.info("[DISPATCH RECOVERY] Đã phục hồi {} lệnh điều phối chưa ACK từ SQLite vào RAM queue", recoveredCount);
                }
            }
        } catch (Exception e) {
            LOGGER.warn("[DISPATCH RECOVERY] Lỗi khi nạp lệnh chờ từ DB: {}", e.getMessage());
        }
    }

    private void acceptLoop() {
        try (ServerSocket socket = new ServerSocket(meshPort, 50, InetAddress.getByName(meshHost))) {
            serverSocket = socket;
            LOGGER.info("[INFO] MeshTcpServer đang lắng nghe tại {}:{}", meshHost, meshPort);

            while (running) {
                try {
                    Socket clientSocket = socket.accept();
                    String clientAddress = clientSocket.getInetAddress().getHostAddress()
                            + ":" + clientSocket.getPort();
                    LOGGER.info("[INFO] Mesh client kết nối: {}", clientAddress);
                    meshExecutor.execute(() -> handleClient(clientSocket, clientAddress));
                } catch (SocketException exception) {
                    if (running) {
                        LOGGER.error("[ERROR] Mesh accept loop bị ngắt: {}", exception.getMessage(), exception);
                    }
                    break;
                }
            }
        } catch (IOException exception) {
            LOGGER.error("[ERROR] Không mở được Mesh TCP port {}: {}", meshPort, exception.getMessage(), exception);
            publishEvent("/topic/system", new SystemEvent("MESH_TCP_ERROR", exception.getMessage()));
        } finally {
            serverSocket = null;
            running = false;
            LOGGER.info("[INFO] MeshTcpServer đã dừng tại port {}", meshPort);
        }
    }

    /**
     * Đưa một lệnh điều phối vào hàng đợi của targetNodeId để gửi khi node kết nối.
     */
    public void enqueueDispatch(String targetNodeId, MeshPacket packet) {
        if (targetNodeId == null || packet == null) return;
        pendingDispatches.computeIfAbsent(targetNodeId, k -> new ConcurrentLinkedQueue<>()).add(packet);
        LOGGER.info("[DISPATCH QUEUE] Đã xếp lệnh vào hàng đợi của {}: {}", targetNodeId, packet.getPacketId());
    }

    private void handleClient(Socket clientSocket, String clientAddress) {
        try (Socket socket = clientSocket;
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             BufferedWriter writer = new BufferedWriter(
                     new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))) {
            socket.setSoTimeout(CLIENT_READ_TIMEOUT_MS);

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }

                MeshPacket packet = MeshPacket.fromJson(line, gson);
                if (packet == null) {
                    LOGGER.warn("[DROP] PARSE_ERROR từ {}", clientAddress);
                    analyticsService.recordPacketDropped();
                    publishEvent("/topic/drops", new PacketDropEvent("UNKNOWN", "PARSE_ERROR"));
                    continue;
                }

                // Đếm tất cả packet chạm cổng TCP, bao gồm duplicate/checksum fail.
                analyticsService.recordPacketReceived();
                boolean accepted = routingEngine.processPacket(packet);

                // --- INTEGRATION_CONTRACT §0.1: Phản hồi trên cùng kết nối TCP ---
                // 1. Chỉ gửi ACK RECEIVED khi gói tin SOS_DATA hoàn toàn hợp lệ (vượt qua Checksum & Duplicate check)
                if (accepted && MeshPacket.TYPE_SOS_DATA.equals(packet.getPacketType())) {
                    MeshPacket ack = PacketFactory.createAck(
                            MeshPacket.NODE_BASE_STATION,
                            packet.getPacketId(),
                            packet.getSourceNodeId(),
                            "RECEIVED"
                    );
                    writer.write(ack.toJson(gson));
                    writer.write("\n");
                    writer.flush();
                    LOGGER.info("[ACK SENT] Đã gửi ACK RECEIVED cho {} (packet={})", packet.getSourceNodeId(), packet.getPacketId());
                }

                // 2. Gửi 0..N lệnh điều phối đang chờ (pending dispatches) của nodeId này
                String senderNodeId = packet.getSourceNodeId();
                if (senderNodeId != null) {
                    ConcurrentLinkedQueue<MeshPacket> queue = pendingDispatches.get(senderNodeId);
                    if (queue != null && !queue.isEmpty()) {
                        MeshPacket cmd;
                        // Chỉ dequeue lệnh khỏi hàng đợi khi đã ghi socket và flush thành công
                        // Nếu mất kết nối giữa chừng (IOException), lệnh vẫn còn nguyên trong queue cho lần kết nối sau
                        while ((cmd = queue.peek()) != null) {
                            writer.write(cmd.toJson(gson));
                            writer.write("\n");
                            writer.flush();
                            queue.poll(); // Dequeue an toàn sau khi flush thành công
                            LOGGER.info("[DOWNLINK DISPATCH] Đã gửi lệnh điều phối {} xuống {}", cmd.getPacketId(), senderNodeId);
                        }
                    }
                }
            }
        } catch (IOException exception) {
            if (running) {
                LOGGER.warn("[ERROR] Kết nối Mesh client {} kết thúc: {}", clientAddress, exception.getMessage());
            }
        }
    }

    @Override
    public void onPacketArrived(MeshPacket packet) {
        if (MeshPacket.TYPE_ACK.equals(packet.getPacketType())) {
            String ackedId = packet.getPayload() != null ? packet.getPayload().getAckedPacketId() : null;
            if (ackedId != null) {
                c2Service.getDispatchDao().updateAck(ackedId);
                LOGGER.info("[ACK] Đã ghi nhận ACK cho dispatchId / packetId: {}", ackedId);
            }
            publishEvent("/topic/acks", packet);
            return;
        }

        c2Service.processSosPacket(packet);
        LOGGER.warn("[ALERT] SOS mới từ {} | route={} | hops={}",
                packet.getSourceNodeId(), packet.getRouteHistory(), packet.getHopCount());
        publishEvent("/topic/victims", packet);
    }

    @Override
    public void onPacketRelayed(MeshPacket packet, int nextHop) {
        analyticsService.recordPacketForwarded();
        publishEvent("/topic/packets", packet);
    }

    @Override
    public void onPacketDropped(String packetId, String reason) {
        analyticsService.recordPacketDropped();
        if ("DUPLICATE".equals(reason)) {
            analyticsService.recordDuplicateDrop();
        } else if ("CHECKSUM_FAIL".equals(reason)) {
            analyticsService.recordChecksumFail();
        }
        LOGGER.warn("[DROP] {} — {}", shortId(packetId), reason);
        publishEvent("/topic/drops", new PacketDropEvent(packetId, reason));
    }

    @Override
    public void onForwardError(int nextHop, String errorMessage) {
        LOGGER.error("[ERROR] Không relay được đến port {}: {}", nextHop, errorMessage);
        publishEvent("/topic/system", new SystemEvent("FORWARD_ERROR", errorMessage));
    }

    @Override
    public void onDispatchReceived(MeshPacket packet) {
        LOGGER.info("[INFO] Nhận DISPATCH_CMD cho {}", packet.getDestinationNodeId());
        publishEvent("/topic/dispatch-ack", packet);
    }

    @Override
    public void onHeartbeatReceived(MeshPacket packet) {
        c2Service.processHeartbeat(packet);
        analyticsService.setActiveRelayCount(c2Service.getRelayNodeDao().countOnline());
        publishEvent("/topic/relay-status", packet);
    }

    private void publishEvent(String destination, Object payload) {
        messagingTemplate.convertAndSend(destination, payload);
    }

    @PreDestroy
    public void stop() {
        synchronized (lifecycleLock) {
            running = false;
            ServerSocket socket = serverSocket;
            if (socket != null && !socket.isClosed()) {
                try {
                    socket.close();
                } catch (IOException exception) {
                    LOGGER.warn("[ERROR] Không đóng được Mesh TCP server: {}", exception.getMessage());
                }
            }
        }
        routingEngine.shutdown();
        analyticsService.shutdown();
        if (databaseManager.isRunning()) {
            databaseManager.shutdown();
        }
    }

    public boolean isRunning() {
        return running;
    }

    public int getMeshPort() {
        return meshPort;
    }

    public String getMeshHost() {
        return meshHost;
    }

    public int getSeenPacketCacheSize() {
        return routingEngine.getCacheSize();
    }

    private String shortId(String packetId) {
        if (packetId == null) {
            return "UNKNOWN";
        }
        return packetId.length() > 8 ? packetId.substring(0, 8) : packetId;
    }

    /** Payload tối giản cho luồng WebSocket drop events. */
    public record PacketDropEvent(String packetId, String reason) {
    }

    /** Payload tối giản cho trạng thái lỗi TCP / relay. */
    public record SystemEvent(String type, String message) {
    }
}
