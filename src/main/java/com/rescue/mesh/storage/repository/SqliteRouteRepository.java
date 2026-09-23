package com.rescue.mesh.storage.repository;

import com.rescue.mesh.storage.DatabaseManager;
import com.rescue.mesh.storage.model.RouteRecord;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class SqliteRouteRepository implements RouteRepository {

    private final DatabaseManager databaseManager;

    public SqliteRouteRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    @Override
    public void insert(RouteRecord record) {
        try (Connection conn = databaseManager.getConnection()) {
            insert(conn, record);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to insert route: " + record.getNodeId(), e);
        }
    }

    @Override
    public void insert(Connection conn, RouteRecord record) throws SQLException {
        String sql = """
            INSERT INTO routes (packet_id, node_id, route_history, learned_time, expiry_time)
            VALUES (?, ?, ?, ?, ?)
        """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, record.getPacketId());
            ps.setString(2, record.getNodeId());
            ps.setString(3, record.getRouteHistory());
            ps.setLong(4, record.getLearnedTime());
            if (record.getExpiryTime() != null) ps.setLong(5, record.getExpiryTime()); else ps.setNull(5, java.sql.Types.INTEGER);
            ps.executeUpdate();
        }
    }

    @Override
    public List<RouteRecord> findByNodeId(String nodeId) {
        List<RouteRecord> list = new ArrayList<>();
        try (Connection conn = databaseManager.getConnection()) {
            String sql = "SELECT * FROM routes WHERE node_id = ? ORDER BY learned_time DESC";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, nodeId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        list.add(mapRow(rs));
                    }
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find routes by node: " + nodeId, e);
        }
        return list;
    }

    @Override
    public List<RouteRecord> findAll() {
        List<RouteRecord> list = new ArrayList<>();
        try (Connection conn = databaseManager.getConnection()) {
            String sql = "SELECT * FROM routes ORDER BY learned_time DESC";
            try (PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find all routes", e);
        }
        return list;
    }

    private RouteRecord mapRow(ResultSet rs) throws SQLException {
        Long expiry = rs.getObject("expiry_time") != null ? rs.getLong("expiry_time") : null;
        return new RouteRecord(
            rs.getLong("id"),
            rs.getString("packet_id"),
            rs.getString("node_id"),
            rs.getString("route_history"),
            rs.getLong("learned_time"),
            expiry
        );
    }
}
