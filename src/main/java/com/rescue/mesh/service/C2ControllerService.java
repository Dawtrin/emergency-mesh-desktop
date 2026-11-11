package com.rescue.mesh.service;

import com.rescue.mesh.db.*;
import com.rescue.mesh.model.MeshPacket;
import com.rescue.mesh.model.VictimEntity;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Dịch vụ điều phối nghiệp vụ chỉ huy trung tâm (C2 Controller Service).
 * Tương đương IncidentLifecycleService trong FINAL spec §7 — quản lý REQ→ACQ→REL (Đề 54).
 *
 * Tầng trung gian giữa UI ↔ DB ↔ Network:
 *   - Tiếp nhận SOS → tạo VictimEntity → lưu DB → trả kết quả cho UI
 *   - Phát lệnh Dispatch → lưu DB → gửi packet qua network
 *   - Đóng ca cứu nạn (Resolve) → cập nhật DB → thông báo UI
 *   - Xử lý Heartbeat → cập nhật relay node topology
 *
 * Design Pattern: Facade + Service Layer
 *   - Gom toàn bộ nghiệp vụ phức tạp vào 1 service duy nhất
 *   - UI chỉ cần gọi 1 method thay vì thao tác nhiều DAO
 */
public class C2ControllerService {

    private final VictimDao victimDao;
    private final PacketDao packetDao;
    private final DispatchDao dispatchDao;
    private final RelayNodeDao relayNodeDao;
    private final DatabaseManager dbManager;

    /** Danh sách incident đang hoạt động — cache in-memory cho hiệu năng UI */
    private final CopyOnWriteArrayList<VictimEntity> activeIncidents = new CopyOnWriteArrayList<>();

    public C2ControllerService(DatabaseManager dbManager) {
        this.dbManager = dbManager;
        this.victimDao = new VictimDao(dbManager);
        this.packetDao = new PacketDao(dbManager);
        this.dispatchDao = new DispatchDao(dbManager);
        this.relayNodeDao = new RelayNodeDao(dbManager);
    }

    // =========================================================
    // XỬ LÝ SOS — PENDING (REQ)
    // =========================================================

    /**
     * Tiếp nhận gói tin SOS mới:
     *   1. Lưu gói tin vào mesh_packets (Evidence Log)
     *   2. Tạo hoặc cập nhật victim trong DB
     *   3. Tạo VictimEntity in-memory
     *
     * @param packet Gói tin SOS đã nhận
     * @return VictimEntity đã tạo
     */
    public VictimEntity processSosPacket(MeshPacket packet) {
        // 1. Lưu gói tin vào evidence log
        packetDao.insert(packet);

        // 2. Tạo entity
        VictimEntity entity = VictimEntity.fromSosPacket(packet);

        // 3. Lưu vào DB victims (kèm GPS fix info & auto-update hồ sơ y tế)
        victimDao.insertOrUpdate(
                entity.getNodeId(),
                entity.getFullName(),
                entity.getPhoneNumber(),
                entity.getBloodType(),
                entity.getMedicalNotes(),
                entity.getVictimCount(),
                entity.getAlertType(),
                entity.getSeverity(),
                entity.getLatitude(),
                entity.getLongitude(),
                entity.getHasFix(),
                entity.getAccuracyM(),
                entity.getFixAgeMs()
        );

        // 4. Cache in-memory
        // Kiểm tra nếu đã có entity cho node này → cập nhật
        boolean updated = false;
        for (int i = 0; i < activeIncidents.size(); i++) {
            if (activeIncidents.get(i).getNodeId().equals(entity.getNodeId())) {
                activeIncidents.set(i, entity);
                updated = true;
                break;
            }
        }
        if (!updated) {
            activeIncidents.add(entity);
        }

        // 5. Cập nhật relay nodes từ route history
        if (packet.getRouteHistory() != null) {
            for (String relayNodeId : packet.getRouteHistory()) {
                if (!MeshPacket.NODE_BASE_STATION.equals(relayNodeId)) {
                    relayNodeDao.insertOrUpdate(relayNodeId, relayNodeId, "auto-detected", 0);
                    relayNodeDao.incrementPacketsForwarded(relayNodeId);
                }
            }
        }

        System.out.println("[C2] Victim tạo mới: " + entity);

        // 6. Ghi vết sự kiện dòng thời gian Dossier Timeline (Append-Only)
        dbManager.getIncidentEventDao().record(
                entity.getNodeId(),
                1,
                "SOS_RECEIVED",
                entity.getFullName(),
                entity.getMedicalNotes(),
                entity.getLatitude(),
                entity.getLongitude(),
                false
        );

        return entity;
    }

