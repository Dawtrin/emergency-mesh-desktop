package com.rescue.mesh.ui.controller;

import com.rescue.mesh.model.MeshPacket;
import com.rescue.mesh.network.NodeConfig;
import com.rescue.mesh.network.SocketServer;
import com.rescue.mesh.routing.RoutingEngine;
import com.rescue.mesh.util.PacketFactory;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;

import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.ResourceBundle;

/**
 * JavaFX Controller cho Node Giả Lập (Victim / Relay).
 *
 * Design Pattern:
 *   - MVC: Controller kết nối Model (MeshPacket) với View (FXML)
 *   - Observer: Implement RoutingCallback + ServerEventListener
 *     để nhận sự kiện từ RoutingEngine và SocketServer
 *   - Thread Confinement: Mọi UI update từ network thread
 *     phải qua Platform.runLater() (JavaFX thread-safety rule)
 *
 * 2 chế độ:
 *   - VICTIM: Hiện form nhập SOS, nút gửi, và log
 *   - RELAY:  Ẩn form, chỉ hiện log (auto relay)
 *
 * Lifecycle:
 *   1. FXML Loader gọi initialize() → setup ComboBox items
 *   2. App gọi initializeNode(config) → khởi tạo RoutingEngine + SocketServer
 *   3. User tương tác → onSendSos(), onQuickSos(), etc.
 *   4. App gọi shutdown() khi đóng cửa sổ
 */
