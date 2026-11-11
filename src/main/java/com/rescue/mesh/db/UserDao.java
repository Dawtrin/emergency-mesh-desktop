package com.rescue.mesh.db;

import com.rescue.mesh.model.UserEntity;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object cho bảng users — Quản trị Cán bộ & Phân quyền RBAC.
 */
public class UserDao {

    private final DatabaseManager dbManager;

    public UserDao(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    public boolean insert(UserEntity user) {
        String sql = """
            INSERT INTO users (username, display_name, password_hash, role, active,
                              failed_logins, locked_until, must_change_password, last_login_at, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, user.getUsername());
            ps.setString(2, user.getDisplayName());
            ps.setString(3, user.getPasswordHash());
            ps.setString(4, user.getRole());
            ps.setInt(5, user.isActive() ? 1 : 0);
            ps.setInt(6, user.getFailedLogins());
            if (user.getLockedUntil() != null) ps.setLong(7, user.getLockedUntil()); else ps.setNull(7, Types.INTEGER);
            ps.setInt(8, user.isMustChangePassword() ? 1 : 0);
            if (user.getLastLoginAt() != null) ps.setLong(9, user.getLastLoginAt()); else ps.setNull(9, Types.INTEGER);
            ps.setLong(10, user.getCreatedAt());

            int affected = ps.executeUpdate();
            if (affected > 0) {
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) user.setId(rs.getLong(1));
                }
                return true;
            }
        } catch (SQLException e) {
            System.err.println("[DB] UserDao.insert error: " + e.getMessage());
        }
        return false;
    }

    public UserEntity findByUsername(String username) {
        String sql = "SELECT * FROM users WHERE username = ?";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        } catch (SQLException e) {
            System.err.println("[DB] UserDao.findByUsername error: " + e.getMessage());
        }
        return null;
    }

    public List<UserEntity> findAll() {
        String sql = "SELECT * FROM users ORDER BY created_at ASC";
        List<UserEntity> list = new ArrayList<>();
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapRow(rs));
            }
        } catch (SQLException e) {
            System.err.println("[DB] UserDao.findAll error: " + e.getMessage());
        }
        return list;
    }

    public int countUsers() {
        String sql = "SELECT COUNT(*) FROM users";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            System.err.println("[DB] UserDao.countUsers error: " + e.getMessage());
        }
        return 0;
    }

    public void recordSuccessLogin(String username) {
        String sql = "UPDATE users SET failed_logins = 0, locked_until = NULL, last_login_at = ? WHERE username = ?";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, System.currentTimeMillis());
            ps.setString(2, username);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[DB] UserDao.recordSuccessLogin error: " + e.getMessage());
        }
    }

    public void recordFailedLogin(String username, int maxAttempts, long lockDurationMs) {
        UserEntity u = findByUsername(username);
        if (u == null) return;
        int failed = u.getFailedLogins() + 1;
        Long lockUntil = failed >= maxAttempts ? System.currentTimeMillis() + lockDurationMs : null;

        String sql = "UPDATE users SET failed_logins = ?, locked_until = ? WHERE username = ?";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, failed);
            if (lockUntil != null) ps.setLong(2, lockUntil); else ps.setNull(2, Types.INTEGER);
            ps.setString(3, username);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[DB] UserDao.recordFailedLogin error: " + e.getMessage());
        }
    }

    public void updatePassword(String username, String newPasswordHash) {
        String sql = "UPDATE users SET password_hash = ?, must_change_password = 0 WHERE username = ?";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, newPasswordHash);
            ps.setString(2, username);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[DB] UserDao.updatePassword error: " + e.getMessage());
        }
    }

    public void updateRoleAndStatus(String username, String role, boolean active) {
        String sql = "UPDATE users SET role = ?, active = ? WHERE username = ?";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, role);
            ps.setInt(2, active ? 1 : 0);
            ps.setString(3, username);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[DB] UserDao.updateRoleAndStatus error: " + e.getMessage());
        }
    }

    private UserEntity mapRow(ResultSet rs) throws SQLException {
        UserEntity u = new UserEntity();
        u.setId(rs.getLong("id"));
        u.setUsername(rs.getString("username"));
        u.setDisplayName(rs.getString("display_name"));
        u.setPasswordHash(rs.getString("password_hash"));
        u.setRole(rs.getString("role"));
        u.setActive(rs.getInt("active") == 1);
        u.setFailedLogins(rs.getInt("failed_logins"));
        long lockedUntil = rs.getLong("locked_until");
        u.setLockedUntil(rs.wasNull() ? null : lockedUntil);
        u.setMustChangePassword(rs.getInt("must_change_password") == 1);
        long lastLoginAt = rs.getLong("last_login_at");
        u.setLastLoginAt(rs.wasNull() ? null : lastLoginAt);
        u.setCreatedAt(rs.getLong("created_at"));
        return u;
    }
}
