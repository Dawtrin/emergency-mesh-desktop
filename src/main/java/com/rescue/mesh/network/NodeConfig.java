package com.rescue.mesh.network;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

/**
 * Cấu hình của một Node trong hệ thống Mesh.
 *
 * Design Pattern: Value Object — bất biến sau khi tạo,
 * truyền qua constructor để tránh trạng thái không nhất quán.
 *
 * Hỗ trợ 2 cách cấu hình:
 *   1. CLI args:     java -jar app.jar --mode VICTIM --port 8001 --host 192.168.1.11 --next-hop 8002 --id NODE_A
 *   2. Properties:   java -jar app.jar --config config/nodeA.properties
 *   3. Kết hợp:      java -jar app.jar --config config/nodeA.properties --port 9001
 *                     (CLI args ghi đè giá trị trong file)
 */
public class NodeConfig {

    public enum NodeMode {
        VICTIM,       // Node nạn nhân: có UI nhập SOS, gửi đến relay
        RELAY,        // Node relay: lắng nghe và chuyển tiếp, không có UI nhập
        BASE_STATION  // Trạm chỉ huy: nhận SOS, hiển thị dashboard
    }

    private final NodeMode mode;
    private final String   nodeId;
    private final int      listenPort;
    private final String   nextHopHost;
    private final int      nextHopPort;

    public NodeConfig(NodeMode mode, String nodeId,
                      int listenPort, String nextHopHost, int nextHopPort) {
        this.mode        = mode;
        this.nodeId      = nodeId;
        this.listenPort  = listenPort;
        this.nextHopHost = nextHopHost;
        this.nextHopPort = nextHopPort;
    }

    /**
     * Parse NodeConfig từ file .properties.
     *
     * Các key hỗ trợ:
     *   node.id=NODE_A_VICTIM
     *   node.role=VICTIM           (hoặc RELAY / BASE_STATION)
     *   self.port=8001
     *   upstream.host=192.168.1.10  (hoặc relay.host)
     *   upstream.port=8002          (hoặc relay.port)
     *
     * @param filePath Đường dẫn file .properties
     * @return NodeConfig đã parse
     * @throws IOException Nếu file không tồn tại hoặc không đọc được
     */
    public static NodeConfig fromProperties(String filePath) throws IOException {
        Properties props = new Properties();
        try (FileInputStream fis = new FileInputStream(filePath)) {
            props.load(fis);
        }

        NodeMode mode = NodeMode.VICTIM;
        String roleProp = props.getProperty("node.role", "VICTIM");
        try {
            mode = NodeMode.valueOf(roleProp.toUpperCase());
        } catch (IllegalArgumentException e) {
            System.err.println("[ERROR] NodeConfig: role không hợp lệ trong properties: " + roleProp);
        }

        String nodeId = props.getProperty("node.id", "NODE_A_VICTIM");

        int listenPort = 8001;
        try {
            listenPort = Integer.parseInt(props.getProperty("self.port", "8001"));
        } catch (NumberFormatException ignored) {}

        // Hỗ trợ cả upstream.host và relay.host
        String nextHopHost = props.getProperty("upstream.host",
                props.getProperty("relay.host", "localhost"));

        int nextHopPort = 8002;
        try {
            nextHopPort = Integer.parseInt(
                    props.getProperty("upstream.port",
                            props.getProperty("relay.port", "8002")));
        } catch (NumberFormatException ignored) {}

        // Base Station không có next hop
        if (mode == NodeMode.BASE_STATION) {
            nextHopPort = -1;
        }

        System.out.println("[INFO] NodeConfig: Đã đọc config từ file: " + filePath);
        return new NodeConfig(mode, nodeId, listenPort, nextHopHost, nextHopPort);
    }

