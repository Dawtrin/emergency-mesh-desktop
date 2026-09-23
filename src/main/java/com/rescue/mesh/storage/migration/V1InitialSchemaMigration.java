package com.rescue.mesh.storage.migration;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Migration V1: Tạo các bảng cốt lõi và chỉ mục cho Desktop Base Station.
 *
 * Các bảng:
 * - packets: Lưu toàn bộ gói tin thô đã nhận (raw JSON, checksum, metadata, status).
 * - sos_events: Lưu sự kiện SOS (nạn nhân, severity, GPS, rescue status).
 * - nodes: Danh bạ node mạng (last seen, host, port, capability).
 * - dispatch_commands: Lưu lệnh điều phối và hàng đợi outbox (trạng thái, số lần thử, next attempt).
 * - routes: Lưu trữ tuyến đường và lịch sử định tuyến.
 */
public class V1InitialSchemaMigration implements Migration {

    @Override
    public int getVersion() {
        return 1;
    }

    @Override
    public String getDescription() {
        return "Create core tables: packets, sos_events, nodes, dispatch_commands, routes and indexes";
    }

    @Override
    public void apply(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {

            // 1. Bảng packets
            stmt.execute("""
                CREATE TABLE packets (
                    packet_id TEXT PRIMARY KEY,
                    protocol_version TEXT NOT NULL,
                    packet_type TEXT NOT NULL,
                    source_node_id TEXT NOT NULL,
                    destination_node_id TEXT NOT NULL,
                    sender_hop_id TEXT NOT NULL,
                    timestamp INTEGER NOT NULL,
                    ttl INTEGER NOT NULL,
                    hop_count INTEGER NOT NULL,
                    checksum TEXT NOT NULL,
                    raw_json TEXT NOT NULL,
                    processing_status TEXT NOT NULL,
                    received_at INTEGER NOT NULL
                );
            """);

            // 2. Bảng sos_events
            stmt.execute("""
                CREATE TABLE sos_events (
                    packet_id TEXT PRIMARY KEY REFERENCES packets(packet_id) ON DELETE CASCADE,
                    sender_name TEXT,
                    alert_type TEXT,
                    message TEXT,
                    victim_count INTEGER,
                    severity TEXT,
                    latitude REAL,
                    longitude REAL,
                    altitude REAL,
                    accuracy REAL,
                    rescue_status TEXT NOT NULL
                );
            """);

            // 3. Bảng nodes
            stmt.execute("""
                CREATE TABLE nodes (
                    node_id TEXT PRIMARY KEY,
                    last_seen INTEGER NOT NULL,
                    host_address TEXT,
                    port INTEGER,
                    capability_status TEXT
                );
            """);

            // 4. Bảng dispatch_commands
            stmt.execute("""
                CREATE TABLE dispatch_commands (
                    packet_id TEXT PRIMARY KEY,
                    target_node_id TEXT NOT NULL,
                    message TEXT NOT NULL,
                    severity TEXT NOT NULL,
                    delivery_status TEXT NOT NULL,
                    attempt_count INTEGER NOT NULL DEFAULT 0,
                    next_attempt_time INTEGER,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL
                );
            """);

            // 5. Bảng routes
            stmt.execute("""
                CREATE TABLE routes (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    packet_id TEXT REFERENCES packets(packet_id) ON DELETE SET NULL,
                    node_id TEXT,
                    route_history TEXT,
                    learned_time INTEGER NOT NULL,
                    expiry_time INTEGER
                );
            """);

            // 6. Các chỉ mục (indexes) tối ưu truy vấn
            stmt.execute("CREATE INDEX idx_packets_timestamp ON packets(timestamp);");
            stmt.execute("CREATE INDEX idx_packets_source ON packets(source_node_id);");
            stmt.execute("CREATE INDEX idx_packets_type ON packets(packet_type);");
            stmt.execute("CREATE INDEX idx_sos_severity ON sos_events(severity);");
            stmt.execute("CREATE INDEX idx_sos_status ON sos_events(rescue_status);");
            stmt.execute("CREATE INDEX idx_nodes_last_seen ON nodes(last_seen);");
            stmt.execute("CREATE INDEX idx_dispatch_status ON dispatch_commands(delivery_status);");
            stmt.execute("CREATE INDEX idx_dispatch_target ON dispatch_commands(target_node_id);");
            stmt.execute("CREATE INDEX idx_routes_node ON routes(node_id);");
        }
    }
}