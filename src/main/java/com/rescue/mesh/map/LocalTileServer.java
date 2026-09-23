package com.rescue.mesh.map;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A lightweight loopback HTTP server that serves map tiles from an MBTiles file.
 * <p>
 * Security constraints:
 * <ul>
 *   <li>Binds ONLY to 127.0.0.1 (localhost)</li>
 *   <li>Uses a dynamically assigned port (port 0)</li>
 *   <li>Validates all URL paths and tile coordinates</li>
 *   <li>Prevents path traversal attacks</li>
 *   <li>Returns correct HTTP status codes and content types</li>
 *   <li>Uses a bounded thread pool</li>
 * </ul>
 * <p>
 * URL format: http://127.0.0.1:{port}/tiles/{z}/{x}/{y}.png
 */
public class LocalTileServer implements Closeable {

    private static final Logger LOGGER = Logger.getLogger(LocalTileServer.class.getName());

    /** Maximum zoom level supported */
    private static final int MAX_ZOOM = 22;

    /** Maximum coordinate value at zoom 0 is 1, so max at zoom 22 is 2^22 */
    private static final int MAX_COORD = (1 << MAX_ZOOM);

    /** Pattern for tile URL: /tiles/{z}/{x}/{y}.{ext} */
    private static final Pattern TILE_PATH_PATTERN =
            Pattern.compile("^/tiles/(\\d{1,2})/(\\d{1,7})/(\\d{1,7})\\.[a-z]{3,4}$");

    /** Maximum worker threads for serving tiles */
    private static final int MAX_WORKERS = 4;
    private static final int MAX_QUEUED_REQUESTS = 32;

    private final MBTilesReader reader;
    private HttpServer httpServer;
    private ExecutorService executor;
    private int port = -1;
    private final AtomicBoolean started = new AtomicBoolean(false);
    private final AtomicBoolean stopped = new AtomicBoolean(false);

    /**
     * Creates a new LocalTileServer backed by the given MBTilesReader.
     *
     * @param reader the MBTiles reader to serve tiles from
     */
    public LocalTileServer(MBTilesReader reader) {
        if (reader == null) {
            throw new IllegalArgumentException("MBTilesReader must not be null");
        }
        this.reader = reader;
    }

    /**
     * Starts the tile server on a dynamically assigned loopback port.
     *
     * @throws IOException if the server cannot be started
     */
    public synchronized void start() throws IOException {
        if (stopped.get()) {
            throw new IOException("LocalTileServer has been stopped and cannot be restarted");
        }
        if (started.get()) {
            return; // already started
        }

        // Bind to 127.0.0.1 only, port 0 for dynamic assignment
        InetSocketAddress bindAddress = new InetSocketAddress("127.0.0.1", 0);
        httpServer = HttpServer.create(bindAddress, 10); // backlog of 10

        // Register tile handler
        httpServer.createContext("/tiles/", new TileHandler());

        // Use bounded thread pool
        executor = new ThreadPoolExecutor(
                MAX_WORKERS, MAX_WORKERS, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(MAX_QUEUED_REQUESTS), r -> {
                    Thread t = new Thread(r, "TileServer-Worker");
                    t.setDaemon(true);
                    return t;
                },
                // Apply backpressure rather than accumulating unbounded tile requests.
                new ThreadPoolExecutor.CallerRunsPolicy());
        httpServer.setExecutor(executor);

        httpServer.start();
        port = httpServer.getAddress().getPort();
        started.set(true);

        LOGGER.info("LocalTileServer started on 127.0.0.1:" + port);
    }

    /**
     * Returns the port the server is listening on, or -1 if not started.
     */
    public int getPort() {
        return port;
    }

    /**
     * Returns the base URL for tile requests.
     */
    public String getTileUrlTemplate() {
        if (port < 0) return null;
        return "http://127.0.0.1:" + port + "/tiles/{z}/{x}/{y}.png";
    }

    /**
     * Returns whether the server has been started.
     */
    public boolean isStarted() {
        return started.get() && !stopped.get();
    }

    /** Visible for integration tests and operational diagnostics. */
    public int getQueuedRequestCount() {
        return executor instanceof ThreadPoolExecutor pool ? pool.getQueue().size() : 0;
    }

    public int getMaxQueuedRequests() {
        return MAX_QUEUED_REQUESTS;
    }

