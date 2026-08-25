package com.rescue.mesh.network;

import com.google.gson.Gson;
import com.rescue.mesh.model.MeshPacket;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;

/**
 * Client TCP — gửi gói tin MeshPacket đến node hoặc server qua Socket.
 *
 * Cơ sở lý thuyết:
 *   Đóng vai trò Transport Layer client trong kiến trúc TCP/IP.
 *   Mỗi lần gửi mở 1 TCP connection mới (short-lived connection),
 *   phù hợp với traffic pattern thấp của hệ thống cứu nạn.
 *
 * Design Pattern: Gateway Pattern
 *   - Đóng gói toàn bộ logic TCP I/O vào 1 class duy nhất
 *   - Cung cấp API đơn giản: send(host, port, packet)
 *   - Tích hợp retry logic (3 lần thử), timeout 5 giây
 *   - UI layer chỉ cần gọi 1 method, không cần biết Socket API
 *
 * Thread Safety:
 *   - Mỗi lời gọi send() tạo Socket riêng → an toàn multi-thread
 *   - Không có shared mutable state (stateless utility class)
 *   - Có thể gọi đồng thời từ nhiều thread
 */
public class SocketClient {

    /** Timeout kết nối TCP (milliseconds) */
    private static final int CONNECTION_TIMEOUT_MS = 5000;

    /** Số lần thử lại tối đa khi ConnectException */
    private static final int MAX_RETRY_ATTEMPTS = 3;

    /** Thời gian chờ giữa các lần thử lại (milliseconds) */
    private static final long RETRY_DELAY_MS = 1000;

    /** Gson instance dùng chung — thread-safe vì Gson immutable */
    private static final Gson GSON = new Gson();

    /** Private constructor — utility class, không cho phép tạo instance */
    private SocketClient() {}

    // =========================================================
    // CALLBACK INTERFACE
    // =========================================================

    /**
     * Callback để thông báo kết quả gửi gói tin lên tầng trên.
     * Dùng bởi UI layer để cập nhật trạng thái sau khi gửi.
     */
    public interface SendCallback {
        /**
         * Được gọi khi gửi gói tin thành công.
         *
         * @param packetId ID của gói tin đã gửi
         * @param host     Địa chỉ host đã gửi đến
         * @param port     Port đã gửi đến
         */
        void onSendSuccess(String packetId, String host, int port);

        /**
         * Được gọi khi gửi gói tin thất bại sau tất cả lần thử.
         *
         * @param packetId ID của gói tin không gửi được
         * @param host     Địa chỉ host đã thử gửi
         * @param port     Port đã thử gửi
         * @param error    Mô tả lỗi
         */
        void onSendFailed(String packetId, String host, int port, String error);
    }

    // =========================================================
    // GỬI GÓI TIN — ĐỒNG BỘ (BLOCKING)
    // =========================================================

    /**
     * Gửi MeshPacket đến host:port — phiên bản đồng bộ không callback.
     *
     * @param host   Địa chỉ host đích (thường "localhost" trong giả lập)
     * @param port   Port đích (8001 / 8002 / 8888)
     * @param packet Gói tin cần gửi (không được null)
     * @return true nếu gửi thành công, false nếu thất bại
     */
    public static boolean send(String host, int port, MeshPacket packet) {
        return send(host, port, packet, null);
    }

