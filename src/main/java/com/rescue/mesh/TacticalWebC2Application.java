package com.rescue.mesh;

import com.rescue.mesh.socket.MeshTcpServer;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Tactical Web C2 — Entry point Spring Boot 3 cho Trạm Chỉ Huy Tác Chiến.
 *
 * Dual-Engine Hybrid (theo SUPER_PROMPT_JAVAWEB_C2.md §4):
 *   Engine A (Mesh TCP 8888): MeshTcpServer — lắng nghe SOS_DATA từ Android/Relay,
 *                              reuse Controlled Flooding (RoutingEngine) + SeenPacketCache + SHA-256.
 *   Engine B (Web 8080):      Tomcat + STOMP WebSocket (/ws → /topic/*) — phục vụ đa màn hình iPad/Laptop
 *                              qua Wi-Fi nội bộ, 100% offline (không CDN).
 *
 * Tách biệt với JavaFX entry (BaseStationApp / BaseStationLauncher):
 *   - Chạy Web:       java -jar TacticalWebC2.jar  (hoặc mvn spring-boot:run)
 *   - Chạy Desktop:   java -jar BaseStationServer-jar-with-dependencies.jar
 *   Chỉ chạy 1 entry tại 1 thời điểm trên cùng port 8888 để tránh xung đột.
 */
@SpringBootApplication
@EnableScheduling
public class TacticalWebC2Application {

    public static void main(String[] args) {
        SpringApplication.run(TacticalWebC2Application.class, args);
    }

    /**
     * Khởi động Mesh TCP Server ngay sau khi Spring context sẵn sàng.
     * Dùng CommandLineRunner để đảm bảo Tomcat đã bind 8080 trước khi mở 8888.
     */
    @Bean
    CommandLineRunner startMeshEngine(MeshTcpServer meshTcpServer) {
        return args -> {
            System.out.println("[C2] TacticalWebC2Application — khởi động Dual-Engine...");
            meshTcpServer.start();
        };
    }
}
