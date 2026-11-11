package com.rescue.mesh.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object cho bảng network_metrics — Giám sát tải mạng (Đề 53).
 * Ghi nhận số liệu băng thông, tỷ lệ trùng lặp, và lưu lượng theo chu kỳ.
 */
public class MetricDao {

    private final DatabaseManager dbManager;

    public MetricDao(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    /**
     * Ghi nhận một bản ghi metric mới.
     */
    public void insertMetric(int packetsReceived, int packetsForwarded,
                              int packetsDropped, int duplicateDrops,
                              int activeRelayCount) {
        String sql = """
            INSERT INTO network_metrics
            (timestamp, packets_received, packets_forwarded, packets_dropped, duplicate_drops, active_relay_count)
            VALUES (?, ?, ?, ?, ?, ?)
        """;
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, System.currentTimeMillis());
            ps.setInt(2, packetsReceived);
            ps.setInt(3, packetsForwarded);
            ps.setInt(4, packetsDropped);
            ps.setInt(5, duplicateDrops);
            ps.setInt(6, activeRelayCount);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[DB] MetricDao.insertMetric error: " + e.getMessage());
        }
    }

    /**
     * Lấy N bản ghi metric gần nhất (cho biểu đồ LineChart).
     */
    public List<MetricRecord> findRecent(int limit) {
        String sql = "SELECT * FROM network_metrics ORDER BY timestamp DESC LIMIT ?";
        List<MetricRecord> results = new ArrayList<>();
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) results.add(mapRow(rs));
            }
        } catch (SQLException e) {
            System.err.println("[DB] MetricDao.findRecent error: " + e.getMessage());
        }
        // Reverse để thời gian cũ ở đầu (cho biểu đồ)
        List<MetricRecord> reversed = new ArrayList<>(results.size());
        for (int i = results.size() - 1; i >= 0; i--) reversed.add(results.get(i));
        return reversed;
    }

    /**
     * Lấy tổng thống kê relay load (packets forwarded per relay node).
     */
    public List<RelayLoadStat> getRelayLoadStats() {
        String sql = """
            SELECT node_id, node_name, packets_forwarded_count, status
            FROM relay_nodes
            ORDER BY packets_forwarded_count DESC
        """;
        List<RelayLoadStat> results = new ArrayList<>();
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                results.add(new RelayLoadStat(
                        rs.getString("node_id"),
                        rs.getString("node_name"),
                        rs.getInt("packets_forwarded_count"),
                        rs.getString("status")
                ));
            }
        } catch (SQLException e) {
            System.err.println("[DB] MetricDao.getRelayLoadStats error: " + e.getMessage());
        }
        return results;
    }

    /**
     * Tính tỷ lệ duplicate drop rate tổng thể (%).
     */
    public double getDuplicateDropRate() {
        String sql = "SELECT SUM(packets_received) as total_recv, SUM(duplicate_drops) as total_dup FROM network_metrics";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                long totalRecv = rs.getLong("total_recv");
                long totalDup = rs.getLong("total_dup");
                if (totalRecv > 0) return (double) totalDup / totalRecv * 100.0;
            }
        } catch (SQLException e) {
            System.err.println("[DB] MetricDao.getDuplicateDropRate error: " + e.getMessage());
        }
        return 0.0;
    }

    private MetricRecord mapRow(ResultSet rs) throws SQLException {
        return new MetricRecord(
                rs.getInt("id"),
                rs.getLong("timestamp"),
                rs.getInt("packets_received"),
                rs.getInt("packets_forwarded"),
                rs.getInt("packets_dropped"),
                rs.getInt("duplicate_drops"),
                rs.getInt("active_relay_count")
        );
    }

    public record MetricRecord(
            int id, long timestamp, int packetsReceived, int packetsForwarded,
            int packetsDropped, int duplicateDrops, int activeRelayCount
    ) {}

    public record RelayLoadStat(
            String nodeId, String nodeName, int packetsForwarded, String status
    ) {}
}
