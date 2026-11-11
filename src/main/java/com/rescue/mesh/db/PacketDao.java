package com.rescue.mesh.db;

import com.google.gson.Gson;
import com.rescue.mesh.model.MeshPacket;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object cho bảng mesh_packets — Nhật ký gói tin.
 * Lưu trữ vĩnh viễn toàn bộ gói tin đã xử lý làm bằng chứng (Evidence Log).
 */
public class PacketDao {

    private final DatabaseManager dbManager;
    private static final Gson GSON = new Gson();

    public PacketDao(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    /**
     * Lưu gói tin vào bảng mesh_packets.
     */
    public void insert(MeshPacket packet) {
        if (packet == null) return;

        String sql = """
            INSERT OR IGNORE INTO mesh_packets
            (packet_id, packet_type, source_node_id, destination_node_id, sender_hop_id,
             ttl, hop_count, timestamp, route_history, checksum, payload_json)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;

        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, packet.getPacketId());
            ps.setString(2, packet.getPacketType());
            ps.setString(3, packet.getSourceNodeId());
            ps.setString(4, packet.getDestinationNodeId());
            ps.setString(5, packet.getSenderHopId());
            ps.setInt(6, packet.getTtl());
            ps.setInt(7, packet.getHopCount());
            ps.setLong(8, packet.getTimestamp());
            ps.setString(9, packet.getRouteHistory() != null
                    ? String.join("->", packet.getRouteHistory()) : "");
            ps.setString(10, packet.getChecksum());
            ps.setString(11, packet.getPayload() != null ? GSON.toJson(packet.getPayload()) : "{}");
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[DB] PacketDao.insert error: " + e.getMessage());
        }
    }

    /**
     * Lấy gói tin theo node nguồn.
     */
    public List<PacketRecord> findBySourceNode(String sourceNodeId) {
        String sql = "SELECT * FROM mesh_packets WHERE source_node_id = ? ORDER BY timestamp DESC";
        List<PacketRecord> results = new ArrayList<>();
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, sourceNodeId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) results.add(mapRow(rs));
            }
        } catch (SQLException e) {
            System.err.println("[DB] PacketDao.findBySourceNode error: " + e.getMessage());
        }
        return results;
    }

    /**
     * Lấy N gói tin gần nhất.
     */
    public List<PacketRecord> findRecent(int limit) {
        String sql = "SELECT * FROM mesh_packets ORDER BY timestamp DESC LIMIT ?";
        List<PacketRecord> results = new ArrayList<>();
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) results.add(mapRow(rs));
            }
        } catch (SQLException e) {
            System.err.println("[DB] PacketDao.findRecent error: " + e.getMessage());
        }
        return results;
    }

    /**
     * Đếm tổng số gói tin.
     */
    public int countAll() {
        String sql = "SELECT COUNT(*) FROM mesh_packets";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            System.err.println("[DB] PacketDao.countAll error: " + e.getMessage());
        }
        return 0;
    }

    /**
     * Đếm gói tin theo loại.
     */
    public int countByType(String packetType) {
        String sql = "SELECT COUNT(*) FROM mesh_packets WHERE packet_type = ?";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, packetType);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            System.err.println("[DB] PacketDao.countByType error: " + e.getMessage());
        }
        return 0;
    }

    /**
     * Lấy gói tin với filter tùy chọn (theo FINAL spec §6.1: GET /api/packets?nodeId=&limit=).
     * Dùng cho Packet Sniffer (Trang 3) và Audit Trail (Trang 6).
     */
    public List<PacketRecord> findWithFilter(String nodeId, int limit) {
        if (nodeId != null && !nodeId.isBlank()) {
            String sql = "SELECT * FROM mesh_packets WHERE source_node_id = ? ORDER BY timestamp DESC LIMIT ?";
            List<PacketRecord> results = new ArrayList<>();
            try (Connection conn = dbManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, nodeId);
                ps.setInt(2, limit);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) results.add(mapRow(rs));
                }
            } catch (SQLException e) {
                System.err.println("[DB] PacketDao.findWithFilter error: " + e.getMessage());
            }
            return results;
        }
        return findRecent(limit);
    }

    private PacketRecord mapRow(ResultSet rs) throws SQLException {
        return new PacketRecord(
                rs.getString("packet_id"),
                rs.getString("packet_type"),
                rs.getString("source_node_id"),
                rs.getString("destination_node_id"),
                rs.getString("sender_hop_id"),
                rs.getInt("ttl"),
                rs.getInt("hop_count"),
                rs.getLong("timestamp"),
                rs.getString("route_history"),
                rs.getString("checksum"),
                rs.getString("payload_json")
        );
    }

    public record PacketRecord(
            String packetId, String packetType, String sourceNodeId,
            String destinationNodeId, String senderHopId,
            int ttl, int hopCount, long timestamp,
            String routeHistory, String checksum, String payloadJson
    ) {}
}
