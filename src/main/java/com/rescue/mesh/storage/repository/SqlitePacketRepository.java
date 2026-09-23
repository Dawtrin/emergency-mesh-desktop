package com.rescue.mesh.storage.repository;

import com.rescue.mesh.storage.DatabaseManager;
import com.rescue.mesh.storage.model.PacketRecord;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class SqlitePacketRepository implements PacketRepository {

    private final DatabaseManager databaseManager;

    public SqlitePacketRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    @Override
    public void insert(PacketRecord packet) {
        try (Connection conn = databaseManager.getConnection()) {
            insert(conn, packet);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to insert packet: " + packet.getPacketId(), e);
        }
    }

    @Override
    public void insert(Connection conn, PacketRecord packet) throws SQLException {
        String sql = """
            INSERT INTO packets (
                packet_id, protocol_version, packet_type, source_node_id,
                destination_node_id, sender_hop_id, timestamp, ttl,
                hop_count, checksum, raw_json, processing_status, received_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, packet.getPacketId());
            ps.setString(2, packet.getProtocolVersion());
            ps.setString(3, packet.getPacketType());
            ps.setString(4, packet.getSourceNodeId());
            ps.setString(5, packet.getDestinationNodeId());
            ps.setString(6, packet.getSenderHopId());
            ps.setLong(7, packet.getTimestamp());
            ps.setInt(8, packet.getTtl());
            ps.setInt(9, packet.getHopCount());
            ps.setString(10, packet.getChecksum());
            ps.setString(11, packet.getRawJson());
            ps.setString(12, packet.getProcessingStatus());
            ps.setLong(13, packet.getReceivedAt());
            ps.executeUpdate();
        }
    }

    @Override
    public boolean existsById(String packetId) {
        try (Connection conn = databaseManager.getConnection()) {
            return existsById(conn, packetId);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to check packet existence: " + packetId, e);
        }
    }

    @Override
    public boolean existsById(Connection conn, String packetId) throws SQLException {
        String sql = "SELECT 1 FROM packets WHERE packet_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, packetId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    @Override
    public Optional<PacketRecord> findById(String packetId) {
        try (Connection conn = databaseManager.getConnection()) {
            String sql = "SELECT * FROM packets WHERE packet_id = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, packetId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapRow(rs));
                    }
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find packet: " + packetId, e);
        }
        return Optional.empty();
    }

    @Override
    public List<PacketRecord> findAllOrderByReceivedAtDesc(int limit) {
        List<PacketRecord> list = new ArrayList<>();
        try (Connection conn = databaseManager.getConnection()) {
            String sql = "SELECT * FROM packets ORDER BY received_at DESC LIMIT ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, limit);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        list.add(mapRow(rs));
                    }
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find packets", e);
        }
        return list;
    }

    @Override
    public int count() {
        try (Connection conn = databaseManager.getConnection()) {
            String sql = "SELECT COUNT(*) FROM packets";
            try (PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to count packets", e);
        }
        return 0;
    }

    private PacketRecord mapRow(ResultSet rs) throws SQLException {
        return new PacketRecord(
            rs.getString("packet_id"),
            rs.getString("protocol_version"),
            rs.getString("packet_type"),
            rs.getString("source_node_id"),
            rs.getString("destination_node_id"),
            rs.getString("sender_hop_id"),
            rs.getLong("timestamp"),
            rs.getInt("ttl"),
            rs.getInt("hop_count"),
            rs.getString("checksum"),
            rs.getString("raw_json"),
            rs.getString("processing_status"),
            rs.getLong("received_at")
        );
    }
}
