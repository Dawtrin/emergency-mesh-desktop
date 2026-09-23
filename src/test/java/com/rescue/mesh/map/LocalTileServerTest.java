package com.rescue.mesh.map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Phase 3.3: LocalTileServer Tests")
class LocalTileServerTest {

    private File fixtureFile;
    private MBTilesReader reader;
    private LocalTileServer server;

    @BeforeEach
    void setUp() throws Exception {
        fixtureFile = new File("src/test/resources/fixtures/test-tiles.mbtiles");
        assertTrue(fixtureFile.exists());
        reader = new MBTilesReader(fixtureFile);
        reader.open();
        server = new LocalTileServer(reader);
        server.start();
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.close();
        }
        if (reader != null) {
            reader.close();
        }
    }

    @Test
    @DisplayName("Server starts on loopback 127.0.0.1 with dynamic port > 0")
    void testServerStartsOnLoopback() {
        assertTrue(server.isStarted());
        int port = server.getPort();
        assertTrue(port > 0 && port <= 65535, "Port should be dynamically assigned in valid range");

        String template = server.getTileUrlTemplate();
        assertNotNull(template);
        assertTrue(template.startsWith("http://127.0.0.1:" + port + "/tiles/"));
    }

    @Test
    @DisplayName("HTTP GET existing tile returns 200 OK with correct content-type and PNG data")
    void testGetExistingTileOverHttp() throws Exception {
        URL url = URI.create("http://127.0.0.1:" + server.getPort() + "/tiles/0/0/0.png").toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(3000);
        conn.setReadTimeout(3000);

        assertEquals(200, conn.getResponseCode());
        assertEquals("image/png", conn.getContentType());
        byte[] body = conn.getInputStream().readAllBytes();
        assertTrue(body.length > 0);
        // Verify PNG signature
        assertEquals((byte) 0x89, body[0]);
        assertEquals((byte) 0x50, body[1]);
        assertEquals((byte) 0x4E, body[2]);
        assertEquals((byte) 0x47, body[3]);
        conn.disconnect();
    }

    @Test
    @DisplayName("HTTP GET missing tile returns 404 with transparent 1x1 PNG")
    void testGetMissingTileReturns404() throws Exception {
        URL url = URI.create("http://127.0.0.1:" + server.getPort() + "/tiles/15/500/500.png").toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(3000);
        conn.setReadTimeout(3000);

        assertEquals(404, conn.getResponseCode());
        assertEquals("image/png", conn.getContentType());
        byte[] body = conn.getErrorStream().readAllBytes();
        assertTrue(body.length > 0, "404 should return transparent fallback tile");
        assertEquals((byte) 0x89, body[0]);
        conn.disconnect();
    }

    @Test
    @DisplayName("Path traversal attempts return 400 Bad Request")
    void testPathTraversalRejected() throws Exception {
        // Path traversal using ..
        URL url = URI.create("http://127.0.0.1:" + server.getPort() + "/tiles/../secret.txt").toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(3000);

        // Either Java URL normalizes it or server rejects it with 400
        int code = conn.getResponseCode();
        assertTrue(code == 400 || code == 404, "Traversal attempt should be rejected with 400 or 404");
        conn.disconnect();
    }

    @Test
    @DisplayName("Malformed tile path format returns 400 Bad Request")
    void testMalformedPathReturns400() throws Exception {
        URL url = URI.create("http://127.0.0.1:" + server.getPort() + "/tiles/invalid/path").toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(3000);

        assertEquals(400, conn.getResponseCode());
        conn.disconnect();
    }

    @Test
    @DisplayName("Coordinate out of bounds for zoom level returns 400 Bad Request")
    void testOutOfBoundsCoordinatesReturn400() throws Exception {
        // At zoom 0, max x and y is 0 (maxCoord is 1). x=5 is out of bounds!
        URL url = URI.create("http://127.0.0.1:" + server.getPort() + "/tiles/0/5/0.png").toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(3000);

        assertEquals(400, conn.getResponseCode());
        conn.disconnect();
    }

    @Test
    @DisplayName("HTTP POST method returns 405 Method Not Allowed")
    void testPostMethodReturns405() throws Exception {
        URL url = URI.create("http://127.0.0.1:" + server.getPort() + "/tiles/0/0/0.png").toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setConnectTimeout(3000);

        assertEquals(405, conn.getResponseCode());
        conn.disconnect();
    }

    @Test
    @DisplayName("Graceful shutdown closes HTTP server and releases port")
    void testGracefulShutdown() {
        assertTrue(server.isStarted());
        int oldPort = server.getPort();

        server.close();
        assertFalse(server.isStarted());
        assertEquals(-1, server.getPort());
        assertNull(server.getTileUrlTemplate());

        // Subsequent requests should fail to connect
        assertThrows(IOException.class, () -> {
            URL url = URI.create("http://127.0.0.1:" + oldPort + "/tiles/0/0/0.png").toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(1000);
            conn.getResponseCode();
            conn.disconnect();
        });
    }
}
