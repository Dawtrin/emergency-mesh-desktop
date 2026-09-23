package com.rescue.mesh.routing;

import com.google.gson.Gson;
import com.rescue.mesh.model.MeshPacket;
import com.rescue.mesh.util.PacketFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class RoutingEngineForwardingTest {

    private final List<RoutingEngine> engines = new ArrayList<>();
    private final Gson gson = new Gson();

    @AfterEach
    void tearDown() {
        engines.forEach(RoutingEngine::shutdown);
    }

    @Test
    void validPacketIsMutatedExactlyOnceOnForwardingCopy() {
        RecordingCallback callback = new RecordingCallback();
        AtomicReference<String> host = new AtomicReference<>();
        AtomicInteger port = new AtomicInteger();
        AtomicReference<MeshPacket> sent = new AtomicReference<>();
        RoutingEngine engine = engine("RELAY-1", "10.10.0.8", 18888, callback,
                (h, p, packet) -> {
                    host.set(h);
                    port.set(p);
                    sent.set(packet);
                    return true;
                });

        MeshPacket original = PacketFactory.createSosPacket(
                "VICTIM-1", "Victim", MeshPacket.ALERT_MEDICAL, "Need help",
                1, MeshPacket.SEVERITY_HIGH, 16.0, 108.0);
        int initialTtl = original.getTtl();
        int initialHops = original.getHopCount();
        String initialChecksum = original.getChecksum();

        engine.processPacket(original);

        MeshPacket forwarded = sent.get();
        assertNotNull(forwarded);
        assertAll(
                () -> assertEquals("10.10.0.8", host.get()),
                () -> assertEquals(18888, port.get()),
                () -> assertEquals(initialTtl - 1, forwarded.getTtl()),
                () -> assertEquals(initialHops + 1, forwarded.getHopCount()),
                () -> assertEquals(List.of("RELAY-1"), forwarded.getRouteHistory()),
                () -> assertEquals("RELAY-1", forwarded.getSenderHopId()),
                () -> assertTrue(forwarded.verifyChecksum(gson)),
                () -> assertNotEquals(initialChecksum, forwarded.getChecksum()),
                () -> assertEquals(initialTtl, original.getTtl()),
                () -> assertEquals(initialHops, original.getHopCount()),
                () -> assertTrue(original.getRouteHistory().isEmpty()),
                () -> assertEquals(initialChecksum, original.getChecksum()),
                () -> assertEquals(1, callback.relayed.get()));
    }

    @Test
    void explicitDestinationRouteUsesItsOwnHostAndPort() {
        AtomicReference<String> endpoint = new AtomicReference<>();
        RoutingEngine engine = engine("RELAY", "10.0.0.1", 9000, new RecordingCallback(),
                (host, port, packet) -> {
                    endpoint.set(host + ':' + port);
                    return true;
                });
        engine.recordRoute("VICTIM-X", "192.168.50.7", 19001);

        MeshPacket dispatch = PacketFactory.createDispatchCommand("VICTIM-X", "Evacuate", MeshPacket.SEVERITY_HIGH);
        engine.processPacket(dispatch);

        assertEquals("192.168.50.7:19001", endpoint.get());
    }

    @Test
    void dispatchWithoutExplicitVictimRouteDoesNotLoopUpstream() {
        AtomicInteger sends = new AtomicInteger();
        RecordingCallback callback = new RecordingCallback();
        RoutingEngine engine = engine("RELAY", "127.0.0.1", 18888, callback,
                (h, p, packet) -> { sends.incrementAndGet(); return true; });
        MeshPacket dispatch = PacketFactory.createDispatchCommand(
                "UNKNOWN-VICTIM", "Evacuate", MeshPacket.SEVERITY_HIGH);
        int ttl = dispatch.getTtl();
        String checksum = dispatch.getChecksum();

        engine.processPacket(dispatch);

        assertAll(
                () -> assertEquals(0, sends.get()),
                () -> assertTrue(callback.lastDrop.get().startsWith("NO_NEXT_HOP")),
                () -> assertEquals(ttl, dispatch.getTtl()),
                () -> assertEquals(checksum, dispatch.getChecksum()));
    }

    @Test
    void invalidChecksumDoesNotConsumeDuplicateCacheOrReachTransport() {
        AtomicInteger sends = new AtomicInteger();
        RecordingCallback callback = new RecordingCallback();
        RoutingEngine engine = engine("RELAY", "127.0.0.1", 18888, callback,
                (h, p, packet) -> { sends.incrementAndGet(); return true; });
        MeshPacket packet = PacketFactory.createSosPacket(
                "VICTIM", "Victim", MeshPacket.ALERT_MEDICAL, "Help", 1,
                MeshPacket.SEVERITY_HIGH, 16.0, 108.0);
        packet.setChecksum("0".repeat(64));

        engine.processPacket(packet);

        assertEquals(0, sends.get());
        assertEquals(0, engine.getCacheSize());
        assertEquals("CHECKSUM_FAIL", callback.lastDrop.get());
    }

    @Test
    void duplicateAndExpiredPacketsNeverReachTransport() {
        AtomicInteger sends = new AtomicInteger();
        RecordingCallback callback = new RecordingCallback();
        RoutingEngine engine = engine("RELAY", "127.0.0.1", 18888, callback,
                (h, p, packet) -> { sends.incrementAndGet(); return true; });

        MeshPacket valid = PacketFactory.createSosPacket(
                "VICTIM", "Victim", MeshPacket.ALERT_MEDICAL, "Help", 1,
                MeshPacket.SEVERITY_HIGH, 16.0, 108.0);
        engine.processPacket(valid);
        engine.processPacket(valid);
        assertEquals(1, sends.get());
        assertEquals("DUPLICATE", callback.lastDrop.get());

        MeshPacket expired = PacketFactory.createSosPacket(
                "VICTIM-2", "Victim", MeshPacket.ALERT_MEDICAL, "Help", 1,
                MeshPacket.SEVERITY_HIGH, 16.0, 108.0);
        expired.setTtl(1);
        expired.computeAndSetChecksum(gson);
        engine.processPacket(expired);

        assertEquals(1, sends.get());
        assertEquals("TTL_EXPIRED", callback.lastDrop.get());
    }

    @Test
    void missingNextHopDropsWithoutMutationOrTransport() {
        AtomicInteger sends = new AtomicInteger();
        RecordingCallback callback = new RecordingCallback();
        RoutingEngine engine = engine("ISOLATED", null, -1, callback,
                (h, p, packet) -> { sends.incrementAndGet(); return true; });
        MeshPacket packet = PacketFactory.createSosPacket(
                "VICTIM", "Victim", MeshPacket.ALERT_MEDICAL, "Help", 1,
                MeshPacket.SEVERITY_HIGH, 16.0, 108.0);
        int ttl = packet.getTtl();
        String checksum = packet.getChecksum();

        engine.processPacket(packet);

        assertEquals(0, sends.get());
        assertTrue(callback.lastDrop.get().startsWith("NO_NEXT_HOP"));
        assertEquals(ttl, packet.getTtl());
        assertEquals(checksum, packet.getChecksum());
    }

    private RoutingEngine engine(String id, String host, int port, RecordingCallback callback,
                                 RoutingEngine.PacketSender sender) {
        RoutingEngine engine = new RoutingEngine(id, host, port, callback, sender);
        engines.add(engine);
        return engine;
    }

    private static class RecordingCallback implements RoutingEngine.RoutingCallback {
        final AtomicInteger relayed = new AtomicInteger();
        final AtomicReference<String> lastDrop = new AtomicReference<>();

        @Override public void onPacketArrived(MeshPacket packet) {}
        @Override public void onPacketRelayed(MeshPacket packet, int nextHop) { relayed.incrementAndGet(); }
        @Override public void onPacketDropped(String packetId, String reason) { lastDrop.set(reason); }
        @Override public void onForwardError(int nextHop, String errorMessage) {}
        @Override public void onDispatchReceived(MeshPacket packet) {}
    }
}
