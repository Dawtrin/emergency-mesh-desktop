package com.rescue.mesh.network;

/**
 * Cấu hình của một Node trong hệ thống Mesh.
 *
 * Design Pattern: Value Object — bất biến sau khi tạo,
 * truyền qua constructor để tránh trạng thái không nhất quán.
 *
 * Được parse từ args dòng lệnh:
 *   java -jar app.jar --mode VICTIM --port 8001 --next-hop 8002 --id NODE_A_VICTIM
 *   java -jar app.jar --mode RELAY  --port 8002 --next-hop 8888 --id NODE_B_RELAY
 *   java -jar app.jar --mode BASE_STATION --port 8888
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
     * Parse NodeConfig từ mảng args dòng lệnh.
     * Ví dụ: --mode VICTIM --port 8001 --next-hop 8002 --id NODE_A_VICTIM
     *
     * @param args Mảng args từ main(String[] args)
     * @return NodeConfig đã parse, hoặc config mặc định nếu args rỗng
     */
    public static NodeConfig fromArgs(String[] args) {
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