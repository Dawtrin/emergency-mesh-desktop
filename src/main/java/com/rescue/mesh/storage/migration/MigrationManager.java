package com.rescue.mesh.storage.migration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Quản lý phiên bản schema và thực thi các migrations tuần tự, an toàn, có transaction.
 */
public class MigrationManager {

    private static final List<Migration> REGISTERED_MIGRATIONS = List.of(
            new V1InitialSchemaMigration()
    );

    /**
     * Khởi tạo bảng schema_migrations và áp dụng tất cả các migrations chưa chạy.
     *
     * @param conn Database connection đang mở
     * @throws SQLException nếu xảy ra lỗi trong quá trình migration
     */
    public static void migrate(Connection conn) throws SQLException {
        ensureSchemaMigrationsTable(conn);

        Set<Integer> appliedVersions = getAppliedVersions(conn);

        for (Migration migration : REGISTERED_MIGRATIONS) {
            int version = migration.getVersion();
            if (appliedVersions.contains(version)) {
                continue;
            }

            boolean originalAutoCommit = conn.getAutoCommit();
            try {
                conn.setAutoCommit(false);

                // Áp dụng migration
                migration.apply(conn);

                // Ghi nhận version vào schema_migrations
                recordMigration(conn, migration);

                conn.commit();
                System.out.println("[DB] Áp dụng thành công migration V" + version + ": " + migration.getDescription());
            } catch (SQLException e) {
                conn.rollback();
                System.err.println("[DB ERROR] Thất bại khi áp dụng migration V" + version + ": " + e.getMessage());
                throw e;
            } finally {
                conn.setAutoCommit(originalAutoCommit);
            }
        }
    }

    private static void ensureSchemaMigrationsTable(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS schema_migrations (
                    version INTEGER PRIMARY KEY,
                    applied_at INTEGER NOT NULL,
                    description TEXT NOT NULL
                );
            """);
        }
    }

    public static Set<Integer> getAppliedVersions(Connection conn) throws SQLException {
        Set<Integer> versions = new HashSet<>();
        ensureSchemaMigrationsTable(conn);
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT version FROM schema_migrations;")) {
            while (rs.next()) {
                versions.add(rs.getInt("version"));
            }
        }
        return versions;
    }

    public static int getCurrentVersion(Connection conn) throws SQLException {
        ensureSchemaMigrationsTable(conn);
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COALESCE(MAX(version), 0) FROM schema_migrations;")) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        }
        return 0;
    }

    private static void recordMigration(Connection conn, Migration migration) throws SQLException {
        String sql = "INSERT INTO schema_migrations (version, applied_at, description) VALUES (?, ?, ?);";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, migration.getVersion());
            ps.setLong(2, System.currentTimeMillis());
            ps.setString(3, migration.getDescription());
            ps.executeUpdate();
        }
    }

    public static boolean isTableExists(Connection conn, String tableName) throws SQLException {
        String sql = "SELECT name FROM sqlite_master WHERE type='table' AND name=?;";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tableName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public static boolean isIndexExists(Connection conn, String indexName) throws SQLException {
        String sql = "SELECT name FROM sqlite_master WHERE type='index' AND name=?;";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, indexName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }
}