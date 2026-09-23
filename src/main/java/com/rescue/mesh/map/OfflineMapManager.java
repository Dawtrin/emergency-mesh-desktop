package com.rescue.mesh.map;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manages the lifecycle of offline map resources: MBTilesReader and LocalTileServer.
 * <p>
 * Usage:
 * <pre>
 *   OfflineMapManager mgr = new OfflineMapManager();
 *   mgr.setMapFilePath("data/map/region.mbtiles");
 *   mgr.start();
 *   String tileUrl = mgr.getTileUrlTemplate(); // http://127.0.0.1:{port}/tiles/{z}/{x}/{y}.png
 *   // ... use in Leaflet ...
 *   mgr.close(); // on shutdown
 * </pre>
 */
public class OfflineMapManager implements Closeable {

    private static final Logger LOGGER = Logger.getLogger(OfflineMapManager.class.getName());

    /** Default map file path relative to working directory */
    public static final String DEFAULT_MAP_PATH = "data/map/region.mbtiles";

    /** Environment variable to override map path */
    public static final String ENV_MAP_FILE = "MESH_MAP_FILE";

    private String mapFilePath;
    private MBTilesReader reader;
    private LocalTileServer tileServer;
    private volatile boolean started = false;
    private volatile boolean closed = false;

    /** Error message if map initialization failed */
    private String errorMessage;

    /**
     * Sets the path to the MBTiles file.
     * @param path file path (absolute or relative to working directory)
     */
    public void setMapFilePath(String path) {
        this.mapFilePath = path;
    }

    /**
     * Resolves the map file path from (in priority order):
     * 1. Explicitly set path via setMapFilePath
     * 2. MESH_MAP_FILE environment variable
     * 3. Default path: data/map/region.mbtiles
     */
    private File resolveMapFile() {
        String path = mapFilePath;
        if (path == null || path.isBlank()) {
            path = System.getenv(ENV_MAP_FILE);
        }
        if (path == null || path.isBlank()) {
            path = DEFAULT_MAP_PATH;
        }
        return new File(path);
    }

    /**
     * Starts the offline map system:
     * 1. Opens the MBTiles file
     * 2. Starts the local tile server
     *
     * @return true if both reader and server started successfully
     */
    public boolean start() {
        if (closed) {
            errorMessage = "OfflineMapManager has been closed";
            return false;
        }
        if (started) {
            return true;
        }

        File mapFile = resolveMapFile();

        // Check if map file exists
        if (!mapFile.exists()) {
            errorMessage = "Offline map file not found: " + mapFile.getAbsolutePath()
                    + "\nPlace an MBTiles file at '" + DEFAULT_MAP_PATH
                    + "' or set --map-file <path> or MESH_MAP_FILE environment variable.";
            LOGGER.warning(errorMessage);
            return false;
        }

        if (!mapFile.canRead()) {
            errorMessage = "Offline map file is not readable: " + mapFile.getAbsolutePath();
            LOGGER.warning(errorMessage);
            return false;
        }

        // Open MBTiles reader
        try {
            reader = new MBTilesReader(mapFile);
            reader.open();
        } catch (SQLException e) {
            errorMessage = "Failed to open offline map: " + e.getMessage();
            LOGGER.log(Level.WARNING, errorMessage, e);
            reader = null;
            return false;
        }

        // Start local tile server
        try {
            tileServer = new LocalTileServer(reader);
            tileServer.start();
        } catch (IOException e) {
            errorMessage = "Failed to start tile server: " + e.getMessage();
            LOGGER.log(Level.WARNING, errorMessage, e);
            reader.close();
            reader = null;
            tileServer = null;
            return false;
        }

        started = true;
        errorMessage = null;
        LOGGER.info("OfflineMapManager started: " + mapFile.getName()
                + " → " + getTileUrlTemplate());
        return true;
    }

    /**
     * Returns the Leaflet tile URL template, or null if not started.
     */
    public String getTileUrlTemplate() {
        return tileServer != null ? tileServer.getTileUrlTemplate() : null;
    }

    /**
     * Returns the port the tile server is listening on, or -1.
     */
    public int getTileServerPort() {
        return tileServer != null ? tileServer.getPort() : -1;
    }

    public int getMinNativeZoom() {
        return reader != null ? reader.getMinZoom() : 0;
    }

    public int getMaxNativeZoom() {
        return reader != null ? reader.getMaxZoom() : 19;
    }

    /**
     * Returns true if the map system is running.
     */
    public boolean isStarted() {
        return started && !closed;
    }

    /**
     * Returns the error message if initialization failed.
     */
    public String getErrorMessage() {
        return errorMessage;
    }

    /**
     * Returns the MBTilesReader, or null if not started.
     */
    public MBTilesReader getReader() {
        return reader;
    }

    /**
     * Returns the LocalTileServer, or null if not started.
     */
    public LocalTileServer getTileServer() {
        return tileServer;
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;
        started = false;

        if (tileServer != null) {
            tileServer.close();
            tileServer = null;
        }
        if (reader != null) {
            reader.close();
            reader = null;
        }

        LOGGER.info("OfflineMapManager closed");
    }
}
