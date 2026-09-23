package com.rescue.mesh.storage;

import com.rescue.mesh.storage.migration.MigrationManager;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Quản lý kết nối cơ sở dữ liệu SQLite và vòng đời khởi tạo của Base Station.
 *
 * Đảm bảo:
 * - Bật foreign key constraints (PRAGMA foreign_keys = ON) trên mọi kết nối.
 * - Bật chế độ WAL (Write-Ahead Logging) cho disk DB để tối ưu hóa đọc/ghi đồng thời.
 * - Cấu hình busy timeout (5000ms) tránh SQLITE_BUSY khi nhiều luồng cùng truy cập.
 * - Tự động chạy Versioned Migrations khi khởi tạo.
 */
public class DatabaseManager implements AutoCloseable {

    private final DatabaseConfig config;
    private volatile boolean initialized = false;
    private volatile boolean closed = false;

    // Giữ connection cho in-memory database để tránh việc DB bị xóa khi connection đóng
    private Connection inMemoryKeepAliveConnection;

    public DatabaseManager(DatabaseConfig config) {
        this.config = config != null ? config : DatabaseConfig.defaultConfiguration();
    }

    /**
     * Khởi tạo cơ sở dữ liệu: tạo thư mục lưu trữ, mở kết nối và chạy migrations.
     */
    public synchronized void initialize() throws SQLException, IOException {
        if (initialized) {
            return;
        }

        config.ensureDirectoryExists();

        if (config.isInMemory()) {
            inMemoryKeepAliveConnection = createRawConnection();
            configureConnection(inMemoryKeepAliveConnection);
            MigrationManager.migrate(inMemoryKeepAliveConnection);
        } else {
            try (Connection conn = getConnection()) {
                MigrationManager.migrate(conn);
            }
        }

        initialized = true;
        System.out.println("[DB] DatabaseManager khởi tạo thành công tại: " + config.getJdbcUrl());
    }

    /**
     * Tạo và cấu hình kết nối mới tới SQLite.
     */
    public Connection getConnection() throws SQLException {
        if (closed) {
            throw new SQLException("DatabaseManager has been closed");
        }
        Connection conn = createRawConnection();
        configureConnection(conn);
        return conn;
    }

    private Connection createRawConnection() throws SQLException {
        return DriverManager.getConnection(config.getJdbcUrl());
    }

    private void configureConnection(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            // Bắt buộc bật foreign keys trên mọi connection
            stmt.execute("PRAGMA foreign_keys = ON;");
            stmt.execute("PRAGMA busy_timeout = 5000;");

            if (!config.isInMemory()) {
                stmt.execute("PRAGMA journal_mode = WAL;");
                stmt.execute("PRAGMA synchronous = NORMAL;");
            }
        }
    }

    public DatabaseConfig getConfig() {
        return config;
    }

    public boolean isInitialized() {
        return initialized;
    }

    public boolean isClosed() {
        return closed;
    }

    @Override
    public synchronized void close() {
        closed = true;
        if (inMemoryKeepAliveConnection != null) {
            try {
                if (!inMemoryKeepAliveConnection.isClosed()) {
                    inMemoryKeepAliveConnection.close();
                }
            } catch (SQLException e) {
                System.err.println("[DB ERROR] Lỗi khi đóng in-memory keep-alive connection: " + e.getMessage());
            }
            inMemoryKeepAliveConnection = null;
        }
        initialized = false;
    }
}