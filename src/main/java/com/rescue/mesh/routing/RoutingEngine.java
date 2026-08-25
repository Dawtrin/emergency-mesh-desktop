package com.rescue.mesh.routing;

import com.google.gson.Gson;
import com.rescue.mesh.model.MeshPacket;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.ConnectException;
import java.net.Socket;

/**
 * Bộ máy định tuyến trung tâm — trái tim của hệ thống Mesh Rescue.
 *
 * Cơ sở lý thuyết:
 *   Cài đặt thuật toán Controlled Flooding với TTL + Duplicate Suppression,
 *   tham chiếu từ RFC 3973 (PIM-DM) và thiết kế Meshtastic mesh.proto.
 *
 * Luồng xử lý mỗi gói tin đến (theo đặc tả):
 *   1. Kiểm tra duplicate (SeenPacketCache) → DROP nếu đã thấy
 *   2. Xác minh checksum SHA-256 → DROP nếu bị lỗi/giả mạo
 *   3. Nếu là đích đến → hiển thị alert, phát còi, gửi ACK
 *   4. Nếu TTL > 1 → giảm TTL, tăng hopCount, relay sang next hop
 *   5. Nếu TTL ≤ 1 → DROP (hết hạn)
 *
 * Design Pattern:
 *   - Strategy Pattern: RoutingCallback interface → UI/Network layer
 *     tự quyết định làm gì khi nhận gói tin (hiển thị, log, phát còi)
 *   - Dependency Inversion: RoutingEngine không biết về JavaFX hay Console,
 *     chỉ gọi qua interface → dễ tái sử dụng cho Android giai đoạn 2
 *
 * Thread Safety:
 *   processPacket() được gọi từ nhiều worker thread đồng thời.
 *   SeenPacketCache.checkAndMark() dùng putIfAbsent() → atomic, an toàn.
 */
public class RoutingEngine {

    // =========================================================
    // CALLBACK INTERFACE — Tách biệt routing logic khỏi UI
    // =========================================================

    /**
     * Interface callback để RoutingEngine thông báo sự kiện lên tầng trên.
     * UI layer (JavaFX hoặc Console) implement interface này.
     */
    public interface RoutingCallback {

        /**
         * Được gọi khi node này là đích đến cuối cùng của gói tin SOS.
         * Base Station sẽ hiển thị alert, phát còi, ghim bản đồ.
         *
         * @param packet Gói tin SOS đã đến đích
         */
        void onPacketArrived(MeshPacket packet);

        /**
         * Được gọi khi node relay gói tin thành công sang next hop.
         *
         * @param packet   Gói tin đã được relay
         * @param nextHop  Port của next hop đã gửi tới
         */
        void onPacketRelayed(MeshPacket packet, int nextHop);

        /**
         * Được gọi khi gói tin bị DROP (duplicate, TTL hết, checksum lỗi).
         *
         * @param packetId ID của gói tin bị drop
         * @param reason   Lý do: "DUPLICATE" / "TTL_EXPIRED" / "CHECKSUM_FAIL" / "PARSE_ERROR"
         */
        void onPacketDropped(String packetId, String reason);

        /**
         * Được gọi khi có lỗi kết nối đến next hop.
         *
         * @param nextHop     Port không kết nối được
         * @param errorMessage Mô tả lỗi
         */
        void onForwardError(int nextHop, String errorMessage);

        /**
         * Được gọi khi node nhận được gói tin DISPATCH_CMD từ Base Station.
         *
         * @param packet Gói tin lệnh điều phối
         */
        void onDispatchReceived(MeshPacket packet);
    }

    // =========================================================
    // FIELDS
    // =========================================================

    /** ID của node đang chạy RoutingEngine này */
    private final String myNodeId;

    /** Host của next hop (mặc định localhost trong giả lập giữa kỳ) */
    private final String nextHopHost;

    /** Port của next hop để relay gói tin */
    private final int nextHopPort;

    /** Bộ nhớ đệm chống lặp gói tin */
    private final SeenPacketCache seenPacketCache;

    /** Callback để thông báo sự kiện lên tầng UI/Network */
    private final RoutingCallback callback;

    /** Gson instance dùng chung trong toàn bộ engine */
    private final Gson gson;

    /** Timeout kết nối socket khi relay (milliseconds) */
    private static final int SOCKET_TIMEOUT_MS = 5000;

    // =========================================================
    // CONSTRUCTOR
    // =========================================================