    @Override
    public synchronized void close() {
        if (stopped.getAndSet(true)) {
            return; // already stopped
        }
        started.set(false);

        if (httpServer != null) {
            httpServer.stop(1); // 1 second delay for graceful shutdown
            httpServer = null;
            LOGGER.info("LocalTileServer HTTP server stopped");
        }

        if (executor != null) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
            executor = null;
        }

        LOGGER.info("LocalTileServer fully stopped (port was " + port + ")");
        port = -1;
    }

    /**
     * HTTP handler that serves tile images from the MBTiles reader.
     */
    private class TileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                // Only allow GET requests
                if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                    sendError(exchange, 405, "Method Not Allowed");
                    return;
                }

                String path = exchange.getRequestURI().getPath();

                // Reject path traversal attempts
                if (path.contains("..") || path.contains("//") || path.contains("\\")) {
                    sendError(exchange, 400, "Bad Request: invalid path");
                    return;
                }

                // Parse tile coordinates from URL
                Matcher matcher = TILE_PATH_PATTERN.matcher(path);
                if (!matcher.matches()) {
                    sendError(exchange, 400, "Bad Request: invalid tile path format");
                    return;
                }

                int z, x, y;
                try {
                    z = Integer.parseInt(matcher.group(1));
                    x = Integer.parseInt(matcher.group(2));
                    y = Integer.parseInt(matcher.group(3));
                } catch (NumberFormatException e) {
                    sendError(exchange, 400, "Bad Request: invalid tile coordinates");
                    return;
                }

                // Validate coordinate ranges
                if (z < 0 || z > MAX_ZOOM) {
                    sendError(exchange, 400, "Bad Request: zoom level out of range (0-" + MAX_ZOOM + ")");
                    return;
                }
                int maxCoordForZoom = (1 << z);
                if (x < 0 || x >= maxCoordForZoom || y < 0 || y >= maxCoordForZoom) {
                    sendError(exchange, 400, "Bad Request: tile coordinates out of range for zoom " + z);
                    return;
                }

                // Retrieve tile
                Optional<byte[]> tile = reader.getTile(z, x, y);
                if (tile.isPresent()) {
                    byte[] data = tile.get();
                    exchange.getResponseHeaders().set("Content-Type", reader.getTileContentType());
                    exchange.getResponseHeaders().set("Cache-Control", "public, max-age=86400");
                    exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
                    exchange.sendResponseHeaders(200, data.length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(data);
                    }
                } else {
                    // Tile not found — return 404 with transparent 1x1 PNG
                    sendTransparentTile(exchange);
                }

            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Error handling tile request", e);
                try {
                    sendError(exchange, 500, "Internal Server Error");
                } catch (IOException ignored) {
                    // Response may already have been sent
                }
            }
        }

        /**
         * Sends a 404 response with a transparent 1x1 PNG image.
         * This allows the map to render gracefully when tiles are missing.
         */
        private void sendTransparentTile(HttpExchange exchange) throws IOException {
            // Minimal valid 1x1 transparent PNG
            byte[] transparentPng = {
                (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, // PNG signature
                0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52, // IHDR chunk
                0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01, // 1x1
                0x08, 0x06, 0x00, 0x00, 0x00, 0x1F, 0x15, (byte) 0xC4,
                (byte) 0x89, 0x00, 0x00, 0x00, 0x0A, 0x49, 0x44, 0x41, // IDAT chunk
                0x54, 0x78, (byte) 0x9C, 0x62, 0x00, 0x00, 0x00, 0x02,
                0x00, 0x01, (byte) 0xE5, 0x27, (byte) 0xDE, (byte) 0xFC, 0x00, 0x00, // data
                0x00, 0x00, 0x49, 0x45, 0x4E, 0x44, (byte) 0xAE, 0x42, // IEND chunk
                0x60, (byte) 0x82
            };
            exchange.getResponseHeaders().set("Content-Type", "image/png");
            exchange.sendResponseHeaders(404, transparentPng.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(transparentPng);
            }
        }

        /**
         * Sends a text error response.
         */
        private void sendError(HttpExchange exchange, int code, String message) throws IOException {
            byte[] body = message.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
            exchange.sendResponseHeaders(code, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        }
    }
}
