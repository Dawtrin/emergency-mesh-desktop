package com.rescue.mesh.demo;

import com.rescue.mesh.network.NodeConfig;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Properties;

/** Versioned, secret-free demo topology profile. */
public record DemoProfile(String profileName, boolean threeComputerLan,
                          Endpoint base, Endpoint relay, Endpoint victim,
                          Path mapFile, int retryMaxAttempts, long retryInitialDelayMs,
                          long retryMaxDelayMs) {
    public record Endpoint(String nodeId, String bindHost, int port, String peerHost, int peerPort) {
        public Endpoint {
            NodeConfig.fromArgs(new String[]{"--mode", "VICTIM", "--id", nodeId,
                    "--bind-host", bindHost, "--bind-port", String.valueOf(port),
                    "--next-hop-host", peerHost, "--next-hop-port", String.valueOf(peerPort)});
        }
    }

    public static DemoProfile load(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        Properties p = new Properties();
        try (Reader reader = Files.newBufferedReader(path)) { p.load(reader); }
        Path map = Path.of(required(p, "map.file"));
        if (!map.isAbsolute()) map = path.toAbsolutePath().getParent().resolve(map).normalize();
        return new DemoProfile(required(p, "profile.name"), Boolean.parseBoolean(required(p, "profile.threeComputerLan")),
                endpoint(p, "base"), endpoint(p, "relay"), endpoint(p, "victim"), map,
                integer(p, "retry.maxAttempts"), integer(p, "retry.initialDelayMs"), integer(p, "retry.maxDelayMs"));
    }

    private static Endpoint endpoint(Properties p, String name) {
        return new Endpoint(required(p, name + ".nodeId"), required(p, name + ".bindHost"),
                integer(p, name + ".port"), required(p, name + ".peerHost"), integer(p, name + ".peerPort"));
    }
    private static String required(Properties p, String key) {
        String value = p.getProperty(key);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing required demo profile value: " + key);
        return value.trim();
    }
    private static int integer(Properties p, String key) {
        try { return Integer.parseInt(required(p, key)); }
        catch (NumberFormatException e) { throw new IllegalArgumentException("Invalid integer for " + key, e); }
    }
}
