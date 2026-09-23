package com.rescue.mesh.ui.controller;

import com.rescue.mesh.model.MeshPacket;
import com.rescue.mesh.network.NodeConfig;
import com.rescue.mesh.storage.BaseStationStorageService;
import com.rescue.mesh.storage.DatabaseConfig;
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

/** Regression for ACK recovery after a persisted SOS is retransmitted. */
class BaseStationDuplicateAckTest {

    @Test
    void persistedDuplicateSosIsNotShownTwiceButGetsAnotherAck(@TempDir Path tempDir) throws Exception {
        BaseStationController controller = new BaseStationController();
        List<MeshPacket> acknowledgements = new ArrayList<>();
        controller.setAckSender((host, port, ack) -> acknowledgements.add(ack));

        int listenPort = freePort();
        int relayPort = freePort();
        controller.setDatabaseConfig(DatabaseConfig.forPath(tempDir.resolve("duplicate-ack.db")));
        controller.initializeStation(NodeConfig.fromArgs(new String[]{
                "--mode", "BASE_STATION",
                "--id", MeshPacket.NODE_BASE_STATION,
                "--bind-host", "127.0.0.1",
                "--bind-port", Integer.toString(listenPort),
                "--relay-host", "127.0.0.1",
                "--relay-port", Integer.toString(relayPort)
        }));

        try {
            assertTrue(controller.awaitInitialization(5, TimeUnit.SECONDS));
            assertTrue(controller.isStorageHealthy());

            MeshPacket sos = PacketFactory.createSosPacket(
                    "VICTIM-DUP-01", "Duplicate Victim", MeshPacket.ALERT_MEDICAL,
                    "Need help", 1, MeshPacket.SEVERITY_CRITICAL,
                    16.0745, 108.1502
            );

            controller.onPacketArrived(sos);
            controller.onPacketArrived(sos);

            assertEquals(2, acknowledgements.size(),
                    "a retransmitted persisted SOS must receive an ACK again");
            assertEquals(sos.getPacketId(), acknowledgements.get(0).getPayload().getAckForPacketId());
            assertEquals(sos.getPacketId(), acknowledgements.get(1).getPayload().getAckForPacketId());

            BaseStationStorageService storage = controller.getStorageService();
            assertEquals(1, storage.getSosStats().getTotal(),
                    "duplicate retransmission must not create a second SOS event/UI item");
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
