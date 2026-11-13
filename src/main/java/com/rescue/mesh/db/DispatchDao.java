package com.rescue.mesh.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Data Access Object cho bảng dispatch_logs — Lịch sử lệnh điều phối.
 * Theo dõi toàn bộ lệnh chỉ đạo từ Trạm trưởng + trạng thái ACK.
 */
public class DispatchDao {

    private final DatabaseManager dbManager;

    public DispatchDao(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    /**
     * Lưu lệnh điều phối mới.
     * @return dispatch_id đã tạo
     */
    public String insert(String targetNodeId, String commandMessage, String senderName) {
        String dispatchId = UUID.randomUUID().toString();
        String sql = """
            INSERT INTO dispatch_logs (dispatch_id, target_node_id, command_message, sender_name, dispatched_at)
            VALUES (?, ?, ?, ?, ?)
        """;
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, dispatchId);
            ps.setString(2, targetNodeId);
            ps.setString(3, commandMessage);
            ps.setString(4, senderName);
            ps.setLong(5, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[DB] DispatchDao.insert error: " + e.getMessage());
        }
        return dispatchId;
    }

    /**
     * Cập nhật ACK khi nhận được xác nhận từ node nạn nhân.
     */
    public void updateAck(String dispatchId) {
        String sql = "UPDATE dispatch_logs SET ack_received = 1, ack_timestamp = ? WHERE dispatch_id = ?";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, System.currentTimeMillis());
            ps.setString(2, dispatchId);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[DB] DispatchDao.updateAck error: " + e.getMessage());
        }
    }

    /**
     * Lấy lịch sử lệnh theo node đích.
     */
    public List<DispatchRecord> findByTarget(String targetNodeId) {
        String sql = "SELECT * FROM dispatch_logs WHERE target_node_id = ? ORDER BY dispatched_at DESC";
        List<DispatchRecord> results = new ArrayList<>();
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, targetNodeId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) results.add(mapRow(rs));
            }
        } catch (SQLException e) {
            System.err.println("[DB] DispatchDao.findByTarget error: " + e.getMessage());
        }
        return results;
    }

    /**
     * Lấy toàn bộ lịch sử lệnh.
     */
    public List<DispatchRecord> findAll() {
        String sql = "SELECT * FROM dispatch_logs ORDER BY dispatched_at DESC";
        List<DispatchRecord> results = new ArrayList<>();
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) results.add(mapRow(rs));
        } catch (SQLException e) {
            System.err.println("[DB] DispatchDao.findAll error: " + e.getMessage());
        }
        return results;
    }

    /**
     * Đếm lệnh chưa nhận ACK.
     */
    public int countPendingAck() {
        String sql = "SELECT COUNT(*) FROM dispatch_logs WHERE ack_received = 0";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            System.err.println("[DB] DispatchDao.countPendingAck error: " + e.getMessage());
        }
        return 0;
    }

    /**
     * Lấy các lệnh điều phối chưa nhận được ACK để nạp lại vào hàng đợi khi khởi động máy chủ (RAM recovery).
     */
    public List<DispatchRecord> findPendingDispatches() {
        String sql = "SELECT * FROM dispatch_logs WHERE ack_received = 0 ORDER BY dispatched_at ASC";
        List<DispatchRecord> results = new ArrayList<>();
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) results.add(mapRow(rs));
        } catch (SQLException e) {
            System.err.println("[DB] DispatchDao.findPendingDispatches error: " + e.getMessage());
        }
        return results;
    }

    /**
     * Lấy N lệnh gần nhất dưới dạng Map (phục vụ ReportExportService audit trail).
     */
    public java.util.List<java.util.Map<String, Object>> findRecentAsMap(int limit) {
        String sql = "SELECT * FROM dispatch_logs ORDER BY dispatched_at DESC LIMIT ?";
        java.util.List<java.util.Map<String, Object>> results = new java.util.ArrayList<>();
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    java.util.Map<String, Object> row = new java.util.LinkedHashMap<>();
                    row.put("dispatchId", rs.getString("dispatch_id"));
                    row.put("targetNodeId", rs.getString("target_node_id"));
                    row.put("commandMessage", rs.getString("command_message"));
                    row.put("senderName", rs.getString("sender_name"));
                    row.put("dispatchedAt", rs.getLong("dispatched_at"));
                    row.put("ackReceived", rs.getInt("ack_received") == 1);
                    results.add(row);
                }
            }
        } catch (SQLException e) {
            System.err.println("[DB] DispatchDao.findRecentAsMap error: " + e.getMessage());
        }
        return results;
    }

    private DispatchRecord mapRow(ResultSet rs) throws SQLException {
        return new DispatchRecord(
                rs.getString("dispatch_id"),
                rs.getString("target_node_id"),
                rs.getString("command_message"),
                rs.getString("sender_name"),
                rs.getLong("dispatched_at"),
                rs.getInt("ack_received") == 1,
                rs.getLong("ack_timestamp")
        );
    }

    public record DispatchRecord(
            String dispatchId, String targetNodeId, String commandMessage,
            String senderName, long dispatchedAt, boolean ackReceived, long ackTimestamp
    ) {}
}
