package com.rescue.mesh.ui.controller;

import com.rescue.mesh.audio.AlarmPlayer;
import com.rescue.mesh.map.OfflineMapManager;
import com.rescue.mesh.model.MeshPacket;
import com.rescue.mesh.network.NodeConfig;
import com.rescue.mesh.network.SocketClient;
import com.rescue.mesh.network.SocketServer;
import com.rescue.mesh.routing.RoutingEngine;
import com.rescue.mesh.service.DispatchOutboxService;
import com.rescue.mesh.storage.BaseStationStorageService;
import com.rescue.mesh.storage.DatabaseConfig;
import com.rescue.mesh.storage.DatabaseManager;
import com.rescue.mesh.storage.model.SosEventRecord;
import com.rescue.mesh.ui.component.VictimTableRow;
import com.rescue.mesh.util.PacketFactory;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

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
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

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
    private MapScriptBridge mapScriptBridge;
    private BaseStationStorageService storageService;
    private DispatchOutboxService dispatchOutboxService;

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

    /** Quản lý bản đồ ngoại tuyến (Phase 3) */
    private OfflineMapManager offlineMapManager;

    /** Đường dẫn file MBTiles ngoại tuyến (cấu hình qua --map-file) */
    private String mapFilePath;

    /** Hàng đợi SOS packets nhận được trước khi WebView/bản đồ sẵn sàng (Phase 3.4) */
    private final List<MeshPacket> pendingMapPackets = Collections.synchronizedList(new ArrayList<>());

    /** Gson instance cho JSON serialization an toàn (marker bridge) */
    private static final Gson GSON = new Gson();

    /** Small seam around WebEngine so map commands can be verified without a GUI. */
    @FunctionalInterface
    public interface MapScriptBridge {
        void execute(String script);
    }

    /** Resolves a relay node ID to a real, verified coordinate when one is known. */
    @FunctionalInterface
    public interface RouteCoordinateResolver {
        Optional<MapCoordinate> resolve(String nodeId);
    }

    public record MapCoordinate(double latitude, double longitude) {
        public boolean isValid() {
            return Double.isFinite(latitude) && Double.isFinite(longitude)
                    && latitude >= -90.0 && latitude <= 90.0
                    && longitude >= -180.0 && longitude <= 180.0;
        }
    }

    private RouteCoordinateResolver routeCoordinateResolver = nodeId -> Optional.empty();

    private static final Logger LOGGER = Logger.getLogger(BaseStationController.class.getName());

    /** Managed single-thread background executor cho database initialization, migration, restore và background storage work */
    private final ExecutorService backgroundStorageExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "BaseStation-StorageExecutor");
        t.setDaemon(true);
        return t;
    });

    private final CountDownLatch initLatch = new CountDownLatch(1);
    private DatabaseConfig databaseConfig;
    private DatabaseManager databaseManager;
    private volatile boolean storageHealthy = false;
    private volatile String startupErrorMessage = null;

    /** Trạng thái vòng đời của BaseStationController */
    public enum LifecycleState {
        NEW,
        INITIALIZING,
        RUNNING,
        SHUTTING_DOWN,
        STOPPED
    }

    private final java.util.concurrent.atomic.AtomicReference<LifecycleState> lifecycleState =
            new java.util.concurrent.atomic.AtomicReference<>(LifecycleState.NEW);

    public boolean isShuttingDownOrStopped() {
        LifecycleState state = lifecycleState.get();
        return state == LifecycleState.SHUTTING_DOWN || state == LifecycleState.STOPPED;
    }

    public LifecycleState getLifecycleState() {
        return lifecycleState.get();
    }

    /** Seam điều phối UI có thể inject để kiểm thử cách ly */
    public interface UiDispatcher {
        void dispatch(Runnable action);
    }

    private UiDispatcher uiDispatcher = this::defaultDispatch;

    private void defaultDispatch(Runnable action) {
        try {
            Platform.runLater(action);
        } catch (IllegalStateException e) {
            action.run();
        }
    }

    public void setUiDispatcher(UiDispatcher uiDispatcher) {
        this.uiDispatcher = uiDispatcher;
    }

    public void runOnUiThread(Runnable action) {
        if (uiDispatcher != null) {
            uiDispatcher.dispatch(action);
        } else {
            defaultDispatch(action);
        }
    }

    /** Interface phát tán ACK để dễ dàng kiểm thử tự động */
    public interface AckSender {
        void sendAck(String host, int port, MeshPacket ackPacket);
    }
    private AckSender ackSender = (h, p, pkt) -> SocketClient.sendAsync(h, p, pkt, null);

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
        tblVictims.setRowFactory(table -> new TableRow<>() {
            @Override
            protected void updateItem(VictimTableRow item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().removeAll("severity-critical", "severity-high", "severity-medium");
                if (!empty && item != null) {
                    String severityClass = severityStyleClass(item.getSeverity());
                    if (severityClass != null) {
                        getStyleClass().add(severityClass);
                    }
                }
            }
        });

        // Khi chọn dòng SOS trong bảng → focus marker tương ứng trên bản đồ (Phase 3.4)
        tblVictims.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && newVal.getMarkerId() != null) {
                focusMarkerOnMap(newVal.getMarkerId());
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

    /**
     * Maps the protocol severity to the CSS class used by the TableView row.
     * Keeping this mapping in code (rather than relying on a log/UI convention)
     * makes the red/orange/yellow triage styling follow restored and live rows.
     */
    static String severityStyleClass(String severity) {
        if (severity == null) {
            return null;
        }
        return switch (severity.trim().toUpperCase(java.util.Locale.ROOT)) {
            case MeshPacket.SEVERITY_CRITICAL -> "severity-critical";
            case MeshPacket.SEVERITY_HIGH -> "severity-high";
            case MeshPacket.SEVERITY_MEDIUM -> "severity-medium";
            default -> null;
        };
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
        if (isShuttingDownOrStopped()) {
            LOGGER.warning("Không thể initializeStation vì controller đang shutdown hoặc đã stopped.");
            return;
        }
        lifecycleState.compareAndSet(LifecycleState.NEW, LifecycleState.INITIALIZING);
        this.config = config;
        this.relayHost = config.getRelayHost();
        this.relayNodePort = config.getRelayPort();
        this.mapFilePath = config.getMapFilePath();

        // 1. Khởi tạo WebView bản đồ
        initializeMap();

        // 2. Khởi tạo SQLite Storage Service & chạy Migration trên managed background executor trước khi mở network services
        runOnUiThread(() -> appendLog("[INFO] Đang khởi tạo SQLite Database và khôi phục dữ liệu..."));
        backgroundStorageExecutor.submit(() -> {
            try {
                // Checkpoint 1: before database initialization
                if (isShuttingDownOrStopped()) {
                    LOGGER.info("Shutdown phát hiện trước khi khởi tạo database. Dừng khởi động.");
                    cleanupDatabaseIfShuttingDown();
                    return;
                }

                boolean initOk = initializeStorage();

                // Checkpoint 2: after database initialization
                if (isShuttingDownOrStopped()) {
                    LOGGER.info("Shutdown phát hiện sau khi khởi tạo database. Dừng khởi động và đóng CSDL.");
                    cleanupDatabaseIfShuttingDown();
                    return;
                }

                if (!initOk) {
                    LOGGER.severe("FATAL: Khởi tạo SQLite Storage thất bại! Dừng toàn bộ SocketServer và DispatchOutboxService.");
                    runOnUiThread(() -> {
                        if (!isShuttingDownOrStopped()) {
                            setServerStatus("LỖI CSDL (STORAGE FAILED)", "disconnected");
                            appendLog("[FATAL] Khởi tạo SQLite Storage thất bại: " + startupErrorMessage);
                        }
                    });
                    return;
                }

                String dbPathStr = (databaseConfig != null && databaseConfig.getDbPath() != null)
                        ? databaseConfig.getDbPath().toString() : "configured";
                runOnUiThread(() -> {
                    if (!isShuttingDownOrStopped()) {
                        appendLog("[INFO] SQLite Storage đã sẵn sàng tại: " + dbPathStr);
                    }
                });

                // Khôi phục state từ SQLite trên background thread
                restoreStateFromDatabase();

                // Checkpoint 3: before scheduling the UI continuation
                if (isShuttingDownOrStopped()) {
                    LOGGER.info("Shutdown phát hiện trước khi schedule UI continuation. Không schedule network services.");
                    cleanupDatabaseIfShuttingDown();
                    return;
                }

                // Schedule UI continuation
                runOnUiThread(() -> {
                    // Checkpoint 4: inside the UI continuation before starting DispatchOutboxService or SocketServer
                    if (isShuttingDownOrStopped()) {
                        LOGGER.info("Shutdown phát hiện bên trong UI continuation. Hủy khởi động DispatchOutboxService và SocketServer.");
                        cleanupDatabaseIfShuttingDown();
                        return;
                    }

                    startDispatchOutboxService();

                    if (isShuttingDownOrStopped()) {
                        LOGGER.info("Shutdown phát hiện trước khi khởi động SocketServer. Dừng outbox và hủy SocketServer.");
                        if (dispatchOutboxService != null) {
                            dispatchOutboxService.stop();
                        }
                        cleanupDatabaseIfShuttingDown();
                        return;
                    }

                    startNetworkServices();
                    lifecycleState.compareAndSet(LifecycleState.INITIALIZING, LifecycleState.RUNNING);
                });

            } catch (Throwable t) {
                LOGGER.log(Level.SEVERE, "Lỗi nghiêm trọng trong quá trình khởi tạo background: " + t.getMessage(), t);
                storageHealthy = false;
                startupErrorMessage = t.getMessage();
                cleanupDatabaseIfShuttingDown();
                runOnUiThread(() -> {
                    if (!isShuttingDownOrStopped()) {
                        setServerStatus("LỖI CSDL (FATAL)", "disconnected");
                        appendLog("[FATAL] Ngoại lệ khởi tạo hệ thống: " + t.getMessage());
                    }
                });
            } finally {
                initLatch.countDown();
            }
        });
    }

    boolean initializeStorage() {
        if (isShuttingDownOrStopped()) {
            return false;
        }
        if (storageService != null) {
            storageHealthy = true;
            return true;
        }
        try {
            if (databaseManager == null) {
                if (databaseConfig == null) {
                    databaseConfig = DatabaseConfig.defaultConfiguration();
                }
                databaseManager = new DatabaseManager(databaseConfig);
            }
            databaseManager.initialize();
            this.storageService = new BaseStationStorageService(databaseManager);
            this.storageHealthy = true;
            LOGGER.info("SQLite Storage đã sẵn sàng tại: " + (databaseConfig != null ? databaseConfig.getDbPath() : "injected"));
            return true;
        } catch (Exception e) {
            this.storageHealthy = false;
            this.startupErrorMessage = e.getMessage();
            LOGGER.log(Level.SEVERE, "FATAL: Lỗi khởi tạo SQLite Storage: " + e.getMessage(), e);
            return false;
        }
    }

    private void startDispatchOutboxService() {
        if (!storageHealthy || storageService == null || isShuttingDownOrStopped()) {
            return;
        }
        if (dispatchOutboxService == null) {
            this.dispatchOutboxService = new DispatchOutboxService(
                storageService,
                relayHost,
                relayNodePort,
                config.getNodeId(),
                (h, p, pkt) -> SocketClient.send(h, p, pkt)
            );
            this.dispatchOutboxService.start(2000);
            appendLog("[INFO] Dispatch Outbox Service đã khởi động (chu kỳ 2s, đích: " + relayHost + ":" + relayNodePort + ").");
        }
    }

    /**
     * Khởi tạo RoutingEngine và bắt đầu lắng nghe SocketServer sau khi dữ liệu đã được khôi phục.
     */
    private void startNetworkServices() {
        if (!storageHealthy || storageService == null || isShuttingDownOrStopped()) {
            LOGGER.severe("Không thể khởi động Network Services khi SQLite storage chưa sẵn sàng hoặc controller đã shutdown.");
            return;
        }
        // Khởi tạo RoutingEngine — Base Station là đích cuối (nextHopPort = -1)
        routingEngine = new RoutingEngine(
                config.getNodeId(),
                config.getRelayHost(),
                -1,
                this  // implements RoutingCallback
        );

        // Khởi động SocketServer
        try {
            socketServer = new SocketServer(
                    config.getBindHost(),
                    config.getListenPort(),
                    routingEngine,
                    this  // implements ServerEventListener
            );
            socketServer.start();
            appendLog("[INFO] Base Station Server đang lắng nghe tại "
                    + config.getBindHost() + ":" + config.getListenPort() + "...");
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Không thể khởi động SocketServer: " + e.getMessage(), e);
            appendLog("[ERROR] Không thể khởi động SocketServer: " + e.getMessage());
            setServerStatus("LỖI PORT", "disconnected");
        }
    }

    /**
     * Khôi phục danh sách sự kiện SOS và thống kê từ SQLite Database sau khi khởi động lại.
     * Đọc I/O trên background thread và cập nhật JavaFX UI thông qua runOnUiThread().
     */
    private void restoreStateFromDatabase() {
        if (!storageHealthy || storageService == null) return;
        try {
            List<SosEventRecord> savedEvents = storageService.loadAllSosEvents();
            BaseStationStorageService.SosStats stats = storageService.getSosStats();

            runOnUiThread(() -> {
                for (SosEventRecord record : savedEvents) {
                    totalSos++;
                    if (victimData != null) {
                        VictimTableRow row = VictimTableRow.fromSosRecord(record, totalSos);
                        victimData.add(row);
                    }
                    addMarkerToMap(toPacketForMap(record));
                    if (cboTargetNode != null && record.getSourceNodeId() != null && !cboTargetNode.getItems().contains(record.getSourceNodeId())) {
                        cboTargetNode.getItems().add(record.getSourceNodeId());
                    }
                }
                if (cboTargetNode != null && cboTargetNode.getValue() == null && !cboTargetNode.getItems().isEmpty()) {
                    cboTargetNode.setValue(cboTargetNode.getItems().get(0));
                }
                if (stats != null) {
                    this.totalSos = stats.getTotal();
                    this.criticalCount = stats.getCritical();
                    this.highCount = stats.getHigh();
                    this.mediumCount = stats.getMedium();
                }
                updateStats();
                if (!savedEvents.isEmpty()) {
                    appendLog("[INFO] Đã khôi phục " + savedEvents.size() + " sự kiện SOS từ database.");
                }
            });
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Không thể khôi phục dữ liệu từ SQLite: " + e.getMessage(), e);
            runOnUiThread(() -> appendLog("[WARN] Lỗi đọc state cũ từ SQLite: " + e.getMessage()));
        }
    }

    /**
     * Tải bản đồ Leaflet (fully offline) vào WebView.
     * <p>
     * Phase 3: Khởi tạo OfflineMapManager → LocalTileServer, sau đó load
     * leaflet_offline.html từ resources cục bộ. Khi WebView load xong, inject
     * tile URL template hoặc hiển thị thông báo lỗi nếu MBTiles không khả dụng.
     */
    private void initializeMap() {
        if (mapWebView == null) {
            return;
        }
        webEngine = mapWebView.getEngine();
        mapScriptBridge = webEngine::executeScript;

        // Khởi tạo bản đồ ngoại tuyến (MBTiles + loopback tile server)
        offlineMapManager = new OfflineMapManager();
        if (mapFilePath != null && !mapFilePath.isBlank()) {
            offlineMapManager.setMapFilePath(mapFilePath);
        }
        boolean mapAvailable = offlineMapManager.start();

        if (mapAvailable) {
            appendLog("[INFO] Tile server ngoại tuyến khởi động tại port " + offlineMapManager.getTileServerPort());
        } else {
            appendLog("[WARN] Bản đồ ngoại tuyến không khả dụng: " + offlineMapManager.getErrorMessage());
        }

        // Khi WebView load HTML xong → inject tile URL hoặc hiển thị lỗi, rồi signal map ready
        webEngine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
            if (isShuttingDownOrStopped()) return; // Không làm gì nếu đã shutdown

            if (newState == javafx.concurrent.Worker.State.SUCCEEDED) {
                if (mapAvailable) {
                    // Inject tile URL template từ loopback server
                    String tileUrl = offlineMapManager.getTileUrlTemplate();
                    String escapedUrl = GSON.toJson(tileUrl); // JSON-safe string
                    JsonObject mapConfig = new JsonObject();
                    mapConfig.addProperty("minNativeZoom", offlineMapManager.getMinNativeZoom());
                    mapConfig.addProperty("maxNativeZoom", offlineMapManager.getMaxNativeZoom());
                    executeMapScript("setTileUrl(" + escapedUrl + ", " + GSON.toJson(mapConfig) + ")");
                    appendLog("[INFO] Bản đồ ngoại tuyến đã tải — tiles từ " + tileUrl);
                } else {
                    // Hiển thị thông báo lỗi rõ ràng trên bản đồ
                    String errorMsg = offlineMapManager.getErrorMessage();
                    String escapedError = GSON.toJson(errorMsg != null ? errorMsg : "Offline map not found");
                    executeMapScript("showMapError(" + escapedError + ")");
                }

                // Signal map ready → replay queued markers
                onMapReady();
            } else if (newState == javafx.concurrent.Worker.State.FAILED) {
                appendLog("[ERROR] Không tải được bản đồ: " + webEngine.getLoadWorker().getException());
            }
        });

        // Load HTML từ resources cục bộ (không có dependency mạng)
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
            appendLog("[VALIDATION] Nội dung lệnh điều phối không được để trống.");
            return;
        }

        String severity = cboDispatchSeverity.getValue();
        if (severity == null || severity.isBlank()) {
            appendLog("[VALIDATION] Chưa chọn mức ưu tiên cho lệnh điều phối.");
            return;
        }

        final String displayCmd = command.trim();
        appendLog("[QUEUE] Đang xếp lệnh điều phối cho " + targetNode
                + " qua relay " + relayHost + ":" + relayNodePort);

        if (dispatchOutboxService != null) {
            dispatchOutboxService.setTargetHost(relayHost);
            dispatchOutboxService.setTargetPort(relayNodePort);
            String pktId = dispatchOutboxService.enqueueDispatch(targetNode, displayCmd, severity);
            appendLog("[QUEUED] " + shortId(pktId)
                    + " đã lưu SQLite; chưa được coi là gửi hoặc ACKED.");
            dispatchOutboxService.triggerImmediateScan();
        } else {
            MeshPacket dispatchPacket = PacketFactory.createDispatchCommand(
                    targetNode,
                    displayCmd,
                    severity
            );
            if (storageService != null) {
                storageService.saveDispatchCommand(dispatchPacket, displayCmd, severity);
            }
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
        }

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
        if (packet == null) return;

        // BẢO VỆ TOÀN VẸN: Nếu storage không khả dụng, tuyệt đối KHÔNG sinh ACK!
        if (!storageHealthy || storageService == null) {
            LOGGER.warning("Từ chối xử lý và KHÔNG gửi ACK cho packet " + packet.getPacketId() + ": Storage không khả dụng.");
            runOnUiThread(() -> appendLog("[DROP] Bỏ qua gói tin " + shortId(packet.getPacketId()) + ": CSDL SQLite chưa sẵn sàng."));
            return;
        }

        // Lưu vào SQLite trong background thread (không block UI thread)
        try {
            boolean isNew = storageService.saveIncomingPacketIdempotent(packet, null);
            if (!isNew) {
                // A duplicate SOS is not shown/alarmed a second time, but it
                // may be a retransmission after the first Base -> relay ACK
                // was lost.  Re-send the ACK only after the idempotent DB
                // check succeeds; a storage exception above still returns
                // without acknowledging anything.
                if (isSosPacket(packet)) {
                    sendSosAck(packet);
                }
                runOnUiThread(() -> appendLog("[DROP] Gói tin đã tồn tại trong SQLite: " + shortId(packet.getPacketId())));
                return;
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Lỗi cơ sở dữ liệu khi lưu incoming packet " + packet.getPacketId() + ": " + e.getMessage(), e);
            runOnUiThread(() -> appendLog("[DB ERROR] Không thể lưu gói tin " + shortId(packet.getPacketId()) + " vào SQLite: " + e.getMessage()));
            return; // Lỗi ghi SQLite -> KHÔNG gửi ACK!
        }

        // The routing callback also carries HEARTBEAT/ROUTE_DISCOVERY frames
        // addressed to the Base Station.  Persist those frames for audit, but
        // only an SOS is a victim event: it must not inflate SOS statistics,
        // create a victim row, or trigger the rescue alarm.
        if (!isSosPacket(packet)) {
            runOnUiThread(() -> appendLog("[INFO] Đã lưu packet " + shortId(packet.getPacketId())
                    + " type=" + packet.getPacketType() + "; không phải SOS."));
            return;
        }

        // Nếu là gói tin SOS, sinh ACK gửi ngược về node nguồn qua relay (Machine-Readable ACK)
        sendSosAck(packet);

        runOnUiThread(() -> {
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
            if (victimData != null) {
                VictimTableRow row = VictimTableRow.fromMeshPacket(packet, totalSos);
                victimData.add(row);
            }

            // Cập nhật ComboBox target node
            String sourceNodeId = packet.getSourceNodeId();
            if (cboTargetNode != null) {
                if (!cboTargetNode.getItems().contains(sourceNodeId)) {
                    cboTargetNode.getItems().add(sourceNodeId);
                }
                if (cboTargetNode.getValue() == null) {
                    cboTargetNode.setValue(sourceNodeId);
                }
            }

            // Thêm marker trên bản đồ
            addMarkerToMap(packet);

            // Phát còi báo động
            if (alarmPlayer != null) {
                alarmPlayer.play();
            }

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
            if (tblVictims != null) {
                tblVictims.getSelectionModel().selectLast();
                tblVictims.scrollTo(victimData.size() - 1);
            }
        });
    }

    private boolean isSosPacket(MeshPacket packet) {
        if (packet == null || packet.getPacketId() == null || packet.getSourceNodeId() == null
                || packet.getSourceNodeId().isBlank()) {
            return false;
        }
        String packetType = packet.getPacketType();
        return MeshPacket.TYPE_SOS_BROADCAST.equals(packetType)
                || MeshPacket.TYPE_SOS_DATA.equals(packetType);
    }

    /** Send the machine-readable ACK after a successful/duplicate DB decision. */
    private void sendSosAck(MeshPacket packet) {
        if (!isSosPacket(packet) || ackSender == null || config == null) {
            return;
        }
        MeshPacket ackPacket = PacketFactory.createAck(
                config.getNodeId(),
                packet.getPacketId(),
                packet.getSourceNodeId()
        );
        ackSender.sendAck(relayHost, relayNodePort, ackPacket);
    }

    @Override
    public void onPacketRelayed(MeshPacket packet, int nextHop) {
        // Base Station không relay — method này không được gọi
    }

    @Override
    public void onPacketDropped(String packetId, String reason) {
        runOnUiThread(() -> {
            appendLog("[DROP] " + shortId(packetId) + " — " + reason);
            if (lblCacheSize != null && routingEngine != null) {
                lblCacheSize.setText(String.valueOf(routingEngine.getCacheSize()));
            }
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
    public void onAckReceived(MeshPacket packet) {
        boolean accepted = dispatchOutboxService != null
                && dispatchOutboxService.handleIncomingAck(packet);
        String ackFor = packet.getPayload() != null
                ? packet.getPayload().getAckForPacketId() : null;
        runOnUiThread(() -> {
            if (accepted) {
                appendLog("[ACKED] Dispatch " + shortId(ackFor)
                        + " đã được victim xác nhận hợp lệ.");
            } else {
                appendLog("[ACK REJECTED] Không cập nhật SQLite cho ACK "
                        + shortId(packet.getPacketId()) + ".");
            }
        });
    }

    // =========================================================
    // SERVER EVENT LISTENER
    // =========================================================

    @Override
    public void onServerStarted(int port) {
        runOnUiThread(() -> {
            appendLog("[INFO] Base Station Server tại port " + port + " — OK");
            setServerStatus("Đang lắng nghe — Port " + port, "connected");
        });
    }

    @Override
    public void onClientConnected(String clientAddress) {
        runOnUiThread(() -> appendLog("[PEER] Kết nối nhận từ " + clientAddress
                + "; relay gửi đi vẫn dùng endpoint cấu hình "
                + relayHost + ":" + relayNodePort));
    }

    @Override
    public void onServerError(String errorMessage) {
        runOnUiThread(() -> {
            appendLog("[ERROR] Server: " + errorMessage);
            setServerStatus("Lỗi server", "disconnected");
        });
    }

    @Override
    public void onServerStopped() {
        runOnUiThread(() -> {
            appendLog("[INFO] Server đã dừng.");
            setServerStatus("Đã dừng", "disconnected");
        });
    }

    // =========================================================
    // MAP BRIDGE — Java → JavaScript (Phase 3.4)
    // =========================================================

    /**
     * Thêm marker SOS lên bản đồ Leaflet.
     * <p>
     * Nếu bản đồ chưa sẵn sàng (mapReady == false), xếp hàng packet vào pendingMapPackets
     * để phát lại đúng 1 lần khi WebView load xong (Phase 3.4.1 & 3.4.2).
     * Dữ liệu được serialize bằng Gson JSON an toàn (Phase 3.4.3).
     * Validate biên GPS để không crash khi gặp tọa độ lỗi/ngoài vùng (Phase 3.4.7).
     */
    public void addMarkerToMap(MeshPacket packet) {
        if (isShuttingDownOrStopped() || packet == null || packet.getPayload() == null) {
            return;
        }

        MeshPacket.Payload payload = packet.getPayload();
        MeshPacket.Location loc = payload.getLocation();
        if (loc == null) return;

        // Phase 3.4.7: Validate coordinates — non-null, finite, hợp lệ [-90, 90] và [-180, 180]
        Double lat = loc.getLatitude();
        Double lon = loc.getLongitude();
        if (lat == null || lon == null || !Double.isFinite(lat) || !Double.isFinite(lon)
                || lat < -90.0 || lat > 90.0 || lon < -180.0 || lon > 180.0) {
            LOGGER.warning("Bỏ qua vẽ marker: Tọa độ không hợp lệ [lat=" + lat + ", lon=" + lon + "]");
            return;
        }

        // Phase 3.4.1: Nếu map chưa sẵn sàng, xếp hàng để replay sau
        if (!mapReady || !hasMapScriptBridge()) {
            pendingMapPackets.add(packet);
            LOGGER.info("Bản đồ chưa sẵn sàng, đã xếp hàng marker cho node " + packet.getSourceNodeId());
            return;
        }

        sendMarkerToWebEngine(packet);
    }

    /**
     * Replay các gói tin SOS đang xếp hàng sau khi bản đồ sẵn sàng (Phase 3.4.2).
     */
    private void replayPendingMapPackets() {
        List<MeshPacket> packetsToReplay;
        synchronized (pendingMapPackets) {
            packetsToReplay = new ArrayList<>(pendingMapPackets);
            pendingMapPackets.clear();
        }
        for (MeshPacket pkt : packetsToReplay) {
            sendMarkerToWebEngine(pkt);
        }
    }

    /** Marks the JavaScript map ready once and drains the Java-side queue exactly once. */
    public void onMapReady() {
        if (isShuttingDownOrStopped()) {
            return;
        }
        if (!mapReady) {
            executeMapScript("onMapReady()");
            mapReady = true;
            replayPendingMapPackets();
        }
        appendLog("[INFO] Bản đồ Leaflet đã tải xong (offline mode).");
    }

    /**
     * Gửi marker đến WebEngine bằng JSON serialization an toàn (Phase 3.4.3).
     */
    private void sendMarkerToWebEngine(MeshPacket packet) {
        if (isShuttingDownOrStopped() || !hasMapScriptBridge()) {
            return;
        }

        MeshPacket.Payload payload = packet.getPayload();
        if (payload == null || payload.getLocation() == null) return;
        MeshPacket.Location loc = payload.getLocation();

        JsonObject markerObj = new JsonObject();
        markerObj.addProperty("lat", loc.getLatitude());
        markerObj.addProperty("lon", loc.getLongitude());
        markerObj.addProperty("markerId", packet.getPacketId() != null ? packet.getPacketId() : packet.getSourceNodeId());
        markerObj.addProperty("nodeId", packet.getSourceNodeId() != null ? packet.getSourceNodeId() : "");
        markerObj.addProperty("severity", payload.getSeverity() != null ? payload.getSeverity() : "MEDIUM");
        markerObj.addProperty("alertType", payload.getAlertType() != null ? payload.getAlertType() : "");
        markerObj.addProperty("message", payload.getMessage() != null ? payload.getMessage() : "");
        markerObj.addProperty("victimCount", payload.getVictimCount());

        String jsonPayload = GSON.toJson(markerObj);

        runOnUiThread(() -> {
            if (!hasMapScriptBridge() || isShuttingDownOrStopped()) return;
            try {
                // GSON.toJson(jsonPayload) escape an toàn chuỗi JSON thành JavaScript string argument
                executeMapScript("addSOSMarkerFromJson(" + GSON.toJson(jsonPayload) + ")");
                renderRouteIfAvailable(packet);
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Lỗi khi gọi addSOSMarkerFromJson: " + e.getMessage(), e);
                appendLog("[ERROR] Không thêm được marker bản đồ: " + e.getMessage());
            }
        });
    }

    /**
     * Focus vào marker trên bản đồ khi người dùng chọn dòng tương ứng trong bảng (Phase 3.4.6).
     */
    public void focusMarkerOnMap(String nodeId) {
        if (nodeId == null || !mapReady || !hasMapScriptBridge() || isShuttingDownOrStopped()) {
            return;
        }
        runOnUiThread(() -> {
            if (hasMapScriptBridge() && !isShuttingDownOrStopped()) {
                try {
                    executeMapScript("focusMarker(" + GSON.toJson(nodeId) + ")");
                } catch (Exception e) {
                    LOGGER.log(Level.FINE, "focusMarker error: " + e.getMessage());
                }
            }
        });
    }

    /**
     * Vẽ đường đi lịch sử chuyển tiếp nếu có tọa độ (Phase 3.4.5).
     */
    private void renderRouteIfAvailable(MeshPacket packet) {
        if (packet == null || packet.getPayload() == null || packet.getPayload().getLocation() == null) {
            return;
        }
        List<String> route = packet.getRouteHistory();
        if (route == null || route.isEmpty()) {
            return;
        }

        MeshPacket.Location loc = packet.getPayload().getLocation();
        com.google.gson.JsonArray coords = new com.google.gson.JsonArray();

        // A route must contain at least one independently resolved relay coordinate.
        // Never draw a fabricated direct victim-to-base line from node IDs alone.
        List<MapCoordinate> resolvedRelayCoordinates = new ArrayList<>();
        for (String nodeId : route) {
            if (nodeId == null || nodeId.isBlank()) continue;
            routeCoordinateResolver.resolve(nodeId)
                    .filter(MapCoordinate::isValid)
                    .filter(point -> resolvedRelayCoordinates.stream().noneMatch(existing ->
                            Double.compare(existing.latitude(), point.latitude()) == 0
                                    && Double.compare(existing.longitude(), point.longitude()) == 0))
                    .ifPresent(resolvedRelayCoordinates::add);
        }
        if (resolvedRelayCoordinates.isEmpty()) {
            return;
        }

        // Start at the actual victim location.
        com.google.gson.JsonArray victimCoord = new com.google.gson.JsonArray();
        victimCoord.add(loc.getLatitude());
        victimCoord.add(loc.getLongitude());
        coords.add(victimCoord);

        for (MapCoordinate point : resolvedRelayCoordinates) {
            com.google.gson.JsonArray relayCoord = new com.google.gson.JsonArray();
            relayCoord.add(point.latitude());
            relayCoord.add(point.longitude());
            coords.add(relayCoord);
        }

        // Điểm kết thúc: Trạm Base Station (15.9753, 108.2532)
        com.google.gson.JsonArray bsCoord = new com.google.gson.JsonArray();
        bsCoord.add(15.9753);
        bsCoord.add(108.2532);
        coords.add(bsCoord);

        String coordsJson = GSON.toJson(coords);
        try {
            executeMapScript("drawRoute(" + GSON.toJson(coordsJson) + ")");
        } catch (Exception ignored) {}
    }

    private boolean hasMapScriptBridge() {
        return mapScriptBridge != null || webEngine != null;
    }

    private void executeMapScript(String script) {
        if (mapScriptBridge != null) {
            mapScriptBridge.execute(script);
        } else if (webEngine != null) {
            webEngine.executeScript(script);
        }
    }

    private MeshPacket toPacketForMap(SosEventRecord record) {
        if (record == null || record.getLatitude() == null || record.getLongitude() == null) {
            return null;
        }
        MeshPacket packet = new MeshPacket();
        packet.setPacketId(record.getPacketId());
        packet.setSourceNodeId(record.getSourceNodeId());
        packet.setPacketType(MeshPacket.TYPE_SOS_BROADCAST);
        packet.setTimestamp(record.getTimestamp());
        MeshPacket.Payload payload = new MeshPacket.Payload();
        payload.setAlertType(record.getAlertType());
        payload.setSeverity(record.getSeverity());
        payload.setMessage(record.getMessage());
        payload.setVictimCount(record.getVictimCount());
        payload.setLocation(new MeshPacket.Location(record.getLatitude(), record.getLongitude()));
        packet.setPayload(payload);
        return packet;
    }

    // =========================================================
    // UTILITY
    // =========================================================

    /**
     * Cập nhật các label thống kê.
     */
    private void updateStats() {
        if (lblTotalSos != null) lblTotalSos.setText(String.valueOf(totalSos));
        if (lblCritical != null) lblCritical.setText(String.valueOf(criticalCount));
        if (lblHigh != null) lblHigh.setText(String.valueOf(highCount));
        if (lblMedium != null) lblMedium.setText(String.valueOf(mediumCount));
        if (lblCacheSize != null && routingEngine != null) lblCacheSize.setText(String.valueOf(routingEngine.getCacheSize()));
    }

    /**
     * Cập nhật trạng thái server trên header (đảm bảo thread confinement cho JavaFX).
     */
    private void setServerStatus(String text, String type) {
        Runnable updateAction = () -> {
            if (lblServerStatus != null) {
                lblServerStatus.setText(text);
            }
            if (serverStatusDot != null) {
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
        };

        try {
            if (Platform.isFxApplicationThread()) {
                updateAction.run();
            } else {
                runOnUiThread(updateAction);
            }
        } catch (IllegalStateException e) {
            updateAction.run();
        }
    }

    /**
     * Thêm dòng log + auto-scroll (đảm bảo thread confinement cho JavaFX).
     */
    private void appendLog(String message) {
        String timestamp = logTimeFormat.format(new Date());
        String line = "[" + timestamp + "] " + message + "\n";
        LOGGER.info("[Log] " + message);
        if (txtLog != null) {
            try {
                if (Platform.isFxApplicationThread()) {
                    txtLog.appendText(line);
                    txtLog.setScrollTop(Double.MAX_VALUE);
                } else {
                    runOnUiThread(() -> {
                        if (txtLog != null) {
                            txtLog.appendText(line);
                            txtLog.setScrollTop(Double.MAX_VALUE);
                        }
                    });
                }
            } catch (IllegalStateException e) {
                txtLog.appendText(line);
                txtLog.setScrollTop(Double.MAX_VALUE);
            }
        }
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
     * Dọn dẹp tài nguyên khi đóng ứng dụng (Thread-safe & Idempotent).
     */
    public synchronized void shutdown() {
        LifecycleState current = lifecycleState.get();
        if (current == LifecycleState.SHUTTING_DOWN || current == LifecycleState.STOPPED) {
            LOGGER.info("Shutdown đã được gọi trước đó (idempotent).");
            return;
        }
        lifecycleState.set(LifecycleState.SHUTTING_DOWN);

        try {
            if (alarmPlayer != null) {
                alarmPlayer.dispose();
            }
            if (socketServer != null) {
                socketServer.stop();
            }
            if (routingEngine != null) {
                routingEngine.shutdown();
            }
            if (dispatchOutboxService != null) {
                dispatchOutboxService.stop();
            }
            if (backgroundStorageExecutor != null) {
                backgroundStorageExecutor.shutdown();
                try {
                    if (!backgroundStorageExecutor.awaitTermination(3, TimeUnit.SECONDS)) {
                        backgroundStorageExecutor.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    backgroundStorageExecutor.shutdownNow();
                    Thread.currentThread().interrupt();
                }
            }
            if (offlineMapManager != null) {
                offlineMapManager.close();
            }
            pendingMapPackets.clear();
            cleanupDatabaseIfShuttingDown();
            runOnUiThread(() -> appendLog("[INFO] Base Station đã shutdown."));
        } finally {
            lifecycleState.set(LifecycleState.STOPPED);
        }
    }

    private void cleanupDatabaseIfShuttingDown() {
        if (storageService != null && storageService.getDatabaseManager() != null) {
            storageService.getDatabaseManager().close();
        } else if (databaseManager != null) {
            databaseManager.close();
        }
    }

    public boolean awaitInitialization(long timeout, TimeUnit unit) throws InterruptedException {
        return initLatch.await(timeout, unit);
    }

    public boolean isStorageHealthy() {
        return storageHealthy;
    }

    public String getStartupErrorMessage() {
        return startupErrorMessage;
    }

    public SocketServer getSocketServer() {
        return socketServer;
    }

    public void setDatabaseConfig(DatabaseConfig databaseConfig) {
        this.databaseConfig = databaseConfig;
    }

    public void setDatabaseManager(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public void setAckSender(AckSender ackSender) {
        this.ackSender = ackSender;
    }

    public RoutingEngine getRoutingEngine() {
        return routingEngine;
    }

    public void setStorageService(BaseStationStorageService storageService) {
        this.storageService = storageService;
    }

    public BaseStationStorageService getStorageService() {
        return storageService;
    }

    public void setDispatchOutboxService(DispatchOutboxService dispatchOutboxService) {
        this.dispatchOutboxService = dispatchOutboxService;
    }

    public DispatchOutboxService getDispatchOutboxService() {
        return dispatchOutboxService;
    }

    public OfflineMapManager getOfflineMapManager() {
        return offlineMapManager;
    }

    public void setOfflineMapManager(OfflineMapManager offlineMapManager) {
        this.offlineMapManager = offlineMapManager;
    }

    public void setMapFilePath(String mapFilePath) {
        this.mapFilePath = mapFilePath;
    }

    public String getMapFilePath() {
        return mapFilePath;
    }

    public List<MeshPacket> getPendingMapPackets() {
        return pendingMapPackets;
    }

    public boolean isMapReady() {
        return mapReady;
    }

    public void setMapReady(boolean mapReady) {
        this.mapReady = mapReady;
    }

    public void setMapScriptBridge(MapScriptBridge mapScriptBridge) {
        this.mapScriptBridge = mapScriptBridge;
    }

    public void setRouteCoordinateResolver(RouteCoordinateResolver routeCoordinateResolver) {
        this.routeCoordinateResolver = routeCoordinateResolver != null
                ? routeCoordinateResolver : nodeId -> Optional.empty();
    }
}
