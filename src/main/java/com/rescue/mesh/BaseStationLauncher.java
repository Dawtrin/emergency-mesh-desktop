package com.rescue.mesh;

/**
 * Launcher cho Base Station — workaround cho JavaFX module system.
 *
 * Vấn đề:
 *   Khi chạy JavaFX từ Fat JAR (uber-jar), lớp chính không được
 *   extends Application trực tiếp, vì JavaFX module system kiểm tra
 *   module-path tại thời điểm khởi tạo JVM.
 *
 * Giải pháp:
 *   Tạo 1 lớp thường (không extends Application) làm main class
 *   trong MANIFEST.MF, rồi delegate sang BaseStationApp.main().
 *
 * Cách dùng:
 *   java -jar BaseStationServer-jar-with-dependencies.jar [args...]
 *
 * Tham khảo:
 *   https://github.com/openjfx/javafx-maven-plugin/issues/92
 */
public class BaseStationLauncher {

    /**
     * Main entry — delegate sang BaseStationApp.
     * Chạy trên bất kỳ JVM nào, không cần module-path setup.
     *
     * @param args Command line arguments, truyền nguyên vẹn sang BaseStationApp
     */
    public static void main(String[] args) {
        BaseStationApp.main(args);
    }
}
