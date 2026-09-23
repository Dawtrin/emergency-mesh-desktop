package com.rescue.mesh.storage.repository;

import com.rescue.mesh.storage.DatabaseManager;
import com.rescue.mesh.storage.model.SosEventRecord;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class SqliteSosEventRepository implements SosEventRepository {

    private final DatabaseManager databaseManager;

    public SqliteSosEventRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    @Override
    public void insert(SosEventRecord record) {
        try (Connection conn = databaseManager.getConnection()) {
            insert(conn, record);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to insert SOS event: " + record.getPacketId(), e);
        }
    }

    @Override
    public void insert(Connection conn, SosEventRecord record) throws SQLException {
        String sql = """
            INSERT INTO sos_events (
                packet_id, sender_name, alert_type, message,
                victim_count, severity, latitude, longitude,
                altitude, accuracy, rescue_status
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, record.getPacketId());
            ps.setString(2, record.getSenderName());
            ps.setString(3, record.getAlertType());
            ps.setString(4, record.getMessage());
            ps.setInt(5, record.getVictimCount());
            ps.setString(6, record.getSeverity());
            if (record.getLatitude() != null) ps.setDouble(7, record.getLatitude()); else ps.setNull(7, java.sql.Types.REAL);
            if (record.getLongitude() != null) ps.setDouble(8, record.getLongitude()); else ps.setNull(8, java.sql.Types.REAL);
            if (record.getAltitude() != null) ps.setDouble(9, record.getAltitude()); else ps.setNull(9, java.sql.Types.REAL);
            if (record.getAccuracy() != null) ps.setDouble(10, record.getAccuracy()); else ps.setNull(10, java.sql.Types.REAL);
            ps.setString(11, record.getRescueStatus());
            ps.executeUpdate();
        }
    }

    @Override
    public boolean existsById(String packetId) {
        try (Connection conn = databaseManager.getConnection()) {
            return existsById(conn, packetId);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to check SOS existence: " + packetId, e);
        }
    }

    @Override
    public boolean existsById(Connection conn, String packetId) throws SQLException {
        String sql = "SELECT 1 FROM sos_events WHERE packet_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, packetId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    @Override
    public Optional<SosEventRecord> findById(String packetId) {
        try (Connection conn = databaseManager.getConnection()) {
            String sql = """
                SELECT s.*, p.source_node_id, p.timestamp, r.route_history
                FROM sos_events s
                JOIN packets p ON s.packet_id = p.packet_id
                LEFT JOIN routes r ON s.packet_id = r.packet_id
                WHERE s.packet_id = ?
            """;
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, packetId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapRow(rs));
                    }
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find SOS event: " + packetId, e);
        }
        return Optional.empty();
    }

    @Override
    public List<SosEventRecord> findAllOrderByTimeAsc() {
        return queryList("""
            SELECT s.*, p.source_node_id, p.timestamp, r.route_history
            FROM sos_events s
            JOIN packets p ON s.packet_id = p.packet_id
            LEFT JOIN routes r ON s.packet_id = r.packet_id
            ORDER BY p.timestamp ASC
        """);
    }

    @Override
    public List<SosEventRecord> findAllOrderByTimeDesc() {
        return queryList("""
            SELECT s.*, p.source_node_id, p.timestamp, r.route_history
            FROM sos_events s
            JOIN packets p ON s.packet_id = p.packet_id
            LEFT JOIN routes r ON s.packet_id = r.packet_id
            ORDER BY p.timestamp DESC
        """);
    }

    private List<SosEventRecord> queryList(String sql) {
        List<SosEventRecord> list = new ArrayList<>();
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to query SOS events", e);
        }
        return list;
    }

    @Override
    public void updateRescueStatus(String packetId, String status) {
        try (Connection conn = databaseManager.getConnection()) {
            String sql = "UPDATE sos_events SET rescue_status = ? WHERE packet_id = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, status);
                ps.setString(2, packetId);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update rescue status: " + packetId, e);
        }
    }

    @Override
    public int countTotal() {
        try (Connection conn = databaseManager.getConnection()) {
            String sql = "SELECT COUNT(*) FROM sos_events";
            try (PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to count SOS events", e);
        }
        return 0;
    }

    @Override
    public int countBySeverity(String severity) {
        try (Connection conn = databaseManager.getConnection()) {
            String sql = "SELECT COUNT(*) FROM sos_events WHERE severity = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, severity);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to count SOS events by severity: " + severity, e);
        }
        return 0;
    }

    private SosEventRecord mapRow(ResultSet rs) throws SQLException {
        Double lat = rs.getObject("latitude") != null ? rs.getDouble("latitude") : null;
        Double lon = rs.getObject("longitude") != null ? rs.getDouble("longitude") : null;
        Double alt = rs.getObject("altitude") != null ? rs.getDouble("altitude") : null;
        Double acc = rs.getObject("accuracy") != null ? rs.getDouble("accuracy") : null;

        return new SosEventRecord(
            rs.getString("packet_id"),
            rs.getString("source_node_id"),
            rs.getString("sender_name"),
            rs.getString("alert_type"),
            rs.getString("message"),
            rs.getInt("victim_count"),
            rs.getString("severity"),
            lat,
            lon,
            alt,
            acc,
            rs.getString("rescue_status"),
            rs.getLong("timestamp"),
            rs.getString("route_history")
        );
    }
}
