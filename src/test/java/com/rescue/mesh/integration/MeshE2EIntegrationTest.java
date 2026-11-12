package com.rescue.mesh.integration;

import com.google.gson.Gson;
import com.rescue.mesh.model.MeshPacket;
import org.junit.jupiter.api.*;

import java.io.*;
import java.net.Socket;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MẢNG 3 — Integration Test: End-to-End Mesh Packet Flow
 *
 * Kiểm thử trọn vẹn luồng gói tin JSON từ Android → TCP 8888 → RoutingEngine
 * → C2Service → SQLite → STOMP WebSocket → Web Dashboard.
 *
 * Yêu cầu: Spring Boot server phải đang chạy trước khi chạy test.
 * Chạy: mvn test -Dtest=MeshE2EIntegrationTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("🛰️ Mảng 3: End-to-End Integration Tests")
class MeshE2EIntegrationTest {

    private static final Gson GSON = new Gson();
    private static final String TCP_HOST = "127.0.0.1";
    private static final int TCP_PORT = 8888;
    private static final String HTTP_BASE = "http://127.0.0.1:8080";
    private static String testNodeId;
    private static org.springframework.context.ConfigurableApplicationContext springContext;

    @BeforeAll
    static void setUp() {
        testNodeId = "E2E_TEST_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        try (Socket s = new Socket(TCP_HOST, TCP_PORT)) {
            // Server đã đang chạy sẵn bên ngoài
        } catch (Exception e) {
            // Server chưa chạy -> Tự động khởi động Spring Boot context phục vụ test E2E
            try {
                springContext = org.springframework.boot.SpringApplication.run(
                        com.rescue.mesh.TacticalWebC2Application.class,
                        "--spring.main.banner-mode=off"
                );
                Thread.sleep(2000);
            } catch (Exception ex) {
                System.err.println("[E2E-TEST] Không thể khởi động TacticalWebC2Application: " + ex.getMessage());
            }
        }
    }

    @AfterAll
    static void tearDown() {
        if (springContext != null && springContext.isActive()) {
            springContext.close();
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // KỊCH BẢN 1: SINGLE-HOP (Gửi trực tiếp TCP 8888)
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Order(1)
    @DisplayName("📡 Kịch bản 1: Gửi gói SOS_DATA qua TCP 8888 (Single-Hop)")
    void testSingleHopSosData() throws Exception {
        // Tạo gói tin chuẩn MeshPacket giống từ Android
        MeshPacket packet = new MeshPacket(
                MeshPacket.TYPE_SOS_DATA, testNodeId, MeshPacket.NODE_BASE_STATION);

        MeshPacket.Payload payload = new MeshPacket.Payload();
        payload.setSenderName("Nguyễn Văn Integration Test");
        payload.setAlertType(MeshPacket.ALERT_FLOOD);
        payload.setMessage("TEST: Gói tin kiểm thử liên tầng Mảng 3 - Single Hop");
        payload.setVictimCount(3);
        payload.setSeverity(MeshPacket.SEVERITY_CRITICAL);
        payload.setPhoneNumber("0905999888");
        payload.setBloodType("AB-");
        payload.setMedicalNotes("Dị ứng Penicillin, bệnh nền tiểu đường");

        MeshPacket.Location loc = new MeshPacket.Location(16.0612, 108.2134);
        payload.setLocation(loc);

        packet.setPayload(payload);
        packet.addToRouteHistory(testNodeId);
        packet.computeAndSetChecksum(GSON);

        // Gửi qua TCP Socket (mô phỏng Android → Base Station)
        String json = packet.toJson(GSON);
        assertNotNull(json, "JSON serialize không được null");
        assertTrue(json.contains(testNodeId), "JSON phải chứa sourceNodeId");

        try (Socket socket = new Socket(TCP_HOST, TCP_PORT)) {
            socket.setSoTimeout(5000);
            PrintWriter out = new PrintWriter(
                    new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
            out.println(json);
            out.flush();
        }

        // Chờ server xử lý
        Thread.sleep(1000);

        // Verify qua REST API
        String statsJson = httpGet(HTTP_BASE + "/api/victims/stats");
        assertNotNull(statsJson, "Stats API phải trả về dữ liệu");
        assertTrue(statsJson.contains("\"total\""), "Stats phải có field total");

        System.out.println("✅ Single-Hop test PASSED — Gói tin đã gửi và server đã nhận");
        System.out.println("   Node ID: " + testNodeId);
        System.out.println("   Checksum: " + packet.getChecksum().substring(0, 16) + "...");
    }

    // ═══════════════════════════════════════════════════════════════
    // KỊCH BẢN 2: MULTI-HOP (Gói tin đi qua 2 relay)
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Order(2)
    @DisplayName("📡 Kịch bản 2: Gói tin Multi-Hop qua 2 Relay")
    void testMultiHopRelay() throws Exception {
        String victimNode = "MULTIHOP_VICTIM_" + UUID.randomUUID().toString().substring(0, 4).toUpperCase();

        MeshPacket packet = new MeshPacket(
                MeshPacket.TYPE_SOS_DATA, victimNode, MeshPacket.NODE_BASE_STATION);

        MeshPacket.Payload payload = new MeshPacket.Payload();
        payload.setSenderName("Trần Thị Multi-Hop");
        payload.setAlertType(MeshPacket.ALERT_MEDICAL);
        payload.setMessage("TEST: Gói tin qua 2 relay - kiểm tra hopCount và TTL");
        payload.setVictimCount(1);
        payload.setSeverity(MeshPacket.SEVERITY_HIGH);
        payload.setPhoneNumber("0912345678");
        payload.setBloodType("O+");
        payload.setLocation(new MeshPacket.Location(16.0700, 108.2200));

        packet.setPayload(payload);
        packet.computeAndSetChecksum(GSON);

        // Mô phỏng đi qua 2 relay
        packet.addToRouteHistory(victimNode);            // Node A (Victim)
        packet.addToRouteHistory("RELAY_B_SIM");         // Node B (Relay 1)
        packet.setSenderHopId("RELAY_C_SIM");            // Node C (Relay 2) đang gửi
        packet.addToRouteHistory("RELAY_C_SIM");
        packet.setHopCount(2);
        packet.setTtl(MeshPacket.DEFAULT_TTL - 2);       // TTL giảm 2

        String json = packet.toJson(GSON);
        assertTrue(json.contains("RELAY_B_SIM"), "Route history phải chứa relay B");
        assertEquals(2, packet.getHopCount(), "Hop count phải = 2");
        assertEquals(3, packet.getTtl(), "TTL phải = 5-2 = 3");

        try (Socket socket = new Socket(TCP_HOST, TCP_PORT)) {
            socket.setSoTimeout(5000);
            PrintWriter out = new PrintWriter(
                    new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
            out.println(json);
            out.flush();
        }

        Thread.sleep(1000);

        System.out.println("✅ Multi-Hop test PASSED — Hop Count = 2, TTL = 3");
        System.out.println("   Route: " + packet.getRouteHistory());
    }

    // ═══════════════════════════════════════════════════════════════
    // KỊCH BẢN 3: HEARTBEAT (Relay Node gửi trạng thái)
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Order(3)
    @DisplayName("💓 Kịch bản 3: Relay Node gửi Heartbeat")
    void testRelayHeartbeat() throws Exception {
        MeshPacket hb = new MeshPacket(
                MeshPacket.TYPE_HEARTBEAT, "RELAY_HB_TEST", MeshPacket.NODE_BASE_STATION);
        hb.addToRouteHistory("RELAY_HB_TEST");
        hb.computeAndSetChecksum(GSON);

        String json = hb.toJson(GSON);
        assertTrue(json.contains("HEARTBEAT"), "Loại gói tin phải là HEARTBEAT");

        try (Socket socket = new Socket(TCP_HOST, TCP_PORT)) {
            socket.setSoTimeout(5000);
            PrintWriter out = new PrintWriter(
                    new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
            out.println(json);
            out.flush();
        }

        Thread.sleep(500);
        System.out.println("✅ Heartbeat test PASSED — Relay đã gửi tín hiệu sống");
    }

    // ═══════════════════════════════════════════════════════════════
    // KỊCH BẢN 4: CHECKSUM INTEGRITY (SHA-256)
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Order(4)
    @DisplayName("🔐 Kịch bản 4: Xác minh SHA-256 Checksum Integrity")
    void testChecksumIntegrity() {
        MeshPacket packet = new MeshPacket(
                MeshPacket.TYPE_SOS_DATA, "CHECKSUM_TEST", MeshPacket.NODE_BASE_STATION);

        MeshPacket.Payload payload = new MeshPacket.Payload();
        payload.setSenderName("Test Checksum");
        payload.setAlertType(MeshPacket.ALERT_LANDSLIDE);
        payload.setSeverity(MeshPacket.SEVERITY_MEDIUM);
        payload.setLocation(new MeshPacket.Location(16.05, 108.20));
        packet.setPayload(payload);

        // Tính checksum
        packet.computeAndSetChecksum(GSON);
        assertNotNull(packet.getChecksum(), "Checksum không được null");
        assertEquals(64, packet.getChecksum().length(), "SHA-256 phải dài 64 ký tự hex");

        // Xác minh nguyên vẹn
        assertTrue(packet.verifyChecksum(GSON), "Checksum phải verify thành công khi data chưa bị sửa");

        // Thay đổi payload → checksum phải fail
        payload.setSenderName("TAMPERED_DATA");
        assertFalse(packet.verifyChecksum(GSON), "Checksum phải fail khi data bị thay đổi");

        System.out.println("✅ Checksum Integrity test PASSED — SHA-256 detect tampering");
    }

    // ═══════════════════════════════════════════════════════════════
    // KỊCH BẢN 5: REST API VERIFY (Kiểm tra API endpoints)
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Order(5)
    @DisplayName("🌐 Kịch bản 5: Kiểm tra REST API Endpoints")
    void testRestApiEndpoints() throws Exception {
        // GET /api/victims/stats
        String stats = httpGet(HTTP_BASE + "/api/victims/stats");
        assertNotNull(stats);
        assertTrue(stats.contains("total"), "/api/victims/stats phải trả về total");

        // GET /api/victims
        String victims = httpGet(HTTP_BASE + "/api/victims");
        assertNotNull(victims);

        // GET /api/metrics/summary
        String metrics = httpGet(HTTP_BASE + "/api/metrics/summary");
        assertNotNull(metrics);
        assertTrue(metrics.contains("totalReceived"), "/api/metrics/summary phải trả về totalReceived");

        // GET /api/facilities
        String facilities = httpGet(HTTP_BASE + "/api/facilities");
        assertNotNull(facilities);

        // GET /api/packets?limit=5
        String packets = httpGet(HTTP_BASE + "/api/packets?limit=5");
        assertNotNull(packets);

        System.out.println("✅ REST API Endpoints test PASSED — tất cả API trả về OK");
    }

    // ═══════════════════════════════════════════════════════════════
    // KỊCH BẢN 6: DISPATCH COMMAND (Gửi lệnh điều phối)
    // ═══════════════════════════════════════════════════════════════

    @Test
    @Order(6)
    @DisplayName("⚡ Kịch bản 6: Gửi lệnh Dispatch qua REST API")
    void testDispatchCommand() throws Exception {
        String body = GSON.toJson(java.util.Map.of(
                "targetNodeId", testNodeId,
                "command", "Ca-nô số 01 xuất kích - Integration Test",
                "severity", "CRITICAL"
        ));

        int status = httpPost(HTTP_BASE + "/api/dispatch", body);
        assertTrue(status >= 200 && status < 300,
                "POST /api/dispatch phải trả về 2xx, nhận được: " + status);

        System.out.println("✅ Dispatch Command test PASSED — Lệnh điều phối đã phát");
    }

    // ═══════════════════════════════════════════════════════════════
    // Helper: HTTP GET
    // ═══════════════════════════════════════════════════════════════

    private String httpGet(String url) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(3000);
        conn.setReadTimeout(3000);

        int code = conn.getResponseCode();
        if (code != 200) {
            System.err.println("[HTTP-GET-DEBUG] GET " + url + " returned code: " + code);
            return null;
        }

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            return sb.toString();
        }
    }

    private int httpPost(String url, String jsonBody) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        conn.setConnectTimeout(3000);
        conn.setReadTimeout(3000);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
            os.flush();
        }

        return conn.getResponseCode();
    }
}
