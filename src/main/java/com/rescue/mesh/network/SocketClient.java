package com.rescue.mesh.network;

import com.google.gson.Gson;
import com.rescue.mesh.model.MeshPacket;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Bounded TCP gateway; UI callers use its shared daemon executor. */
public final class SocketClient {
    private static final int CONNECTION_TIMEOUT_MS = 5_000;
    private static final Gson GSON = new Gson();
    private static final ThreadPoolExecutor ASYNC_EXECUTOR = new ThreadPoolExecutor(
            1, 2, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<>(32), new DaemonThreadFactory(),
            new ThreadPoolExecutor.AbortPolicy());

    private SocketClient() {}

    public interface SendCallback {
        void onSendSuccess(String packetId, String host, int port);
        void onSendFailed(String packetId, String host, int port, String error);
    }

    /** One TCP connect/write attempt. A true result is transport delivery only, never an ACK. */
    public static boolean send(String host, int port, MeshPacket packet) { return send(host, port, packet, null); }

    public static boolean send(String host, int port, MeshPacket packet, SendCallback callback) {
        String packetId = packet == null ? "UNKNOWN" : packet.getPacketId();
        if (packet == null) {
            notifyFailure(callback, packetId, host, port, "Gói tin trống.");
            return false;
        }
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), CONNECTION_TIMEOUT_MS);
            socket.setSoTimeout(CONNECTION_TIMEOUT_MS);
            PrintWriter writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
            writer.println(GSON.toJson(packet));
            writer.flush();
            if (writer.checkError()) throw new IOException("Không ghi được đầy đủ frame TCP");
            log(packetId, "OUT", host, port, "SENT", "TCP write completed; not ACKED");
            if (callback != null) callback.onSendSuccess(packetId, host, port);
            return true;
        } catch (IOException | IllegalArgumentException e) {
            String message = "Không kết nối/gửi được: " + safeMessage(e);
            log(packetId, "OUT", host, port, "DISCONNECTED", message);
            notifyFailure(callback, packetId, host, port, message);
            return false;
        }
    }

    public static boolean sendRaw(String host, int port, String json) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), CONNECTION_TIMEOUT_MS);
            PrintWriter writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
            writer.println(json);
            writer.flush();
            return !writer.checkError();
        } catch (IOException | IllegalArgumentException e) { return false; }
    }

    /** Returns false immediately if the bounded transport queue is full. */
    public static boolean sendAsync(String host, int port, MeshPacket packet, SendCallback callback) {
        try {
            ASYNC_EXECUTOR.execute(() -> send(host, port, packet, callback));
            return true;
        } catch (java.util.concurrent.RejectedExecutionException e) {
            String packetId = packet == null ? "UNKNOWN" : packet.getPacketId();
            notifyFailure(callback, packetId, host, port, "Hàng đợi gửi đang đầy; thử lại sau.");
            return false;
        }
    }

    public static int getAsyncQueueSize() { return ASYNC_EXECUTOR.getQueue().size(); }

    private static void notifyFailure(SendCallback callback, String packetId, String host, int port, String error) {
        if (callback != null) callback.onSendFailed(packetId, host, port, error);
    }
    private static void log(String packetId, String direction, String host, int port, String state, String detail) {
        System.out.println("[NET] ts=" + Instant.now() + " node=unknown packet=" + shortId(packetId)
                + " direction=" + direction + " endpoint=" + host + ':' + port + " state=" + state + " " + detail);
    }
    private static String safeMessage(Throwable e) { return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage(); }
    private static String shortId(String id) { return id == null ? "unknown" : id.length() > 8 ? id.substring(0, 8) : id; }

    private static final class DaemonThreadFactory implements ThreadFactory {
        private final AtomicInteger sequence = new AtomicInteger();
        @Override public Thread newThread(Runnable task) {
            Thread thread = new Thread(task, "SocketClient-Transport-" + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    }
}