public class NodeClientController implements Initializable,
        RoutingEngine.RoutingCallback,
        SocketServer.ServerEventListener {

    // =========================================================
    // @FXML BINDINGS — khớp fx:id trong NodeClient.fxml
    // =========================================================

    @FXML private Label lblNodeId;
    @FXML private Label lblNodeInfo;
    @FXML private Label lblStatus;
    @FXML private Circle statusDot;
    @FXML private VBox sosFormContainer;
    @FXML private VBox dispatchCard;
    @FXML private Label lblDispatchContent;
    @FXML private TextField txtSenderName;
    @FXML private ComboBox<String> cboAlertType;
    @FXML private ComboBox<String> cboSeverity;
    @FXML private TextField txtVictimCount;
    @FXML private TextField txtLatitude;
    @FXML private TextField txtLongitude;
    @FXML private TextArea txtMessage;
    @FXML private Button btnSendSos;
    @FXML private Button btnQuickSos;
    @FXML private Button btnHeartbeat;
    @FXML private TextArea txtLog;

    // =========================================================
    // STATE
    // =========================================================

    private NodeConfig config;
    private RoutingEngine routingEngine;
    private SocketServer socketServer;

    /** Format thời gian cho log entries */
    private final SimpleDateFormat logTimeFormat = new SimpleDateFormat("HH:mm:ss");

    /** Đếm số gói tin SOS đã gửi */
    private int sentCount = 0;

    // =========================================================
    // INITIALIZE — Pha 1: Setup UI components
    // =========================================================

    /**
     * Được gọi tự động bởi FXMLLoader sau khi load FXML xong.
     * Setup giá trị mặc định cho ComboBox và form fields.
     */
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // Populate ComboBox items
        cboAlertType.getItems().addAll(
                "FLOOD_TRAPPED",    // Mắc kẹt lũ lụt
                "MEDICAL",          // Cấp cứu y tế
                "LANDSLIDE"         // Sạt lở đất
        );
        cboAlertType.setValue("FLOOD_TRAPPED");

        cboSeverity.getItems().addAll(
                "CRITICAL",         // Nguy hiểm tính mạng
                "HIGH",             // Nguy hiểm cao
                "MEDIUM"            // Cần hỗ trợ
        );
        cboSeverity.setValue("CRITICAL");

        // Giá trị mặc định — Khuôn viên Trường ĐH CNTT&TT Việt - Hàn (VKU, Ngũ Hành Sơn, Đà Nẵng)
        txtSenderName.setText("Sinh Viên VKU");
        txtVictimCount.setText("1");
        txtLatitude.setText("15.9738");
        txtLongitude.setText("108.2515");

        // Log area không cho edit
        txtLog.setEditable(false);
    }

    // =========================================================
    // INITIALIZE NODE — Pha 2: Khởi tạo networking
    // =========================================================

    /**
     * Khởi tạo RoutingEngine và SocketServer với config từ App.
     * Được gọi SAU khi FXML đã load xong.
     *
     * @param config Cấu hình node (mode, port, next-hop, nodeId)
     */
    public void initializeNode(NodeConfig config) {
        this.config = config;

        // Cập nhật header
        lblNodeId.setText(config.getNodeId());
        lblNodeInfo.setText("Port: " + config.getListenPort()
                + " → Next: " + (config.getNextHopPort() > 0 ? config.getNextHopPort() : "N/A"));

        // Ẩn form SOS nếu là RELAY mode
        if (config.getMode() == NodeConfig.NodeMode.RELAY) {
            sosFormContainer.setVisible(false);
            sosFormContainer.setManaged(false);
            appendLog("[INFO] Chế độ RELAY — tự động chuyển tiếp gói tin.");
        }

        // Khởi tạo RoutingEngine — controller này implement callback
        routingEngine = new RoutingEngine(
                config.getNodeId(),
                config.getNextHopHost(),
                config.getNextHopPort(),
                this  // NodeClientController implements RoutingCallback
        );

        // Khởi động SocketServer — lắng nghe gói tin đến
        socketServer = new SocketServer(
                config.getListenPort(),
                routingEngine,
                this  // NodeClientController implements ServerEventListener
        );
        socketServer.start();

        appendLog("[INFO] Node " + config.getNodeId() + " đang khởi tạo...");
    }

    // =========================================================
    // EVENT HANDLERS — Xử lý sự kiện UI
    // =========================================================

    /**
     * Xử lý nút "GỬI TÍN HIỆU SOS" — tạo packet SOS đầy đủ.
     */
    @FXML
    private void onSendSos() {
        // Đọc giá trị từ form
        String senderName = txtSenderName.getText().trim();
        if (senderName.isEmpty()) senderName = "Sinh Viên VKU";

        String alertType = cboAlertType.getValue();
        String severity = cboSeverity.getValue();

        int victimCount = 1;
        try {
            victimCount = Integer.parseInt(txtVictimCount.getText().trim());
        } catch (NumberFormatException e) {
            appendLog("[WARN] Số nạn nhân không hợp lệ, dùng mặc định = 1");
        }

        double latitude = 15.9738;
        try {
            latitude = Double.parseDouble(txtLatitude.getText().trim());
        } catch (NumberFormatException e) {
            appendLog("[WARN] Vĩ độ không hợp lệ, dùng mặc định = 15.9738");
        }

        double longitude = 108.2515;
        try {
            longitude = Double.parseDouble(txtLongitude.getText().trim());
        } catch (NumberFormatException e) {
            appendLog("[WARN] Kinh độ không hợp lệ, dùng mặc định = 108.2515");
        }

        String message = txtMessage.getText() != null ? txtMessage.getText().trim() : "";
        if (message.isEmpty()) message = "Mắc kẹt tại Ký túc xá VKU, nước dâng cao!";

        // Tạo gói tin SOS
        MeshPacket sosPacket = PacketFactory.createSosPacket(
                config.getNodeId(),
                senderName,
                alertType,
                message,
                victimCount,
                severity,
                latitude,
                longitude
        );

        sentCount++;
        appendLog("[SEND] SOS #" + sentCount + " — " + alertType + " / " + severity
                + " / " + victimCount + " người");

        // Xử lý trong background thread — không block UI
        Thread sendThread = new Thread(() -> routingEngine.processPacket(sosPacket),
                "SOS-Send-" + sentCount);
        sendThread.setDaemon(true);
        sendThread.start();
    }

    /**
     * Xử lý nút "SOS Nhanh" — gửi với thông tin mặc định.
     */
    @FXML
    private void onQuickSos() {
        appendLog("[INFO] Gửi SOS nhanh tại VKU...");

        MeshPacket quickSos = PacketFactory.createSosPacket(
                config.getNodeId(),
                "Sinh Viên VKU",
                MeshPacket.ALERT_FLOOD,
                "DEMO: Mắc kẹt tại tầng 2 Ký túc xá VKU, cần cano cứu hộ ngay!",
                2,
                MeshPacket.SEVERITY_CRITICAL,
                15.9738,
                108.2515
        );

        sentCount++;
        Thread sendThread = new Thread(() -> routingEngine.processPacket(quickSos),
                "QuickSOS-" + sentCount);
        sendThread.setDaemon(true);
        sendThread.start();
    }

    /**
     * Xử lý nút "Heartbeat" — kiểm tra kết nối.
     */
    @FXML
    private void onHeartbeat() {
        appendLog("[INFO] Gửi HEARTBEAT...");

        MeshPacket heartbeat = PacketFactory.createHeartbeat(config.getNodeId());

        Thread hbThread = new Thread(() -> routingEngine.processPacket(heartbeat),
                "Heartbeat");
        hbThread.setDaemon(true);
        hbThread.start();
    }

    // =========================================================
    // ROUTING CALLBACK — Nhận sự kiện từ RoutingEngine
    // =========================================================
    // Tất cả method được gọi từ NETWORK thread →
    // PHẢI dùng Platform.runLater() cho UI update!

    @Override
    public void onPacketArrived(MeshPacket packet) {
        Platform.runLater(() -> {
            if (MeshPacket.TYPE_DISPATCH_CMD.equals(packet.getPacketType())) {
                // Hiển thị lệnh chỉ đạo
                onDispatchReceived(packet);
            } else {
                appendLog("[ALERT] *** TÍN HIỆU SOS ĐÃ ĐẾN ĐÍCH ***");
                if (packet.getPayload() != null) {
                    appendLog("[ALERT]   Loại: " + packet.getPayload().getAlertType());
                    appendLog("[ALERT]   Mức: " + packet.getPayload().getSeverity());
                    appendLog("[ALERT]   Tin nhắn: " + packet.getPayload().getMessage());
                }
                appendLog("[ALERT]   Đường đi: " + packet.getRouteHistory());
            }
        });
    }

    @Override
    public void onPacketRelayed(MeshPacket packet, int nextHop) {
        Platform.runLater(() ->
                appendLog("[RELAY] " + shortId(packet.getPacketId())
                        + " → port " + nextHop
                        + " (TTL=" + packet.getTtl() + ", hops=" + packet.getHopCount() + ")")
        );
    }

    @Override
    public void onPacketDropped(String packetId, String reason) {
        Platform.runLater(() ->
                appendLog("[DROP] " + shortId(packetId) + " — " + reason)
        );
    }

    @Override
    public void onForwardError(int nextHop, String errorMessage) {
        Platform.runLater(() -> {
            appendLog("[ERROR] Không relay được → port " + nextHop + ": " + errorMessage);
            setStatus("Lỗi kết nối", "disconnected");
        });
    }

    @Override
    public void onDispatchReceived(MeshPacket packet) {
        Platform.runLater(() -> {
            // Hiện card dispatch
            dispatchCard.setVisible(true);
            dispatchCard.setManaged(true);

            String content = "";
            if (packet.getPayload() != null) {
                content = "Lệnh: " + packet.getPayload().getMessage()
                        + "\nMức ưu tiên: " + packet.getPayload().getSeverity();
            }
            lblDispatchContent.setText(content);
            appendLog("[INFO] *** NHẬN LỆNH CHỈ ĐẠO TỪ TRẠM CHỈ HUY ***");
            if (packet.getPayload() != null) {
                appendLog("[INFO]   " + packet.getPayload().getMessage());
            }
        });
    }

    // =========================================================
    // SERVER EVENT LISTENER — Nhận sự kiện từ SocketServer
    // =========================================================

    @Override
    public void onServerStarted(int port) {
        Platform.runLater(() -> {
            appendLog("[INFO] Server lắng nghe tại port " + port + " — OK");
            setStatus("Đang lắng nghe", "connected");
        });
    }

    @Override
    public void onClientConnected(String clientAddress) {
        Platform.runLater(() ->
                appendLog("[INFO] Kết nối từ: " + clientAddress)
        );
    }

    @Override
    public void onServerError(String errorMessage) {
        Platform.runLater(() -> {
            appendLog("[ERROR] Server: " + errorMessage);
            setStatus("Lỗi server", "disconnected");
        });
    }

    @Override
    public void onServerStopped() {
        Platform.runLater(() -> {
            appendLog("[INFO] Server đã dừng.");
            setStatus("Đã dừng", "disconnected");
        });
    }

    // =========================================================
    // UTILITY
    // =========================================================

    /**
     * Thêm dòng log với timestamp vào TextArea.
     * Auto-scroll xuống dòng mới nhất.
     */
    private void appendLog(String message) {
        String timestamp = logTimeFormat.format(new Date());
        String line = "[" + timestamp + "] " + message + "\n";
        txtLog.appendText(line);
        // Auto-scroll xuống cuối
        txtLog.setScrollTop(Double.MAX_VALUE);
    }

    /**
     * Cập nhật trạng thái trên header.
     */
    private void setStatus(String text, String type) {
        lblStatus.setText(text);
        statusDot.getStyleClass().removeAll(
                "status-dot-connected", "status-dot-disconnected", "status-dot-waiting");
        switch (type) {
            case "connected":
                statusDot.getStyleClass().add("status-dot-connected");
                break;
            case "disconnected":
                statusDot.getStyleClass().add("status-dot-disconnected");
                break;
            default:
                statusDot.getStyleClass().add("status-dot-waiting");
        }
    }

    /**
     * Trả về 8 ký tự đầu của UUID.
     */
    private String shortId(String id) {
        if (id == null) return "null";
        return id.length() > 8 ? id.substring(0, 8) : id;
    }

    /**
     * Dọn dẹp tài nguyên khi đóng cửa sổ.
     */
    public void shutdown() {
        if (socketServer != null) {
            socketServer.stop();
        }
        if (routingEngine != null) {
            routingEngine.shutdown();
        }
        appendLog("[INFO] Node đã shutdown.");
    }
}
