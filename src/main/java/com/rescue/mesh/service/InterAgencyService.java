package com.rescue.mesh.service;

import com.rescue.mesh.db.DatabaseManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

/**
 * Service chuyển tiếp dữ liệu sang Cổng Liên Ngành 115 / 114 / 113.
 * Quản trị vòng đời 4 bước (DISPATCHED → EN_ROUTE → ON_SCENE → HANDOVER),
 * điều phối dung lượng 15 cơ sở dã chiến và nhật ký đàm thoại VHF CH16.
 */
public class InterAgencyService {

    private static final Logger LOGGER = LoggerFactory.getLogger(InterAgencyService.class);
    private final DatabaseManager dbManager;

    public InterAgencyService(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    /**
     * Chuyển tiếp hồ sơ / Lập phiếu điều phối liên ngành
     */
    public long dispatchToAgency(String victimNodeId, String agencyType,
                                  String agencyName, String payloadJson) {
        return createMissionTicket(victimNodeId, agencyType, agencyName, payloadJson,
                "Phương tiện tiêu chuẩn", "Cấp cứu / Hỗ trợ khẩn cấp", "HIGH");
    }

    /**
     * Lập Phiếu Điều Phối Điện Tử Đầy Đủ
     */
    public long createMissionTicket(String victimNodeId, String agencyType,
                                    String agencyName, String payloadJson,
                                    String requiredVehicle, String specialNeeds, String priority) {
        String sql = """
            INSERT INTO interagency_dispatches 
            (victim_node_id, agency_type, agency_name, payload_sent, dispatched_at, status,
             responder_unit, responder_leader, contact_phone, eta_minutes, special_needs, updated_at)
            VALUES (?, ?, ?, ?, ?, 'DISPATCHED', ?, '', '', 10, ?, ?)
        """;

        long now = System.currentTimeMillis();
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, victimNodeId);
            ps.setString(2, agencyType);
            ps.setString(3, agencyName);
            ps.setString(4, payloadJson);
            ps.setLong(5, now);
            ps.setString(6, requiredVehicle);
            ps.setString(7, specialNeeds);
            ps.setLong(8, now);
            ps.executeUpdate();

            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    long id = rs.getLong(1);
                    LOGGER.info("[INTERAGENCY] Phiếu điều phối #{} tạo thành công: {} → {} ({})",
                            id, victimNodeId, agencyType, agencyName);
                    return id;
                }
            }
        } catch (SQLException e) {
            LOGGER.error("[INTERAGENCY] Lỗi tạo phiếu: {}", e.getMessage());
        }
        return -1;
    }

    /**
     * Chuyển bước tiến trình xử lý (Lifecycle: DISPATCHED → EN_ROUTE → ON_SCENE → HANDOVER)
     */
    public boolean advanceTicketStatus(long ticketId, String nextStatus,
                                       String unit, String leader, String phone,
                                       Integer eta) {
        String sql = """
            UPDATE interagency_dispatches
            SET status = ?,
                responder_unit = COALESCE(NULLIF(?, ''), responder_unit),
                responder_leader = COALESCE(NULLIF(?, ''), responder_leader),
                contact_phone = COALESCE(NULLIF(?, ''), contact_phone),
                eta_minutes = COALESCE(?, eta_minutes),
                updated_at = ?
            WHERE id = ?
        """;

        long now = System.currentTimeMillis();
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, nextStatus);
            ps.setString(2, unit);
            ps.setString(3, leader);
            ps.setString(4, phone);
            if (eta != null) ps.setInt(5, eta); else ps.setNull(5, java.sql.Types.INTEGER);
            ps.setLong(6, now);
            ps.setLong(7, ticketId);
            int rows = ps.executeUpdate();

            // Nếu bàn giao xong (HANDOVER): Tự động trừ sức chứa viện và hoàn tất ca victim
            if (rows > 0 && "HANDOVER".equalsIgnoreCase(nextStatus)) {
                handleHandoverCompletion(ticketId, conn);
            }

            return rows > 0;
        } catch (SQLException e) {
            LOGGER.error("[INTERAGENCY] Lỗi cập nhật tiến độ phiếu #{}: {}", ticketId, e.getMessage());
            return false;
        }
    }

    private void handleHandoverCompletion(long ticketId, Connection conn) {
        String query = "SELECT victim_node_id, agency_name FROM interagency_dispatches WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setLong(1, ticketId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String victimNodeId = rs.getString("victim_node_id");
                    String agencyName = rs.getString("agency_name");

                    // 1. Cập nhật victim status → RESOLVED
                    try (PreparedStatement uVictim = conn.prepareStatement("UPDATE victims SET status = 'RESOLVED' WHERE node_id = ?")) {
                        uVictim.setString(1, victimNodeId);
                        uVictim.executeUpdate();
                    }

                    // 2. Giảm sức chứa bệnh viện/đội PCCC
                    try (PreparedStatement uCap = conn.prepareStatement("UPDATE emergency_facilities SET available_capacity = MAX(0, available_capacity - 1) WHERE facility_name = ?")) {
                        uCap.setString(1, agencyName);
                        uCap.executeUpdate();
                    }
                    LOGGER.info("[INTERAGENCY] Bàn giao hoàn tất #{} → Nạn nhân {} đã RESOLVED, trừ sức chứa tại {}", ticketId, victimNodeId, agencyName);
                }
            }
        } catch (SQLException e) {
            LOGGER.warn("[INTERAGENCY] Không thể cập nhật handover: {}", e.getMessage());
        }
    }

    /** Lấy danh sách toàn bộ phiếu điều phối liên ngành */
    public List<Map<String, Object>> getTickets() {
        String sql = "SELECT * FROM interagency_dispatches ORDER BY dispatched_at DESC LIMIT 100";
        List<Map<String, Object>> results = new ArrayList<>();
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", rs.getLong("id"));
                row.put("victimNodeId", rs.getString("victim_node_id"));
                row.put("agencyType", rs.getString("agency_type"));
                row.put("agencyName", rs.getString("agency_name"));
                row.put("payloadSent", rs.getString("payload_sent"));
                row.put("dispatchedAt", rs.getLong("dispatched_at"));
                row.put("status", rs.getString("status"));
                row.put("responderUnit", rs.getString("responder_unit"));
                row.put("responderLeader", rs.getString("responder_leader"));
                row.put("contactPhone", rs.getString("contact_phone"));
                row.put("etaMinutes", rs.getInt("eta_minutes"));
                row.put("specialNeeds", rs.getString("special_needs"));
                row.put("updatedAt", rs.getLong("updated_at"));
                results.add(row);
            }
        } catch (SQLException e) {
            LOGGER.error("[INTERAGENCY] Lỗi query tickets: {}", e.getMessage());
        }
        return results;
    }

    /** Lấy lịch sử chuyển tiếp tương thích ngược API cũ */
    public List<Map<String, Object>> getHistory() {
        return getTickets();
    }

    /** Lấy danh sách cơ sở cấp cứu / an ninh dã chiến (GIS) */
    public List<Map<String, Object>> getFacilities() {
        String sql = "SELECT * FROM emergency_facilities ORDER BY facility_type, facility_name";
        List<Map<String, Object>> results = new ArrayList<>();
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("facilityId", rs.getString("facility_id"));
                row.put("facilityName", rs.getString("facility_name"));
                row.put("facilityType", rs.getString("facility_type"));
                row.put("latitude", rs.getDouble("latitude"));
                row.put("longitude", rs.getDouble("longitude"));
                row.put("contactPhone", rs.getString("contact_phone"));
                row.put("availableCapacity", rs.getInt("available_capacity"));
                row.put("address", rs.getString("address"));
                row.put("district", rs.getString("district"));
                row.put("imageUrl", rs.getString("image_url"));
                results.add(row);
            }
        } catch (SQLException e) {
            LOGGER.error("[INTERAGENCY] Lỗi query facilities: {}", e.getMessage());
        }
        return results;
    }

    /** Điều chỉnh sức chứa giường bệnh / ca-nô dã chiến (+ hoặc -) */
    public boolean updateFacilityCapacity(String facilityId, int delta) {
        String sql = "UPDATE emergency_facilities SET available_capacity = MAX(0, available_capacity + ?) WHERE facility_id = ?";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, delta);
            ps.setString(2, facilityId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOGGER.error("[INTERAGENCY] Lỗi đổi sức chứa {}: {}", facilityId, e.getMessage());
            return false;
        }
    }

    /** Lấy nhật ký đàm thoại bộ đàm VHF CH16 */
    public List<Map<String, Object>> getRadioLogs() {
        String sql = "SELECT * FROM tactical_radio_logs ORDER BY timestamp DESC LIMIT 60";
        List<Map<String, Object>> results = new ArrayList<>();
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", rs.getLong("id"));
                row.put("timestamp", rs.getLong("timestamp"));
                row.put("channel", rs.getString("channel"));
                row.put("sender", rs.getString("sender"));
                row.put("recipient", rs.getString("recipient"));
                row.put("message", rs.getString("message"));
                row.put("urgency", rs.getString("urgency"));
                results.add(row);
            }
        } catch (SQLException e) {
            LOGGER.error("[INTERAGENCY] Lỗi query radio logs: {}", e.getMessage());
        }
        return results;
    }

    /** Bắn khẩu lệnh mới qua Kênh Bộ đàm dã chiến */
    public boolean logRadioTransmission(String channel, String sender, String recipient,
                                        String message, String urgency) {
        String sql = "INSERT INTO tactical_radio_logs (timestamp, channel, sender, recipient, message, urgency) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, System.currentTimeMillis());
            ps.setString(2, channel != null && !channel.isBlank() ? channel : "CH 16 (156.8 MHz)");
            ps.setString(3, sender != null && !sender.isBlank() ? sender : "C2-COMMAND");
            ps.setString(4, recipient != null && !recipient.isBlank() ? recipient : "ALL_STATIONS");
            ps.setString(5, message);
            ps.setString(6, urgency != null ? urgency : "ROUTINE");
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOGGER.error("[INTERAGENCY] Lỗi ghi đàm thoại VHF: {}", e.getMessage());
            return false;
        }
    }
}
