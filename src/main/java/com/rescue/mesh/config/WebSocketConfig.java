package com.rescue.mesh.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Cấu hình STOMP WebSocket cho Tactical C2.
 *
 * Luồng thực địa:
 *   Android/Relay → TCP 8888 (MeshTcpServer) → RoutingEngine → convertAndSend("/topic/sos")
 *   iPad/Laptop   → WS /ws subscribe /topic/sos,/topic/dispatch,/topic/metrics → render 8 phân hệ
 *
 * Cho phép đa màn hình chỉ huy cùng truy cập qua Wi-Fi nội bộ không Internet (offline-first).
 * SockJS fallback giữ tương thích với trình duyệt dã chiến cũ.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // 1. Native WebSocket endpoint (dùng cho standard STOMP clients, mobile apps, Leaflet JS)
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*");

        // 2. SockJS fallback endpoint (dùng cho trình duyệt cũ trong môi trường dã chiến)
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // Client gửi lên server: /app/*
        registry.setApplicationDestinationPrefixes("/app");
        // Server push xuống client: /topic/*
        registry.enableSimpleBroker("/topic");
    }

    /**
     * Scheduler cho heartbeat timeout / periodic push.
     * Tách khỏi meshExecutor để không block accept loop.
     */
    @Bean
    @Primary
    public TaskScheduler c2TaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("c2-scheduler-");
        scheduler.setDaemon(true);
        return scheduler;
    }
}
