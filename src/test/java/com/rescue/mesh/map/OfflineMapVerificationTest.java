package com.rescue.mesh.map;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.rescue.mesh.model.MeshPacket;
import com.rescue.mesh.ui.controller.BaseStationController;
import com.rescue.mesh.util.PacketFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Phase 3.5: Offline Map Verification Tests")
class OfflineMapVerificationTest {

    private static final Gson GSON = new Gson();

    @Test
    @DisplayName("Verification 1: Runtime map HTML contains NO external CDN or online tile URLs")
    void testMapHtmlContainsNoExternalNetworkDependencies() throws IOException {
        File htmlFile = new File("src/main/resources/map/leaflet_offline.html");
        assertTrue(htmlFile.exists(), "leaflet_offline.html must exist");

        String content = Files.readString(htmlFile.toPath(), StandardCharsets.UTF_8);

        // Forbidden external domains in runtime code
        String[] forbiddenTokens = {
            "unpkg.com",
            "cartocdn.com",
            "tile.openstreetmap.org",
            "openstreetmap.org/{z}",
            "cdnjs.cloudflare.com",
            "cdn.jsdelivr.net"
        };

        for (String token : forbiddenTokens) {
            assertFalse(content.contains(token),
                    "leaflet_offline.html must NOT contain external dependency: " + token);
        }

        // Verify that http:// or https:// URLs in the HTML only point to documentation/attribution text,
        // not to any external scripts, stylesheets, or tile sources.
        Pattern srcOrHrefPattern = Pattern.compile("(?:src|href)\\s*=\\s*[\"'](https?://[^\"']+)[\"']", Pattern.CASE_INSENSITIVE);
        Matcher matcher = srcOrHrefPattern.matcher(content);
        assertFalse(matcher.find(),
                () -> "Found external resource reference in leaflet_offline.html: " + matcher.group(1));

        // Verify Leaflet CSS and JS are loaded from local path
        assertTrue(content.contains("href=\"leaflet/leaflet.css\"") || content.contains("href='leaflet/leaflet.css'"),
                "Leaflet CSS must be loaded from local leaflet/ path");
        assertTrue(content.contains("src=\"leaflet/leaflet.js\"") || content.contains("src='leaflet/leaflet.js'"),
                "Leaflet JS must be loaded from local leaflet/ path");
        assertTrue(content.contains("maxNativeZoom: mapConfig.maxNativeZoom"),
                "the map must overzoom a small offline fixture instead of requesting missing Internet tiles");
    }

    @Test
    @DisplayName("Verification 2: OfflineMapManager loads local tile fixture without Internet access")
    void testOfflineMapManagerLoadsLocalFixture() {
        OfflineMapManager manager = new OfflineMapManager();
        manager.setMapFilePath("src/test/resources/fixtures/test-tiles.mbtiles");

        boolean started = manager.start();
        assertTrue(started, "OfflineMapManager should start successfully with valid fixture");
        assertTrue(manager.isStarted());
        assertNull(manager.getErrorMessage());

        String urlTemplate = manager.getTileUrlTemplate();
        assertNotNull(urlTemplate);
        assertTrue(urlTemplate.startsWith("http://127.0.0.1:"));
        assertTrue(urlTemplate.endsWith("/tiles/{z}/{x}/{y}.png"));

        // Verify reader is available
        assertNotNull(manager.getReader());
        assertNotNull(manager.getTileServer());
        assertTrue(manager.getTileServerPort() > 0);
        assertTrue(manager.getMinNativeZoom() >= 0);
        assertTrue(manager.getMaxNativeZoom() >= manager.getMinNativeZoom());
        assertEquals(manager.getReader().getMaxZoom(), manager.getMaxNativeZoom());

        manager.close();
        assertFalse(manager.isStarted());
    }

    @Test
    @DisplayName("Verification 3: Missing map file produces clear error without crashing")
    void testMissingMapFileProducesClearError() {
        OfflineMapManager manager = new OfflineMapManager();
        manager.setMapFilePath("data/non_existent_map/region.mbtiles");

        boolean started = manager.start();
        assertFalse(started, "Starting with missing file should return false");
        assertFalse(manager.isStarted());

        String error = manager.getErrorMessage();
        assertNotNull(error);
        assertTrue(error.contains("Offline map file not found"),
                "Error message should clearly state file was not found");

        // Safe cleanup
        manager.close();
    }

