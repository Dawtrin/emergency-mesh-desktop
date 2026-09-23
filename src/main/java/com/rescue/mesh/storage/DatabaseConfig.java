package com.rescue.mesh.storage;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Cấu hình kết nối cơ sở dữ liệu SQLite cho ứng dụng Desktop.
 *
 * Đảm bảo:
 * - Đường dẫn runtime an toàn, mặc định nằm trong thư mục data/ tách biệt khỏi source code.
 * - File database runtime luôn được .gitignore bảo vệ (*.db, *.sqlite).
 * - Cho phép cấu hình linh hoạt qua tham số hoặc biến môi trường.
 * - Hỗ trợ cơ sở dữ liệu tạm thời cho các bài kiểm thử tự động (Unit / Integration Tests).
 */
public class DatabaseConfig {

    public static final String DEFAULT_DB_DIR = "data";
    public static final String DEFAULT_DB_FILENAME = "mesh_desktop.db";

    private final Path dbPath;
    private final boolean inMemory;
    private final String memoryDbName;

    public DatabaseConfig(Path dbPath) {
        this.dbPath = dbPath != null ? dbPath.toAbsolutePath().normalize() : getDefaultDatabasePath();
        this.inMemory = false;
        this.memoryDbName = null;
    }

    private DatabaseConfig(boolean inMemory, String memoryDbName) {
        this.dbPath = null;
        this.inMemory = inMemory;
        this.memoryDbName = memoryDbName;
    }

    public static DatabaseConfig defaultConfiguration() {
        return new DatabaseConfig(getDefaultDatabasePath());
    }

    public static DatabaseConfig forPath(Path customPath) {
        return new DatabaseConfig(customPath);
    }

    public static DatabaseConfig forFile(File file) {
        return new DatabaseConfig(file != null ? file.toPath() : null);
    }

    public static DatabaseConfig inMemory() {
        return new DatabaseConfig(true, "mem_" + java.util.UUID.randomUUID().toString().replace("-", ""));
    }

    public static DatabaseConfig inMemory(String name) {
        return new DatabaseConfig(true, name);
    }

    public static Path getDefaultDatabasePath() {
        return Paths.get(DEFAULT_DB_DIR, DEFAULT_DB_FILENAME).toAbsolutePath().normalize();
    }

    public String getJdbcUrl() {
        if (inMemory) {
            return "jdbc:sqlite:file:" + memoryDbName + "?mode=memory&cache=shared";
        }
        return "jdbc:sqlite:" + dbPath.toString().replace("\\", "/");
    }

    public Path getDbPath() {
        return dbPath;
    }

    public boolean isInMemory() {
        return inMemory;
    }

    /**
     * Tạo thư mục cha nếu chưa tồn tại.
     */
    public void ensureDirectoryExists() throws IOException {
        if (!inMemory && dbPath != null && dbPath.getParent() != null) {
            Files.createDirectories(dbPath.getParent());
        }
    }
}