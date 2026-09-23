package com.rescue.mesh.network;

import com.rescue.mesh.network.NodeConfig.NodeMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class NodeConfigTest {

    @TempDir
    Path tempDir;

    @Test
    void localDefaultsAreModeSpecificAndLoopbackOnly() {
        NodeConfig victim = NodeConfig.fromArgs(new String[]{"--mode", "VICTIM"});
        NodeConfig relay = NodeConfig.fromArgs(new String[]{"--mode", "RELAY"});
        NodeConfig base = NodeConfig.fromArgs(new String[]{"--mode", "BASE_STATION"});

        assertAll(
                () -> assertEquals("127.0.0.1", victim.getBindHost()),
                () -> assertEquals(8001, victim.getListenPort()),
                () -> assertEquals(8002, victim.getNextHopPort()),
                () -> assertEquals("NODE_B_RELAY", relay.getNodeId()),
                () -> assertEquals(8002, relay.getListenPort()),
                () -> assertEquals(8888, relay.getNextHopPort()),
                () -> assertEquals("NODE_A_VICTIM", relay.getVictimNodeId()),
                () -> assertEquals("127.0.0.1", relay.getVictimHost()),
                () -> assertEquals(8001, relay.getVictimPort()),
                () -> assertEquals("BASE_STATION", base.getNodeId()),
                () -> assertEquals(8888, base.getListenPort()),
                () -> assertEquals(-1, base.getNextHopPort()),
                () -> assertEquals(8002, base.getRelayPort()));
    }

    @Test
    void parsesExplicitLanTopologyAndMapFile() {
        Path map = Path.of("src/test/resources/fixtures/test-tiles.mbtiles");

        NodeConfig config = NodeConfig.fromArgs(new String[]{
                "--mode", "BASE_STATION",
                "--id", "COMMAND-01",
                "--bind-host", "0.0.0.0",
                "--bind-port", "19000",
                "--relay-host", "192.168.10.22",
                "--relay-port", "19002",
                "--map-file", map.toString()
        });

        assertAll(
                () -> assertEquals(NodeMode.BASE_STATION, config.getMode()),
                () -> assertEquals("COMMAND-01", config.getNodeId()),
                () -> assertEquals("0.0.0.0", config.getBindHost()),
                () -> assertEquals(19000, config.getListenPort()),
                () -> assertEquals("192.168.10.22", config.getRelayHost()),
                () -> assertEquals(19002, config.getRelayPort()),
                () -> assertEquals(map.toString(), config.getMapFilePath()));
    }

    @Test
    void parsesExplicitRelayDownstreamRoute() {
        NodeConfig relay = NodeConfig.fromArgs(new String[]{
                "--mode", "RELAY",
                "--victim-id", "VICTIM-LAN-7",
                "--victim-host", "192.168.10.31",
                "--victim-port", "19001"
        });

        assertAll(
                () -> assertEquals("VICTIM-LAN-7", relay.getVictimNodeId()),
                () -> assertEquals("192.168.10.31", relay.getVictimHost()),
                () -> assertEquals(19001, relay.getVictimPort()));
    }

    @Test
    void supportsDocumentedLegacyPortAliases() {
        NodeConfig config = NodeConfig.fromArgs(new String[]{
                "--mode", "RELAY", "--port", "18002", "--host", "10.0.0.5",
                "--next-hop", "18888", "--id", "RELAY-02"
        });

        assertEquals(18002, config.getListenPort());
        assertEquals("10.0.0.5", config.getNextHopHost());
        assertEquals(18888, config.getNextHopPort());
    }

    @Test
    void rejectsUnknownMissingDuplicateAndConflictingOptions() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> NodeConfig.fromArgs(new String[]{"--wat", "x"})),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> NodeConfig.fromArgs(new String[]{"--mode"})),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> NodeConfig.fromArgs(new String[]{"--id", "A", "--id", "B"})),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> NodeConfig.fromArgs(new String[]{"--port", "8001", "--bind-port", "8002"})));
    }

    @Test
    void rejectsInvalidModeNodeHostPortsAndMapPath() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> NodeConfig.fromArgs(new String[]{"--mode", "ANDROID"})),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> NodeConfig.fromArgs(new String[]{"--id", "bad node id"})),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> NodeConfig.fromArgs(new String[]{"--bind-host", "  "})),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> NodeConfig.fromArgs(new String[]{"--port", "0"})),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> NodeConfig.fromArgs(new String[]{"--next-hop", "65536"})),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> NodeConfig.fromArgs(new String[]{"--relay-port", "abc"})),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> NodeConfig.fromArgs(new String[]{"--mode", "RELAY", "--victim-id", "bad victim"})),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> NodeConfig.fromArgs(new String[]{"--mode", "RELAY", "--victim-host", "  "})),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> NodeConfig.fromArgs(new String[]{"--mode", "RELAY", "--victim-port", "0"})),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> NodeConfig.fromArgs(new String[]{"--mode", "BASE_STATION", "--map-file",
                                tempDir.resolve("missing.mbtiles").toString()})),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> NodeConfig.fromArgs(new String[]{"--mode", "BASE_STATION", "--map-file",
                                Files.createFile(tempDir.resolve("empty.mbtiles")).toString()})));
    }
}
