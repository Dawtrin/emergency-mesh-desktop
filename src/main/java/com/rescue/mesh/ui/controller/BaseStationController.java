package com.rescue.mesh.ui.controller;

import com.rescue.mesh.audio.AlarmPlayer;
import com.rescue.mesh.model.MeshPacket;
import com.rescue.mesh.network.NodeConfig;
import com.rescue.mesh.network.SocketClient;
import com.rescue.mesh.network.SocketServer;
import com.rescue.mesh.routing.RoutingEngine;
import com.rescue.mesh.ui.component.VictimTableRow;
import com.rescue.mesh.util.PacketFactory;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.shape.Circle;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;

import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.ResourceBundle;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * JavaFX Controller cho Dashboard Trạm Chỉ Huy Trung Tâm.
 *
 * Design Pattern:
 *   - MVC: Controller kết nối Model (MeshPacket/VictimTableRow) với View (FXML)
 *   - Observer: Implement RoutingCallback + ServerEventListener
 *   - Adapter: VictimTableRow.fromMeshPacket() chuyển đổi dữ liệu
 *   - Thread Confinement: Platform.runLater() cho mọi UI update
 *
 * Chức năng chính:
 *   1. Nhận SOS → hiển thị bảng + bản đồ + còi hú
 *   2. Gửi DISPATCH_CMD ngược về node nạn nhân
 *   3. Thống kê real-time (CRITICAL / HIGH / MEDIUM)
 *   4. WebView bridge gọi JS function trên Leaflet map
 *
 * Lifecycle:
 *   1. initialize() → setup TableView, ComboBox
 *   2. initializeStation(config) → RoutingEngine + SocketServer + WebView
 *   3. Callbacks → update UI
 *   4. shutdown() → cleanup
 */
