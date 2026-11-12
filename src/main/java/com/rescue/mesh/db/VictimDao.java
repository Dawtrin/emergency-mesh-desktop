package com.rescue.mesh.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object cho bảng victims — Hồ sơ nạn nhân.
 *
 * Quản lý vòng đời cứu nạn 3 pha (Đề 54):
 *   PENDING (REQ) → DISPATCHED (ACQ) → RESOLVED (REL)
 *
 * Thread Safety: Mỗi method lấy Connection riêng từ pool → an toàn multi-thread.
 */
public class VictimDao {

    private final DatabaseManager dbManager;

    public VictimDao(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    /**
     * Chèn hoặc cập nhật hồ sơ nạn nhân (hỗ trợ đầy đủ GPS metadata & hồi phục trạng thái).
     */
    public void insertOrUpdate(String nodeId, String fullName, String phone,
                                String bloodType, String medicalNotes,
                                int victimCount, String alertType, String severity,
                                double latitude, double longitude,
                                Boolean hasFix, Integer accuracyM, Long fixAgeMs) {
        String sql = """
            INSERT INTO victims (node_id, full_name, phone_number, blood_type, medical_notes,
                                  victim_count, alert_type, severity, latitude, longitude,
                                  has_fix, accuracy_m, fix_age_ms,
                                  status, first_seen_timestamp, last_updated_timestamp)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDING', ?, ?)
            ON CONFLICT(node_id) DO UPDATE SET
                full_name = excluded.full_name,
                phone_number = excluded.phone_number,
                blood_type = excluded.blood_type,
                medical_notes = excluded.medical_notes,
                victim_count = excluded.victim_count,
                alert_type = excluded.alert_type,
                severity = excluded.severity,
                latitude = excluded.latitude,
                longitude = excluded.longitude,
                has_fix = excluded.has_fix,
                accuracy_m = excluded.accuracy_m,
                fix_age_ms = excluded.fix_age_ms,
                status = CASE WHEN victims.status = 'RESOLVED' THEN 'PENDING' ELSE victims.status END,
                last_updated_timestamp = excluded.last_updated_timestamp
        """;

        long now = System.currentTimeMillis();
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, nodeId);
            ps.setString(2, fullName);
            ps.setString(3, phone);
            ps.setString(4, bloodType != null ? bloodType : "O+");
            ps.setString(5, medicalNotes);
            ps.setInt(6, victimCount);
            ps.setString(7, alertType);
            ps.setString(8, severity);
            ps.setDouble(9, latitude);
            ps.setDouble(10, longitude);
            if (hasFix != null) ps.setInt(11, hasFix ? 1 : 0); else ps.setNull(11, java.sql.Types.INTEGER);
            if (accuracyM != null) ps.setInt(12, accuracyM); else ps.setNull(12, java.sql.Types.INTEGER);
            if (fixAgeMs != null) ps.setLong(13, fixAgeMs); else ps.setNull(13, java.sql.Types.INTEGER);
            ps.setLong(14, now);
            ps.setLong(15, now);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[DB] VictimDao.insertOrUpdate error: " + e.getMessage());
        }
    }

    /**
     * Overload tương thích ngược cho các bộ test và simulator cũ.
     */
    public void insertOrUpdate(String nodeId, String fullName, String phone,
                                String bloodType, String medicalNotes,
                                int victimCount, String alertType, String severity,
                                double latitude, double longitude) {
        insertOrUpdate(nodeId, fullName, phone, bloodType, medicalNotes,
                victimCount, alertType, severity, latitude, longitude,
                true, null, null);
    }

    /**
     * Cập nhật trạng thái cứu nạn (Đề 54: REQ → ACQ → REL).
     *
     * @param nodeId ID nạn nhân
     * @param status PENDING / DISPATCHED / RESOLVED
     */
    public void updateStatus(String nodeId, String status) {
        String sql = "UPDATE victims SET status = ?, last_updated_timestamp = ? WHERE node_id = ?";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setLong(2, System.currentTimeMillis());
            ps.setString(3, nodeId);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[DB] VictimDao.updateStatus error: " + e.getMessage());
        }
    }

    /**
     * Lấy toàn bộ danh sách nạn nhân.
     */
    public List<VictimRecord> findAll() {
        String sql = """
            SELECT * FROM victims 
            ORDER BY 
                CASE status WHEN 'PENDING' THEN 1 WHEN 'DISPATCHED' THEN 2 ELSE 3 END,
                CASE severity WHEN 'CRITICAL' THEN 1 WHEN 'HIGH' THEN 2 ELSE 3 END,
                last_updated_timestamp DESC
        """;
        List<VictimRecord> results = new ArrayList<>();
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                results.add(mapRow(rs));
            }
        } catch (SQLException e) {
            System.err.println("[DB] VictimDao.findAll error: " + e.getMessage());
        }
        return results;
    }

    /**
     * Lấy danh sách nạn nhân theo trạng thái.
     */
    public List<VictimRecord> findByStatus(String status) {
        String sql = "SELECT * FROM victims WHERE status = ? ORDER BY last_updated_timestamp DESC";
        List<VictimRecord> results = new ArrayList<>();
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            System.err.println("[DB] VictimDao.findByStatus error: " + e.getMessage());
        }
        return results;
    }

    /**
     * Tra cứu hồ sơ nạn nhân theo từ khóa (tên, SĐT, nodeId, bệnh nền), nhóm máu và trạng thái.
     */
    public List<VictimRecord> search(String query, String bloodType, String status) {
        StringBuilder sql = new StringBuilder("SELECT * FROM victims WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (query != null && !query.isBlank()) {
            String q = "%" + query.trim().toLowerCase() + "%";
            sql.append(" AND (LOWER(full_name) LIKE ? OR LOWER(phone_number) LIKE ? OR LOWER(node_id) LIKE ? OR LOWER(medical_notes) LIKE ?)");
            params.add(q);
            params.add(q);
            params.add(q);
            params.add(q);
        }

        if (bloodType != null && !bloodType.isBlank() && !"ALL".equalsIgnoreCase(bloodType)) {
            sql.append(" AND blood_type = ?");
            params.add(bloodType.trim());
        }

        if (status != null && !status.isBlank() && !"ALL".equalsIgnoreCase(status)) {
            sql.append(" AND status = ?");
            params.add(status.trim().toUpperCase());
        }

        sql.append(" ORDER BY last_updated_timestamp DESC");

        List<VictimRecord> results = new ArrayList<>();
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            System.err.println("[DB] VictimDao.search error: " + e.getMessage());
        }
        return results;
    }

    /**
     * Đếm số nạn nhân theo trạng thái.
     */
    public int countByStatus(String status) {
        String sql = "SELECT COUNT(*) FROM victims WHERE status = ?";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            System.err.println("[DB] VictimDao.countByStatus error: " + e.getMessage());
        }
        return 0;
    }

    /**
     * Tìm nạn nhân theo node_id.
     */
    public VictimRecord findById(String nodeId) {
        String sql = "SELECT * FROM victims WHERE node_id = ?";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, nodeId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        } catch (SQLException e) {
            System.err.println("[DB] VictimDao.findById error: " + e.getMessage());
        }
        return null;
    }

    private VictimRecord mapRow(ResultSet rs) throws SQLException {
        Object fixObj = rs.getObject("has_fix");
        Boolean hasFix = fixObj instanceof Number n ? n.intValue() == 1 : null;

        Object accObj = rs.getObject("accuracy_m");
        Integer accuracyM = accObj instanceof Number n ? n.intValue() : null;

        Object fixAgeObj = rs.getObject("fix_age_ms");
        Long fixAgeMs = fixAgeObj instanceof Number n ? n.longValue() : null;

        return new VictimRecord(
                rs.getString("node_id"),
                rs.getString("full_name"),
                rs.getString("phone_number"),
                rs.getString("blood_type"),
                rs.getString("medical_notes"),
                rs.getInt("victim_count"),
                rs.getString("alert_type"),
                rs.getString("severity"),
                rs.getDouble("latitude"),
                rs.getDouble("longitude"),
                hasFix,
                accuracyM,
                fixAgeMs,
                rs.getString("status"),
                rs.getString("dispatched_team"),
                rs.getLong("first_seen_timestamp"),
                rs.getLong("last_updated_timestamp")
        );
    }

    /** Record lưu trữ dữ liệu nạn nhân từ DB */
    public record VictimRecord(
            String nodeId, String fullName, String phone, String bloodType,
            String medicalNotes, int victimCount, String alertType, String severity,
            double latitude, double longitude, Boolean hasFix, Integer accuracyM, Long fixAgeMs,
            String status, String dispatchedTeam,
            long firstSeen, long lastUpdated
    ) {}
}
