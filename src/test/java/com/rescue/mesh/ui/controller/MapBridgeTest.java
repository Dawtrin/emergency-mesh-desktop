package com.rescue.mesh.ui.controller;

import com.rescue.mesh.model.MeshPacket;
import com.rescue.mesh.ui.component.VictimTableRow;
import com.rescue.mesh.util.PacketFactory;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class MapBridgeTest {

    @Test
    void queuedMarkersReplayExactlyOnceAndNeverAfterShutdown() {
        BaseStationController controller = new BaseStationController();
        List<String> scripts = new ArrayList<>();
        controller.setMapScriptBridge(scripts::add);

        MeshPacket first = sos("NODE-A", 15.9753, 108.2532);
        MeshPacket second = sos("NODE-B", 15.9800, 108.2600);
        controller.addMarkerToMap(first);
        controller.addMarkerToMap(second);
        assertEquals(2, controller.getPendingMapPackets().size());

        controller.onMapReady();
        assertTrue(scripts.get(0).equals("onMapReady()"));
        assertEquals(2, scripts.stream().filter(s -> s.startsWith("addSOSMarkerFromJson(")).count());
        assertTrue(controller.getPendingMapPackets().isEmpty());

        int scriptCount = scripts.size();
        controller.onMapReady();
        assertEquals(scriptCount, scripts.size(), "a second readiness event must not replay markers");

        controller.shutdown();
        controller.onMapReady();
        assertEquals(scriptCount, scripts.size(), "shutdown must prevent later map work");
    }

    @Test
    void markerIdentityAndFocusUsePacketIdNotSourceNode() {
        BaseStationController controller = new BaseStationController();
        List<String> scripts = new ArrayList<>();
        controller.setMapScriptBridge(scripts::add);
        controller.setMapReady(true);

        MeshPacket first = sos("SAME-NODE", 15.9753, 108.2532);
        MeshPacket second = sos("SAME-NODE", 15.9800, 108.2600);
        controller.addMarkerToMap(first);
        controller.addMarkerToMap(second);
        assertEquals(2, scripts.stream().filter(s -> s.startsWith("addSOSMarkerFromJson(")).count());

        VictimTableRow firstRow = VictimTableRow.fromMeshPacket(first, 1);
        VictimTableRow secondRow = VictimTableRow.fromMeshPacket(second, 2);
        assertNotEquals(firstRow.getMarkerId(), secondRow.getMarkerId());

        controller.focusMarkerOnMap(firstRow.getMarkerId());
        controller.focusMarkerOnMap(secondRow.getMarkerId());
        assertTrue(scripts.stream().anyMatch(s -> s.equals("focusMarker(\"" + first.getPacketId() + "\")")));
        assertTrue(scripts.stream().anyMatch(s -> s.equals("focusMarker(\"" + second.getPacketId() + "\")")));
        controller.shutdown();
    }

    @Test
    void routeIsDrawnOnlyWhenRelayCoordinatesAreResolved() {
        BaseStationController controller = new BaseStationController();
        List<String> scripts = new ArrayList<>();
        controller.setMapScriptBridge(scripts::add);
        controller.setMapReady(true);

        MeshPacket packet = sos("NODE-A", 15.9753, 108.2532);
        packet.setRouteHistory(List.of("RELAY-A"));
        controller.addMarkerToMap(packet);
        assertFalse(scripts.stream().anyMatch(s -> s.startsWith("drawRoute(")),
                "node IDs alone must not create a fabricated route");

        controller.setRouteCoordinateResolver(nodeId -> "RELAY-A".equals(nodeId)
                ? Optional.of(new BaseStationController.MapCoordinate(15.9770, 108.2550))
                : Optional.empty());
        controller.addMarkerToMap(packet);
        assertTrue(scripts.stream().anyMatch(s -> s.startsWith("drawRoute(")),
                "a route is rendered when a real relay coordinate is available");
        controller.shutdown();
    }

    private MeshPacket sos(String nodeId, double lat, double lon) {
        return PacketFactory.createSosPacket(nodeId, "Test sender", "FLOOD", "Need help",
                1, MeshPacket.SEVERITY_HIGH, lat, lon);
    }
}
