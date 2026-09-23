package com.rescue.mesh.storage;

import com.rescue.mesh.storage.migration.MigrationManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Kiểm thử Versioned Migrations, Schema Tables, Indexes và Foreign Key enforcement cho SQLite.
 */
public class DatabaseMigrationTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("Phase 2.1: Tạo database từ trạng thái rỗng và áp dụng migration V1")
    void testCreateDatabaseFromEmptyState() throws Exception {
        Path dbPath = tempDir.resolve("test_empty.db");
        assertFalse(Files.exists(dbPath), "Database file ban đầu không tồn tại");

        DatabaseConfig config = DatabaseConfig.forPath(dbPath);
        try (DatabaseManager dbManager = new DatabaseManager(config)) {
            dbManager.initialize();
            assertTrue(Files.exists(dbPath), "Database file phải được tạo sau khi initialize");

            try (Connection conn = dbManager.getConnection()) {
                // 1. Kiểm tra version hiện tại = 1
                assertEquals(1, MigrationManager.getCurrentVersion(conn), "Schema version phải là 1");

                // 2. Kiểm tra đủ 5 bảng nghiệp vụ cốt lõi + bảng schema_migrations
                assertTrue(MigrationManager.isTableExists(conn, "schema_migrations"), "Bảng schema_migrations phải tồn tại");
                assertTrue(MigrationManager.isTableExists(conn, "packets"), "Bảng packets phải tồn tại");
                assertTrue(MigrationManager.isTableExists(conn, "sos_events"), "Bảng sos_events phải tồn tại");
                assertTrue(MigrationManager.isTableExists(conn, "nodes"), "Bảng nodes phải tồn tại");
                assertTrue(MigrationManager.isTableExists(conn, "dispatch_commands"), "Bảng dispatch_commands phải tồn tại");
                assertTrue(MigrationManager.isTableExists(conn, "routes"), "Bảng routes phải tồn tại");

                // 3. Kiểm tra các indexes bắt buộc
                assertTrue(MigrationManager.isIndexExists(conn, "idx_packets_timestamp"), "Index idx_packets_timestamp phải tồn tại");
                assertTrue(MigrationManager.isIndexExists(conn, "idx_packets_source"), "Index idx_packets_source phải tồn tại");
                assertTrue(MigrationManager.isIndexExists(conn, "idx_packets_type"), "Index idx_packets_type phải tồn tại");
                assertTrue(MigrationManager.isIndexExists(conn, "idx_sos_severity"), "Index idx_sos_severity phải tồn tại");
                assertTrue(MigrationManager.isIndexExists(conn, "idx_sos_status"), "Index idx_sos_status phải tồn tại");
                assertTrue(MigrationManager.isIndexExists(conn, "idx_nodes_last_seen"), "Index idx_nodes_last_seen phải tồn tại");
                assertTrue(MigrationManager.isIndexExists(conn, "idx_dispatch_status"), "Index idx_dispatch_status phải tồn tại");
                assertTrue(MigrationManager.isIndexExists(conn, "idx_dispatch_target"), "Index idx_dispatch_target phải tồn tại");
                assertTrue(MigrationManager.isIndexExists(conn, "idx_routes_node"), "Index idx_routes_node phải tồn tại");
            }
        }
    }

    @Test
    @DisplayName("Phase 2.1: Mở lại database đã có schema mà không bị lỗi hoặc ghi đè")
    void testReopenExistingDatabase() throws Exception {
        Path dbPath = tempDir.resolve("test_reopen.db");
        DatabaseConfig config = DatabaseConfig.forPath(dbPath);

        // Lần 1: Khởi tạo và ghi 1 node
        try (DatabaseManager dbManager = new DatabaseManager(config)) {
            dbManager.initialize();
            try (Connection conn = dbManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "INSERT INTO nodes (node_id, last_seen, host_address, port, capability_status) VALUES (?, ?, ?, ?, ?);")) {
                ps.setString(1, "NODE_TEST_01");
                ps.setLong(2, 1771240000000L);
                ps.setString(3, "127.0.0.1");
                ps.setInt(4, 8001);
                ps.setString(5, "ACTIVE");
                ps.executeUpdate();
            }
        }

        // Lần 2: Mở lại database bằng instance mới
        try (DatabaseManager dbManager2 = new DatabaseManager(config)) {
            dbManager2.initialize();

            try (Connection conn = dbManager2.getConnection()) {
                assertEquals(1, MigrationManager.getCurrentVersion(conn));

                // Dữ liệu cũ phải còn nguyên vẹn
                try (PreparedStatement ps = conn.prepareStatement("SELECT node_id, port FROM nodes WHERE node_id = ?;")) {
                    ps.setString(1, "NODE_TEST_01");
                    try (ResultSet rs = ps.executeQuery()) {
                        assertTrue(rs.next(), "Node đã lưu phải tồn tại sau khi mở lại DB");
                        assertEquals(8001, rs.getInt("port"));
                    }
                }
            }
        }
    }

    @Test
    @DisplayName("Phase 2.1: Chạy migrations 2 lần liên tiếp không gây lỗi (Idempotency)")
    void testRunMigrationsTwiceWithoutCorruption() throws Exception {
        Path dbPath = tempDir.resolve("test_idempotency.db");
        DatabaseConfig config = DatabaseConfig.forPath(dbPath);

        try (DatabaseManager dbManager = new DatabaseManager(config)) {
            dbManager.initialize();

            try (Connection conn = dbManager.getConnection()) {
                // Chạy lại migrate lần 2 trên cùng connection
                assertDoesNotThrow(() -> MigrationManager.migrate(conn),
                        "Chạy lại migrate lần 2 không được ném exception");
                assertEquals(1, MigrationManager.getCurrentVersion(conn));

                // Số lượng records trong schema_migrations vẫn chỉ là 1
                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM schema_migrations;")) {
                    assertTrue(rs.next());
                    assertEquals(1, rs.getInt(1), "Chỉ được có đúng 1 bản ghi migration V1");
                }
            }
        }
    }

    @Test
    @DisplayName("Phase 2.1: Ràng buộc khóa ngoại (Foreign Keys) được bật và bảo vệ tính toàn vẹn")
    void testForeignKeysEnforcedOnEveryConnection() throws Exception {
        Path dbPath = tempDir.resolve("test_foreign_keys.db");
        DatabaseConfig config = DatabaseConfig.forPath(dbPath);

        try (DatabaseManager dbManager = new DatabaseManager(config)) {
            dbManager.initialize();

            try (Connection conn = dbManager.getConnection()) {
                // 1. Kiểm tra PRAGMA foreign_keys = ON
                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery("PRAGMA foreign_keys;")) {
                    assertTrue(rs.next());
                    assertEquals(1, rs.getInt(1), "PRAGMA foreign_keys bắt buộc phải là 1 (ON)");
                }

                // 2. Cố tình insert sos_events trỏ tới packet_id không tồn tại trong packets -> phải ném SQLException
                String badSql = """
                    INSERT INTO sos_events (packet_id, sender_name, alert_type, message, victim_count, severity, latitude, longitude, altitude, accuracy, rescue_status)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);
                """;
                try (PreparedStatement ps = conn.prepareStatement(badSql)) {
                    ps.setString(1, "NON_EXISTENT_PACKET_ID");
                    ps.setString(2, "Test");
                    ps.setString(3, "MEDICAL");
                    ps.setString(4, "Help");
                    ps.setInt(5, 1);
                    ps.setString(6, "HIGH");
                    ps.setDouble(7, 16.0);
                    ps.setDouble(8, 108.0);
                    ps.setDouble(9, 0.0);
                    ps.setDouble(10, 0.0);
                    ps.setString(11, "PENDING");

                    assertThrows(SQLException.class, ps::executeUpdate,
                            "Insert vào sos_events với packet_id không tồn tại phải bị chặn bởi Foreign Key constraint");
                }
            }
        }
    }

    @Test
    @DisplayName("Phase 2.1 Correction: DatabaseConfig.inMemory() chạy migrations và nhiều connections cùng chia sẻ schema")
    void testInMemoryDatabaseMigrationAndMultipleConnections() throws Exception {
        DatabaseConfig config = DatabaseConfig.inMemory();
        try (DatabaseManager dbManager = new DatabaseManager(config)) {
            dbManager.initialize();

            // Mở connection 1 và kiểm tra migration
            try (Connection conn1 = dbManager.getConnection()) {
                assertEquals(1, MigrationManager.getCurrentVersion(conn1), "Schema version trên conn1 phải là 1");
                assertTrue(MigrationManager.isTableExists(conn1, "packets"), "Bảng packets phải tồn tại trên conn1");

                // Insert 1 packet trên conn1
                try (PreparedStatement ps = conn1.prepareStatement(
                        "INSERT INTO packets (packet_id, protocol_version, packet_type, source_node_id, destination_node_id, sender_hop_id, timestamp, ttl, hop_count, checksum, raw_json, processing_status, received_at) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);")) {
                    ps.setString(1, "PKT-MEM-001");
                    ps.setString(2, "1.0");
                    ps.setString(3, "HEARTBEAT");
                    ps.setString(4, "NODE-A");
                    ps.setString(5, "BASE_STATION");
                    ps.setString(6, "NODE-A");
                    ps.setLong(7, 1000L);
                    ps.setInt(8, 5);
                    ps.setInt(9, 0);
                    ps.setString(10, "CHK-001");
                    ps.setString(11, "{}");
                    ps.setString(12, "PROCESSED");
                    ps.setLong(13, 1000L);
                    ps.executeUpdate();
                }
            }

            // Mở connection 2 độc lập từ dbManager và kiểm tra thấy data từ conn1
            try (Connection conn2 = dbManager.getConnection()) {
                assertEquals(1, MigrationManager.getCurrentVersion(conn2), "Schema version trên conn2 cũng phải là 1");
                try (PreparedStatement ps = conn2.prepareStatement("SELECT source_node_id FROM packets WHERE packet_id = ?;")) {
                    ps.setString(1, "PKT-MEM-001");
                    try (ResultSet rs = ps.executeQuery()) {
                        assertTrue(rs.next(), "Dữ liệu được ghi trên conn1 phải hiển thị trên conn2 của in-memory DB");
                        assertEquals("NODE-A", rs.getString("source_node_id"));
                    }
                }
            }
        }
    }

    @Test
    @DisplayName("Phase 2.1 Correction: DatabaseConfig.inMemory() hỗ trợ đầy đủ Repository CRUD")
    void testInMemoryDatabaseRepositoryCrud() throws Exception {
        DatabaseConfig config = DatabaseConfig.inMemory();
        try (DatabaseManager dbManager = new DatabaseManager(config)) {
            dbManager.initialize();

            com.rescue.mesh.storage.repository.SqlitePacketRepository packetRepo = new com.rescue.mesh.storage.repository.SqlitePacketRepository(dbManager);
            com.rescue.mesh.storage.repository.SqliteNodeRepository nodeRepo = new com.rescue.mesh.storage.repository.SqliteNodeRepository(dbManager);

            // 1. Lưu Node
            com.rescue.mesh.storage.model.NodeRecord node = new com.rescue.mesh.storage.model.NodeRecord(
                    "NODE-VICTIM-99", 1771240000000L, "192.168.1.99", 9099, "ACTIVE"
            );
            nodeRepo.upsert(node);
            var foundNode = nodeRepo.findById("NODE-VICTIM-99");
            assertTrue(foundNode.isPresent());
            assertEquals(9099, foundNode.get().getPort());

            // 2. Lưu Packet
            com.rescue.mesh.storage.model.PacketRecord packet = new com.rescue.mesh.storage.model.PacketRecord(
                    "PKT-CRUD-001", "1.0", "DISPATCH",
                    "BASE_STATION", "NODE-VICTIM-99", "BASE_STATION",
                    1771240000000L, 5, 0, "DETERMINISTIC_CHECKSUM",
                    "{\"action\":\"EVACUATE\"}", "PROCESSED", 1771240000005L
            );
            packetRepo.insert(packet);
            var foundPacket = packetRepo.findById("PKT-CRUD-001");
            assertTrue(foundPacket.isPresent());
            assertEquals("DISPATCH", foundPacket.get().getPacketType());
            assertEquals("BASE_STATION", foundPacket.get().getSourceNodeId());
        }
    }
}