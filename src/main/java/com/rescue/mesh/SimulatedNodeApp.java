package com.rescue.mesh;

import com.rescue.mesh.network.NodeConfig;
import com.rescue.mesh.ui.controller.NodeClientController;

import atlantafx.base.theme.CupertinoDark;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;
import java.util.List;

/**
 * Entry point cho Node Giả Lập (Victim / Relay) — phiên bản JavaFX GUI.
 *
 * Chạy Node A (Nạn nhân):
 *   java -cp app.jar com.rescue.mesh.SimulatedNodeApp --mode VICTIM --port 8001 --next-hop 8002 --id NODE_A_VICTIM
 *
 * Chạy Node B (Relay):
 *   java -cp app.jar com.rescue.mesh.SimulatedNodeApp --mode RELAY --port 8002 --next-hop 8888 --id NODE_B_RELAY
 *
 * Kiến trúc:
 *   - JavaFX Application: load NodeClient.fxml + dark-theme.css
 *   - NodeClientController: xử lý UI + implement RoutingCallback
 *   - RoutingEngine + SocketServer: khởi tạo trong controller
 *
 * Lifecycle:
 *   1. main() → launch() → init() → start() → (running) → stop()
 *   2. init(): parse command line args → NodeConfig
 *   3. start(): apply theme, load FXML, initialize controller
 *   4. stop(): graceful shutdown
 */
public class SimulatedNodeApp extends Application {

    /** Cấu hình node — parse từ command line args */
    private NodeConfig config;

    /** Tham chiếu controller để gọi shutdown() */
    private NodeClientController controller;

    // =========================================================
    // JAVAFX LIFECYCLE
    // =========================================================

    /**
     * Pha 1: Parse command line args.
     * Chạy trên launcher thread (không phải FX thread).
     */
    @Override
    public void init() {
        List<String> rawArgs = getParameters().getRaw();
        config = NodeConfig.fromArgs(rawArgs.toArray(new String[0]));
        System.out.println("[INFO] SimulatedNodeApp: config = " + config);
    }

    /**
     * Pha 2: Tạo giao diện JavaFX + khởi tạo networking.
     * Chạy trên JavaFX Application Thread.
     */
    @Override
    public void start(Stage primaryStage) {
        try {
            // ── Apply AtlantaFX CupertinoDark theme (iOS-like) ──
            Application.setUserAgentStylesheet(new CupertinoDark().getUserAgentStylesheet());

            // ── Load FXML ──
            URL fxmlUrl = getClass().getResource("/fxml/NodeClient.fxml");
            if (fxmlUrl == null) {
                System.err.println("[ERROR] Không tìm thấy /fxml/NodeClient.fxml");
                Platform.exit();
                return;
            }

            FXMLLoader loader = new FXMLLoader(fxmlUrl);
            Parent root = loader.load();

            // ── Lấy controller và khởi tạo node ──
            controller = loader.getController();
            controller.initializeNode(config);

            // ── Tạo Scene + áp dụng custom CSS ──
            Scene scene = new Scene(root, 420, 720);
            URL cssUrl = getClass().getResource("/css/dark-theme.css");
            if (cssUrl != null) {
                scene.getStylesheets().add(cssUrl.toExternalForm());
            }

            // ── Cấu hình cửa sổ ──
            String modeEmoji = switch (config.getMode()) {
                case VICTIM -> "🆘";
                case RELAY  -> "🔄";
                default     -> "📡";
            };
            primaryStage.setTitle(modeEmoji + " " + config.getNodeId()
                    + " — Port " + config.getListenPort());
            primaryStage.setScene(scene);
            primaryStage.setMinWidth(380);
            primaryStage.setMinHeight(500);

            // ── Xử lý đóng cửa sổ ──
            primaryStage.setOnCloseRequest(event -> {
                shutdown();
            });

            primaryStage.show();
            System.out.println("[INFO] SimulatedNodeApp: GUI started — " + config.getNodeId());

        } catch (IOException e) {
            System.err.println("[ERROR] SimulatedNodeApp: Không load được FXML — " + e.getMessage());
            e.printStackTrace();
            Platform.exit();
        }
    }

    /**
     * Pha 3: Dọn dẹp khi thoát.
     */
    @Override
    public void stop() {
        shutdown();
    }

    /**
     * Graceful shutdown — dừng server và routing engine.
     */
    private void shutdown() {
        System.out.println("[INFO] SimulatedNodeApp: đang shutdown...");
        if (controller != null) {
            controller.shutdown();
        }
        System.out.println("[INFO] SimulatedNodeApp: đã shutdown.");
    }

    // =========================================================
    // MAIN
    // =========================================================

    /**
     * Entry point chính.
     * JavaFX Application.launch() gọi init() → start() trên đúng thread.
     */
    public static void main(String[] args) {
        launch(args);
    }
}