    // =========================================================
    // ĐIỀU PHỐI — DISPATCHED (ACQ)
    // =========================================================

    /**
     * Phát lệnh điều phối cứu nạn:
     *   1. Lưu lệnh vào dispatch_logs
     *   2. Cập nhật trạng thái victim → DISPATCHED
     *   3. Cập nhật entity in-memory
     *
     * @param targetNodeId ID nạn nhân
     * @param commandMessage Nội dung lệnh
     * @param senderName Tên người phát lệnh
     * @return dispatch_id đã tạo
     */
    public String dispatchRescue(String targetNodeId, String commandMessage, String senderName) {
        // 1. Lưu lệnh
        String dispatchId = dispatchDao.insert(targetNodeId, commandMessage, senderName);

        // 2. Cập nhật DB
        victimDao.updateStatus(targetNodeId, "DISPATCHED");

        // Ghi nhận sự kiện Timeline
        dbManager.getIncidentEventDao().record(
                targetNodeId, 1, "DISPATCH_SENT", senderName,
                commandMessage, null, null, false
        );

        // 3. Cập nhật in-memory
        for (VictimEntity entity : activeIncidents) {
            if (entity.getNodeId().equals(targetNodeId)) {
                entity.dispatch();
                break;
            }
        }

        System.out.println("[C2] Dispatch → " + targetNodeId + ": " + commandMessage);
        return dispatchId;
    }

    // =========================================================
    // HOÀN TẤT — RESOLVED (REL)
    // =========================================================

    /**
     * Đóng ca cứu nạn:
     *   1. Cập nhật trạng thái victim → RESOLVED
     *   2. Cập nhật entity in-memory
     *
     * @param nodeId ID nạn nhân
     */
    public void resolveIncident(String nodeId) {
        victimDao.updateStatus(nodeId, "RESOLVED");

        // Ghi nhận sự kiện Timeline
        dbManager.getIncidentEventDao().record(
                nodeId, 1, "RESOLVED", "Chỉ Huy Trưởng C2",
                "Ca cứu nạn hoàn tất thành công", null, null, false
        );

        for (VictimEntity entity : activeIncidents) {
            if (entity.getNodeId().equals(nodeId)) {
                entity.resolve();
                break;
            }
        }

        System.out.println("[C2] Resolved: " + nodeId);
    }

    // =========================================================
    // HEARTBEAT
    // =========================================================

    /**
     * Xử lý gói tin heartbeat — cập nhật relay topology.
     */
    public void processHeartbeat(MeshPacket packet) {
        packetDao.insert(packet);
        String nodeId = packet.getSourceNodeId();
        relayNodeDao.insertOrUpdate(nodeId, nodeId, "auto-detected", 0);
        relayNodeDao.updateHeartbeat(nodeId);
        System.out.println("[C2] Heartbeat nhận từ: " + nodeId);
    }

    /**
     * Kiểm tra và đánh dấu relay nodes offline.
     */
    public void checkRelayTimeouts() {
        relayNodeDao.markOfflineNodes();
    }

    // =========================================================
    // QUERIES
    // =========================================================

    public List<VictimEntity> getActiveIncidents() { return List.copyOf(activeIncidents); }
    public VictimDao getVictimDao() { return victimDao; }
    public PacketDao getPacketDao() { return packetDao; }
    public DispatchDao getDispatchDao() { return dispatchDao; }
    public RelayNodeDao getRelayNodeDao() { return relayNodeDao; }

    public int getPendingCount() { return victimDao.countByStatus("PENDING"); }
    public int getDispatchedCount() { return victimDao.countByStatus("DISPATCHED"); }
    public int getResolvedCount() { return victimDao.countByStatus("RESOLVED"); }
}
