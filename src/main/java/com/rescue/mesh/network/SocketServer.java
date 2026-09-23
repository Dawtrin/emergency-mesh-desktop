package com.rescue.mesh.network;

import com.google.gson.Gson;
import com.rescue.mesh.model.MeshPacket;
import com.rescue.mesh.routing.RoutingEngine;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * TCP Server đa luồng đã được gia cố (Hardened TCP Socket Gateway) cho Desktop Base Station.
 *
 * Các đặc tính an toàn:
 *   1. Bounded Worker Pool: ThreadPoolExecutor với core=10, max=20, queue=50, rejection policy an toàn.
 *   2. Enforce Max Frame Size: 64 KB (65,536 bytes) chống tấn công làm cạn kiệt bộ nhớ (OOM DoS).
 *   3. Socket Read Timeout: 5000ms ngăn chặn client treo kết nối (Slowloris DoS).
 *   4. Explicit UTF-8 decoding chuẩn xác.
 *   5. Graceful Shutdown: giải phóng ServerSocket, đóng toàn bộ client sockets đang hoạt động,
 *      và chờ worker pool dừng an toàn.
 *   6. Hỗ trợ Dynamic Port (port 0) cho integration tests.
 */
public class SocketServer {

    private static final Logger LOGGER = Logger.getLogger(SocketServer.class.getName());

    public static final int DEFAULT_CORE_POOL_SIZE = 10;
    public static final int DEFAULT_MAX_POOL_SIZE = 20;
    public static final long DEFAULT_KEEP_ALIVE_SECONDS = 60L;
    public static final int DEFAULT_QUEUE_CAPACITY = 50;
    public static final int MAX_FRAME_SIZE_BYTES = 65536; // 64 KB
    public static final int DEFAULT_SOCKET_TIMEOUT_MS = 5000; // 5s

    /** Interface để thông báo sự kiện server lên tầng trên */
    public interface ServerEventListener {
        void onServerStarted(int port);
        void onClientConnected(String clientAddress);
        void onServerError(String errorMessage);
        void onServerStopped();
    }

    private final String bindHost;
    private final int listenPort;
    private final RoutingEngine routingEngine;
    private final Gson gson;
    private final ServerEventListener eventListener;
    private final int corePoolSize;
    private final int maxPoolSize;
    private final int queueCapacity;
    private volatile int socketTimeoutMs = DEFAULT_SOCKET_TIMEOUT_MS;

    private ServerSocket serverSocket;
    private ThreadPoolExecutor workerPool;
    private volatile boolean running = false;
    private Thread acceptThread;
    private volatile int actualPort = -1;

    /** Tập hợp các kết nối socket đang hoạt động để đóng khi shutdown */
    private final Set<Socket> activeClients = ConcurrentHashMap.newKeySet();

    /**
     * @param listenPort    Port cần lắng nghe (8001 / 8002 / 8888 hoặc 0 cho dynamic port)
     * @param routingEngine RoutingEngine đã được khởi tạo với config của node này
     * @param listener      Callback thông báo sự kiện server
     */
    public SocketServer(int listenPort,
                        RoutingEngine routingEngine,
                        ServerEventListener listener) {
        this(NodeConfig.DEFAULT_BIND_HOST, listenPort, routingEngine, listener,
                DEFAULT_CORE_POOL_SIZE, DEFAULT_MAX_POOL_SIZE, DEFAULT_QUEUE_CAPACITY);
    }

    public SocketServer(String bindHost,
                        int listenPort,
                        RoutingEngine routingEngine,
                        ServerEventListener listener) {
        this(bindHost, listenPort, routingEngine, listener,
                DEFAULT_CORE_POOL_SIZE, DEFAULT_MAX_POOL_SIZE, DEFAULT_QUEUE_CAPACITY);
    }

    public SocketServer(int listenPort,
                        RoutingEngine routingEngine,
                        ServerEventListener listener,
                        int corePoolSize,
                        int maxPoolSize,
                        int queueCapacity) {
        this(NodeConfig.DEFAULT_BIND_HOST, listenPort, routingEngine, listener,
                corePoolSize, maxPoolSize, queueCapacity);
    }

