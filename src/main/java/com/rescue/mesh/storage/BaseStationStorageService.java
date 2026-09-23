package com.rescue.mesh.storage;

import com.rescue.mesh.model.MeshPacket;
import com.rescue.mesh.storage.model.DispatchRecord;
import com.rescue.mesh.storage.model.NodeRecord;
import com.rescue.mesh.storage.model.PacketRecord;
import com.rescue.mesh.storage.model.RouteRecord;
import com.rescue.mesh.storage.model.SosEventRecord;
import com.rescue.mesh.storage.repository.DispatchRepository;
import com.rescue.mesh.storage.repository.NodeRepository;
import com.rescue.mesh.storage.repository.PacketRepository;
import com.rescue.mesh.storage.repository.RouteRepository;
import com.rescue.mesh.storage.repository.SosEventRepository;
import com.rescue.mesh.storage.repository.SqliteDispatchRepository;
import com.rescue.mesh.storage.repository.SqliteNodeRepository;
import com.rescue.mesh.storage.repository.SqlitePacketRepository;
import com.rescue.mesh.storage.repository.SqliteRouteRepository;
import com.rescue.mesh.storage.repository.SqliteSosEventRepository;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Service quản lý lưu trữ dữ liệu tập trung cho Base Station.
 * Đảm bảo tính nhất quán (transactional), chống trùng lặp (idempotent),
 * và hỗ trợ dashboard khôi phục trạng thái sau khi restart.
 */
public class BaseStationStorageService {

    private static final Logger LOGGER = Logger.getLogger(BaseStationStorageService.class.getName());

    private final DatabaseManager databaseManager;
    private final PacketRepository packetRepository;
    private final SosEventRepository sosEventRepository;
    private final NodeRepository nodeRepository;
    private final DispatchRepository dispatchRepository;
    private final RouteRepository routeRepository;

    public BaseStationStorageService(DatabaseManager databaseManager) {
        this(
            databaseManager,
            new SqlitePacketRepository(databaseManager),
            new SqliteSosEventRepository(databaseManager),
            new SqliteNodeRepository(databaseManager),
            new SqliteDispatchRepository(databaseManager),
            new SqliteRouteRepository(databaseManager)
        );
    }

    public BaseStationStorageService(DatabaseManager databaseManager,
                                     PacketRepository packetRepository,
                                     SosEventRepository sosEventRepository,
                                     NodeRepository nodeRepository,
                                     DispatchRepository dispatchRepository,
                                     RouteRepository routeRepository) {
        this.databaseManager = databaseManager;
        this.packetRepository = packetRepository;
        this.sosEventRepository = sosEventRepository;
        this.nodeRepository = nodeRepository;
        this.dispatchRepository = dispatchRepository;
        this.routeRepository = routeRepository;
    }

    /**
     * Lưu gói tin đến theo cách idempotent trong một transaction duy nhất.
     * Nếu gói tin đã tồn tại (dựa trên packet_id), bỏ qua và trả về false.
     * Nếu là gói tin mới:
     *   1. Lưu vào bảng packets.
     *   2. Nếu là SOS_BROADCAST hoặc SOS_DATA, lưu vào bảng sos_events.
     *   3. Cập nhật bảng nodes (last_seen, host, capability).
     *   4. Nếu có route_history, lưu vào bảng routes.
     *
     * @param packet Gói tin nhận được
     * @param rawJson Chuỗi JSON thô ban đầu (hoặc null để tự serialize)
     * @return true nếu lưu mới thành công, false nếu là duplicate
     */
    public boolean saveIncomingPacketIdempotent(MeshPacket packet, String rawJson) {
        if (packet == null || packet.getPacketId() == null) {
            return false;
        }

        try (Connection conn = databaseManager.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // 1. Kiểm tra duplicate
                if (packetRepository.existsById(conn, packet.getPacketId())) {
                    conn.rollback();
                    return false;
                }

                // 2. Lưu vào bảng packets
                String jsonContent = (rawJson != null && !rawJson.isEmpty()) ? rawJson : packet.toJson();
                PacketRecord packetRecord = new PacketRecord(
                    packet.getPacketId(),
                    packet.getProtocolVersion() != null ? packet.getProtocolVersion() : "1.0",
                    packet.getPacketType() != null ? packet.getPacketType() : "UNKNOWN",
                    packet.getSourceNodeId() != null ? packet.getSourceNodeId() : "",
                    packet.getDestinationNodeId() != null ? packet.getDestinationNodeId() : "",
                    packet.getSenderHopId() != null ? packet.getSenderHopId() : "",
                    packet.getTimestamp(),
                    packet.getTtl(),
                    packet.getHopCount(),
                    packet.getChecksum() != null ? packet.getChecksum() : "",
                    jsonContent,
                    "PROCESSED",
                    System.currentTimeMillis()
                );
                packetRepository.insert(conn, packetRecord);

                // 3. Nếu là SOS packet, lưu vào sos_events
                String pType = packet.getPacketType();
                if (MeshPacket.TYPE_SOS_BROADCAST.equals(pType) || MeshPacket.TYPE_SOS_DATA.equals(pType)) {
                    MeshPacket.Payload payload = packet.getPayload();
                    String senderName = payload != null ? payload.getSenderName() : null;
                    String alertType = payload != null ? payload.getAlertType() : "MEDICAL";
                    String message = payload != null ? payload.getMessage() : "";
                    int victimCount = payload != null ? payload.getVictimCount() : 1;
                    String severity = payload != null && payload.getSeverity() != null
                            ? payload.getSeverity() : MeshPacket.SEVERITY_CRITICAL;

                    Double lat = null, lon = null, alt = null, acc = null;
                    if (payload != null && payload.getLocation() != null) {
                        lat = payload.getLocation().getLatitude();
                        lon = payload.getLocation().getLongitude();
                        alt = payload.getLocation().getAltitude();
                        acc = payload.getLocation().getAccuracy();
                    }

                    SosEventRecord sosRecord = new SosEventRecord(
                        packet.getPacketId(),
                        packet.getSourceNodeId(),
                        senderName,
                        alertType,
                        message,
                        victimCount,
                        severity,
                        lat,
                        lon,
                        alt,
                        acc,
                        "PENDING",
                        packet.getTimestamp(),
                        packet.getRouteHistory() != null ? packet.getRouteHistory().toString() : null
                    );
                    sosEventRepository.insert(conn, sosRecord);
                }

                // 4. Cập nhật bảng nodes
                if (packet.getSourceNodeId() != null && !packet.getSourceNodeId().isEmpty()) {
                    NodeRecord nodeRecord = new NodeRecord(
                        packet.getSourceNodeId(),
                        packet.getTimestamp() > 0 ? packet.getTimestamp() : System.currentTimeMillis(),
                        packet.getSenderHopId(),
                        null,
                        "ACTIVE"
                    );
                    nodeRepository.upsert(conn, nodeRecord);
                }

                // 5. Nếu có route_history, lưu vào bảng routes
                if (packet.getRouteHistory() != null && !packet.getRouteHistory().isEmpty()) {
                    RouteRecord routeRecord = new RouteRecord(
                        null,
                        packet.getPacketId(),
                        packet.getSourceNodeId(),
                        packet.getRouteHistory().toString(),
                        System.currentTimeMillis(),
                        null
                    );
                    routeRepository.insert(conn, routeRecord);
                }

                conn.commit();
                return true;
            } catch (Exception e) {
                conn.rollback();
                LOGGER.log(Level.SEVERE, "Failed to save packet " + packet.getPacketId(), e);
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Database error saving packet " + packet.getPacketId(), e);
        }
    }

