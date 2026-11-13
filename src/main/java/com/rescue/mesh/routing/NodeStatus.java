package com.rescue.mesh.routing;

import java.time.Duration;
import java.time.Instant;

/**
 * Model lưu trạng thái hoạt động của một Node Relay trong bảng định tuyến.
 *
 * Cơ sở lý thuyết:
 *   Trong thuật toán Load-Aware Routing, mỗi relay node định kỳ gửi
 *   gói LOAD_REPORT về Base Station để báo cáo tải hiện tại.
 *   NodeStatus lưu trữ thông tin này và cung cấp logic kiểm tra
 *   node còn sống (alive) hay đã mất kết nối (offline).
 *
 * Design Pattern: Value Object
 *   - Chứa dữ liệu trạng thái snapshot tại 1 thời điểm
 *   - Được cập nhật mỗi khi nhận LOAD_REPORT mới
 *   - Thread-safe: fields được đọc/ghi từ nhiều thread qua ConcurrentHashMap
 *
 * Lifecycle:
 *   1. LoadBalancer tạo NodeStatus khi nhận LOAD_REPORT đầu tiên từ relay
 *   2. Cập nhật mỗi 3 giây khi nhận heartbeat tiếp theo
 *   3. Nếu quá 10 giây không nhận → isOnline() trả false → LoadBalancer bỏ qua node này
 */
public class NodeStatus {

    /** ID định danh node relay (vd: "NODE_B1_RELAY") */
    private String nodeId;

    /** Số gói tin đang xử lý tại thời điểm báo cáo */
    private int currentLoad;

    /** Tổng số gói tin đã xử lý xong kể từ khi khởi động */
    private int processedTotal;

    /** Thời điểm nhận heartbeat/LOAD_REPORT gần nhất */
    private Instant lastHeartbeat;

    /** Địa chỉ IP thực tế của relay (ghi nhận từ socket connection) */
    private String ipAddress;

    /** Port lắng nghe của relay (để Base Station biết gửi ngược lại) */
    private int port;

    // =========================================================
    // CONSTRUCTORS
    // =========================================================

    public NodeStatus() {
        this.lastHeartbeat = Instant.now();
    }

    public NodeStatus(String nodeId, int currentLoad, int processedTotal,
                      String ipAddress, int port) {
        this.nodeId         = nodeId;
        this.currentLoad    = currentLoad;
        this.processedTotal = processedTotal;
        this.lastHeartbeat  = Instant.now();
        this.ipAddress      = ipAddress;
        this.port           = port;
    }

    // =========================================================
    // ALIVE / ONLINE LOGIC
    // =========================================================

    /**
     * Kiểm tra node còn sống hay không dựa trên thời gian heartbeat.
     *
     * Node được coi là ALIVE nếu heartbeat gần nhất nằm trong
     * khoảng timeoutSeconds giây so với thời điểm hiện tại.
     *
     * @param timeoutSeconds Ngưỡng timeout (vd: 10 giây)
     * @return true nếu node còn sống, false nếu đã mất kết nối
     */
    public boolean isAlive(int timeoutSeconds) {
        if (lastHeartbeat == null) return false;
        long elapsed = Duration.between(lastHeartbeat, Instant.now()).getSeconds();
        return elapsed < timeoutSeconds;
    }

    /**
     * Shortcut kiểm tra node ONLINE với ngưỡng mặc định 10 giây.
     * Dùng trong LoadBalancer.selectBestRelay() để lọc node offline.
     *
     * @return true nếu heartbeat đến trong vòng 10 giây gần nhất
     */
    public boolean isOnline() {
        return isAlive(10);
    }

    /**
     * Thời gian (giây) kể từ heartbeat gần nhất.
     * Dùng cho Monitoring Dashboard hiển thị "Xs ago".
     *
     * @return Số giây kể từ heartbeat cuối, hoặc -1 nếu chưa có heartbeat
     */
    public long getSecondsSinceLastHeartbeat() {
        if (lastHeartbeat == null) return -1;
        return Duration.between(lastHeartbeat, Instant.now()).getSeconds();
    }

    // =========================================================
    // UPDATE — Gọi khi nhận LOAD_REPORT mới
    // =========================================================

    /**
     * Cập nhật trạng thái khi nhận được LOAD_REPORT mới từ relay.
     *
     * @param currentLoad    Số gói đang xử lý
     * @param processedTotal Tổng gói đã xử lý
     */
    public void update(int currentLoad, int processedTotal) {
        this.currentLoad    = currentLoad;
        this.processedTotal = processedTotal;
        this.lastHeartbeat  = Instant.now();
    }

    // =========================================================
    // GETTERS & SETTERS
    // =========================================================

    public String  getNodeId()         { return nodeId; }
    public void    setNodeId(String nodeId) { this.nodeId = nodeId; }

    public int     getCurrentLoad()    { return currentLoad; }
    public void    setCurrentLoad(int currentLoad) { this.currentLoad = currentLoad; }

    public int     getProcessedTotal() { return processedTotal; }
    public void    setProcessedTotal(int processedTotal) { this.processedTotal = processedTotal; }

    public Instant getLastHeartbeat()  { return lastHeartbeat; }
    public void    setLastHeartbeat(Instant lastHeartbeat) { this.lastHeartbeat = lastHeartbeat; }

    public String  getIpAddress()      { return ipAddress; }
    public void    setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }

    public int     getPort()           { return port; }
    public void    setPort(int port)   { this.port = port; }

    // =========================================================
    // DISPLAY
    // =========================================================

    @Override
    public String toString() {
        return "NodeStatus{"
                + "nodeId='" + nodeId + '\''
                + ", load=" + currentLoad
                + ", total=" + processedTotal
                + ", ip='" + ipAddress + '\''
                + ", port=" + port
                + ", online=" + isOnline()
                + ", lastHB=" + getSecondsSinceLastHeartbeat() + "s ago"
                + '}';
    }
}
