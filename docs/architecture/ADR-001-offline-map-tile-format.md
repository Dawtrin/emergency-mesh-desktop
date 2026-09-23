# ADR-001: Offline Map Tile Format

## 1. Status
Accepted

## 2. Context
The dashboard currently loads map tiles from CartoDB and OpenStreetMap CDNs, which fails when offline. We need a fully offline solution.

## 3. Decision
Use MBTiles (SQLite-based) as the local tile format.

## 4. Rationale
- MBTiles is a widely-adopted SQLite-based format (spec by Mapbox)
- Single file, easy to copy/distribute
- Already have `sqlite-jdbc` in `pom.xml`
- Supported by tools like TileMill, QGIS, tilemaker
- Stores PNG/JPEG/WebP tiles indexed by z/x/y

## 5. Runtime Map Path Configuration
- Default path: `data/map/region.mbtiles` relative to application working directory
- Overridable via `--map-file <path>` command line argument
- Environment variable `MESH_MAP_FILE` as fallback

## 6. Supported Format & Zoom Levels
- MBTiles v1.3 specification
- PNG or JPEG tile format
- Zoom levels 1-18 (typical production), test fixtures use zoom 0-2
- TMS (y-flipped) coordinate scheme with automatic detection

## 7. Missing/Corrupt Map Behavior
- Display tactical grid background (dark theme grid pattern)
- Show clear error message in the map area: "Offline map not found"
- Log detailed error to application log
- Never fall back to online tiles
- Never crash

## 8. Attribution & Licensing
- MBTiles format: CC-BY-SA 3.0 (Mapbox specification)
- OpenStreetMap data: ODbL 1.0 (must display attribution)
- Attribution displayed in map corner
- Test fixture: hand-crafted minimal PNG, no third-party data

## 9. Instructions for Preparing Demo Map
- Use tilemaker or QGIS to extract a region
- Export to MBTiles format
- Copy to `data/map/region.mbtiles`
- Recommended: Da Nang area, zoom 10-17, approximately 50-200MB

## 10. Large Files Policy
- Production MBTiles files stay outside Git
- `.gitignore` includes `data/map/*.mbtiles` or `data/map/`
- Only test fixture (tiny MBTiles with 1-4 tiles) is committed
- Test fixture located at `src/test/resources/fixtures/test-tiles.mbtiles`
