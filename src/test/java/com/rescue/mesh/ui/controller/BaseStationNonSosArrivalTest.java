package com.rescue.mesh.ui.controller;

import com.rescue.mesh.model.MeshPacket;
import com.rescue.mesh.network.NodeConfig;
import com.rescue.mesh.storage.DatabaseConfig;
import com.rescue.mesh.storage.BaseStationStorageService;
import com.rescue.mesh.util.PacketFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.ServerSocket;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Ensures non-SOS packets do not become false victim events on the dashboard. */
class BaseStationNonSosArrivalTest {

    @Test
    void heartbeatIsPersistedButDoesNotCreateSosRowOrAlarm(@TempDir Path tempDir) throws Exception {
        BaseStationController controller = new BaseStationController();
        List<MeshPacket> acknowledgements = new ArrayList<>();
        controller.setAckSender((host, port, ack) -> acknowledgements.add(ack));

        controller.setDatabaseConfig(DatabaseConfig.forPath(tempDir.resolve("non-sos.db")));
        controller.initializeStation(NodeConfig.fromArgs(new String[]{
                "--mode", "BASE_STATION",
                "--id", MeshPacket.NODE_BASE_STATION,
                "--bind-host", "127.0.0.1",
                "--bind-port", Integer.toString(freePort()),
                "--relay-host", "127.0.0.1",
                "--relay-port", Integer.toString(freePort())
        }));

        try {
            assertTrue(controller.awaitInitialization(5, TimeUnit.SECONDS));
            assertTrue(controller.isStorageHealthy());

            MeshPacket heartbeat = PacketFactory.createHeartbeat("NODE-HEARTBEAT");
            heartbeat.setDestinationNodeId(MeshPacket.NODE_BASE_STATION);
            heartbeat.computeAndSetChecksum();

            controller.onPacketArrived(heartbeat);

            BaseStationStorageService storage = controller.getStorageService();
            assertEquals(0, storage.getSosStats().getTotal());
            assertTrue(acknowledgements.isEmpty());
            assertTrue(storage.getPacketRepository().findById(heartbeat.getPacketId()).isPresent());
        } finally {
            controller.shutdown();
        }
    }

    private static int freePort() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