    /**
     * Khởi tạo RoutingEngine cho một node cụ thể.
     *
     * @param myNodeId     ID định danh node này (ví dụ: "NODE_A_VICTIM")
     * @param nextHopHost  Địa chỉ host của next hop (thường là "localhost")
     * @param nextHopPort  Port của next hop (-1 nếu node này là đích cuối)
     * @param callback     Callback để thông báo sự kiện lên tầng trên
     */
    public RoutingEngine(String myNodeId,
                         String nextHopHost,
                         int nextHopPort,
                         RoutingCallback callback) {
        this.myNodeId        = myNodeId;
        this.nextHopHost     = nextHopHost;
        this.nextHopPort     = nextHopPort;
        this.callback        = callback;
        this.seenPacketCache = new SeenPacketCache();
        this.gson            = new Gson();
    }

    // =========================================================
    // CORE ROUTING METHOD
    // =========================================================

    /**
     * Xử lý một gói tin nhận được — phương thức cốt lõi của hệ thống.
     *
     * Đây là cài đặt đầy đủ thuật toán định tuyến theo đặc tả:
     *   Bước 1: Duplicate check
     *   Bước 2: Checksum verify
     *   Bước 3: Phân loại gói tin (đích đến / cần relay)
     *   Bước 4: Relay hoặc Alert
     *
     * Method này thread-safe nhờ SeenPacketCache.checkAndMark() atomic.
     *
     * @param packet Gói tin MeshPacket đã được parse từ JSON
     */
    public void processPacket(MeshPacket packet) {

        // ── Bảo vệ null ──────────────────────────────────────
        if (packet == null) {
            System.err.println("[ERROR] [" + myNodeId + "] processPacket: nhận được packet null");
            return;
        }

        String packetId = packet.getPacketId();
        if (packetId == null || packetId.trim().isEmpty()) {
            System.err.println("[ERROR] [" + myNodeId + "] processPacket: packetId null hoặc rỗng");
            callback.onPacketDropped("UNKNOWN", "PARSE_ERROR");
            return;
        }

        // ── Bước 1: Kiểm tra duplicate ────────────────────────
        // checkAndMark() là atomic: kiểm tra VÀ đánh dấu trong 1 thao tác
        // → tránh race condition khi nhiều thread cùng nhận gói tin này
        if (seenPacketCache.checkAndMark(packetId)) {
            System.out.println("[DROP] [" + myNodeId + "] "
                    + shortId(packetId) + " — DUPLICATE (đã xử lý rồi)");
            callback.onPacketDropped(packetId, "DUPLICATE");
            return;
        }

        System.out.println("[RECV] [" + myNodeId + "] "
                + shortId(packetId)
                + " type=" + packet.getPacketType()
                + " from=" + packet.getSenderHopId()
                + " TTL=" + packet.getTtl()
                + " hops=" + packet.getHopCount());

        // ── Bước 2: Xác minh Checksum ─────────────────────────
        if (!packet.verifyChecksum(gson)) {
            System.err.println("[DROP] [" + myNodeId + "] "
                    + shortId(packetId) + " — CHECKSUM_FAIL (dữ liệu bị lỗi hoặc giả mạo)");
            callback.onPacketDropped(packetId, "CHECKSUM_FAIL");
            return;
        }

        // ── Bước 3: Phân loại theo loại gói tin ──────────────
        switch (packet.getPacketType()) {

            case MeshPacket.TYPE_SOS_DATA:
                processSosPacket(packet);
                break;

            case MeshPacket.TYPE_DISPATCH_CMD:
                processDispatchPacket(packet);
                break;

            case MeshPacket.TYPE_ACK:
                System.out.println("[INFO] [" + myNodeId + "] Nhận ACK cho packet "
                        + (packet.getPayload() != null ? packet.getPayload().getMessage() : "unknown"));
                break;

            case MeshPacket.TYPE_HEARTBEAT:
                System.out.println("[INFO] [" + myNodeId + "] HEARTBEAT từ " + packet.getSourceNodeId());
                break;

            default:
                System.err.println("[ERROR] [" + myNodeId + "] Loại packet không xác định: "
                        + packet.getPacketType());
                callback.onPacketDropped(packetId, "UNKNOWN_TYPE");
        }
    }

    // =========================================================
    // XỬ LÝ GÓI TIN SOS
    // =========================================================

