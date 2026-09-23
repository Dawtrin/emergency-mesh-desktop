package com.rescue.mesh.storage;

import com.rescue.mesh.model.MeshPacket;
import com.rescue.mesh.storage.model.DispatchRecord;
import com.rescue.mesh.storage.model.NodeRecord;
import com.rescue.mesh.storage.model.PacketRecord;
import com.rescue.mesh.storage.model.RouteRecord;
import com.rescue.mesh.storage.model.SosEventRecord;
import com.rescue.mesh.storage.repository.SqliteDispatchRepository;
import com.rescue.mesh.storage.repository.SqliteNodeRepository;
import com.rescue.mesh.storage.repository.SqlitePacketRepository;
import com.rescue.mesh.storage.repository.SqliteRouteRepository;
import com.rescue.mesh.storage.repository.SqliteSosEventRepository;
import com.rescue.mesh.util.PacketFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Repository and BaseStationStorageService Tests")
class RepositoryTest {

    @TempDir
    Path tempDir;

    private DatabaseManager dbManager;
    private BaseStationStorageService storageService;
    private SqlitePacketRepository packetRepo;
    private SqliteSosEventRepository sosRepo;
    private SqliteNodeRepository nodeRepo;
    private SqliteDispatchRepository dispatchRepo;
    private SqliteRouteRepository routeRepo;
    private Path dbPath;

    @BeforeEach
    void setUp() throws Exception {
        dbPath = tempDir.resolve("test_mesh.db");
        DatabaseConfig config = DatabaseConfig.forPath(dbPath);
        dbManager = new DatabaseManager(config);
        dbManager.initialize();

        packetRepo = new SqlitePacketRepository(dbManager);
        sosRepo = new SqliteSosEventRepository(dbManager);
        nodeRepo = new SqliteNodeRepository(dbManager);
        dispatchRepo = new SqliteDispatchRepository(dbManager);
        routeRepo = new SqliteRouteRepository(dbManager);

        storageService = new BaseStationStorageService(
            dbManager, packetRepo, sosRepo, nodeRepo, dispatchRepo, routeRepo
        );
    }

    @AfterEach
    void tearDown() {
        if (dbManager != null) {
            dbManager.close();
        }
    }

    @Test
    @DisplayName("PacketRepository: insert, exists, find, count")
    void testPacketRepositoryCrud() {
        PacketRecord record = new PacketRecord(
            "pkt-001", "1.0", MeshPacket.TYPE_SOS_BROADCAST,
            "NODE-A", "BASE_STATION", "NODE-B",
            1700000000000L, 5, 1, "test-checksum",
            "{\"packet_id\":\"pkt-001\"}", "PROCESSED", 1700000001000L
        );

        assertFalse(packetRepo.existsById("pkt-001"));
        packetRepo.insert(record);
        assertTrue(packetRepo.existsById("pkt-001"));
        assertEquals(1, packetRepo.count());

        Optional<PacketRecord> found = packetRepo.findById("pkt-001");
        assertTrue(found.isPresent());
        assertEquals("pkt-001", found.get().getPacketId());
        assertEquals("NODE-A", found.get().getSourceNodeId());
        assertEquals(1700000000000L, found.get().getTimestamp());
        assertEquals("test-checksum", found.get().getChecksum());

        List<PacketRecord> list = packetRepo.findAllOrderByReceivedAtDesc(10);
        assertEquals(1, list.size());
    }

