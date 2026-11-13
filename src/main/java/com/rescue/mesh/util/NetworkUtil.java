package com.rescue.mesh.util;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.SocketException;
import java.util.Enumeration;

/**
 * Tiện ích mạng cho hệ thống Emergency Mesh Rescue.
 *
 * Chức năng chính:
 *   1. Tự động phát hiện IP LAN (192.168.x.x / 10.x.x.x) của máy
 *   2. Kiểm tra port có trống để bind hay không
 *   3. Hiển thị IP lên tiêu đề cửa sổ JavaFX — giúp demo dễ nhận biết
 *
 * Giải thích kỹ thuật:
 *   Java's NetworkInterface API cho phép duyệt qua tất cả card mạng
 *   (Ethernet, Wi-Fi, Loopback) và lấy IP được gán cho từng card.
 *   Chúng ta lọc ra IP thuộc mạng nội bộ (Private IP) theo RFC 1918:
 *     - 10.0.0.0/8
 *     - 172.16.0.0/12
 *     - 192.168.0.0/16
 */
public class NetworkUtil {

    /** Private constructor — utility class */
    private NetworkUtil() {}

    /**
     * Lấy địa chỉ IP LAN (IPv4) của máy tính hiện tại.
     *
     * Ưu tiên theo thứ tự:
     *   1. IP bắt đầu bằng 192.168.x.x (Wi-Fi nội bộ phổ biến nhất)
     *   2. IP bắt đầu bằng 10.x.x.x (mạng nội bộ lớn)
     *   3. IP bắt đầu bằng 172.16-31.x.x (mạng nội bộ trung bình)
     *   4. Fallback: "localhost" nếu không tìm thấy
     *
     * @return Chuỗi IP LAN (vd: "192.168.1.105"), hoặc "localhost"
     */
    public static String getLocalIpAddress() {
        String fallbackIp = "localhost";
        String bestIp = null;

        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();

                // Bỏ qua loopback (127.0.0.1) và interface không hoạt động
                if (ni.isLoopback() || !ni.isUp()) continue;

                Enumeration<InetAddress> addresses = ni.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();

                    // Chỉ lấy IPv4 (bỏ IPv6)
                    if (!(addr instanceof Inet4Address)) continue;

                    String ip = addr.getHostAddress();

                    // Ưu tiên 192.168.x.x (Wi-Fi phổ biến nhất khi demo)
                    if (ip.startsWith("192.168.")) {
                        return ip; // Trả về ngay — ưu tiên cao nhất
                    }

                    // Lưu lại 10.x.x.x hoặc 172.x.x.x làm fallback
                    if (ip.startsWith("10.") || ip.startsWith("172.")) {
                        bestIp = ip;
                    }
                }
            }
        } catch (SocketException e) {
            System.err.println("[WARN] NetworkUtil: Không duyệt được network interfaces: "
                    + e.getMessage());
        }

        return (bestIp != null) ? bestIp : fallbackIp;
    }

    /**
     * Kiểm tra port có đang trống (chưa bị tiến trình khác chiếm) hay không.
     *
     * @param port Cổng cần kiểm tra
     * @return true nếu port trống và có thể bind, false nếu đã bị chiếm
     */
    public static boolean isPortAvailable(int port) {
        if (port < 1 || port > 65535) return false;
        try (ServerSocket ss = new ServerSocket(port)) {
            ss.setReuseAddress(true);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Tạo chuỗi tiêu đề cửa sổ có chứa IP LAN.
     * Dùng để hiển thị trên title bar của JavaFX Stage.
     *
     * @param nodeId   ID của node (vd: "BASE_STATION")
     * @param port     Port đang lắng nghe
     * @param modeEmoji Emoji cho chế độ (🛡️ / 🔄 / 🆘)
     * @return Chuỗi tiêu đề (vd: "🛡️ BASE_STATION — 192.168.1.10:8888")
     */
    public static String buildWindowTitle(String nodeId, int port, String modeEmoji) {
        String ip = getLocalIpAddress();
        return modeEmoji + " " + nodeId + " — " + ip + ":" + port;
    }
}
