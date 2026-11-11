package com.rescue.mesh.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object cho bảng rescue_teams — Đội cứu hộ dã chiến.
 *
 * Quản lý vị trí GPS real-time và trạng thái tác chiến của từng đội:
 *   STANDBY → DEPLOYED → ON_SCENE → RETURNING
 *
 * Thread Safety: Mỗi method lấy Connection riêng từ pool → an toàn multi-thread.
 */
public class RescueTeamDao {

    private final DatabaseManager dbManager;

    public RescueTeamDao(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    /**
     * Record đại diện 1 đội cứu hộ trong DB.
     */
    public record RescueTeamRecord(
            String teamId,
            String teamName,
            String teamType,         // MEDICAL | FIRE | POLICE | SAR (Search & Rescue)
            double latitude,
            double longitude,
            String status,           // STANDBY | DEPLOYED | ON_SCENE | RETURNING
            String assignedVictimId, // node_id nạn nhân đang cứu (null nếu STANDBY)
            int memberCount,
            String vehicleInfo,
            String contactPhone,
            long lastUpdateTimestamp
    ) {}

    /**
     * Chèn hoặc cập nhật đội cứu hộ.
     */
    public void insertOrUpdate(String teamId, String teamName, String teamType,
                                double lat, double lon, String status,
                                String assignedVictimId, int memberCount,
                                String vehicleInfo, String contactPhone) {
        String sql = """
            INSERT INTO rescue_teams (team_id, team_name, team_type, latitude, longitude,
                                       status, assigned_victim_id, member_count, vehicle_info,
                                       contact_phone, last_update_timestamp)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(team_id) DO UPDATE SET
                team_name = excluded.team_name,
                latitude = excluded.latitude,
                longitude = excluded.longitude,
                status = excluded.status,
                assigned_victim_id = excluded.assigned_victim_id,
                member_count = excluded.member_count,
                vehicle_info = excluded.vehicle_info,
                last_update_timestamp = excluded.last_update_timestamp
        """;

        long now = System.currentTimeMillis();
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, teamId);
            ps.setString(2, teamName);
            ps.setString(3, teamType);
            ps.setDouble(4, lat);
            ps.setDouble(5, lon);
            ps.setString(6, status);
            ps.setString(7, assignedVictimId);
            ps.setInt(8, memberCount);
            ps.setString(9, vehicleInfo);
            ps.setString(10, contactPhone);
            ps.setLong(11, now);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[DAO] Lỗi insertOrUpdate rescue_team " + teamId + ": " + e.getMessage());
        }
    }

    /**
     * Cập nhật vị trí GPS real-time của đội.
     */
    public void updateLocation(String teamId, double lat, double lon) {
        String sql = "UPDATE rescue_teams SET latitude=?, longitude=?, last_update_timestamp=? WHERE team_id=?";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDouble(1, lat);
            ps.setDouble(2, lon);
            ps.setLong(3, System.currentTimeMillis());
            ps.setString(4, teamId);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[DAO] Lỗi updateLocation " + teamId + ": " + e.getMessage());
        }
    }

    /**
     * Cập nhật trạng thái và gán nạn nhân.
     */
    public void updateStatus(String teamId, String status, String assignedVictimId) {
        String sql = "UPDATE rescue_teams SET status=?, assigned_victim_id=?, last_update_timestamp=? WHERE team_id=?";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setString(2, assignedVictimId);
            ps.setLong(3, System.currentTimeMillis());
            ps.setString(4, teamId);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[DAO] Lỗi updateStatus " + teamId + ": " + e.getMessage());
        }
    }

    /**
     * Lấy tất cả đội cứu hộ.
     */
    public List<RescueTeamRecord> findAll() {
        String sql = "SELECT * FROM rescue_teams ORDER BY last_update_timestamp DESC";
        List<RescueTeamRecord> list = new ArrayList<>();
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapRow(rs));
            }
        } catch (SQLException e) {
            System.err.println("[DAO] Lỗi findAll rescue_teams: " + e.getMessage());
        }
        return list;
    }

    /**
     * Lấy các đội đang hoạt động (không STANDBY).
     */
    public List<RescueTeamRecord> findActive() {
        String sql = "SELECT * FROM rescue_teams WHERE status != 'STANDBY' ORDER BY last_update_timestamp DESC";
        List<RescueTeamRecord> list = new ArrayList<>();
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapRow(rs));
            }
        } catch (SQLException e) {
            System.err.println("[DAO] Lỗi findActive rescue_teams: " + e.getMessage());
        }
        return list;
    }

    /**
     * Đếm đội theo trạng thái.
     */
    public int countByStatus(String status) {
        String sql = "SELECT COUNT(*) FROM rescue_teams WHERE status=?";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            System.err.println("[DAO] Lỗi countByStatus: " + e.getMessage());
        }
        return 0;
    }

    private RescueTeamRecord mapRow(ResultSet rs) throws SQLException {
        return new RescueTeamRecord(
                rs.getString("team_id"),
                rs.getString("team_name"),
                rs.getString("team_type"),
                rs.getDouble("latitude"),
                rs.getDouble("longitude"),
                rs.getString("status"),
                rs.getString("assigned_victim_id"),
                rs.getInt("member_count"),
                rs.getString("vehicle_info"),
                rs.getString("contact_phone"),
                rs.getLong("last_update_timestamp")
        );
    }
}