    @Test
    @DisplayName("SosEventRepository: insert, find, update status, count by severity")
    void testSosEventRepositoryCrud() {
        // Cần có packet tương ứng trong bảng packets vì có foreign key
        PacketRecord pRec = new PacketRecord(
            "pkt-sos-1", "1.0", MeshPacket.TYPE_SOS_BROADCAST,
            "NODE-VICTIM", "BASE_STATION", "NODE-RELAY",
            1700000000000L, 5, 1, "chk-1", "{}", "PROCESSED", 1700000000000L
        );
        packetRepo.insert(pRec);

        SosEventRecord sosRecord = new SosEventRecord(
            "pkt-sos-1", "NODE-VICTIM", "Nguyen Van A",
            "MEDICAL", "Need insulin urgently", 2,
            MeshPacket.SEVERITY_CRITICAL, 21.0285, 105.8542,
            15.0, 5.0, "PENDING", 1700000000000L, "[\"NODE-VICTIM\", \"NODE-RELAY\"]"
        );

        sosRepo.insert(sosRecord);
        assertTrue(sosRepo.existsById("pkt-sos-1"));
        assertEquals(1, sosRepo.countTotal());
        assertEquals(1, sosRepo.countBySeverity(MeshPacket.SEVERITY_CRITICAL));
        assertEquals(0, sosRepo.countBySeverity(MeshPacket.SEVERITY_HIGH));

        Optional<SosEventRecord> found = sosRepo.findById("pkt-sos-1");
        assertTrue(found.isPresent());
        assertEquals("Nguyen Van A", found.get().getSenderName());
        assertEquals("PENDING", found.get().getRescueStatus());
        assertEquals(2, found.get().getVictimCount());
        assertEquals(21.0285, found.get().getLatitude(), 0.0001);

        // Update status
        sosRepo.updateRescueStatus("pkt-sos-1", "RESOLVED");
        assertEquals("RESOLVED", sosRepo.findById("pkt-sos-1").get().getRescueStatus());
    }

    @Test
    @DisplayName("NodeRepository: upsert new, upsert update lastSeen")
    void testNodeRepositoryUpsert() {
        NodeRecord n1 = new NodeRecord("NODE-01", 1000L, "192.168.1.10", 8001, "ACTIVE");
        nodeRepo.upsert(n1);

        assertEquals(1, nodeRepo.count());
        Optional<NodeRecord> found = nodeRepo.findById("NODE-01");
        assertTrue(found.isPresent());
        assertEquals(1000L, found.get().getLastSeen());
        assertEquals("192.168.1.10", found.get().getHostAddress());

        // Upsert cập nhật last_seen mới hơn
        NodeRecord n1Updated = new NodeRecord("NODE-01", 2000L, "192.168.1.10", 8001, "ACTIVE");
        nodeRepo.upsert(n1Updated);

        assertEquals(1, nodeRepo.count());
        assertEquals(2000L, nodeRepo.findById("NODE-01").get().getLastSeen());
    }

    @Test
    @DisplayName("DispatchRepository: insert, updateStatus, updateRetry, findPendingOrSentDue")
    void testDispatchRepository() {
        long now = 1700000000000L;
        DispatchRecord d1 = new DispatchRecord(
            "cmd-001", "NODE-VICTIM", "Help is coming",
            MeshPacket.SEVERITY_CRITICAL, "PENDING",
            0, now, now, now
        );
        dispatchRepo.insert(d1);

        assertEquals(1, dispatchRepo.count());
        Optional<DispatchRecord> found = dispatchRepo.findById("cmd-001");
        assertTrue(found.isPresent());
        assertEquals("PENDING", found.get().getDeliveryStatus());

        // Update status -> SENT
        dispatchRepo.updateStatus("cmd-001", "SENT");
        assertEquals("SENT", dispatchRepo.findById("cmd-001").get().getDeliveryStatus());

        // Update retry
        dispatchRepo.updateRetry("cmd-001", "PENDING", 1, now + 5000L, now + 1000L);
        DispatchRecord afterRetry = dispatchRepo.findById("cmd-001").get();
        assertEquals(1, afterRetry.getAttemptCount());
        assertEquals(now + 5000L, afterRetry.getNextAttemptTime());

        // Query due: at now + 4000L -> not due yet
        List<DispatchRecord> dueBefore = dispatchRepo.findPendingOrSentDue(now + 4000L);
        assertTrue(dueBefore.isEmpty());

        // Query due: at now + 6000L -> is due!
        List<DispatchRecord> dueAfter = dispatchRepo.findPendingOrSentDue(now + 6000L);
        assertEquals(1, dueAfter.size());
        assertEquals("cmd-001", dueAfter.get(0).getPacketId());
    }

    @Test
    @DisplayName("RouteRepository: insert and find by node")
    void testRouteRepository() {
        PacketRecord pRec = new PacketRecord(
            "pkt-r1", "1.0", MeshPacket.TYPE_ROUTE_DISCOVERY,
            "NODE-A", "BASE_STATION", "NODE-B",
            1000L, 5, 1, "chk", "{}", "PROCESSED", 1000L
        );
        packetRepo.insert(pRec);

        RouteRecord r1 = new RouteRecord(null, "pkt-r1", "NODE-A", "[\"NODE-A\",\"NODE-B\"]", 1000L, null);
        routeRepo.insert(r1);

        List<RouteRecord> routes = routeRepo.findByNodeId("NODE-A");
        assertEquals(1, routes.size());
        assertEquals("pkt-r1", routes.get(0).getPacketId());
    }

