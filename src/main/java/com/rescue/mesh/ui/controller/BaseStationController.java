package com.rescue.mesh.ui.controller;

import com.rescue.mesh.audio.AlarmPlayer;
import com.rescue.mesh.model.MeshPacket;
import com.rescue.mesh.network.NodeConfig;
import com.rescue.mesh.network.SocketClient;
import com.rescue.mesh.network.SocketServer;
import com.rescue.mesh.routing.LoadBalancer;
import com.rescue.mesh.routing.NodeStatus;
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
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.shape.Circle;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;

import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

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

    // --- Monitoring Panel FXML bindings ---
    @FXML private Label lblOnlineNodes;
    @FXML private Label lblNodeStatusList;
    @FXML private Label lblDistribution;

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

    // --- Victim Detail Preview Card ---
    @FXML private Label lblVictimDetailName;
    @FXML private Label lblVictimDetailSeverity;
    @FXML private Label lblVictimDetailMessage;
    @FXML private Label lblVictimDetailRoute;
    @FXML private Label lblVictimDetailCoords;
    @FXML private javafx.scene.layout.VBox victimDetailCard;

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

    /** Bộ cân bằng tải — quản lý bảng định tuyến relay */
    private LoadBalancer loadBalancer;

    /** Scheduler cập nhật panel monitoring mỗi 2 giây */
    private ScheduledExecutorService monitoringScheduler;

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

        // ────────────────────────────────────────────────────────────
        // Phase 1.4: Tối ưu kích thước cột — loại bỏ cuộn ngang
        // ────────────────────────────────────────────────────────────
        tblVictims.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        // ────────────────────────────────────────────────────────────
        // Phase 1.1: RowFactory tô màu dòng theo mức khẩn cấp
        // CSS classes: .severity-critical, .severity-high, .severity-medium
        // ────────────────────────────────────────────────────────────
        tblVictims.setRowFactory(tv -> new TableRow<VictimTableRow>() {
            @Override
            protected void updateItem(VictimTableRow item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().removeAll("severity-critical", "severity-high", "severity-medium");
                if (item != null && !empty) {
                    String sev = item.getSeverity();
                    if ("CRITICAL".equalsIgnoreCase(sev)) getStyleClass().add("severity-critical");
                    else if ("HIGH".equalsIgnoreCase(sev)) getStyleClass().add("severity-high");
                    else if ("MEDIUM".equalsIgnoreCase(sev)) getStyleClass().add("severity-medium");
                }
            }
        });

        // ────────────────────────────────────────────────────────────
        // Phase 1.2: SelectionModel listener — Click bảng → FlyTo bản đồ
        //   + Auto-fill ComboBox gửi lệnh + Hiện Victim Detail Card
        // ────────────────────────────────────────────────────────────
        tblVictims.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                // 1. Zoom và mở popup trên bản đồ
                focusVictimOnMap(newVal.getSourceNode(), newVal.getCoordinates());
                // 2. Tự động chọn nạn nhân vào ComboBox gửi lệnh
                if (!cboTargetNode.getItems().contains(newVal.getSourceNode())) {
                    cboTargetNode.getItems().add(newVal.getSourceNode());
                }
                cboTargetNode.setValue(newVal.getSourceNode());
                // 3. Hiển thị Victim Detail Preview Card
                showVictimDetails(newVal);
            }
        });

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

        // Khởi tạo LoadBalancer
        loadBalancer = new LoadBalancer();

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

        // Khởi động scheduler cập nhật monitoring panel mỗi 2 giây
        startMonitoringScheduler();
    }

    /**
     * Khởi động scheduler làm mới thông tin monitoring trên Dashboard.
     * Cập nhật trạng thái ONLINE/OFFLINE, tải, phân bố mỗi 2 giây.
     */
    private void startMonitoringScheduler() {
        monitoringScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "Monitoring-Refresh");
            t.setDaemon(true);
            return t;
        });

        monitoringScheduler.scheduleAtFixedRate(() -> {
            Platform.runLater(this::refreshMonitoring);
        }, 3, 2, TimeUnit.SECONDS);
    }

    /**
     * Làm mới panel monitoring với dữ liệu từ LoadBalancer.
     */
    private void refreshMonitoring() {
        if (loadBalancer == null) return;

        // Số node online
        int online = loadBalancer.getOnlineCount();
        int total = loadBalancer.getTotalCount();
        if (lblOnlineNodes != null) {
            lblOnlineNodes.setText(online + "/" + total);
        }

        // Danh sách trạng thái từng node
        if (lblNodeStatusList != null) {
            StringBuilder sb = new StringBuilder();
            Map<String, NodeStatus> table = loadBalancer.getRoutingTable();
            if (table.isEmpty()) {
                sb.append("Đang chờ relay kết nối...");
            } else {
                for (Map.Entry<String, NodeStatus> entry : table.entrySet()) {
                    NodeStatus ns = entry.getValue();
                    String status = ns.isOnline() ? "✅ ONLINE" : "❌ OFFLINE";
                    String bar = "█".repeat(Math.min(ns.getCurrentLoad(), 10))
                               + "░".repeat(Math.max(0, 5 - Math.min(ns.getCurrentLoad(), 5)));
                    sb.append(entry.getKey())
                      .append(" [" + status + "] ")
                      .append("Load: ").append(bar).append(" ").append(ns.getCurrentLoad())
                      .append(" | Total: ").append(ns.getProcessedTotal())
                      .append(" | ").append(ns.getSecondsSinceLastHeartbeat()).append("s ago")
                      .append("\n");
                }
            }
            lblNodeStatusList.setText(sb.toString().trim());
        }

        // Phân bố tải
        if (lblDistribution != null) {
            Map<String, Double> dist = loadBalancer.getDistributionStats();
            StringBuilder sb = new StringBuilder();
            dist.forEach((id, pct) ->
                    sb.append(id).append(": ").append(String.format("%.0f%%", pct)).append("  "));
            lblDistribution.setText(sb.toString().trim());
        }
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
     * Gửi lệnh DISPATCH_CMD về node nạn nhân kết hợp Định tuyến nguồn (Strict Source Routing),
     * Thuật toán phân bố tải Least-Load Routing, và Failover Alternate Routing.
     *
     * Phase 4: Nếu relay được chọn bị lỗi → tự động thử relay tải thấp tiếp theo.
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

        // Lấy danh sách relay online, sắp xếp theo tải tăng dần
        List<NodeStatus> onlineRelays = new ArrayList<>();
        if (loadBalancer != null) {
            loadBalancer.getRoutingTable().values().stream()
                    .filter(NodeStatus::isOnline)
                    .sorted(java.util.Comparator.comparingInt(NodeStatus::getCurrentLoad))
                    .forEach(onlineRelays::add);
        }

        // Fallback nếu không có relay nào online
        if (onlineRelays.isEmpty()) {
            NodeStatus fallback = new NodeStatus("NODE_B1_RELAY", 0, 0, relayHost, relayNodePort);
            onlineRelays.add(fallback);
        }

        final String displayCmd = command.trim();
        final String finalSeverity = severity;
        final String finalTargetNode = targetNode;

        // Clear input ngay lập tức
        txtDispatchCommand.clear();

        // ────────────────────────────────────────────────────────────
        // Phase 4: Failover Alternate Routing — thử từng relay
        // ────────────────────────────────────────────────────────────
        Thread failoverThread = new Thread(() -> {
            for (int i = 0; i < onlineRelays.size(); i++) {
                NodeStatus relay = onlineRelays.get(i);
                String chosenRelayId = relay.getNodeId();
                String sendHost = (relay.getIpAddress() != null && !relay.getIpAddress().isEmpty())
                        ? relay.getIpAddress() : relayHost;
                int sendPort = (relay.getPort() > 0) ? relay.getPort() : relayNodePort;

                // Xây dựng lộ trình nguồn chỉ định (Strict Source Routing)
                List<String> designatedRoute = new ArrayList<>(Arrays.asList(
                        MeshPacket.NODE_BASE_STATION,
                        chosenRelayId,
                        finalTargetNode
                ));

                MeshPacket dispatchPacket = PacketFactory.createDispatchCommand(
                        finalTargetNode,
                        displayCmd,
                        finalSeverity,
                        designatedRoute
                );

                String loadInfo = " (Relay: " + chosenRelayId + ", load=" + relay.getCurrentLoad() + ")";
                final int attemptNum = i + 1;
                final int totalRelays = onlineRelays.size();

                Platform.runLater(() -> {
                    appendLog("[DISPATCH] Lệnh chỉ huy tác chiến → " + finalTargetNode);
                    appendLog("[SOURCE-ROUTE] Lộ trình nguồn: " + designatedRoute + loadInfo);
                    appendLog("[FAILOVER] Thử relay " + attemptNum + "/" + totalRelays
                            + " — " + sendHost + ":" + sendPort + "...");
                });

                // Gửi đồng bộ để biết kết quả trước khi thử relay tiếp
                boolean success = SocketClient.send(sendHost, sendPort, dispatchPacket);

                if (success) {
                    Platform.runLater(() ->
                            appendLog("[SEND] ✅ Lệnh chỉ huy đã gửi thành công tới "
                                    + finalTargetNode + " qua " + chosenRelayId + ": " + displayCmd)
                    );
                    return; // Thành công → thoát vòng lặp failover
                } else {
                    Platform.runLater(() ->
                            appendLog("[FAILOVER] ⚠️ Relay " + chosenRelayId
                                    + " không phản hồi — thử relay tiếp theo...")
                    );
                }
            }
            // Tất cả relay đều thất bại
            Platform.runLater(() ->
                    appendLog("[ERROR] ❌ Không thể gửi lệnh tới " + finalTargetNode
                            + " — TẤT CẢ RELAY ĐỀU KHÔNG PHẢN HỒI!")
            );
        }, "Dispatch-Failover");
        failoverThread.setDaemon(true);
        failoverThread.start();
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

    @Override
    public void onLoadReportReceived(MeshPacket packet) {
        if (packet == null || packet.getPayload() == null) return;

        String nodeId = packet.getSourceNodeId();
        int load  = packet.getPayload().getCurrentLoad();
        int total = packet.getPayload().getProcessedTotal();
        int port  = packet.getPayload().getListenPort();

        // Lấy IP thực tế của relay từ sender field (hoặc dùng relayHost đã ghi nhận)
        String ip = (packet.getSenderHopId() != null) ? relayHost : "localhost";

        loadBalancer.updateStatus(nodeId, load, total, ip, port);

        Platform.runLater(() -> {
            appendLog("[LOAD] " + nodeId + " | load=" + load
                    + " total=" + total + " port=" + port);
        });
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
    // MAP INTERACTION — Phase 1.2: Focus Victim on Map
    // =========================================================

    /**
     * Gọi JS function focusVictim() để bản đồ lướt đến vị trí nạn nhân
     * và mở popup marker tương ứng.
     *
     * @param nodeId      ID node nguồn (vd: "NODE_A_VICTIM")
     * @param coordinates Chuỗi tọa độ dạng "15.9738, 108.2515"
     */
    private void focusVictimOnMap(String nodeId, String coordinates) {
        if (!mapReady || webEngine == null || coordinates == null) return;
        try {
            String[] parts = coordinates.split(",");
            if (parts.length >= 2) {
                double lat = Double.parseDouble(parts[0].trim());
                double lon = Double.parseDouble(parts[1].trim());
                String jsCall = String.format(
                        "focusVictim('%s', %f, %f)",
                        escapeJs(nodeId), lat, lon
                );
                webEngine.executeScript(jsCall);
                appendLog("[MAP] Lướt đến vị trí " + nodeId + " (" + coordinates + ")");
            }
        } catch (Exception e) {
            appendLog("[ERROR] Không focus được trên bản đồ: " + e.getMessage());
        }
    }

    // =========================================================
    // VICTIM DETAIL CARD — Phase 1.3
    // =========================================================

    /**
     * Hiển thị thông tin chi tiết nạn nhân trên Victim Detail Preview Card
     * khi click chọn dòng trong bảng.
     */
    private void showVictimDetails(VictimTableRow victim) {
        if (victimDetailCard == null) return;

        victimDetailCard.setVisible(true);
        victimDetailCard.setManaged(true);

        if (lblVictimDetailName != null) {
            lblVictimDetailName.setText(victim.getSourceNode()
                    + " — " + victim.getAlertType()
                    + " (" + victim.getVictimCount() + " người)");
        }
        if (lblVictimDetailSeverity != null) {
            lblVictimDetailSeverity.setText(victim.getSeverity());
            lblVictimDetailSeverity.getStyleClass().removeAll(
                    "label-red", "label-orange", "label-yellow");
            switch (victim.getSeverity()) {
                case "CRITICAL": lblVictimDetailSeverity.getStyleClass().add("label-red"); break;
                case "HIGH":     lblVictimDetailSeverity.getStyleClass().add("label-orange"); break;
                case "MEDIUM":   lblVictimDetailSeverity.getStyleClass().add("label-yellow"); break;
            }
        }
        if (lblVictimDetailMessage != null) {
            lblVictimDetailMessage.setText("\"" + victim.getMessage() + "\"");
        }
        if (lblVictimDetailRoute != null) {
            lblVictimDetailRoute.setText("Lộ trình: " + victim.getRouteHistory());
        }
        if (lblVictimDetailCoords != null) {
            lblVictimDetailCoords.setText("GPS: " + victim.getCoordinates()
                    + " | Thời gian: " + victim.getTime());
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
        if (monitoringScheduler != null) {
            monitoringScheduler.shutdown();
            try {
                if (!monitoringScheduler.awaitTermination(3, TimeUnit.SECONDS)) {
                    monitoringScheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                monitoringScheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
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