    /**
     * Xử lý gói tin SOS_DATA:
     *   - Nếu đây là Base Station hoặc là đích đến → hiển thị alert
     *   - Nếu không → relay sang next hop
     */
    private void processSosPacket(MeshPacket packet) {

        // Kiểm tra node này có phải là đích đến không
        boolean isDestination = MeshPacket.NODE_BASE_STATION.equals(myNodeId)
                || myNodeId.equals(packet.getDestinationNodeId());

        if (isDestination) {
            // ── Node là đích cuối: hiển thị alert ──
            System.out.println("[ALERT] [" + myNodeId + "] *** SOS NHẬN ĐƯỢC ***");
            System.out.println("[ALERT]   Nguồn    : " + packet.getSourceNodeId());
            System.out.println("[ALERT]   Đường đi : " + packet.getRouteHistory());
            System.out.println("[ALERT]   Bước nhảy: " + packet.getHopCount());
            if (packet.getPayload() != null) {
                System.out.println("[ALERT]   Loại     : " + packet.getPayload().getAlertType());
                System.out.println("[ALERT]   Mức độ   : " + packet.getPayload().getSeverity());
                System.out.println("[ALERT]   Nạn nhân : " + packet.getPayload().getVictimCount() + " người");
                System.out.println("[ALERT]   Tin nhắn : " + packet.getPayload().getMessage());
                if (packet.getPayload().getLocation() != null) {
                    System.out.println("[ALERT]   Tọa độ  : "
                            + packet.getPayload().getLocation().getLatitude()
                            + ", "
                            + packet.getPayload().getLocation().getLongitude());
                }
            }
            callback.onPacketArrived(packet);

        } else {
            // ── Node là relay trung gian: forward sang next hop ──
            forwardPacket(packet);
        }
    }

    // =========================================================
    // XỬ LÝ GÓI TIN DISPATCH COMMAND
    // =========================================================

    /** Bảng định tuyến ánh xạ Node ID sang cổng kết nối (hỗ trợ cả Forward và Reverse routing) */
    private static final java.util.concurrent.ConcurrentHashMap<String, Integer> ROUTING_TABLE = new java.util.concurrent.ConcurrentHashMap<>();

    static {
        ROUTING_TABLE.put(MeshPacket.NODE_A_VICTIM, 8001);
        ROUTING_TABLE.put("NODE_A", 8001);
        ROUTING_TABLE.put(MeshPacket.NODE_B_RELAY, 8002);
        ROUTING_TABLE.put("NODE_B", 8002);
        ROUTING_TABLE.put(MeshPacket.NODE_BASE_STATION, 8888);
    }

    /**
     * Ghi nhận cổng của node để hỗ trợ định tuyến ngược (Reverse Route Learning).
     */
    public void recordRoute(String nodeId, int port) {
        if (nodeId != null && port > 0) {
            ROUTING_TABLE.put(nodeId, port);
        }
    }

    /**
     * Xác định cổng đích cần chuyển tiếp (Reverse Route hoặc Upstream Hop).
     */
    private int resolveTargetPort(MeshPacket packet) {
        String destId = packet.getDestinationNodeId();

        // 1. Nếu là DISPATCH_CMD hoặc đích đến là node cụ thể khác BASE_STATION (Định tuyến ngược)
        if (destId != null && !MeshPacket.NODE_BASE_STATION.equals(destId)) {
            Integer targetPort = ROUTING_TABLE.get(destId);
            if (targetPort != null && targetPort > 0) {
                return targetPort;
            }
            if (destId.contains("NODE_A") || destId.contains("VICTIM")) {
                return 8001;
            }
        }

        // 2. Mặc định chuyển tiếp theo nextHopPort cấu hình (hướng về Base Station)
        return nextHopPort;
    }

    // =========================================================
    // XỬ LÝ GÓI TIN DISPATCH COMMAND
    // =========================================================

    /**
     * Xử lý gói tin DISPATCH_CMD từ Base Station:
     *   - Nếu đây là đích → hiển thị lệnh chỉ đạo
     *   - Nếu không → relay ngược về node nạn nhân
     */
    private void processDispatchPacket(MeshPacket packet) {

        String destId = packet.getDestinationNodeId();
        boolean isDestination = myNodeId.equals(destId)
                || (destId != null && destId.contains("NODE_A") && myNodeId.contains("NODE_A"));

        if (isDestination) {
            System.out.println("[INFO] [" + myNodeId + "] *** LỆNH CHỈ ĐẠO NHẬN ĐƯỢC ***");
            if (packet.getPayload() != null) {
                System.out.println("[INFO]   Lệnh: " + packet.getPayload().getMessage());
                System.out.println("[INFO]   Mức độ: " + packet.getPayload().getSeverity());
            }
            callback.onDispatchReceived(packet);

        } else {
            forwardPacket(packet);
        }
    }

