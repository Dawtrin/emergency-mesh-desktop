package com.rescue.mesh.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object cho bảng relay_nodes — Danh sách trạm tiếp sóng.
 * Theo dõi heartbeat và trạng thái online/offline của các relay node.
 */
public class RelayNodeDao {

    private final DatabaseManager dbManager;

    /** Timeout heartbeat (ms) — nếu node không gửi heartbeat trong thời gian này → TIMEOUT */
    private static final long HEARTBEAT_TIMEOUT_MS = 30_000;

    public RelayNodeDao(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    /**
     * Thêm hoặc cập nhật relay node khi nhận heartbeat.
     */
    public void insertOrUpdate(String nodeId, String nodeName, String ipAddress, int port) {
        String sql = """
            INSERT INTO relay_nodes (node_id, node_name, ip_address, port, last_heartbeat_timestamp, status)
            VALUES (?, ?, ?, ?, ?, 'ONLINE')
            ON CONFLICT(node_id) DO UPDATE SET
                node_name = excluded.node_name,
                ip_address = excluded.ip_address,
                port = excluded.port,
                last_heartbeat_timestamp = excluded.last_heartbeat_timestamp,
                status = 'ONLINE'
        """;
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, nodeId);
            ps.setString(2, nodeName != null ? nodeName : nodeId);
            ps.setString(3, ipAddress);
            ps.setInt(4, port);
            ps.setLong(5, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[DB] RelayNodeDao.insertOrUpdate error: " + e.getMessage());
        }
    }

    /**
     * Cập nhật thời gian heartbeat gần nhất.
     */
    public void updateHeartbeat(String nodeId) {
        String sql = "UPDATE relay_nodes SET last_heartbeat_timestamp = ?, status = 'ONLINE' WHERE node_id = ?";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, System.currentTimeMillis());
            ps.setString(2, nodeId);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[DB] RelayNodeDao.updateHeartbeat error: " + e.getMessage());
        }
    }

    /**
     * Tăng bộ đếm số gói tin đã chuyển tiếp.
     */
    public void incrementPacketsForwarded(String nodeId) {
        String sql = "UPDATE relay_nodes SET packets_forwarded_count = packets_forwarded_count + 1 WHERE node_id = ?";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, nodeId);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[DB] RelayNodeDao.incrementPacketsForwarded error: " + e.getMessage());
        }
    }

    /**
     * Đánh dấu các node quá hạn heartbeat thành TIMEOUT.
     */
    public void markOfflineNodes() {
        String sql = "UPDATE relay_nodes SET status = 'TIMEOUT' WHERE status = 'ONLINE' AND (? - last_heartbeat_timestamp) > ?";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, System.currentTimeMillis());
            ps.setLong(2, HEARTBEAT_TIMEOUT_MS);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[DB] RelayNodeDao.markOfflineNodes error: " + e.getMessage());
        }
    }

    /**
     * Lấy toàn bộ danh sách relay nodes.
     */
    public List<RelayNodeRecord> findAll() {
        String sql = "SELECT * FROM relay_nodes ORDER BY last_heartbeat_timestamp DESC";
        List<RelayNodeRecord> results = new ArrayList<>();
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) results.add(mapRow(rs));
        } catch (SQLException e) {
            System.err.println("[DB] RelayNodeDao.findAll error: " + e.getMessage());
        }
        return results;
    }

    /**
     * Đếm số node đang ONLINE.
     */
    public int countOnline() {
        String sql = "SELECT COUNT(*) FROM relay_nodes WHERE status = 'ONLINE'";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            System.err.println("[DB] RelayNodeDao.countOnline error: " + e.getMessage());
        }
        return 0;
    }

    private RelayNodeRecord mapRow(ResultSet rs) throws SQLException {
        return new RelayNodeRecord(
                rs.getString("node_id"),
                rs.getString("node_name"),
                rs.getString("ip_address"),
                rs.getInt("port"),
                rs.getLong("last_heartbeat_timestamp"),
                rs.getInt("packets_forwarded_count"),
                rs.getString("status")
        );
    }

    /**
     * Lấy toàn bộ relay nodes dưới dạng Map (phục vụ WebSocket broadcast).
     */
    public java.util.List<java.util.Map<String, Object>> findAllAsMap() {
        String sql = "SELECT * FROM relay_nodes ORDER BY last_heartbeat_timestamp DESC";
        java.util.List<java.util.Map<String, Object>> results = new java.util.ArrayList<>();
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                java.util.Map<String, Object> row = new java.util.LinkedHashMap<>();
                row.put("nodeId", rs.getString("node_id"));
                row.put("nodeName", rs.getString("node_name"));
                row.put("ipAddress", rs.getString("ip_address"));
                row.put("port", rs.getInt("port"));
                row.put("lastHeartbeat", rs.getLong("last_heartbeat_timestamp"));
                row.put("packetsForwarded", rs.getInt("packets_forwarded_count"));
                row.put("status", rs.getString("status"));
                results.add(row);
            }
        } catch (SQLException e) {
            System.err.println("[DB] RelayNodeDao.findAllAsMap error: " + e.getMessage());
        }
        return results;
    }

    public record RelayNodeRecord(
            String nodeId, String nodeName, String ipAddress, int port,
            long lastHeartbeat, int packetsForwarded, String status
    ) {}
}
