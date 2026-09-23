package com.rescue.mesh.storage.repository;

import com.rescue.mesh.storage.DatabaseManager;
import com.rescue.mesh.storage.model.DispatchRecord;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class SqliteDispatchRepository implements DispatchRepository {

    private final DatabaseManager databaseManager;

    public SqliteDispatchRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    @Override
    public void insert(DispatchRecord record) {
        try (Connection conn = databaseManager.getConnection()) {
            insert(conn, record);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to insert dispatch command: " + record.getPacketId(), e);
        }
    }

    @Override
    public void insert(Connection conn, DispatchRecord record) throws SQLException {
        String sql = """
            INSERT INTO dispatch_commands (
                packet_id, target_node_id, message, severity,
                delivery_status, attempt_count, next_attempt_time,
                created_at, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, record.getPacketId());
            ps.setString(2, record.getTargetNodeId());
            ps.setString(3, record.getMessage());
            ps.setString(4, record.getSeverity());
            ps.setString(5, record.getDeliveryStatus());
            ps.setInt(6, record.getAttemptCount());
            if (record.getNextAttemptTime() != null) ps.setLong(7, record.getNextAttemptTime()); else ps.setNull(7, java.sql.Types.INTEGER);
            ps.setLong(8, record.getCreatedAt());
            ps.setLong(9, record.getUpdatedAt());
            ps.executeUpdate();
        }
    }

    @Override
    public Optional<DispatchRecord> findById(String packetId) {
        try (Connection conn = databaseManager.getConnection()) {
            String sql = "SELECT * FROM dispatch_commands WHERE packet_id = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, packetId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapRow(rs));
                    }
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find dispatch command: " + packetId, e);
        }
        return Optional.empty();
    }

    @Override
    public void updateStatus(String packetId, String deliveryStatus) {
        try (Connection conn = databaseManager.getConnection()) {
            String sql;
            if ("ACKED".equals(deliveryStatus)) {
                sql = "UPDATE dispatch_commands SET delivery_status = 'ACKED', next_attempt_time = NULL, updated_at = ? WHERE packet_id = ?";
            } else {
                sql = "UPDATE dispatch_commands SET delivery_status = ?, updated_at = ? WHERE packet_id = ? AND delivery_status != 'ACKED'";
            }
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                if ("ACKED".equals(deliveryStatus)) {
                    ps.setLong(1, System.currentTimeMillis());
                    ps.setString(2, packetId);
                } else {
                    ps.setString(1, deliveryStatus);
                    ps.setLong(2, System.currentTimeMillis());
                    ps.setString(3, packetId);
                }
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update dispatch status: " + packetId, e);
        }
    }

    @Override
    public void updateRetry(String packetId, String deliveryStatus, int attemptCount, Long nextAttemptTime, long updatedAt) {
        try (Connection conn = databaseManager.getConnection()) {
            String sql = """
                UPDATE dispatch_commands
                SET delivery_status = ?, attempt_count = ?, next_attempt_time = ?, updated_at = ?
                WHERE packet_id = ? AND delivery_status != 'ACKED'
            """;
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, deliveryStatus);
                ps.setInt(2, attemptCount);
                if (nextAttemptTime != null) ps.setLong(3, nextAttemptTime); else ps.setNull(3, java.sql.Types.INTEGER);
                ps.setLong(4, updatedAt);
                ps.setString(5, packetId);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update dispatch retry: " + packetId, e);
        }
    }

    @Override
    public List<DispatchRecord> findPendingOrSentDue(long nowTimestamp) {
        List<DispatchRecord> list = new ArrayList<>();
        try (Connection conn = databaseManager.getConnection()) {
            String sql = """
                SELECT * FROM dispatch_commands
                WHERE delivery_status IN ('PENDING', 'SENT')
                  AND (next_attempt_time IS NOT NULL AND next_attempt_time <= ?)
                ORDER BY created_at ASC
            """;
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, nowTimestamp);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        list.add(mapRow(rs));
                    }
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find pending dispatches", e);
        }
        return list;
    }

    @Override
    public List<DispatchRecord> findAll() {
        List<DispatchRecord> list = new ArrayList<>();
        try (Connection conn = databaseManager.getConnection()) {
            String sql = "SELECT * FROM dispatch_commands ORDER BY created_at DESC";
            try (PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find all dispatches", e);
        }
        return list;
    }

    @Override
    public int count() {
        try (Connection conn = databaseManager.getConnection()) {
            String sql = "SELECT COUNT(*) FROM dispatch_commands";
            try (PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to count dispatches", e);
        }
        return 0;
    }

    private DispatchRecord mapRow(ResultSet rs) throws SQLException {
        Long nextAttempt = rs.getObject("next_attempt_time") != null ? rs.getLong("next_attempt_time") : null;
        return new DispatchRecord(
            rs.getString("packet_id"),
            rs.getString("target_node_id"),
            rs.getString("message"),
            rs.getString("severity"),
            rs.getString("delivery_status"),
            rs.getInt("attempt_count"),
            nextAttempt,
            rs.getLong("created_at"),
            rs.getLong("updated_at")
        );
    }
}