    public SocketServer(String bindHost,
                        int listenPort,
                        RoutingEngine routingEngine,
                        ServerEventListener listener,
                        int corePoolSize,
                        int maxPoolSize,
                        int queueCapacity) {
        if (bindHost == null || bindHost.isBlank()) {
            throw new IllegalArgumentException("bindHost must not be blank");
        }
        if (listenPort < 0 || listenPort > 65535) {
            throw new IllegalArgumentException("listenPort must be from 0 to 65535: " + listenPort);
        }
        this.bindHost = bindHost.trim();
        this.listenPort = listenPort;
        this.routingEngine = routingEngine;
        this.gson = new Gson();
        this.eventListener = listener;
        this.corePoolSize = corePoolSize;
        this.maxPoolSize = maxPoolSize;
        this.queueCapacity = queueCapacity;
    }

    public void setSocketTimeoutMs(int socketTimeoutMs) {
        this.socketTimeoutMs = socketTimeoutMs;
    }

    public int getSocketTimeoutMs() {
        return socketTimeoutMs;
    }

    private final java.util.concurrent.atomic.AtomicBoolean stoppedNotified = new java.util.concurrent.atomic.AtomicBoolean(false);

    /**
     * Khởi động server trong một thread riêng (non-blocking).
     * ServerSocket được bind đồng bộ trước khi start() trả về, đảm bảo port sẵn sàng và tránh race condition.
     */
    public synchronized void start() {
        if (running) {
            LOGGER.info("Server đã đang chạy tại port " + getPort());
            return;
        }
        stoppedNotified.set(false);

        // Bounded ThreadPoolExecutor
        RejectedExecutionHandler rejectionHandler = (r, executor) -> {
            String msg = "Server overload: Worker pool queue full (max=" + maxPoolSize
                    + ", queue=" + queueCapacity + ")";
            LOGGER.warning(msg);
            if (eventListener != null) {
                eventListener.onServerError(msg);
            }
            throw new RejectedExecutionException(msg);
        };

        ThreadFactory threadFactory = new ThreadFactory() {
            private final AtomicInteger threadNum = new AtomicInteger(1);
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "SocketServer-Worker-" + listenPort + "-" + threadNum.getAndIncrement());
                t.setDaemon(true);
                return t;
            }
        };

        workerPool = new ThreadPoolExecutor(
                corePoolSize,
                maxPoolSize,
                DEFAULT_KEEP_ALIVE_SECONDS,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(queueCapacity),
                threadFactory,
                rejectionHandler
        );

        try {
            serverSocket = new ServerSocket();
            serverSocket.setReuseAddress(true);
            serverSocket.bind(new java.net.InetSocketAddress(bindHost, listenPort));
            actualPort = serverSocket.getLocalPort();
            running = true;

            LOGGER.info("SocketServer đang lắng nghe tại " + bindHost + ":" + actualPort);
            if (eventListener != null) {
                eventListener.onServerStarted(actualPort);
            }
        } catch (IOException | IllegalArgumentException e) {
            String endpoint = bindHost + ":" + listenPort;
            LOGGER.log(Level.SEVERE, "Không thể bind ServerSocket tại " + endpoint + ": " + e.getMessage(), e);
            if (eventListener != null) {
                eventListener.onServerError("Không bind được " + endpoint + ": " + e.getMessage());
            }
            if (workerPool != null) {
                workerPool.shutdownNow();
            }
            if (serverSocket != null) {
                try {
                    serverSocket.close();
                } catch (IOException ignored) {}
            }
            running = false;
            throw new IllegalStateException("Failed to bind SocketServer to " + endpoint, e);
        }

        acceptThread = new Thread(this::acceptLoop, "SocketServer-Accept-" + actualPort);
        acceptThread.setDaemon(true);
        acceptThread.start();
    }

    /**
     * Vòng lặp chính: chờ kết nối đến liên tục từ ServerSocket đã bind.
     */
    private void acceptLoop() {
        while (running) {
            try {
                final Socket clientSocket = serverSocket.accept();
                // Theo dõi ngay lập tức socket vừa accept trước khi submit vào worker pool
                activeClients.add(clientSocket);
                clientSocket.setSoTimeout(socketTimeoutMs);
                String clientAddr = clientSocket.getInetAddress().getHostAddress() + ":" + clientSocket.getPort();

                if (eventListener != null) {
                    eventListener.onClientConnected(clientAddr);
                }

                try {
                    workerPool.submit(() -> handleClient(clientSocket, clientAddr));
                } catch (RejectedExecutionException e) {
                    LOGGER.warning("Từ chối kết nối từ " + clientAddr + ": server quá tải");
                    activeClients.remove(clientSocket);
                    try {
                        clientSocket.close();
                    } catch (IOException ignored) {}
                }

            } catch (SocketException e) {
                if (running) {
                    LOGGER.log(Level.SEVERE, "SocketServer bị ngắt bất ngờ: " + e.getMessage(), e);
                    if (eventListener != null) {
                        eventListener.onServerError("SocketException: " + e.getMessage());
                    }
                }
                break;
            } catch (IOException e) {
                if (running) {
                    LOGGER.log(Level.SEVERE, "Lỗi I/O trên ServerSocket: " + e.getMessage(), e);
                }
                break;
            }
        }
        notifyServerStopped();
    }

    /**
     * Xử lý một client connection trong worker thread:
     * - Giới hạn frame size tối đa 64 KB (MAX_FRAME_SIZE_BYTES).
     * - Decode chuỗi JSON với UTF-8 tường minh.
     * - Áp dụng read timeout 5000ms.
     */
    private void handleClient(Socket clientSocket, String clientAddr) {
        activeClients.add(clientSocket);
        try (InputStream in = new BufferedInputStream(clientSocket.getInputStream())) {

            while (running && !clientSocket.isClosed()) {
                String line;
                try {
                    line = readBoundedLine(in, MAX_FRAME_SIZE_BYTES);
                } catch (FrameTooLargeException e) {
                    LOGGER.warning("Client " + clientAddr + " gửi frame vượt quá giới hạn "
                            + MAX_FRAME_SIZE_BYTES + " bytes. Đóng kết nối an toàn.");
                    if (routingEngine != null) {
                        routingEngine.getCallback().onPacketDropped("UNKNOWN", "FRAME_TOO_LARGE: " + e.getMessage());
                    }
                    break;
                }

                if (line == null) {
                    // EOF
                    break;
                }

                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }

                // Parse JSON -> MeshPacket
                MeshPacket packet = MeshPacket.fromJson(line, gson);
                if (packet == null) {
                    LOGGER.warning("Không parse được JSON từ " + clientAddr + ": "
                            + line.substring(0, Math.min(line.length(), 100)));
                    if (routingEngine != null) {
                        routingEngine.getCallback().onPacketDropped("UNKNOWN", "PARSE_ERROR");
                    }
                    continue;
                }

                // Trao cho RoutingEngine xử lý (thread-safe, kiểm tra validation, checksum, duplicate)
                if (routingEngine != null) {
                    routingEngine.processPacket(packet);
                }
            }

        } catch (SocketTimeoutException e) {
            LOGGER.info("Client " + clientAddr + " hết thời gian chờ dữ liệu (read timeout 5s).");
        } catch (IOException e) {
            if (running) {
                LOGGER.fine("Kết thúc đọc từ client " + clientAddr + ": " + e.getMessage());
            }
        } finally {
            activeClients.remove(clientSocket);
            try {
                if (!clientSocket.isClosed()) {
                    clientSocket.close();
                }
            } catch (IOException e) {
                LOGGER.fine("Lỗi đóng clientSocket: " + e.getMessage());
            }
        }
    }

    /**
     * Đọc một dòng ký tự kết thúc bằng '\n' với giới hạn số byte tối đa.
     * Ngăn chặn client gửi stream vô hạn không xuống dòng làm tràn bộ nhớ.
     *
     * @param in Stream dữ liệu từ socket
     * @param maxBytes Giới hạn kích thước tối đa cho 1 frame
     * @return Chuỗi đọc được (UTF-8), hoặc null nếu đã đến cuối stream (EOF)
     * @throws IOException Nếu có lỗi I/O
     * @throws FrameTooLargeException Nếu kích thước frame vượt quá maxBytes
     */
    private String readBoundedLine(InputStream in, int maxBytes) throws IOException, FrameTooLargeException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        int rawBytesConsumed = 0;
        int b;
        while ((b = in.read()) != -1) {
            rawBytesConsumed++;
            if (rawBytesConsumed > maxBytes) {
                throw new FrameTooLargeException("Raw frame length exceeded " + maxBytes + " bytes");
            }
            if (b == '\n') {
                break;
            }
            if (b == '\r') {
                continue;
            }
            baos.write(b);
        }
        if (rawBytesConsumed == 0 && b == -1) {
            return null; // EOF
        }
        return baos.toString(StandardCharsets.UTF_8);
    }

    /**
     * Dừng server một cách an toàn và dứt điểm (Graceful + Hard Shutdown):
     * 1. Đánh dấu running = false.
     * 2. Đóng ServerSocket để giải phóng port và unblock accept().
     * 3. Chờ acceptThread kết thúc (join).
     * 4. Hủy bỏ (cancel) toàn bộ task đang thực thi trong worker pool (shutdownNow).
     * 5. Đóng toàn bộ client sockets đã accept (cả đang xếp hàng chờ và đang chạy).
     * 6. Chờ worker pool dừng hoàn toàn.
     */
    public synchronized void stop() {
        if (!running && (serverSocket == null || serverSocket.isClosed())) {
            return;
        }
        running = false;

        // 1. Đóng ServerSocket để ngừng nhận kết nối mới và giải phóng port
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Lỗi khi đóng ServerSocket: " + e.getMessage(), e);
        }

        // 2. Chờ acceptThread kết thúc hoàn toàn
        if (acceptThread != null && acceptThread.isAlive()) {
            acceptThread.interrupt();
            try {
                acceptThread.join(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        // 3. Hủy bỏ tác vụ đang chạy trong worker pool
        if (workerPool != null) {
            workerPool.shutdownNow();
        }

        // 4. Đóng toàn bộ client sockets đang mở hoặc đã accept
        for (Socket client : activeClients) {
            try {
                if (client != null && !client.isClosed()) {
                    client.close();
                }
            } catch (IOException ignored) {}
        }
        activeClients.clear();

        // 5. Chờ worker pool kết thúc hoàn toàn
        if (workerPool != null) {
            try {
                if (!workerPool.awaitTermination(2, TimeUnit.SECONDS)) {
                    workerPool.shutdownNow();
                }
            } catch (InterruptedException e) {
                workerPool.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        LOGGER.info("SocketServer tại port " + getPort() + " đã dừng an toàn.");
        notifyServerStopped();
    }

    private void notifyServerStopped() {
        if (stoppedNotified.compareAndSet(false, true)) {
            if (eventListener != null) {
                eventListener.onServerStopped();
            }
        }
    }

    public boolean isRunning() {
        return running;
    }

    public String getBindHost() {
        return bindHost;
    }

    public int getPort() {
        return actualPort > 0 ? actualPort : listenPort;
    }

    public int getActiveClientCount() {
        return activeClients.size();
    }

    public int getActiveWorkerCount() {
        return workerPool != null ? workerPool.getActiveCount() : 0;
    }

    /**
     * Ngoại lệ ném ra khi client gửi gói tin vượt quá giới hạn 64 KB.
     */
    public static class FrameTooLargeException extends Exception {
        public FrameTooLargeException(String message) {
            super(message);
        }
    }
}