public class BaseStationController implements Initializable,
        RoutingEngine.RoutingCallback,
        SocketServer.ServerEventListener {

    // =========================================================
    // @FXML BINDINGS
    // =========================================================

    @FXML private Circle serverStatusDot;
    @FXML private Label lblServerStatus;
    @FXML private Label lblTotalSos;
    @FXML private Label lblCritical;
    @FXML private Label lblHigh;
    @FXML private Label lblMedium;
    @FXML private Label lblCacheSize;

    @FXML private TableView<VictimTableRow> tblVictims;
    @FXML private TableColumn<VictimTableRow, String> colStt;
    @FXML private TableColumn<VictimTableRow, String> colSourceNode;
    @FXML private TableColumn<VictimTableRow, String> colAlertType;
    @FXML private TableColumn<VictimTableRow, String> colSeverity;
    @FXML private TableColumn<VictimTableRow, String> colVictimCount;
    @FXML private TableColumn<VictimTableRow, String> colCoordinates;
    @FXML private TableColumn<VictimTableRow, String> colTime;

    @FXML private WebView mapWebView;
    @FXML private TextArea txtLog;

    @FXML private ComboBox<String> cboTargetNode;
    @FXML private TextField txtDispatchCommand;
    @FXML private ComboBox<String> cboDispatchSeverity;
    @FXML private Button btnSendDispatch;
    @FXML private Button btnStopAlarm;

    // =========================================================
    // STATE
    // =========================================================

    private NodeConfig config;
    private RoutingEngine routingEngine;
    private SocketServer socketServer;
    private AlarmPlayer alarmPlayer;
    private WebEngine webEngine;

    /** Danh sách nạn nhân — ObservableList bind vào TableView */
    private final ObservableList<VictimTableRow> victimData = FXCollections.observableArrayList();

    /** Lưu gói tin gốc để tham chiếu khi gửi DISPATCH */
    private final CopyOnWriteArrayList<MeshPacket> receivedSosList = new CopyOnWriteArrayList<>();

    /** Thống kê */
    private int totalSos = 0;
    private int criticalCount = 0;
    private int highCount = 0;
    private int mediumCount = 0;

    /** Host/IP của relay node để gửi DISPATCH ngược */
    private String relayHost = "localhost";

    /** Port relay node để gửi DISPATCH ngược */
    private int relayNodePort = 8002;

    /** Format thời gian cho log */
    private final SimpleDateFormat logTimeFormat = new SimpleDateFormat("HH:mm:ss");

    /** Flag bản đồ đã load xong chưa */
    private volatile boolean mapReady = false;

    // =========================================================
    // INITIALIZE — Pha 1: Setup UI
    // =========================================================

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // Setup TableView columns — PropertyValueFactory bind tên field
        colStt.setCellValueFactory(new PropertyValueFactory<>("stt"));
        colSourceNode.setCellValueFactory(new PropertyValueFactory<>("sourceNode"));
        colAlertType.setCellValueFactory(new PropertyValueFactory<>("alertType"));
        colSeverity.setCellValueFactory(new PropertyValueFactory<>("severity"));
        colVictimCount.setCellValueFactory(new PropertyValueFactory<>("victimCount"));
        colCoordinates.setCellValueFactory(new PropertyValueFactory<>("coordinates"));
        colTime.setCellValueFactory(new PropertyValueFactory<>("time"));

        tblVictims.setItems(victimData);

        // Setup Dispatch severity combo
        cboDispatchSeverity.getItems().addAll("CRITICAL", "HIGH", "MEDIUM");
        cboDispatchSeverity.setValue("CRITICAL");

        // Log area
        txtLog.setEditable(false);

        // Alarm player
        alarmPlayer = new AlarmPlayer();
    }

    // =========================================================
    // INITIALIZE STATION — Pha 2: Networking + Map
    // =========================================================

    /**
     * Khởi tạo RoutingEngine, SocketServer, và WebView map.
     *
     * @param config Cấu hình Base Station
     */
    public void initializeStation(NodeConfig config) {
        this.config = config;

        // Đọc relay port từ config hoặc dùng mặc định
        relayNodePort = 8002;

        // Khởi tạo RoutingEngine — Base Station là đích cuối (nextHopPort = -1)
        routingEngine = new RoutingEngine(
                MeshPacket.NODE_BASE_STATION,
                "localhost",
                -1,
                this  // implements RoutingCallback
        );

        // Khởi động SocketServer
        socketServer = new SocketServer(
                config.getListenPort(),
                routingEngine,
                this  // implements ServerEventListener
        );
        socketServer.start();

        // Khởi tạo WebView bản đồ
        initializeMap();

        appendLog("[INFO] Base Station đang khởi tạo tại port " + config.getListenPort() + "...");
    }

    /**
     * Tải bản đồ Leaflet vào WebView.
     */
    private void initializeMap() {
        webEngine = mapWebView.getEngine();

        // Khi map load xong → đánh dấu sẵn sàng
        webEngine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
            if (newState == javafx.concurrent.Worker.State.SUCCEEDED) {
                mapReady = true;
                appendLog("[INFO] Bản đồ Leaflet đã tải xong.");
            } else if (newState == javafx.concurrent.Worker.State.FAILED) {
                appendLog("[ERROR] Không tải được bản đồ: " + webEngine.getLoadWorker().getException());
            }
        });

        // Load HTML từ resources
        URL mapUrl = getClass().getResource("/map/leaflet_offline.html");
        if (mapUrl != null) {
            webEngine.load(mapUrl.toExternalForm());
        } else {
            appendLog("[ERROR] Không tìm thấy file leaflet_offline.html trong resources.");
        }
    }

    // =========================================================
    // EVENT HANDLERS
    // =========================================================

    /**
     * Gửi lệnh DISPATCH_CMD về node nạn nhân qua relay.
     */
    @FXML
    private void onSendDispatch() {
        String targetNode = cboTargetNode.getValue();
        if (targetNode == null || targetNode.isEmpty()) {
            appendLog("[WARN] Chưa chọn nạn nhân để gửi lệnh.");
            return;
        }

        String command = txtDispatchCommand.getText();
        if (command == null || command.trim().isEmpty()) {
            command = "Di chuyển đến điểm tập kết an toàn. Lực lượng cứu hộ đang đến.";
        }

        String severity = cboDispatchSeverity.getValue();
        if (severity == null) severity = MeshPacket.SEVERITY_CRITICAL;

        MeshPacket dispatchPacket = PacketFactory.createDispatchCommand(
                targetNode,
                command.trim(),
                severity
        );

        final String displayCmd = command.trim();
        appendLog("[SEND] Gửi lệnh điều phối → " + targetNode + " qua relay " + relayHost + ":" + relayNodePort);

        // Gửi async — không block UI
        SocketClient.sendAsync(relayHost, relayNodePort, dispatchPacket,
                new SocketClient.SendCallback() {
                    @Override
                    public void onSendSuccess(String packetId, String host, int port) {
                        Platform.runLater(() ->
                                appendLog("[SEND] Lệnh đã gửi thành công: " + displayCmd)
                        );
                    }

                    @Override
                    public void onSendFailed(String packetId, String host, int port, String error) {
                        Platform.runLater(() ->
                                appendLog("[ERROR] Gửi lệnh thất bại: " + error)
                        );
                    }
                });

        // Clear input
        txtDispatchCommand.clear();
    }

    /**
     * Tắt còi báo động.
     */
    @FXML
    private void onStopAlarm() {
        alarmPlayer.stop();
        appendLog("[INFO] Đã tắt còi báo động.");
    }

    // =========================================================
    // ROUTING CALLBACK
    // =========================================================

    @Override
    public void onPacketArrived(MeshPacket packet) {
        Platform.runLater(() -> {
            // Lưu SOS
            receivedSosList.add(packet);
            totalSos++;

            // Đếm theo severity
            if (packet.getPayload() != null) {
                String sev = packet.getPayload().getSeverity();
                if (MeshPacket.SEVERITY_CRITICAL.equals(sev)) criticalCount++;
                else if (MeshPacket.SEVERITY_HIGH.equals(sev)) highCount++;
                else if (MeshPacket.SEVERITY_MEDIUM.equals(sev)) mediumCount++;
            }

            // Cập nhật stats
            updateStats();

            // Thêm vào bảng
            VictimTableRow row = VictimTableRow.fromMeshPacket(packet, totalSos);
            victimData.add(row);

            // Cập nhật ComboBox target node
            String sourceNodeId = packet.getSourceNodeId();
            if (!cboTargetNode.getItems().contains(sourceNodeId)) {
                cboTargetNode.getItems().add(sourceNodeId);
            }
            if (cboTargetNode.getValue() == null) {
                cboTargetNode.setValue(sourceNodeId);
            }

            // Thêm marker trên bản đồ
            addMarkerToMap(packet);

            // Phát còi báo động
            alarmPlayer.play();

            // Log chi tiết
            appendLog("╔══════════════════════════════════════════╗");
            appendLog("║  🚨 SOS MỚI — TÍN HIỆU #" + totalSos + " ĐÃ TIẾP NHẬN");
            appendLog("╠══════════════════════════════════════════╣");
            appendLog("║  Nguồn  : " + packet.getSourceNodeId());
            if (packet.getPayload() != null) {
                appendLog("║  Loại   : " + packet.getPayload().getAlertType());
                appendLog("║  Mức    : " + packet.getPayload().getSeverity());
                appendLog("║  Nạn nhân: " + packet.getPayload().getVictimCount() + " người");
                if (packet.getPayload().getLocation() != null) {
                    appendLog("║  Tọa độ : " + packet.getPayload().getLocation().getLatitude()
                            + ", " + packet.getPayload().getLocation().getLongitude());
                }
            }
            appendLog("║  Route  : " + packet.getRouteHistory());
            appendLog("║  Hops   : " + packet.getHopCount());
            appendLog("╚══════════════════════════════════════════╝");

            // Auto-select new row
            tblVictims.getSelectionModel().selectLast();
            tblVictims.scrollTo(victimData.size() - 1);
        });
    }

    @Override
    public void onPacketRelayed(MeshPacket packet, int nextHop) {
        // Base Station không relay — method này không được gọi
    }

    @Override
    public void onPacketDropped(String packetId, String reason) {
        Platform.runLater(() -> {
            appendLog("[DROP] " + shortId(packetId) + " — " + reason);
            lblCacheSize.setText(String.valueOf(routingEngine.getCacheSize()));
        });
    }

    @Override
    public void onForwardError(int nextHop, String errorMessage) {
        Platform.runLater(() ->
                appendLog("[ERROR] Forward error → port " + nextHop + ": " + errorMessage)
        );
    }

    @Override
    public void onDispatchReceived(MeshPacket packet) {
        // Base Station gửi đi DISPATCH, không nhận
    }

    // =========================================================
    // SERVER EVENT LISTENER
    // =========================================================

    @Override
    public void onServerStarted(int port) {
        Platform.runLater(() -> {
            appendLog("[INFO] Base Station Server tại port " + port + " — OK");
            setServerStatus("Đang lắng nghe — Port " + port, "connected");
        });
    }

    @Override
    public void onClientConnected(String clientAddress) {
        Platform.runLater(() -> {
            appendLog("[INFO] Relay node kết nối: " + clientAddress);
            if (clientAddress != null && clientAddress.contains(":")) {
                String ip = clientAddress.split(":")[0];
                if (!"127.0.0.1".equals(ip) && !"localhost".equals(ip) && !ip.isEmpty()) {
                    this.relayHost = ip;
                    appendLog("[INFO] Đã ghi nhận địa chỉ Relay IP: " + ip);
                }
            }
        });
    }

    @Override
    public void onServerError(String errorMessage) {
        Platform.runLater(() -> {
            appendLog("[ERROR] Server: " + errorMessage);
            setServerStatus("Lỗi server", "disconnected");
        });
    }

    @Override
    public void onServerStopped() {
        Platform.runLater(() -> {
            appendLog("[INFO] Server đã dừng.");
            setServerStatus("Đã dừng", "disconnected");
        });
    }

    // =========================================================
    // MAP BRIDGE — Java → JavaScript
    // =========================================================

    /**
     * Thêm marker SOS lên bản đồ Leaflet qua WebEngine.
     * Gọi JS function addSOSMarker() đã định nghĩa trong leaflet_offline.html.
     */
    private void addMarkerToMap(MeshPacket packet) {
        if (!mapReady || webEngine == null || packet.getPayload() == null) {
            return;
        }

        try {
            MeshPacket.Payload payload = packet.getPayload();
            MeshPacket.Location loc = payload.getLocation();

            if (loc == null) return;

            // Escape strings cho JavaScript
            String nodeId = escapeJs(packet.getSourceNodeId());
            String severity = escapeJs(payload.getSeverity());
            String alertType = escapeJs(payload.getAlertType());
            String message = escapeJs(payload.getMessage());
            int victimCount = payload.getVictimCount();

            String jsCall = String.format(
                    "addSOSMarker(%f, %f, '%s', '%s', '%s', '%s', %d)",
                    loc.getLatitude(), loc.getLongitude(),
                    nodeId, severity, alertType, message, victimCount
            );

            webEngine.executeScript(jsCall);

        } catch (Exception e) {
            appendLog("[ERROR] Không thêm được marker bản đồ: " + e.getMessage());
        }
    }

    // =========================================================
    // UTILITY
    // =========================================================

    /**
     * Cập nhật các label thống kê.
     */
    private void updateStats() {
        lblTotalSos.setText(String.valueOf(totalSos));
        lblCritical.setText(String.valueOf(criticalCount));
        lblHigh.setText(String.valueOf(highCount));
        lblMedium.setText(String.valueOf(mediumCount));
        lblCacheSize.setText(String.valueOf(routingEngine.getCacheSize()));
    }

    /**
     * Cập nhật trạng thái server trên header.
     */
    private void setServerStatus(String text, String type) {
        lblServerStatus.setText(text);
        serverStatusDot.getStyleClass().removeAll(
                "status-dot-connected", "status-dot-disconnected", "status-dot-waiting");
        switch (type) {
            case "connected":
                serverStatusDot.getStyleClass().add("status-dot-connected");
                break;
            case "disconnected":
                serverStatusDot.getStyleClass().add("status-dot-disconnected");
                break;
            default:
                serverStatusDot.getStyleClass().add("status-dot-waiting");
        }
    }

    /**
     * Thêm dòng log + auto-scroll.
     */
    private void appendLog(String message) {
        String timestamp = logTimeFormat.format(new Date());
        String line = "[" + timestamp + "] " + message + "\n";
        txtLog.appendText(line);
        txtLog.setScrollTop(Double.MAX_VALUE);
    }

    /**
     * Escape chuỗi cho JS string literal.
     */
    private String escapeJs(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\")
                  .replace("'", "\\'")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "");
    }

    private String shortId(String id) {
        if (id == null) return "null";
        return id.length() > 8 ? id.substring(0, 8) : id;
    }

    /**
     * Đặt relay port.
     */
    public void setRelayNodePort(int port) {
        this.relayNodePort = port;
    }

    /**
     * Đặt relay host (IP hoặc domain).
     */
    public void setRelayHost(String host) {
        if (host != null && !host.trim().isEmpty()) {
            this.relayHost = host.trim();
        }
    }

    /**
     * Dọn dẹp tài nguyên khi đóng ứng dụng.
     */
    public void shutdown() {
        if (alarmPlayer != null) {
            alarmPlayer.dispose();
        }
        if (socketServer != null) {
            socketServer.stop();
        }
        if (routingEngine != null) {
            routingEngine.shutdown();
        }
        appendLog("[INFO] Base Station đã shutdown.");
    }
}