    @Test
    @DisplayName("Verification 4: Corrupt map file produces clear error without crashing")
    void testCorruptMapFileProducesClearError(@TempDir Path tempDir) throws Exception {
        File corruptFile = tempDir.resolve("bad.mbtiles").toFile();
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + corruptFile.getAbsolutePath());
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE not_mbtiles (id INT)");
        }

        OfflineMapManager manager = new OfflineMapManager();
        manager.setMapFilePath(corruptFile.getAbsolutePath());

        boolean started = manager.start();
        assertFalse(started, "Starting with corrupt file should return false");
        assertFalse(manager.isStarted());

        String error = manager.getErrorMessage();
        assertNotNull(error);
        assertTrue(error.contains("Failed to open offline map"),
                "Error message should indicate failure to open map");

        manager.close();
    }

    @Test
    @DisplayName("Verification 5: Markers arriving before map readiness are queued in pendingMapPackets")
    void testMarkerQueuedBeforeMapReadiness() {
        BaseStationController controller = new BaseStationController();
        controller.setMapReady(false); // Map is not ready yet

        MeshPacket packet1 = PacketFactory.createSosPacket(
                "VICTIM-QUEUE-01", "Nguyen Van A", "FLOOD", "Ngập lụt",
                2, MeshPacket.SEVERITY_CRITICAL, 15.9753, 108.2532
        );
        MeshPacket packet2 = PacketFactory.createSosPacket(
                "VICTIM-QUEUE-02", "Tran Thi B", "MEDICAL", "Bị thương",
                1, MeshPacket.SEVERITY_HIGH, 15.9800, 108.2600
        );

        // Add markers while map is NOT ready
        controller.addMarkerToMap(packet1);
        controller.addMarkerToMap(packet2);

        // Verify both packets were queued
        List<MeshPacket> pending = controller.getPendingMapPackets();
        assertEquals(2, pending.size(), "Both packets should be queued while map is not ready");
        assertEquals("VICTIM-QUEUE-01", pending.get(0).getSourceNodeId());
        assertEquals("VICTIM-QUEUE-02", pending.get(1).getSourceNodeId());

        controller.shutdown();
        assertTrue(controller.getPendingMapPackets().isEmpty(), "Pending packets should be cleared on shutdown");
    }

    @Test
    @DisplayName("Verification 6: Payload with quotes, backslashes, Unicode Vietnamese, and line breaks serializes safely")
    void testMaliciousPayloadSerializesSafely() {
        // Complex payload with quotes, backslashes, newlines, HTML injection attempt, and Vietnamese Unicode
        String dangerousMessage = "Cứu với! 'Trời mưa to' \"Nước ngập 2m\"\n"
                + "Đường dẫn: C:\\Rescue\\Logs\r\n"
                + "XSS: <script>alert('hack');</script> — Emojis: 🚨🆘🌊";

        MeshPacket packet = PacketFactory.createSosPacket(
                "VICTIM-COMPLEX", "Trần Văn Cảnh", "FLOOD", dangerousMessage,
                3, MeshPacket.SEVERITY_CRITICAL, 15.9753, 108.2532
        );

        MeshPacket.Payload payload = packet.getPayload();
        JsonObject markerObj = new JsonObject();
        markerObj.addProperty("lat", payload.getLocation().getLatitude());
        markerObj.addProperty("lon", payload.getLocation().getLongitude());
        markerObj.addProperty("nodeId", packet.getSourceNodeId());
        markerObj.addProperty("severity", payload.getSeverity());
        markerObj.addProperty("alertType", payload.getAlertType());
        markerObj.addProperty("message", payload.getMessage());
        markerObj.addProperty("victimCount", payload.getVictimCount());

        // Step 1: Serialize to JSON string
        String jsonPayload = GSON.toJson(markerObj);
        assertNotNull(jsonPayload);

        // Step 2: Escape as JavaScript argument (what executeScript receives)
        String jsArgument = GSON.toJson(jsonPayload);
        assertNotNull(jsArgument);

        // Step 3: Verify the escaped string can be safely de-serialized back
        String roundTripJson = GSON.fromJson(jsArgument, String.class);
        JsonObject parsed = GSON.fromJson(roundTripJson, JsonObject.class);

        assertEquals("VICTIM-COMPLEX", parsed.get("nodeId").getAsString());
        assertEquals(dangerousMessage, parsed.get("message").getAsString());
        assertEquals(15.9753, parsed.get("lat").getAsDouble(), 0.0001);
        assertEquals(108.2532, parsed.get("lon").getAsDouble(), 0.0001);
    }

    @Test
    @DisplayName("Verification 7: Invalid, NaN, Infinite, and out-of-range coordinates are rejected safely")
    void testInvalidCoordinatesRejected() {
        BaseStationController controller = new BaseStationController();
        controller.setMapReady(false);

        // Test NaN latitude
        controller.addMarkerToMap(createRawLocationPacket(Double.NaN, 108.0));
        assertEquals(0, controller.getPendingMapPackets().size(), "NaN coordinate must be rejected");

        // Test Infinity longitude
        controller.addMarkerToMap(createRawLocationPacket(15.0, Double.POSITIVE_INFINITY));
        assertEquals(0, controller.getPendingMapPackets().size(), "Infinite coordinate must be rejected");

        // Test out of range latitude (> 90)
        controller.addMarkerToMap(createRawLocationPacket(95.0, 108.0));
        assertEquals(0, controller.getPendingMapPackets().size(), "Latitude > 90 must be rejected");

        // Test out of range longitude (> 180)
        controller.addMarkerToMap(createRawLocationPacket(15.0, 185.0));
        assertEquals(0, controller.getPendingMapPackets().size(), "Longitude > 180 must be rejected");

        // Null location and null packet must be safely ignored without crash
        MeshPacket nullLoc = new MeshPacket();
        nullLoc.setPayload(new MeshPacket.Payload());
        controller.addMarkerToMap(nullLoc);
        assertEquals(0, controller.getPendingMapPackets().size(), "Null location must be rejected");

        controller.addMarkerToMap(null);
        assertEquals(0, controller.getPendingMapPackets().size(), "Null packet must be rejected");

        controller.shutdown();
    }

    private MeshPacket createRawLocationPacket(Double lat, Double lon) {
        MeshPacket packet = new MeshPacket();
        packet.setSourceNodeId("TEST-NODE");
        packet.setPacketType(MeshPacket.TYPE_SOS_BROADCAST);
        MeshPacket.Payload payload = new MeshPacket.Payload();
        payload.setLocation(new MeshPacket.Location(lat, lon));
        payload.setSeverity(MeshPacket.SEVERITY_HIGH);
        payload.setMessage("Test");
        payload.setVictimCount(1);
        packet.setPayload(payload);
        return packet;
    }

    @Test
    @DisplayName("Verification 8: Tile provider binds strictly to loopback interface (127.0.0.1)")
    void testTileProviderBindsOnlyToLoopback() throws Exception {
        OfflineMapManager manager = new OfflineMapManager();
        manager.setMapFilePath("src/test/resources/fixtures/test-tiles.mbtiles");
        assertTrue(manager.start());

        LocalTileServer tileServer = manager.getTileServer();
        assertNotNull(tileServer);
        assertTrue(tileServer.isStarted());

        String template = tileServer.getTileUrlTemplate();
        assertTrue(template.contains("127.0.0.1"), "Server must bind strictly to 127.0.0.1");
        assertFalse(template.contains("0.0.0.0"), "Server must NEVER bind to wildcard 0.0.0.0");

        manager.close();
        assertFalse(manager.isStarted());
    }

    @Test
    @DisplayName("Verification 9: Shutdown cleanly closes map manager, tile server, and clears queue")
    void testCleanShutdownOfMapResources() {
        BaseStationController controller = new BaseStationController();

        OfflineMapManager mapManager = new OfflineMapManager();
        mapManager.setMapFilePath("src/test/resources/fixtures/test-tiles.mbtiles");
        mapManager.start();
        controller.setOfflineMapManager(mapManager);

        assertTrue(mapManager.isStarted());

        // Queue a packet
        MeshPacket packet = PacketFactory.createSosPacket(
                "NODE-PENDING", "Test", "FLOOD", "Msg",
                1, MeshPacket.SEVERITY_HIGH, 15.9753, 108.2532
        );
        controller.addMarkerToMap(packet);
        assertEquals(1, controller.getPendingMapPackets().size());

        // Shutdown
        controller.shutdown();

        assertFalse(mapManager.isStarted(), "OfflineMapManager must be stopped after controller shutdown");
        assertTrue(controller.getPendingMapPackets().isEmpty(), "Pending packets queue must be cleared on shutdown");
        assertEquals(BaseStationController.LifecycleState.STOPPED, controller.getLifecycleState());
    }
}
