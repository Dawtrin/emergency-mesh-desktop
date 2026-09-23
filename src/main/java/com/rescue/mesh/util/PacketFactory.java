package com.rescue.mesh.util;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.rescue.mesh.model.MeshPacket;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Factory tạo các loại MeshPacket chuẩn canonical v1 cho hệ thống.
 *
 * Design Pattern: Factory Method Pattern
 *   - Tập trung logic tạo gói tin vào một nơi duy nhất
 *   - Đảm bảo mọi gói tin đều có đủ metadata bắt buộc và checksum canonical hợp lệ
 *   - Không còn tạo packet mới với loại cũ (SOS_DATA, DISPATCH_CMD)
 */
public class PacketFactory {

    private static final Gson GSON = new Gson();

    private PacketFactory() {}

    // =========================================================
    // SOS_BROADCAST — Gói tin cứu nạn từ nạn nhân
    // =========================================================

    /**
     * Tạo gói tin SOS canonical từ node nạn nhân gửi đến Base Station.
     */
    public static MeshPacket createSosPacket(
            String sourceNodeId,
            String senderName,
            String alertType,
            String message,
            int victimCount,
            String severity,
            double latitude,
            double longitude) {
        return createSosPacket(sourceNodeId, senderName, alertType, message, victimCount, severity, latitude, longitude, 0.0, 0.0);
    }

    /**
     * Tạo gói tin SOS canonical với đầy đủ tọa độ, cao độ và độ chính xác GPS.
     */
    public static MeshPacket createSosPacket(
            String sourceNodeId,
            String senderName,
            String alertType,
            String message,
            int victimCount,
            String severity,
            double latitude,
            double longitude,
            double altitude,
            double accuracy) {

        MeshPacket packet = new MeshPacket(
                MeshPacket.TYPE_SOS_BROADCAST,
                sourceNodeId,
                MeshPacket.NODE_BASE_STATION
        );

        MeshPacket.Payload payload = new MeshPacket.Payload();
        payload.setSenderName(senderName);
        payload.setAlertType(alertType);
        payload.setMessage(message);
        payload.setVictimCount(victimCount);
        payload.setSeverity(severity);
        payload.setLocation(new MeshPacket.Location(latitude, longitude, altitude, accuracy));

        packet.setPayload(payload);
        packet.computeAndSetChecksum(GSON);

        return packet;
    }

    // =========================================================
    // DISPATCH_COMMAND — Lệnh chỉ đạo từ Base Station về nạn nhân
    // =========================================================

    /**
     * Tạo gói tin lệnh điều phối canonical từ Base Station gửi về node chỉ định.
     */
    public static MeshPacket createDispatchCommand(
            String destinationNodeId,
            String commandMessage,
            String severity) {

        MeshPacket packet = new MeshPacket(
                MeshPacket.TYPE_DISPATCH_COMMAND,
                MeshPacket.NODE_BASE_STATION,
                destinationNodeId
        );

        MeshPacket.Payload payload = new MeshPacket.Payload();
        payload.setSenderName("BASE_STATION_COMMANDER");
        payload.setAlertType("DISPATCH");
        payload.setMessage(commandMessage);
        payload.setSeverity(severity);
        payload.setVictimCount(0);
        payload.setLocation(new MeshPacket.Location(0.0, 0.0, 0.0, 0.0));

        packet.setPayload(payload);
        packet.computeAndSetChecksum(GSON);

        return packet;
    }

    // =========================================================
    // ACK — Xác nhận biên nhận gói tin (Machine-Readable)
    // =========================================================

    /**
     * Tạo gói tin ACK canonical xác nhận đã nhận gói tin.
     * Có trường machine-readable ack_for_packet_id.
     */
    public static MeshPacket createAck(
            String myNodeId,
            String originalPacketId,
            String destinationNodeId) {

        MeshPacket packet = new MeshPacket(
                MeshPacket.TYPE_ACK,
                myNodeId,
                destinationNodeId
        );

        MeshPacket.Payload payload = new MeshPacket.Payload();
        payload.setSenderName(myNodeId);
        payload.setAlertType("ACK");
        payload.setMessage("ACK for packet: " + originalPacketId);
        payload.setAckForPacketId(originalPacketId);
        payload.setSeverity(MeshPacket.SEVERITY_MEDIUM);
        payload.setVictimCount(0);
        payload.setLocation(new MeshPacket.Location(0.0, 0.0, 0.0, 0.0));

        packet.setPayload(payload);
        packet.computeAndSetChecksum(GSON);

        return packet;
    }

    // =========================================================
    // HEARTBEAT — Kiểm tra node còn sống không
    // =========================================================

    /**
     * Tạo gói tin HEARTBEAT canonical.
     */
    public static MeshPacket createHeartbeat(String sourceNodeId) {

        MeshPacket packet = new MeshPacket(
                MeshPacket.TYPE_HEARTBEAT,
                sourceNodeId,
                MeshPacket.NODE_BASE_STATION
        );

        MeshPacket.Payload payload = new MeshPacket.Payload();
        payload.setSenderName(sourceNodeId);
        payload.setAlertType("HEARTBEAT");
        payload.setMessage("Node " + sourceNodeId + " is alive");
        payload.setSeverity(MeshPacket.SEVERITY_MEDIUM);
        payload.setVictimCount(0);
        payload.setLocation(new MeshPacket.Location(0.0, 0.0, 0.0, 0.0));

        packet.setPayload(payload);
        packet.computeAndSetChecksum(GSON);

        return packet;
    }

    // =========================================================
    // ROUTE_DISCOVERY — Khám phá tuyến đường ad-hoc
    // =========================================================

    /**
     * Tạo gói tin ROUTE_DISCOVERY canonical.
     */
    public static MeshPacket createRouteDiscovery(String sourceNodeId, String destinationNodeId) {

        MeshPacket packet = new MeshPacket(
                MeshPacket.TYPE_ROUTE_DISCOVERY,
                sourceNodeId,
                destinationNodeId != null ? destinationNodeId : MeshPacket.NODE_BROADCAST
        );

        MeshPacket.Payload payload = new MeshPacket.Payload();
        payload.setSenderName(sourceNodeId);
        payload.setAlertType("ROUTE_DISCOVERY");
        payload.setMessage("Route discovery from " + sourceNodeId);
        payload.setSeverity(MeshPacket.SEVERITY_MEDIUM);
        payload.setVictimCount(0);
        payload.setLocation(new MeshPacket.Location(0.0, 0.0, 0.0, 0.0));

        Map<String, Object> discoveryInfo = new LinkedHashMap<>();
        discoveryInfo.put("metric", "hop_count");
        discoveryInfo.put("target_node_id", destinationNodeId != null ? destinationNodeId : MeshPacket.NODE_BROADCAST);
        payload.setDiscoveryInfo(discoveryInfo);

        packet.setPayload(payload);
        packet.computeAndSetChecksum(GSON);

        return packet;
    }

    /**
     * Trả về instance Gson dùng chung.
     */
    public static Gson getGson() {
        return GSON;
    }
}
