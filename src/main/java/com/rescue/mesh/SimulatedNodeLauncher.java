package com.rescue.mesh;

/**
 * Launcher cho Simulated Node (Victim / Relay) — workaround cho JavaFX module system.
 *
 * Tương tự BaseStationLauncher, class này không extends Application
 * để tránh lỗi "JavaFX runtime components are missing" khi chạy từ JAR.
 */
public class SimulatedNodeLauncher {

    public static void main(String[] args) {
        SimulatedNodeApp.main(args);
    }
}
