package com.rescue.mesh.storage.repository;

import com.rescue.mesh.storage.DatabaseManager;
import com.rescue.mesh.storage.model.NodeRecord;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class SqliteNodeRepository implements NodeRepository {

    private final DatabaseManager databaseManager;

    public SqliteNodeRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    @Override
    public void upsert(NodeRecord node) {
        try (Connection conn = databaseManager.getConnection()) {
            upsert(conn, node);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to upsert node: " + node.getNodeId(), e);
        }
    }

    @Override
    public void upsert(Connection conn, NodeRecord node) throws SQLException {
        String sql = """
            INSERT INTO nodes (node_id, last_seen, host_address, port, capability_status)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT(node_id) DO UPDATE SET
                last_seen = excluded.last_seen,
                host_address = COALESCE(excluded.host_address, nodes.host_address),
                port = COALESCE(excluded.port, nodes.port),
                capability_status = COALESCE(excluded.capability_status, nodes.capability_status)
        """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, node.getNodeId());
            ps.setLong(2, node.getLastSeen());
            ps.setString(3, node.getHostAddress());
            if (node.getPort() != null) ps.setInt(4, node.getPort()); else ps.setNull(4, java.sql.Types.INTEGER);
            ps.setString(5, node.getCapabilityStatus());
            ps.executeUpdate();
        }
    }

    @Override
    public Optional<NodeRecord> findById(String nodeId) {
        try (Connection conn = databaseManager.getConnection()) {
            String sql = "SELECT * FROM nodes WHERE node_id = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, nodeId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapRow(rs));
                    }
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find node: " + nodeId, e);
        }
        return Optional.empty();
    }

    @Override
    public List<NodeRecord> findAll() {
        List<NodeRecord> list = new ArrayList<>();
        try (Connection conn = databaseManager.getConnection()) {
            String sql = "SELECT * FROM nodes ORDER BY last_seen DESC";
            try (PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find all nodes", e);
        }
        return list;
    }

    @Override
    public int count() {
        try (Connection conn = databaseManager.getConnection()) {
            String sql = "SELECT COUNT(*) FROM nodes";
            try (PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to count nodes", e);
        }
        return 0;
    }

    private NodeRecord mapRow(ResultSet rs) throws SQLException {
        Integer port = rs.getObject("port") != null ? rs.getInt("port") : null;
        return new NodeRecord(
            rs.getString("node_id"),
            rs.getLong("last_seen"),
            rs.getString("host_address"),
            port,
            rs.getString("capability_status")
        );
    }
}
