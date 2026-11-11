package com.rescue.mesh.config;

import com.rescue.mesh.db.DatabaseManager;
import com.rescue.mesh.db.MetricDao;
import com.rescue.mesh.db.RelayNodeDao;
import com.rescue.mesh.db.RescueTeamDao;
import com.rescue.mesh.db.VictimDao;
import com.rescue.mesh.service.C2ControllerService;
import com.rescue.mesh.service.InterAgencyService;
import com.rescue.mesh.service.NetworkAnalyticsService;
import com.rescue.mesh.service.ReportExportService;
import com.rescue.mesh.service.ScenarioSimulatorService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Cầu nối giữa Spring DI container và tầng legacy singleton (DatabaseManager, DAOs, Services).
 *
 * Phase 1 dùng singleton thủ công; Phase 2 Spring Boot cần @Bean để inject.
 * Config này tạo proxy bean cho mỗi class, giữ backward compatible với JavaFX entry.
 */
@Configuration
public class ServiceBeanConfig {

    @Bean
    public DatabaseManager databaseManager() {
        return DatabaseManager.getInstance();
    }

    @Bean
    public VictimDao victimDao(DatabaseManager db) {
        return new VictimDao(db);
    }

    @Bean
    public MetricDao metricDao(DatabaseManager db) {
        return new MetricDao(db);
    }

    @Bean
    public RelayNodeDao relayNodeDao(DatabaseManager db) {
        return new RelayNodeDao(db);
    }

    @Bean
    public RescueTeamDao rescueTeamDao(DatabaseManager db) {
        return new RescueTeamDao(db);
    }

    @Bean
    public C2ControllerService c2ControllerService(DatabaseManager db) {
        return new C2ControllerService(db);
    }

    @Bean
    public NetworkAnalyticsService networkAnalyticsService(DatabaseManager db) {
        return new NetworkAnalyticsService(db);
    }

    @Bean
    public InterAgencyService interAgencyService(DatabaseManager db) {
        return new InterAgencyService(db);
    }

    @Bean
    public ScenarioSimulatorService scenarioSimulatorService(DatabaseManager db) {
        return new ScenarioSimulatorService(db);
    }

    @Bean
    public ReportExportService reportExportService(DatabaseManager db, InterAgencyService interAgencyService) {
        return new ReportExportService(db, interAgencyService);
    }
}
