package com.rescue.mesh.routing;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thuật toán phân bố tải Least-Load Routing cho hệ thống Mesh Rescue.
 *
 * Cơ sở lý thuyết:
 *   Trong mạng cứu hộ thực tế, nếu chỉ dùng 1 relay node cố định,
 *   node đó sẽ bị quá tải khi nhiều nạn nhân gửi SOS đồng thời,
 *   trong khi các relay khác rảnh rỗi.
 *
 *   LoadBalancer giải quyết bằng cách:
 *   1. Thu thập LOAD_REPORT (heartbeat) từ mỗi relay mỗi 3 giây
 *   2. Duy trì bảng định tuyến realtime (routingTable)
 *   3. Khi cần forward gói tin → chọn relay có currentLoad thấp nhất
 *      và còn ONLINE (heartbeat < 10 giây)
 *
 *   Thuật toán: Least-Load First (tương tự Least-Connections trong
 *   Load Balancer của Nginx/HAProxy nhưng áp dụng cho mạng ad-hoc).
 *
 * Design Pattern: Strategy Pattern
 *   - selectBestRelay() là chiến lược chọn đường
 *   - Có thể thay thế bằng Round-Robin, Weighted, etc.
 *
 * Thread Safety:
 *   - Dùng ConcurrentHashMap cho routingTable
 *   - updateStatus() được gọi từ network thread
 *   - selectBestRelay() được gọi từ routing thread
 *   - getRoutingTable() trả về unmodifiable snapshot
 */
public class LoadBalancer {

    /**
     * Bảng định tuyến: nodeId → NodeStatus.
     * ConcurrentHashMap đảm bảo thread-safe khi đọc/ghi đồng thời.
     */
    private final ConcurrentHashMap<String, NodeStatus> routingTable = new ConcurrentHashMap<>();

    // =========================================================
    // CẬP NHẬT TRẠNG THÁI
    // =========================================================

    /**
     * Cập nhật trạng thái node relay khi nhận được gói LOAD_REPORT.
     * Nếu node chưa tồn tại trong bảng → thêm mới.
     * Nếu đã tồn tại → cập nhật tải và thời gian heartbeat.
     *
     * @param nodeId         ID của relay node (vd: "NODE_B1_RELAY")
     * @param currentLoad    Số gói tin đang xử lý
     * @param processedTotal Tổng gói đã xử lý
     * @param ipAddress      IP thực tế của relay (từ socket)
     * @param port           Port lắng nghe của relay
     */
    public void updateStatus(String nodeId, int currentLoad, int processedTotal,
                             String ipAddress, int port) {
        NodeStatus existing = routingTable.get(nodeId);
        if (existing != null) {
            // Cập nhật node đã biết
            existing.update(currentLoad, processedTotal);
            if (ipAddress != null && !ipAddress.isEmpty()) {
                existing.setIpAddress(ipAddress);
            }
            if (port > 0) {
                existing.setPort(port);
            }
        } else {
            // Node mới — thêm vào bảng định tuyến
            NodeStatus newStatus = new NodeStatus(nodeId, currentLoad, processedTotal,
                    ipAddress, port);
            routingTable.put(nodeId, newStatus);
            System.out.println("[LOAD_BALANCER] Node mới đã đăng ký: " + nodeId
                    + " (" + ipAddress + ":" + port + ")");
        }
    }

    // =========================================================
    // CHỌN RELAY TỐT NHẤT — THUẬT TOÁN LEAST-LOAD
    // =========================================================

    /**
     * Chọn relay node có tải thấp nhất trong số các node đang ONLINE.
     *
     * Thuật toán:
     *   1. Lọc các node có isOnline() == true (heartbeat < 10s)
     *   2. So sánh currentLoad
     *   3. Trả về nodeId có currentLoad nhỏ nhất
     *
     * @return ID của relay node tối ưu
     * @throws RuntimeException nếu không còn relay nào online
     */
    public String selectBestRelay() {
        return routingTable.entrySet().stream()
                .filter(e -> e.getValue().isOnline())
                .min(Comparator.comparingInt(e -> e.getValue().getCurrentLoad()))
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    /**
     * Lấy NodeStatus của relay tốt nhất (dùng khi cần cả IP + Port).
     *
     * @return NodeStatus tối ưu, hoặc null nếu không có relay online
     */
    public NodeStatus selectBestRelayStatus() {
        return routingTable.values().stream()
                .filter(NodeStatus::isOnline)
                .min(Comparator.comparingInt(NodeStatus::getCurrentLoad))
                .orElse(null);
    }

    // =========================================================
    // TRUY VẤN BẢNG ĐỊNH TUYẾN
    // =========================================================

    /**
     * Trả về bản sao bảng định tuyến (read-only).
     * Dùng cho Monitoring Dashboard hiển thị trạng thái.
     *
     * @return Map không thể sửa đổi chứa trạng thái tất cả relay
     */
    public Map<String, NodeStatus> getRoutingTable() {
        return Collections.unmodifiableMap(routingTable);
    }

    /**
     * Đếm số relay node đang ONLINE.
     *
     * @return Số node có heartbeat trong vòng 10 giây
     */
    public int getOnlineCount() {
        return (int) routingTable.values().stream()
                .filter(NodeStatus::isOnline)
                .count();
    }

    /**
     * Tổng số relay node đã đăng ký (cả online lẫn offline).
     *
     * @return Tổng số node trong bảng định tuyến
     */
    public int getTotalCount() {
        return routingTable.size();
    }

    /**
     * Tính phần trăm phân bố tải giữa các relay (dùng cho Dashboard).
     *
     * @return Map {nodeId → phần trăm gói đã xử lý}
     */
    public Map<String, Double> getDistributionStats() {
        Map<String, Double> stats = new HashMap<>();
        int grandTotal = routingTable.values().stream()
                .mapToInt(NodeStatus::getProcessedTotal)
                .sum();

        if (grandTotal == 0) {
            // Phân đều khi chưa có gói nào
            routingTable.forEach((id, status) ->
                    stats.put(id, routingTable.size() > 0
                            ? 100.0 / routingTable.size() : 0.0));
        } else {
            routingTable.forEach((id, status) ->
                    stats.put(id, (status.getProcessedTotal() * 100.0) / grandTotal));
        }
        return stats;
    }

    /**
     * Kiểm tra node cụ thể có online không.
     *
     * @param nodeId ID node cần kiểm tra
     * @return true nếu node tồn tại và online
     */
    public boolean isNodeOnline(String nodeId) {
        NodeStatus status = routingTable.get(nodeId);
        return status != null && status.isOnline();
    }

    /**
     * Lấy trạng thái của node cụ thể.
     *
     * @param nodeId ID node
     * @return NodeStatus hoặc null
     */
    public NodeStatus getNodeStatus(String nodeId) {
        return routingTable.get(nodeId);
    }

    // =========================================================
    // DISPLAY
    // =========================================================

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("LoadBalancer RoutingTable:\n");
        routingTable.forEach((id, status) ->
                sb.append("  ").append(id)
                  .append(" | Load: ").append(status.getCurrentLoad())
                  .append(" | Total: ").append(status.getProcessedTotal())
                  .append(" | ").append(status.isOnline() ? "ONLINE" : "OFFLINE")
                  .append(" | ").append(status.getSecondsSinceLastHeartbeat()).append("s ago")
                  .append("\n")
        );
        return sb.toString();
    }
}
