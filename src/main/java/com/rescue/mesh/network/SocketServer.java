package com.rescue.mesh.network;

import com.google.gson.Gson;
import com.rescue.mesh.model.MeshPacket;
import com.rescue.mesh.routing.RoutingEngine;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * TCP Server đa luồng — lắng nghe kết nối đến từ các node khác.
 *
 * Kiến trúc:
 *   - 1 thread chính (accept loop): chờ kết nối mới từ ServerSocket.accept()
 *   - N worker threads (ExecutorService): mỗi client connection chạy trên
 *     1 thread riêng để xử lý song song, không block accept loop.
 *
 * Design Pattern: Reactor Pattern (đơn giản hóa)
 *   - Tương tự cách Netty và Tomcat xử lý concurrent connections
 *   - ServerSocket.accept() → trao connection cho worker thread
 *   - Worker thread đọc JSON → parse → RoutingEngine.processPacket()
 *
 * Thread Safety:
 *   - ExecutorService.newCachedThreadPool() tự quản lý thread pool
 *   - RoutingEngine.processPacket() thread-safe nhờ SeenPacketCache atomic
 */
public class SocketServer {

    /** Interface để thông báo sự kiện server lên tầng trên */
    public interface ServerEventListener {
        void onServerStarted(int port);
        void onClientConnected(String clientAddress);
        void onServerError(String errorMessage);
        void onServerStopped();
    }

    private final int           listenPort;
    private final RoutingEngine routingEngine;
    private final Gson          gson;
    private final ServerEventListener eventListener;

    private ServerSocket        serverSocket;
    private ExecutorService     workerPool;
    private volatile boolean    running = false;
    private Thread              acceptThread;

    /**
     * @param listenPort    Port cần lắng nghe (8001 / 8002 / 8888)
     * @param routingEngine RoutingEngine đã được khởi tạo với config của node này
     * @param listener      Callback thông báo sự kiện server
     */
    public SocketServer(int listenPort,
                        RoutingEngine routingEngine,
                        ServerEventListener listener) {
        this.listenPort    = listenPort;
        this.routingEngine = routingEngine;
        this.gson          = new Gson();
        this.eventListener = listener;
    }

    /**
     * Khởi động server trong một thread riêng (non-blocking với luồng gọi).
     * ServerSocket.accept() là blocking nên phải chạy trong background thread.
     */
    public void start() {
        if (running) {
            System.out.println("[INFO] Server đã đang chạy tại port " + listenPort);
            return;
        }

        workerPool   = Executors.newCachedThreadPool();
        acceptThread = new Thread(this::acceptLoop, "SocketServer-Accept-" + listenPort);
        acceptThread.setDaemon(true);
        acceptThread.start();
    }

    /**
     * Vòng lặp chính: mở ServerSocket và chờ kết nối đến liên tục.
     * Chạy trong acceptThread — không block luồng chính.
     */
    private void acceptLoop() {
        try {
            serverSocket = new ServerSocket(listenPort);
            running      = true;

            System.out.println("[INFO] SocketServer đang lắng nghe tại port " + listenPort);
            eventListener.onServerStarted(listenPort);

            while (running) {
                try {
                    // accept() block tại đây cho đến khi có client kết nối
                    Socket clientSocket = serverSocket.accept();
                    String clientAddr   = clientSocket.getInetAddress().getHostAddress()
                            + ":" + clientSocket.getPort();

                    System.out.println("[INFO] Kết nối mới từ " + clientAddr);
                    eventListener.onClientConnected(clientAddr);

                    // Trao client cho worker thread — không block accept loop
                    workerPool.submit(() -> handleClient(clientSocket, clientAddr));

                } catch (SocketException e) {
                    // ServerSocket bị đóng (do stop() được gọi) → thoát vòng lặp bình thường
                    if (running) {
                        System.err.println("[ERROR] SocketServer bị ngắt bất ngờ: " + e.getMessage());
                        eventListener.onServerError("SocketException: " + e.getMessage());
                    }
                    break;
                }
            }

        } catch (IOException e) {
            System.err.println("[ERROR] Không thể mở ServerSocket tại port " + listenPort
                    + ": " + e.getMessage());
            System.err.println("[ERROR] Kiểm tra: port đang bị dùng bởi tiến trình khác?");
            eventListener.onServerError("Không mở được port " + listenPort + ": " + e.getMessage());
        } finally {
            eventListener.onServerStopped();
        }
    }

    /**
     * Xử lý một client connection: đọc JSON → parse → RoutingEngine.
     * Chạy trong worker thread riêng, không ảnh hưởng accept loop.
     *
     * @param clientSocket Socket của client vừa kết nối
     * @param clientAddr   Địa chỉ client (để log)
     */
    private void handleClient(Socket clientSocket, String clientAddr) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(clientSocket.getInputStream()))) {

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;

                // Parse JSON → MeshPacket
                MeshPacket packet = MeshPacket.fromJson(line, gson);

                if (packet == null) {
                    System.err.println("[ERROR] Không parse được JSON từ " + clientAddr
                            + ": " + line.substring(0, Math.min(line.length(), 100)));
                    continue;
                }

                // Trao cho RoutingEngine xử lý (thread-safe)
                routingEngine.processPacket(packet);
            }

        } catch (IOException e) {
            if (running) {
                System.err.println("[ERROR] Lỗi đọc dữ liệu từ client " + clientAddr
                        + ": " + e.getMessage());
            }
        } finally {
            try {
                clientSocket.close();
            } catch (IOException e) {
                System.err.println("[ERROR] Không đóng được clientSocket: " + e.getMessage());
            }
        }
    }

    /**
     * Dừng server, đóng ServerSocket và giải phóng thread pool.
     */
    public void stop() {
        running = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            System.err.println("[ERROR] Lỗi khi đóng ServerSocket: " + e.getMessage());
        }

        if (workerPool != null) {
            workerPool.shutdown();
            try {
                if (!workerPool.awaitTermination(5, TimeUnit.SECONDS)) {
                    workerPool.shutdownNow();
                }
            } catch (InterruptedException e) {
                workerPool.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        System.out.println("[INFO] SocketServer tại port " + listenPort + " đã dừng.");
    }

    public boolean isRunning() { return running; }
    public int     getPort()   { return listenPort; }
}