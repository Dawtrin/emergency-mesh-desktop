package com.rescue.mesh.network;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NodeConfigPropertiesTest {
    @TempDir Path temporaryDirectory;

    @Test
    void loadsRelayTopologyFromPropertiesAndLetsCliOverridePort() throws Exception {
        Path file = temporaryDirectory.resolve("relay.properties");
        Files.writeString(file, String.join(System.lineSeparator(),
                "node.id=RELAY-01", "node.role=RELAY", "bind.host=0.0.0.0", "self.port=18002",
                "upstream.host=192.168.56.1", "upstream.port=18888", "victim.id=VICTIM-01",
                "victim.host=127.0.0.1", "victim.port=18001"));

        NodeConfig config = NodeConfig.fromArgs(new String[] {
                "--config", file.toString(), "--bind-port", "19002" });

        assertEquals(NodeConfig.NodeMode.RELAY, config.getMode());
        assertEquals("RELAY-01", config.getNodeId());
        assertEquals(19002, config.getListenPort());
        assertEquals("192.168.56.1", config.getNextHopHost());
        assertEquals(18888, config.getNextHopPort());
        assertEquals("VICTIM-01", config.getVictimNodeId());
        assertEquals("127.0.0.1", config.getVictimHost());
        assertEquals(18001, config.getVictimPort());
    }
}