    @Test
    @DisplayName("BaseStationStorageService: saveIncomingPacketIdempotent suppresses duplicates")
    void testIdempotentPacketSave() {
        MeshPacket sosPacket = PacketFactory.createSosPacket(
            "VICTIM-01", "Tran Thi B", "FLOOD_TRAPPED", "Water rising fast",
            3, MeshPacket.SEVERITY_CRITICAL, 21.03, 105.85
        );

        // Lần 1: lưu thành công
        boolean firstSaved = storageService.saveIncomingPacketIdempotent(sosPacket, null);
        assertTrue(firstSaved, "First save of unique packet should return true");

        assertEquals(1, packetRepo.count());
        assertEquals(1, sosRepo.countTotal());
        assertEquals(1, nodeRepo.count());

        // Lần 2: duplicate packetId -> phải trả về false và không thêm bản ghi
        boolean secondSaved = storageService.saveIncomingPacketIdempotent(sosPacket, null);
        assertFalse(secondSaved, "Second save of same packet should return false (duplicate)");

        assertEquals(1, packetRepo.count(), "Packet count should still be 1");
        assertEquals(1, sosRepo.countTotal(), "SOS count should still be 1");
        assertEquals(1, nodeRepo.count(), "Node count should still be 1");
    }

    @Test
    @DisplayName("BaseStationStorageService: Non-SOS packets are stored in packets table but not in sos_events")
    void testNonSosPacketSaved() {
        MeshPacket ackPacket = PacketFactory.createAck("BASE_STATION", "pkt-sos-origin", "VICTIM-01");
        boolean saved = storageService.saveIncomingPacketIdempotent(ackPacket, null);
        assertTrue(saved);

        assertEquals(1, packetRepo.count());
        assertEquals(0, sosRepo.countTotal(), "ACK packet should not be in sos_events");
    }

    @Test
    @DisplayName("BaseStationStorageService: Reopen database and verify state persistence")
    void testReopenDatabasePersistence() throws Exception {
        MeshPacket sos1 = PacketFactory.createSosPacket(
            "VICTIM-01", "User 1", "MEDICAL", "Need help",
            1, MeshPacket.SEVERITY_CRITICAL, 21.0, 105.0
        );
        MeshPacket sos2 = PacketFactory.createSosPacket(
            "VICTIM-02", "User 2", "LANDSLIDE", "Blocked road",
            2, MeshPacket.SEVERITY_HIGH, 21.1, 105.1
        );

        storageService.saveIncomingPacketIdempotent(sos1, null);
        storageService.saveIncomingPacketIdempotent(sos2, null);

        // Đóng database
        dbManager.close();

        // Mở lại kết nối từ cùng file SQLite
        DatabaseConfig config = DatabaseConfig.forPath(dbPath);
        DatabaseManager reopenedDb = new DatabaseManager(config);
        reopenedDb.initialize(); // Idempotent

        BaseStationStorageService restoredService = new BaseStationStorageService(reopenedDb);

        List<SosEventRecord> allSos = restoredService.loadAllSosEvents();
        assertEquals(2, allSos.size());

        BaseStationStorageService.SosStats stats = restoredService.getSosStats();
        assertEquals(2, stats.getTotal());
        assertEquals(1, stats.getCritical());
        assertEquals(1, stats.getHigh());
        assertEquals(0, stats.getMedium());

        reopenedDb.close();
    }

    @Test
    @DisplayName("Foreign keys: deleting packet cascades to sos_events")
    void testCascadeDelete() throws Exception {
        MeshPacket sosPacket = PacketFactory.createSosPacket(
            "VICTIM-01", "User 1", "MEDICAL", "Need help",
            1, MeshPacket.SEVERITY_CRITICAL, 21.0, 105.0
        );
        storageService.saveIncomingPacketIdempotent(sosPacket, null);

        assertEquals(1, packetRepo.count());
        assertEquals(1, sosRepo.countTotal());

        try (Connection conn = dbManager.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM packets WHERE packet_id = '" + sosPacket.getPacketId() + "'");
        }

        assertEquals(0, packetRepo.count());
        assertEquals(0, sosRepo.countTotal(), "Cascade delete should remove sos_event");
    }
}
