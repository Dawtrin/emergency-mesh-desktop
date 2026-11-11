package com.rescue.mesh.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Thread pool chuyên dụng cho tầng TCP Mesh.
 *
 * Tách accept loop và các client worker khỏi servlet/WebSocket threads:
 *   - 1 task accept() blocking.
 *   - N task handleClient() parse JSON và gọi RoutingEngine.processPacket().
 *
 * RoutingEngine tiếp tục thread-safe qua SeenPacketCache.checkAndMark() atomic.
 */
@Configuration
@EnableAsync
public class AsyncSocketConfig {

    @Bean(name = "meshExecutor")
    public TaskExecutor meshExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("mesh-tcp-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(5);
        executor.initialize();
        return executor;
    }
}
