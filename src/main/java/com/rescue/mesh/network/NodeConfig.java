package com.rescue.mesh.network;

import com.rescue.mesh.map.MBTilesReader;
import com.rescue.mesh.map.OfflineMapManager;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Immutable, validated runtime topology for one desktop mesh process.
 *
 * <p>Local demo defaults are deliberately explicit in this class. Every value
 * can be overridden for a LAN deployment without relying on a node-ID or port
 * convention.</p>
 */
public final class NodeConfig {

    public enum NodeMode {
        VICTIM,
        RELAY,
        BASE_STATION
    }

    public static final String DEFAULT_BIND_HOST = "127.0.0.1";
    public static final String DEFAULT_NEXT_HOP_HOST = "127.0.0.1";
    public static final String DEFAULT_RELAY_HOST = "127.0.0.1";
    public static final String DEFAULT_VICTIM_HOST = "127.0.0.1";

    private static final Pattern NODE_ID_PATTERN =
            Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,63}");
    private static final Set<String> VALUE_OPTIONS = Set.of(
            "--mode", "--id", "--bind-host", "--bind-port", "--port",
            "--next-hop-host", "--next-hop-port", "--next-hop", "--host",
            "--relay-host", "--relay-port", "--victim-id", "--victim-host",
            "--victim-port", "--map-file");

    private final NodeMode mode;
    private final String nodeId;
    private final String bindHost;
    private final int listenPort;
    private final String nextHopHost;
    private final int nextHopPort;
    private final String relayHost;
    private final int relayPort;
    private final String victimNodeId;
    private final String victimHost;
    private final int victimPort;
    private final String mapFilePath;

    /**
     * Compatibility constructor for existing embedded uses. New application
     * code should parse command-line arguments or use the full constructor.
     */
    public NodeConfig(NodeMode mode, String nodeId,
                      int listenPort, String nextHopHost, int nextHopPort) {
        this(mode, nodeId, DEFAULT_BIND_HOST, listenPort, nextHopHost, nextHopPort,
                DEFAULT_RELAY_HOST, 8002, null);
    }

    public NodeConfig(NodeMode mode, String nodeId, String bindHost,
                      int listenPort, String nextHopHost, int nextHopPort,
                      String relayHost, int relayPort, String mapFilePath) {
        this(mode, nodeId, bindHost, listenPort, nextHopHost, nextHopPort,
                relayHost, relayPort, "NODE_A_VICTIM", DEFAULT_VICTIM_HOST,
                8001, mapFilePath);
    }

    public NodeConfig(NodeMode mode, String nodeId, String bindHost,
                      int listenPort, String nextHopHost, int nextHopPort,
                      String relayHost, int relayPort, String victimNodeId,
                      String victimHost, int victimPort, String mapFilePath) {
        this.mode = requireNonNull(mode, "mode");
        this.nodeId = validateNodeId(nodeId);
        this.bindHost = validateHost("bind host", bindHost);
        this.listenPort = validatePort("bind port", listenPort);
        this.nextHopHost = nextHopPort > 0
                ? validateHost("next-hop host", nextHopHost)
                : normalizeOptionalHost(nextHopHost);
        this.nextHopPort = validateOptionalPort("next-hop port", nextHopPort);
        this.relayHost = relayPort > 0
                ? validateHost("relay host", relayHost)
                : normalizeOptionalHost(relayHost);
        this.relayPort = validateOptionalPort("relay port", relayPort);
        this.victimNodeId = victimPort > 0
                ? validateNodeId(victimNodeId)
                : normalizeOptional(victimNodeId);
        this.victimHost = victimPort > 0
                ? validateHost("victim host", victimHost)
                : normalizeOptionalHost(victimHost);
        this.victimPort = validateOptionalPort("victim port", victimPort);
        this.mapFilePath = normalizeOptional(mapFilePath);

        if (mode != NodeMode.BASE_STATION && nextHopPort < 1) {
            throw new IllegalArgumentException(mode + " requires --next-hop-host and --next-hop-port");
        }
        if (mode == NodeMode.BASE_STATION && relayPort < 1) {
            throw new IllegalArgumentException("BASE_STATION requires --relay-host and --relay-port");
        }
        if (mode == NodeMode.RELAY && victimPort < 1) {
            throw new IllegalArgumentException("RELAY requires --victim-id, --victim-host, and --victim-port");
        }
    }

    /**
     * Parses command-line options and fails fast for malformed or unknown input.
     * Legacy aliases {@code --port}, {@code --next-hop}, and {@code --host}
     * remain supported for the existing local demo scripts.
     */
    public static NodeConfig fromArgs(String[] args) {
        if (args == null) {
            args = new String[0];
        }

        Map<String, String> options = new HashMap<>();
        for (int i = 0; i < args.length; i++) {
            String option = args[i].toLowerCase(Locale.ROOT);
            if (!VALUE_OPTIONS.contains(option)) {
                throw new IllegalArgumentException("Unknown option: " + args[i]);
            }
            if (i + 1 >= args.length || args[i + 1].startsWith("--")) {
                throw new IllegalArgumentException("Missing value for option " + args[i]);
            }
            if (options.put(option, args[++i]) != null) {
                throw new IllegalArgumentException("Duplicate option: " + option);
            }
        }

        NodeMode mode = parseMode(options.getOrDefault("--mode", "VICTIM"));
        Defaults defaults = Defaults.forMode(mode);

        String nodeId = options.getOrDefault("--id", defaults.nodeId());
        String bindHost = options.getOrDefault("--bind-host", DEFAULT_BIND_HOST);
        int bindPort = parsePortOption(options, "--bind-port", "--port", defaults.bindPort());

        String nextHopHost = firstPresent(options, "--next-hop-host", "--host");
        if (nextHopHost == null) {
            nextHopHost = DEFAULT_NEXT_HOP_HOST;
        }
        int nextHopPort = parsePortOption(options, "--next-hop-port", "--next-hop", defaults.nextHopPort());

        String relayHost = options.getOrDefault("--relay-host", DEFAULT_RELAY_HOST);
        int relayPort = parseSinglePort(options, "--relay-port", defaults.relayPort());
        String victimNodeId = options.getOrDefault("--victim-id", defaults.victimNodeId());
        String victimHost = options.getOrDefault("--victim-host", DEFAULT_VICTIM_HOST);
        int victimPort = parseSinglePort(options, "--victim-port", defaults.victimPort());
        String mapFile = options.get("--map-file");
        validateExplicitMapFile(mapFile);

        if (mode == NodeMode.BASE_STATION) {
            nextHopPort = -1;
        }

        return new NodeConfig(mode, nodeId, bindHost, bindPort, nextHopHost,
                nextHopPort, relayHost, relayPort, victimNodeId, victimHost,
                victimPort, mapFile);
    }

    private static NodeMode parseMode(String value) {
        try {
            return NodeMode.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid mode '" + value
                    + "'; expected VICTIM, RELAY, or BASE_STATION", e);
        }
    }

    private static int parsePortOption(Map<String, String> options, String primary,
                                       String alias, int defaultValue) {
        if (options.containsKey(primary) && options.containsKey(alias)) {
            throw new IllegalArgumentException("Use either " + primary + " or " + alias + ", not both");
        }
        String value = firstPresent(options, primary, alias);
        return value == null ? defaultValue : parsePort(primary, value);
    }

    private static int parseSinglePort(Map<String, String> options, String option, int defaultValue) {
        String value = options.get(option);
        return value == null ? defaultValue : parsePort(option, value);
    }

    private static int parsePort(String option, String value) {
        try {
            return validatePort(option, Integer.parseInt(value));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(option + " must be an integer from 1 to 65535: " + value, e);
        }
    }

    private static String firstPresent(Map<String, String> options, String primary, String alias) {
        if (options.containsKey(primary) && options.containsKey(alias)) {
            throw new IllegalArgumentException("Use either " + primary + " or " + alias + ", not both");
        }
        return options.containsKey(primary) ? options.get(primary) : options.get(alias);
    }

    private static void validateExplicitMapFile(String mapFile) {
        if (mapFile == null) {
            return;
        }
        if (mapFile.isBlank()) {
            throw new IllegalArgumentException("--map-file must not be blank");
        }
        try {
            Path path = Path.of(mapFile);
            if (!Files.isRegularFile(path)) {
                throw new IllegalArgumentException("Offline map file does not exist or is not a file: "
                        + path.toAbsolutePath());
            }
            if (!Files.isReadable(path)) {
                throw new IllegalArgumentException("Offline map file is not readable: " + path.toAbsolutePath());
            }
            try (MBTilesReader reader = new MBTilesReader(path.toFile())) {
                reader.open();
            } catch (SQLException e) {
                throw new IllegalArgumentException("Invalid --map-file MBTiles database: "
                        + path.toAbsolutePath() + " (" + e.getMessage() + ")", e);
            }
        } catch (InvalidPathException e) {
            throw new IllegalArgumentException("Invalid --map-file path: " + mapFile, e);
        }
    }

    private static String validateNodeId(String value) {
        if (value == null || !NODE_ID_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("node ID must match " + NODE_ID_PATTERN.pattern());
        }
        return value;
    }

    private static String validateHost(String label, String value) {
        String normalized = normalizeOptional(value);
        if (normalized == null || normalized.length() > 253
                || normalized.chars().anyMatch(Character::isWhitespace)
                || normalized.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(label + " must be a non-blank hostname or IP address");
        }
        return normalized;
    }

    private static int validatePort(String label, int port) {
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException(label + " must be from 1 to 65535: " + port);
        }
        return port;
    }

    private static int validateOptionalPort(String label, int port) {
        if (port == -1) {
            return port;
        }
        return validatePort(label, port);
    }

    private static String normalizeOptionalHost(String value) {
        return normalizeOptional(value);
    }

    private static String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private static <T> T requireNonNull(T value, String label) {
        if (value == null) {
            throw new IllegalArgumentException(label + " must not be null");
        }
        return value;
    }

    private record Defaults(String nodeId, int bindPort, int nextHopPort,
                            int relayPort, String victimNodeId, int victimPort) {
        private static Defaults forMode(NodeMode mode) {
            return switch (mode) {
                case VICTIM -> new Defaults("NODE_A_VICTIM", 8001, 8002, 8002,
                        "NODE_A_VICTIM", 8001);
                case RELAY -> new Defaults("NODE_B_RELAY", 8002, 8888, 8002,
                        "NODE_A_VICTIM", 8001);
                case BASE_STATION -> new Defaults("BASE_STATION", 8888, -1, 8002,
                        "NODE_A_VICTIM", 8001);
            };
        }
    }

    public NodeMode getMode() { return mode; }
    public String getNodeId() { return nodeId; }
    public String getBindHost() { return bindHost; }
    public int getListenPort() { return listenPort; }
    public String getNextHopHost() { return nextHopHost; }
    public int getNextHopPort() { return nextHopPort; }
    public String getRelayHost() { return relayHost; }
    public int getRelayPort() { return relayPort; }
    public String getVictimNodeId() { return victimNodeId; }
    public String getVictimHost() { return victimHost; }
    public int getVictimPort() { return victimPort; }
    public String getMapFilePath() { return mapFilePath; }

    @Override
    public String toString() {
        return "NodeConfig{" +
                "mode=" + mode +
                ", nodeId='" + nodeId + '\'' +
                ", bind=" + bindHost + ':' + listenPort +
                ", nextHop=" + (nextHopPort > 0 ? nextHopHost + ':' + nextHopPort : "none") +
                ", relay=" + (relayPort > 0 ? relayHost + ':' + relayPort : "none") +
                ", victimRoute=" + (victimPort > 0
                ? victimNodeId + '@' + victimHost + ':' + victimPort : "none") +
                ", mapFile='" + (mapFilePath != null ? mapFilePath : OfflineMapManager.DEFAULT_MAP_PATH) + '\'' +
                '}';
    }
}