    /**
     * Đọc toàn bộ danh sách sự kiện SOS (phục vụ khôi phục TableView).
     */
    public List<SosEventRecord> loadAllSosEvents() {
        return sosEventRepository.findAllOrderByTimeAsc();
    }

    /**
     * Thống kê số lượng SOS và theo mức độ severity.
     */
    public SosStats getSosStats() {
        int total = sosEventRepository.countTotal();
        int critical = sosEventRepository.countBySeverity(MeshPacket.SEVERITY_CRITICAL);
        int high = sosEventRepository.countBySeverity(MeshPacket.SEVERITY_HIGH);
        int medium = sosEventRepository.countBySeverity(MeshPacket.SEVERITY_MEDIUM);
        return new SosStats(total, critical, high, medium);
    }

    /**
     * Lưu lệnh điều phối mới vào bảng dispatch_commands (hàng đợi outbox).
     */
    public void saveDispatchCommand(MeshPacket dispatchPacket, String message, String severity) {
        long now = System.currentTimeMillis();
        DispatchRecord record = new DispatchRecord(
            dispatchPacket.getPacketId(),
            dispatchPacket.getDestinationNodeId(),
            message != null ? message : "",
            severity != null ? severity : MeshPacket.SEVERITY_CRITICAL,
            "PENDING",
            0,
            now, // sẵn sàng gửi ngay
            now,
            now
        );
        dispatchRepository.insert(record);
    }

    /**
     * Cập nhật trạng thái lệnh điều phối.
     */
    public void updateDispatchStatus(String packetId, String status) {
        dispatchRepository.updateStatus(packetId, status);
    }

    /**
     * Cập nhật thông tin retry lệnh điều phối sau một lần gửi.
     */
    public void updateDispatchRetry(String packetId, String status, int attemptCount, Long nextAttemptTime) {
        dispatchRepository.updateRetry(packetId, status, attemptCount, nextAttemptTime, System.currentTimeMillis());
    }

    /**
     * Tìm kiếm lệnh điều phối theo packetId.
     */
    public Optional<DispatchRecord> findDispatch(String packetId) {
        return dispatchRepository.findById(packetId);
    }

    /**
     * Tìm các lệnh điều phối cần gửi/thử lại đến thời điểm hiện tại.
     */
    public List<DispatchRecord> findPendingOrSentDue(long nowTimestamp) {
        return dispatchRepository.findPendingOrSentDue(nowTimestamp);
    }

    /**
     * Cập nhật trạng thái cứu hộ của một sự kiện SOS.
     */
    public void updateSosRescueStatus(String packetId, String status) {
        sosEventRepository.updateRescueStatus(packetId, status);
    }

    public PacketRepository getPacketRepository() { return packetRepository; }
    public SosEventRepository getSosEventRepository() { return sosEventRepository; }
    public NodeRepository getNodeRepository() { return nodeRepository; }
    public DispatchRepository getDispatchRepository() { return dispatchRepository; }
    public RouteRepository getRouteRepository() { return routeRepository; }
    public DatabaseManager getDatabaseManager() { return databaseManager; }

    /**
     * Data object chứa thống kê SOS.
     */
    public static class SosStats {
        private final int total;
        private final int critical;
        private final int high;
        private final int medium;

        public SosStats(int total, int critical, int high, int medium) {
            this.total = total;
            this.critical = critical;
            this.high = high;
            this.medium = medium;
        }

        public int getTotal() { return total; }
        public int getCritical() { return critical; }
        public int getHigh() { return high; }
        public int getMedium() { return medium; }
    }
}
