package com.rescue.mesh.socket;

import com.google.gson.Gson;
import com.rescue.mesh.model.MeshPacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.ConnectException;
import java.net.Socket;

/**
 * TCP client của Web C2 để gửi DISPATCH_CMD đến gateway/relay Android.
 *
 * Giữ transport JSON tương thích 100% với SocketClient của JavaFX và Android.
 * Batch 1 cung cấp service gửi nền; REST điều phối sẽ dùng service này ở Batch 2.
 */
@Component
public class MeshTcpClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(MeshTcpClient.class);
    private static final int CONNECT_TIMEOUT_MS = 5_000;

    private final TaskExecutor meshExecutor;
    private final Gson gson = new Gson();

    public MeshTcpClient(@Qualifier("meshExecutor") TaskExecutor meshExecutor) {
        this.meshExecutor = meshExecutor;
    }

    /** Gửi packet bất đồng bộ để không block HTTP/WebSocket hay UI operator. */
    public void sendAsync(String host, int port, MeshPacket packet, SendCallback callback) {
        meshExecutor.execute(() -> send(host, port, packet, callback));
    }

    /**
     * Gửi một dòng JSON qua TCP. Caller có thể gọi đồng bộ trong test.
     */
    public void send(String host, int port, MeshPacket packet, SendCallback callback) {
        if (packet == null) {
            notifyFailure(callback, "UNKNOWN", host, port, "Packet không được null");
            return;
        }

        try (Socket socket = new Socket()) {
            socket.connect(new java.net.InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
            try (PrintWriter writer = new PrintWriter(socket.getOutputStream(), true)) {
                writer.println(packet.toJson(gson));
            }
            LOGGER.info("[SEND] {} → {}:{}", shortId(packet.getPacketId()), host, port);
            if (callback != null) {
                callback.onSendSuccess(packet.getPacketId(), host, port);
            }
        } catch (ConnectException exception) {
            String message = "Không kết nối được đến " + host + ":" + port + " — " + exception.getMessage();
            LOGGER.error("[ERROR] {}", message);
            notifyFailure(callback, packet.getPacketId(), host, port, message);
        } catch (IOException exception) {
            String message = "Lỗi I/O khi gửi packet: " + exception.getMessage();
            LOGGER.error("[ERROR] {}", message);
            notifyFailure(callback, packet.getPacketId(), host, port, message);
        }
    }

    private void notifyFailure(SendCallback callback, String packetId, String host, int port, String message) {
        if (callback != null) {
            callback.onSendFailed(packetId, host, port, message);
        }
    }

    private String shortId(String packetId) {
        if (packetId == null) {
            return "UNKNOWN";
        }
        return packetId.length() > 8 ? packetId.substring(0, 8) : packetId;
    }

    public interface SendCallback {
        void onSendSuccess(String packetId, String host, int port);

        void onSendFailed(String packetId, String host, int port, String errorMessage);
    }
}
