package com.rescue.mesh.map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Phase 3.3: MBTilesReader Tests")
class MBTilesReaderTest {

    private File fixtureFile;
    private MBTilesReader reader;

    @BeforeEach
    void setUp() {
        fixtureFile = new File("src/test/resources/fixtures/test-tiles.mbtiles");
        assertTrue(fixtureFile.exists(), "Fixture test-tiles.mbtiles must exist at " + fixtureFile.getAbsolutePath());
    }

    @AfterEach
    void tearDown() {
        if (reader != null) {
            reader.close();
        }
    }

    @Test
    @DisplayName("Open MBTiles and read metadata correctly")
    void testOpenAndReadMetadata() throws SQLException {
        reader = new MBTilesReader(fixtureFile);
        reader.open();

        assertFalse(reader.isClosed());
        assertEquals("image/png", reader.getTileContentType());
    }

    @Test
    @DisplayName("Retrieve existing tile returns valid PNG data")
    void testGetExistingTile() throws SQLException {
        reader = new MBTilesReader(fixtureFile);
        reader.open();

        // In test fixture: z=0, x=0, y=0 (TMS y=0 means XYZ y=0 for z=0: (1<<0)-1-0 = 0)
        Optional<byte[]> tileData = reader.getTile(0, 0, 0);
        assertTrue(tileData.isPresent(), "Tile at 0/0/0 should exist in fixture");

        byte[] bytes = tileData.get();
        assertTrue(bytes.length > 0, "Tile data should not be empty");

        // Verify PNG signature: 89 50 4E 47 0D 0A 1A 0A
        assertEquals((byte) 0x89, bytes[0]);
        assertEquals((byte) 0x50, bytes[1]);
        assertEquals((byte) 0x4E, bytes[2]);
        assertEquals((byte) 0x47, bytes[3]);
    }

    @Test
    @DisplayName("Retrieve non-existing tile returns empty Optional")
    void testGetNonExistentTile() throws SQLException {
        reader = new MBTilesReader(fixtureFile);
        reader.open();

        Optional<byte[]> tileData = reader.getTile(10, 500, 300);
        assertFalse(tileData.isPresent(), "Non-existent tile should return empty");
    }

    @Test
    @DisplayName("Tile caching works and bounds cache size")
    void testTileCaching() throws SQLException {
        reader = new MBTilesReader(fixtureFile);
        reader.open();

        assertEquals(0, reader.getCacheSize());

        // First call loads into cache
        Optional<byte[]> tile1 = reader.getTile(0, 0, 0);
        assertTrue(tile1.isPresent());
        assertEquals(1, reader.getCacheSize());

        // Second call retrieves from cache
        Optional<byte[]> tile2 = reader.getTile(0, 0, 0);
        assertTrue(tile2.isPresent());
        assertSame(tile1.get(), tile2.get(), "Cached tile should return identical byte array reference");
        assertEquals(1, reader.getCacheSize());

        // Clear cache
        reader.clearCache();
        assertEquals(0, reader.getCacheSize());
    }

    @Test
    @DisplayName("Tile cache stays bounded when many distinct tiles are read")
    void testTileCacheEvictsAtCapacity(@TempDir Path tempDir) throws Exception {
        File mapFile = tempDir.resolve("many-tiles.mbtiles").toFile();
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + mapFile.getAbsolutePath());
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE metadata (name TEXT, value TEXT)");
            stmt.execute("CREATE TABLE tiles (zoom_level INTEGER, tile_column INTEGER, tile_row INTEGER, tile_data BLOB)");
            stmt.execute("INSERT INTO metadata VALUES ('format', 'png')");
            for (int x = 0; x < 300; x++) {
                stmt.execute("INSERT INTO tiles VALUES (9, " + x + ", 511, X'89504E47')");
            }
        }

        reader = new MBTilesReader(mapFile);
        reader.open();
        for (int x = 0; x < 300; x++) {
            assertTrue(reader.getTile(9, x, 0).isPresent());
        }
        assertEquals(reader.getCacheCapacity(), reader.getCacheSize());
    }

    @Test
    @DisplayName("Opening missing file throws SQLException")
    void testOpenMissingFile() {
        File missing = new File("non_existent_map_file.mbtiles");
        MBTilesReader r = new MBTilesReader(missing);
        assertThrows(SQLException.class, r::open);
        r.close();
    }

    @Test
    @DisplayName("Opening corrupt SQLite database without tiles/metadata tables throws SQLException")
    void testOpenCorruptDatabase(@TempDir Path tempDir) throws Exception {
        File corruptFile = tempDir.resolve("corrupt.mbtiles").toFile();
        // Create an SQLite db with wrong schema
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + corruptFile.getAbsolutePath());
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE foo (id INTEGER PRIMARY KEY)");
        }

        MBTilesReader r = new MBTilesReader(corruptFile);
        SQLException ex = assertThrows(SQLException.class, r::open);
        assertTrue(ex.getMessage().contains("missing 'tiles' or 'metadata' table"));
        r.close();
    }

    @Test
    @DisplayName("Closing reader releases resources and prevents further queries")
    void testCloseReader() throws SQLException {
        reader = new MBTilesReader(fixtureFile);
        reader.open();
        assertFalse(reader.isClosed());

        reader.close();
        assertTrue(reader.isClosed());

        // Queries after close return empty
        Optional<byte[]> tile = reader.getTile(0, 0, 0);
        assertFalse(tile.isPresent());

        // Re-opening after close throws SQLException
        assertThrows(SQLException.class, () -> reader.open());
    }
}