    /**
     * Gửi MeshPacket đến host:port — có callback thông báo kết quả.
     * Tự động thử lại MAX_RETRY_ATTEMPTS lần khi ConnectException.
     *
     * @param host     Địa chỉ host đích
     * @param port     Port đích
     * @param packet   Gói tin cần gửi
     * @param callback Callback thông báo thành công/thất bại (có thể null)
     * @return true nếu gửi thành công, false nếu thất bại
     */
    public static boolean send(String host, int port, MeshPacket packet, SendCallback callback) {
        if (packet == null) {
            System.err.println("[ERROR] SocketClient.send: packet null — hủy gửi");
            if (callback != null) {
                callback.onSendFailed("UNKNOWN", host, port, "Packet is null");
            }
            return false;
        }

        String json = GSON.toJson(packet);
        String packetId = packet.getPacketId();

        for (int attempt = 1; attempt <= MAX_RETRY_ATTEMPTS; attempt++) {
            try (Socket socket = new Socket()) {
                // Kết nối với timeout
                socket.connect(new InetSocketAddress(host, port), CONNECTION_TIMEOUT_MS);
                socket.setSoTimeout(CONNECTION_TIMEOUT_MS);

                // Gửi JSON qua stream — auto-flush
                PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);
                writer.println(json);
                writer.flush();

                System.out.println("[SEND] SocketClient: "
                        + shortId(packetId) + " → " + host + ":" + port
                        + " (attempt " + attempt + "/" + MAX_RETRY_ATTEMPTS + ") — OK");

                if (callback != null) {
                    callback.onSendSuccess(packetId, host, port);
                }
                return true;

            } catch (ConnectException e) {
                System.err.println("[ERROR] SocketClient: Không kết nối được "
                        + host + ":" + port
                        + " (attempt " + attempt + "/" + MAX_RETRY_ATTEMPTS + ") — "
                        + e.getMessage());

                // Chờ trước khi thử lại
                if (attempt < MAX_RETRY_ATTEMPTS) {
                    try {
                        Thread.sleep(RETRY_DELAY_MS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }

            } catch (SocketTimeoutException e) {
                System.err.println("[ERROR] SocketClient: Timeout kết nối "
                        + host + ":" + port
                        + " (attempt " + attempt + "/" + MAX_RETRY_ATTEMPTS + ")");

                if (attempt < MAX_RETRY_ATTEMPTS) {
                    try {
                        Thread.sleep(RETRY_DELAY_MS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }

            } catch (IOException e) {
                System.err.println("[ERROR] SocketClient: Lỗi I/O gửi đến "
                        + host + ":" + port + ": " + e.getMessage());
                // Lỗi I/O chung → không retry (có thể lỗi cấu trúc dữ liệu)
                break;
            }
        }

        // Hết retry → thông báo thất bại
        String errorMsg = "Không gửi được sau " + MAX_RETRY_ATTEMPTS + " lần thử";
        System.err.println("[ERROR] SocketClient: " + shortId(packetId) + " — " + errorMsg);

        if (callback != null) {
            callback.onSendFailed(packetId, host, port, errorMsg);
        }
        return false;
    }

    // =========================================================
    // GỬI JSON THÔ — KHÔNG CẦN MESHPACKET
    // =========================================================

    /**
     * Gửi chuỗi JSON thô đến host:port — không retry, không callback.
     * Dùng cho trường hợp đặc biệt (test, debug).
     *
     * @param host Địa chỉ host
     * @param port Port đích
     * @param json Chuỗi JSON cần gửi
     * @return true nếu gửi thành công
     */
    public static boolean sendRaw(String host, int port, String json) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), CONNECTION_TIMEOUT_MS);
            socket.setSoTimeout(CONNECTION_TIMEOUT_MS);

            PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);
            writer.println(json);
            writer.flush();

            System.out.println("[SEND] SocketClient.sendRaw → " + host + ":" + port + " — OK");
            return true;

        } catch (IOException e) {
            System.err.println("[ERROR] SocketClient.sendRaw: " + host + ":" + port
                    + " — " + e.getMessage());
            return false;
        }
    }

    // =========================================================
    // GỬI BẤT ĐỒNG BỘ (NON-BLOCKING)
    // =========================================================

    /**
     * Gửi MeshPacket trong một thread riêng — không block luồng gọi.
     * Kết quả được thông báo qua callback (nếu có).
     *
     * @param host     Địa chỉ host
     * @param port     Port đích
     * @param packet   Gói tin cần gửi
     * @param callback Callback thông báo kết quả
     */
    public static void sendAsync(String host, int port, MeshPacket packet, SendCallback callback) {
        Thread sendThread = new Thread(() -> send(host, port, packet, callback),
                "SocketClient-Async-" + shortId(packet != null ? packet.getPacketId() : "null"));
        sendThread.setDaemon(true);
        sendThread.start();
    }

    // =========================================================
    // UTILITY
    // =========================================================

    /**
     * Trả về 8 ký tự đầu của ID để log ngắn gọn.
     */
    private static String shortId(String id) {
        if (id == null) return "null";
        return id.length() > 8 ? id.substring(0, 8) : id;
    }
}
