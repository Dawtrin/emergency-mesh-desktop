package com.rescue.mesh.db;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object cho bảng incident_events — Ghi vết sự kiện dòng thời gian (Append-Only Dossier Timeline).
 */
public class IncidentEventDao {

    private final DatabaseManager dbManager;

    public IncidentEventDao(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    public void record(String victimNodeId, int episode, String eventType, String actor,
                       String detail, Double lat, Double lng, boolean isDrill) {
        String sql = """
            INSERT INTO incident_events (victim_node_id, episode, event_type, actor, detail, lat, lng, is_drill, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, victimNodeId);
            ps.setInt(2, episode);
            ps.setString(3, eventType);
            ps.setString(4, actor);
            ps.setString(5, detail);
            if (lat != null) ps.setDouble(6, lat); else ps.setNull(6, Types.REAL);
            if (lng != null) ps.setDouble(7, lng); else ps.setNull(7, Types.REAL);
            ps.setInt(8, isDrill ? 1 : 0);
            ps.setLong(9, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[DB] IncidentEventDao.record error: " + e.getMessage());
        }
    }

    public List<EventRecord> findByVictim(String victimNodeId) {
        String sql = "SELECT * FROM incident_events WHERE victim_node_id = ? ORDER BY created_at ASC";
        List<EventRecord> list = new ArrayList<>();
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, victimNodeId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new EventRecord(
                            rs.getLong("id"),
                            rs.getString("victim_node_id"),
                            rs.getInt("episode"),
                            rs.getString("event_type"),
                            rs.getString("actor"),
                            rs.getString("detail"),
                            (Double) rs.getObject("lat"),
                            (Double) rs.getObject("lng"),
                            rs.getInt("is_drill") == 1,
                            rs.getLong("created_at")
                    ));
                }
            }
        } catch (SQLException e) {
            System.err.println("[DB] IncidentEventDao.findByVictim error: " + e.getMessage());
        }
        return list;
    }

    public record EventRecord(
            long id, String victimNodeId, int episode, String eventType, String actor,
            String detail, Double lat, Double lng, boolean isDrill, long createdAt
    ) {}
}
