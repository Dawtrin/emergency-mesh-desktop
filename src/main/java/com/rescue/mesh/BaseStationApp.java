package com.rescue.mesh;

import com.rescue.mesh.network.NodeConfig;
import com.rescue.mesh.ui.controller.BaseStationController;

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
 * Entry point cho Trạm Chỉ Huy Trung Tâm (Base Station) — JavaFX GUI.
 *
 * Chạy:
 *   java -cp app.jar com.rescue.mesh.BaseStationApp --mode BASE_STATION --port 8888
 *
 * Hoặc qua launcher (Fat JAR):
 *   java -jar BaseStationServer-jar-with-dependencies.jar
 *
 * Kiến trúc:
 *   - JavaFX Application: load BaseStation.fxml + dark-theme.css
 *   - BaseStationController: dashboard + map + dispatch + alarm
 *   - RoutingEngine + SocketServer: nhận SOS, hiển thị, gửi lệnh
 *
 * Design Language: iOS 27 Inspired
 *   - CupertinoDark theme (AtlantaFX)
 *   - Custom dark-theme.css overlay
 *   - Pure black background, rounded cards, glassmorphism
 */
public class BaseStationApp extends Application {

    /** Cấu hình Base Station */
    private NodeConfig config;

    /** Tham chiếu controller để shutdown */
    private BaseStationController controller;

    // =========================================================
    // JAVAFX LIFECYCLE
    // =========================================================

    @Override
    public void init() {
        // Parse command line args — ép mode BASE_STATION
        List<String> rawArgs = getParameters().getRaw();
        String[] args = rawArgs.toArray(new String[0]);

        // Thêm --mode BASE_STATION nếu chưa có
        boolean hasMode = false;
        for (String arg : args) {
            if ("--mode".equalsIgnoreCase(arg)) {
                hasMode = true;
                break;
            }
        }

        if (!hasMode) {
            String[] newArgs = new String[args.length + 2];
            System.arraycopy(args, 0, newArgs, 0, args.length);
            newArgs[args.length] = "--mode";
            newArgs[args.length + 1] = "BASE_STATION";
            args = newArgs;
        }

        config = NodeConfig.fromArgs(args);
        System.out.println("[INFO] BaseStationApp: config = " + config);
    }

    @Override
    public void start(Stage primaryStage) {
        try {
            // ── Apply AtlantaFX CupertinoDark theme ──
            Application.setUserAgentStylesheet(new CupertinoDark().getUserAgentStylesheet());

            // ── Load FXML ──
            URL fxmlUrl = getClass().getResource("/fxml/BaseStation.fxml");
            if (fxmlUrl == null) {
                System.err.println("[ERROR] Không tìm thấy /fxml/BaseStation.fxml");
                Platform.exit();
                return;
            }

            FXMLLoader loader = new FXMLLoader(fxmlUrl);
            Parent root = loader.load();

            // ── Lấy controller và khởi tạo station ──
            controller = loader.getController();
            controller.initializeStation(config);

            // Đọc --relay-port và --relay-host từ args nếu có
            List<String> params = getParameters().getRaw();
            for (int i = 0; i < params.size() - 1; i++) {
                if ("--relay-port".equalsIgnoreCase(params.get(i))) {
                    try {
                        int relayPort = Integer.parseInt(params.get(i + 1));
                        controller.setRelayNodePort(relayPort);
                    } catch (NumberFormatException e) {
                        System.err.println("[WARN] relay-port không hợp lệ, dùng mặc định 8002");
                    }
                } else if ("--relay-host".equalsIgnoreCase(params.get(i))) {
                    controller.setRelayHost(params.get(i + 1));
                }
            }

            // ── Tạo Scene + custom CSS ──
            Scene scene = new Scene(root, 1100, 780);
            URL cssUrl = getClass().getResource("/css/dark-theme.css");
            if (cssUrl != null) {
                scene.getStylesheets().add(cssUrl.toExternalForm());
            }

            // ── Cấu hình cửa sổ ──
            primaryStage.setTitle("🛡️ Emergency Mesh Rescue — Trạm Chỉ Huy Trung Tâm");
            primaryStage.setScene(scene);
            primaryStage.setMinWidth(900);
            primaryStage.setMinHeight(600);

            // ── Xử lý đóng cửa sổ ──
            primaryStage.setOnCloseRequest(event -> {
                shutdown();
            });

            primaryStage.show();
            System.out.println("[INFO] BaseStationApp: GUI started — Port " + config.getListenPort());

        } catch (IOException e) {
            System.err.println("[ERROR] BaseStationApp: Không load được FXML — " + e.getMessage());
            e.printStackTrace();
            Platform.exit();
        }
    }

    @Override
    public void stop() {
        shutdown();
    }

    private void shutdown() {
        System.out.println("[INFO] BaseStationApp: đang shutdown...");
        if (controller != null) {
            controller.shutdown();
        }
        System.out.println("[INFO] BaseStationApp: đã shutdown.");
    }

    // =========================================================
    // MAIN
    // =========================================================

    public static void main(String[] args) {
        launch(args);
    }
}