    /**
     * Parse NodeConfig từ mảng args dòng lệnh.
     * Hỗ trợ --config để đọc file .properties trước, sau đó CLI args ghi đè.
     *
     * Ví dụ:
     *   --mode VICTIM --port 8001 --next-hop 8002 --id NODE_A_VICTIM
     *   --config config/nodeA.properties
     *   --config config/nodeA.properties --port 9001  (ghi đè port)
     *
     * @param args Mảng args từ main(String[] args)
     * @return NodeConfig đã parse, hoặc config mặc định nếu args rỗng
     */
    public static NodeConfig fromArgs(String[] args) {
        // Kiểm tra xem có --config flag không
        for (int i = 0; i < args.length - 1; i++) {
            if ("--config".equalsIgnoreCase(args[i])) {
                String configPath = args[i + 1];
                try {
                    NodeConfig baseConfig = fromProperties(configPath);
                    // Nếu có thêm CLI args → dùng chúng ghi đè
                    return mergeWithCliArgs(baseConfig, args);
                } catch (IOException e) {
                    System.err.println("[ERROR] NodeConfig: Không đọc được file config: "
                            + configPath + " — " + e.getMessage());
                    System.err.println("[INFO] Dùng giá trị mặc định từ CLI args...");
                }
            }
        }

        // Không có --config → parse thuần từ CLI args (giữ nguyên logic cũ)
        NodeMode mode        = NodeMode.VICTIM;
        String   nodeId      = "NODE_A_VICTIM";
        int      listenPort  = 8001;
        String   nextHopHost = "localhost";
        int      nextHopPort = 8002;

        for (int i = 0; i < args.length - 1; i++) {
            switch (args[i].toLowerCase()) {
                case "--mode":
                    try {
                        mode = NodeMode.valueOf(args[i + 1].toUpperCase());
                    } catch (IllegalArgumentException e) {
                        System.err.println("[ERROR] Mode không hợp lệ: " + args[i + 1]
                                + ". Dùng: VICTIM / RELAY / BASE_STATION");
                    }
                    break;
                case "--port":
                    try {
                        listenPort = Integer.parseInt(args[i + 1]);
                    } catch (NumberFormatException e) {
                        System.err.println("[ERROR] Port không hợp lệ: " + args[i + 1]);
                    }
                    break;
                case "--next-hop":
                    try {
                        nextHopPort = Integer.parseInt(args[i + 1]);
                    } catch (NumberFormatException e) {
                        System.err.println("[ERROR] Next-hop port không hợp lệ: " + args[i + 1]);
                    }
                    break;
                case "--id":
                    nodeId = args[i + 1];
                    break;
                case "--host":
                    nextHopHost = args[i + 1];
                    break;
                default:
                    break;
            }
        }

        // Tự động gán nodeId mặc định theo mode nếu chưa set
        if (nodeId.equals("NODE_A_VICTIM") && mode == NodeMode.RELAY) {
            nodeId = "NODE_B_RELAY";
        } else if (mode == NodeMode.BASE_STATION) {
            nodeId      = "BASE_STATION";
            listenPort  = (listenPort == 8001) ? 8888 : listenPort;
            nextHopPort = -1; // Base Station không có next hop
        }

        return new NodeConfig(mode, nodeId, listenPort, nextHopHost, nextHopPort);
    }

    /**
     * Merge config từ file .properties với CLI args (CLI ghi đè).
     */
    private static NodeConfig mergeWithCliArgs(NodeConfig base, String[] args) {
        NodeMode mode        = base.mode;
        String   nodeId      = base.nodeId;
        int      listenPort  = base.listenPort;
        String   nextHopHost = base.nextHopHost;
        int      nextHopPort = base.nextHopPort;

        for (int i = 0; i < args.length - 1; i++) {
            switch (args[i].toLowerCase()) {
                case "--config": i++; break; // Bỏ qua --config
                case "--mode":
                    try { mode = NodeMode.valueOf(args[i + 1].toUpperCase()); }
                    catch (IllegalArgumentException ignored) {}
                    break;
                case "--port":
                    try { listenPort = Integer.parseInt(args[i + 1]); }
                    catch (NumberFormatException ignored) {}
                    break;
                case "--next-hop":
                    try { nextHopPort = Integer.parseInt(args[i + 1]); }
                    catch (NumberFormatException ignored) {}
                    break;
                case "--id": nodeId = args[i + 1]; break;
                case "--host": nextHopHost = args[i + 1]; break;
            }
        }

        return new NodeConfig(mode, nodeId, listenPort, nextHopHost, nextHopPort);
    }

    // ===== Getters =====
    public NodeMode getMode()        { return mode; }
    public String   getNodeId()      { return nodeId; }
    public int      getListenPort()  { return listenPort; }
    public String   getNextHopHost() { return nextHopHost; }
    public int      getNextHopPort() { return nextHopPort; }

    @Override
    public String toString() {
        return "NodeConfig{"
                + "mode=" + mode
                + ", nodeId='" + nodeId + '\''
                + ", listenPort=" + listenPort
                + ", nextHop=" + nextHopHost + ":" + nextHopPort
                + '}';
    }
}