    // =========================================================
    // RELAY (FORWARD) GÓI TIN
    // =========================================================

    /**
     * Chuyển tiếp gói tin sang next hop nếu TTL còn hạn.
     *
     * Thực hiện các bước theo đặc tả:
     *   - Giảm TTL đi 1
     *   - Tăng hopCount lên 1
     *   - Cập nhật senderHopId thành myNodeId
     *   - Thêm myNodeId vào routeHistory
     *   - Gửi qua TCP Socket đến target port
     *
     * @param packet Gói tin cần relay
     */
    private void forwardPacket(MeshPacket packet) {

        // ── Kiểm tra TTL ──
        if (packet.getTtl() <= 1) {
            System.out.println("[DROP] [" + myNodeId + "] "
                    + shortId(packet.getPacketId()) + " — TTL_EXPIRED (TTL=" + packet.getTtl() + ")");
            callback.onPacketDropped(packet.getPacketId(), "TTL_EXPIRED");
            return;
        }

        // Xác định port đích cần gửi
        int targetPort = resolveTargetPort(packet);

        if (targetPort <= 0) {
            System.err.println("[ERROR] [" + myNodeId + "] Không có target port hợp lệ để relay");
            callback.onPacketDropped(packet.getPacketId(), "NO_NEXT_HOP");
            return;
        }

        // ── Cập nhật thông tin routing trên gói tin ──
        packet.setTtl(packet.getTtl() - 1);
        packet.setHopCount(packet.getHopCount() + 1);
        packet.addToRouteHistory(myNodeId);
        packet.setSenderHopId(myNodeId);

        // ── Gửi qua TCP Socket đến target port ──
        String packetJson = packet.toJson(gson);

        System.out.println("[RELAY] [" + myNodeId + "] "
                + shortId(packet.getPacketId())
                + " → " + nextHopHost + ":" + targetPort
                + " (TTL còn: " + packet.getTtl()
                + ", hops: " + packet.getHopCount() + ")");

        sendToPort(packet, targetPort, packetJson);
    }

    /**
     * Gửi JSON của gói tin đến target port qua TCP Socket.
     * Dùng try-with-resources để đảm bảo Socket được đóng dù có lỗi hay không.
     *
     * @param packet     Gói tin gốc (để callback báo lỗi)
     * @param targetPort Port đích cần gửi tới
     * @param packetJson Chuỗi JSON cần gửi
     */
    private void sendToPort(MeshPacket packet, int targetPort, String packetJson) {
        try (Socket socket = new Socket(nextHopHost, targetPort)) {
            socket.setSoTimeout(SOCKET_TIMEOUT_MS);
            PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);
            writer.println(packetJson);
            System.out.println("[SEND] [" + myNodeId + "] "
                    + shortId(packet.getPacketId())
                    + " đã gửi thành công đến port " + targetPort);
            callback.onPacketRelayed(packet, targetPort);

        } catch (ConnectException e) {
            System.err.println("[ERROR] [" + myNodeId + "] Không kết nối được đến "
                    + nextHopHost + ":" + targetPort
                    + " — Node kế tiếp chưa chạy hoặc đã tắt. " + e.getMessage());
            callback.onForwardError(targetPort, "Không kết nối được: " + e.getMessage());

        } catch (IOException e) {
            System.err.println("[ERROR] [" + myNodeId + "] Lỗi I/O khi gửi packet "
                    + shortId(packet.getPacketId())
                    + " đến port " + targetPort + ": " + e.getMessage());
            callback.onForwardError(targetPort, "Lỗi I/O: " + e.getMessage());
        }
    }

    // =========================================================
    // UTILITY
    // =========================================================

    /**
     * Trả về 8 ký tự đầu của UUID để log ngắn gọn hơn.
     * Ví dụ: "550e8400-e29b-41d4..." → "550e8400"
     */
    private String shortId(String packetId) {
        if (packetId == null) return "null";
        return packetId.length() > 8 ? packetId.substring(0, 8) : packetId;
    }

    /**
     * Dừng cache cleanup thread khi shutdown node.
     * Gọi khi ứng dụng thoát.
     */
    public void shutdown() {
        seenPacketCache.shutdown();
        System.out.println("[INFO] [" + myNodeId + "] RoutingEngine đã shutdown.");
    }

    // ===== Getters =====
    public String getMyNodeId()      { return myNodeId; }
    public int    getNextHopPort()   { return nextHopPort; }
    public String getNextHopHost()   { return nextHopHost; }
    public int    getCacheSize()     { return seenPacketCache.size(); }
}