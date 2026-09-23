package com.rescue.mesh.demo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class DemoPreflightTest {
    @TempDir Path tempDir;

    @Test
    void profileAndPreflightAcceptValidInputs() throws Exception {
        Path map = tempDir.resolve("demo.mbtiles");
        Files.write(map, "SQLite format 3\000fixture".getBytes(StandardCharsets.US_ASCII));
        Path base = Files.writeString(tempDir.resolve("base.jar"), "jar");
        Path node = Files.writeString(tempDir.resolve("node.jar"), "jar");
        Path profileFile = writeProfile("false", map.getFileName().toString(), "192.168.1.10");
        DemoProfile profile = DemoProfile.load(profileFile);
        DemoPreflight.Result result = DemoPreflight.check(profile, base, node, 21, (host, port) -> true);
        assertTrue(result.passed(), result.messages().toString());
    }

    @Test
    void preflightReportsEveryCommonFailureWithoutTouchingSystem() throws Exception {
        Path profileFile = writeProfile("true", "missing.mbtiles", "127.0.0.1");
        DemoProfile profile = DemoProfile.load(profileFile);
        DemoPreflight.Result result = DemoPreflight.check(profile, tempDir.resolve("missing-base.jar"),
                tempDir.resolve("missing-node.jar"), 17, (host, port) -> false);
        assertFalse(result.passed());
        String report = String.join("\n", result.messages());
        assertTrue(report.contains("JDK 21"));
        assertTrue(report.contains("port đang bận"));
        assertTrue(report.contains("map MBTiles"));
        assertTrue(report.contains("127.0.0.1"));
        assertTrue(report.contains("Firewall"));
    }

    private Path writeProfile(String lan, String map, String host) throws Exception {
        return Files.writeString(tempDir.resolve("profile.properties"), """
                profile.name=test
                profile.threeComputerLan=%s
                base.nodeId=BASE-01
                base.bindHost=%s
                base.port=18888
                base.peerHost=%s
                base.peerPort=18002
                relay.nodeId=RELAY-01
                relay.bindHost=%s
                relay.port=18002
                relay.peerHost=%s
                relay.peerPort=18888
                victim.nodeId=VICTIM-01
                victim.bindHost=%s
                victim.port=18001
                victim.peerHost=%s
                victim.peerPort=18002
                map.file=%s
                retry.maxAttempts=5
                retry.initialDelayMs=1
                retry.maxDelayMs=2
                """.formatted(lan, host, host, host, host, host, host, map));
    }
}
