package com.rescue.mesh.map;

import java.io.Closeable;
import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Reads map tiles from an MBTiles (SQLite) file.
 * <p>
 * MBTiles spec: https://github.com/mapbox/mbtiles-spec/blob/master/1.3/spec.md
 * <p>
 * Tiles are indexed by (zoom_level, tile_column, tile_row) where tile_row
 * uses TMS (y-flipped) numbering. This reader auto-detects and handles both
 * TMS and XYZ schemes transparently.
 * <p>
 * Thread-safe: uses synchronized access to the underlying SQLite connection.
 */
public class MBTilesReader implements Closeable {

    private static final Logger LOGGER = Logger.getLogger(MBTilesReader.class.getName());

    /** Maximum cached tiles (bounded LRU cache) */
    private static final int MAX_CACHE_SIZE = 256;

    private final File mbtilesFile;
    private Connection connection;
    private volatile boolean closed = false;

    /** Content type from metadata, defaults to image/png */
    private String tileContentType = "image/png";

    /** Whether tiles use TMS y-flip (default per MBTiles spec) */
    private boolean tmsYFlip = true;
    private int minZoom = 0;
    private int maxZoom = 19;

    /** LRU tile cache with bounded size */
    private final Map<String, byte[]> tileCache = new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, byte[]> eldest) {
            return size() > MAX_CACHE_SIZE;
        }
    };

    /**
     * Creates a new MBTilesReader for the given file.
     *
     * @param mbtilesFile path to the .mbtiles file
     * @throws IllegalArgumentException if file is null
     */
    public MBTilesReader(File mbtilesFile) {
        if (mbtilesFile == null) {
            throw new IllegalArgumentException("MBTiles file path must not be null");
        }
        this.mbtilesFile = mbtilesFile;
    }

    /**
     * Opens the MBTiles database and reads metadata.
     *
     * @throws SQLException if the file cannot be opened or is not a valid MBTiles database
     */
    public synchronized void open() throws SQLException {
        if (closed) {
            throw new SQLException("MBTilesReader has been closed");
        }
        if (connection != null && !connection.isClosed()) {
            return; // already open
        }

        if (!mbtilesFile.exists()) {
            throw new SQLException("MBTiles file does not exist: " + mbtilesFile.getAbsolutePath());
        }
        if (!mbtilesFile.canRead()) {
            throw new SQLException("MBTiles file is not readable: " + mbtilesFile.getAbsolutePath());
        }

        String url = "jdbc:sqlite:" + mbtilesFile.getAbsolutePath();
        connection = DriverManager.getConnection(url);

        try {
            // Validate that required tables exist
            validateSchema();

            // Read metadata
            readMetadata();
        } catch (SQLException e) {
            close();
            throw e;
        }
    }

    /**
     * Validates that the MBTiles file has the required tables.
     */
    private void validateSchema() throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT count(*) FROM sqlite_master WHERE type='table' AND name IN ('tiles', 'metadata')")) {
            ResultSet rs = ps.executeQuery();
            if (rs.next() && rs.getInt(1) < 2) {
                throw new SQLException("Invalid MBTiles file: missing 'tiles' or 'metadata' table");
            }
        }
    }

    /**
     * Reads metadata from the MBTiles file to determine tile format.
     */
    private void readMetadata() throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT value FROM metadata WHERE name = ?")) {
            // Determine tile format
            ps.setString(1, "format");
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String format = rs.getString(1);
                    if ("jpg".equalsIgnoreCase(format) || "jpeg".equalsIgnoreCase(format)) {
                        tileContentType = "image/jpeg";
                    } else if ("webp".equalsIgnoreCase(format)) {
                        tileContentType = "image/webp";
                    } else if ("pbf".equalsIgnoreCase(format)) {
                        tileContentType = "application/x-protobuf";
                    } else {
                        tileContentType = "image/png";
                    }
                }
            }

            // Check scheme (tms vs xyz)
            ps.setString(1, "scheme");
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String scheme = rs.getString(1);
                    tmsYFlip = !"xyz".equalsIgnoreCase(scheme);
                }
            }
        }
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT MIN(zoom_level), MAX(zoom_level) FROM tiles");
             ResultSet rs = ps.executeQuery()) {
            if (rs.next() && rs.getObject(1) != null && rs.getObject(2) != null) {
                minZoom = rs.getInt(1);
                maxZoom = rs.getInt(2);
            }
        }
        LOGGER.info("MBTiles opened: " + mbtilesFile.getName()
                + " format=" + tileContentType + " tmsFlip=" + tmsYFlip);
    }

    /**
     * Retrieves a tile image as raw bytes.
     *
     * @param z zoom level (0-22)
     * @param x tile column
     * @param y tile row (in XYZ/slippy map convention)
     * @return the tile data, or empty if not found
     */
    public Optional<byte[]> getTile(int z, int x, int y) {
        if (closed) {
            return Optional.empty();
        }

        // Convert XYZ y to TMS y if needed
        int tmsY = tmsYFlip ? ((1 << z) - 1 - y) : y;

        String cacheKey = z + "/" + x + "/" + tmsY;

        // Check cache first
        synchronized (tileCache) {
            byte[] cached = tileCache.get(cacheKey);
            if (cached != null) {
                return Optional.of(cached);
            }
        }

        // Query database
        try {
            byte[] tileData = queryTile(z, x, tmsY);
            if (tileData != null) {
                synchronized (tileCache) {
                    tileCache.put(cacheKey, tileData);
                }
                return Optional.of(tileData);
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Error reading tile " + cacheKey, e);
        }

        return Optional.empty();
    }

    /**
     * Queries a single tile from the database.
     */
    private synchronized byte[] queryTile(int z, int x, int tmsY) throws SQLException {
        if (connection == null || connection.isClosed()) {
            return null;
        }
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT tile_data FROM tiles WHERE zoom_level = ? AND tile_column = ? AND tile_row = ?")) {
            ps.setInt(1, z);
            ps.setInt(2, x);
            ps.setInt(3, tmsY);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getBytes(1);
                }
            }
        }
        return null;
    }

    /**
     * Returns the content type for tiles in this MBTiles file.
     */
    public String getTileContentType() {
        return tileContentType;
    }

    public int getMinZoom() { return minZoom; }

    public int getMaxZoom() { return maxZoom; }

    /**
     * Returns the number of cached tiles.
     */
    public int getCacheSize() {
        synchronized (tileCache) {
            return tileCache.size();
        }
    }

    public int getCacheCapacity() {
        return MAX_CACHE_SIZE;
    }

    /**
     * Clears the tile cache.
     */
    public void clearCache() {
        synchronized (tileCache) {
            tileCache.clear();
        }
    }

    /**
     * Returns whether this reader has been closed.
     */
    public boolean isClosed() {
        return closed;
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;
        synchronized (tileCache) {
            tileCache.clear();
        }
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
                LOGGER.log(Level.WARNING, "Error closing MBTiles connection", e);
            }
            connection = null;
        }
        LOGGER.info("MBTilesReader closed: " + mbtilesFile.getName());
    }
}
