package com.rescue.mesh.util;

import com.google.gson.Gson;
import com.rescue.mesh.model.MeshPacket;

/**
 * Factory tạo các loại MeshPacket chuẩn cho hệ thống.
 *
 * Design Pattern: Factory Method Pattern
 *   - Tập trung logic tạo gói tin vào một nơi duy nhất
 *   - Đảm bảo mọi gói tin đều có đủ field bắt buộc và checksum hợp lệ
 *   - Tránh tạo gói tin thiếu field ở nhiều nơi trong code
 *
 * Mọi method đều tự động:
 *   1. Gán UUID mới
 *   2. Set timestamp hiện tại
 *   3. Tính và gán checksum SHA-256
 */
public class PacketFactory {

    private static final Gson GSON = new Gson();

    // ===== Private constructor: không cho phép tạo instance =====
    private PacketFactory() {}

    // =========================================================
    // SOS_DATA — Gói tin cứu nạn từ nạn nhân
    // =========================================================

    /**
     * Tạo gói tin SOS từ node nạn nhân gửi đến Base Station.
     *
     * @param sourceNodeId  ID của node gửi (ví dụ: "NODE_A_VICTIM")
     * @param senderName    Tên người gửi tín hiệu SOS
     * @param alertType     Loại tình huống: MEDICAL / FLOOD_TRAPPED / LANDSLIDE
     * @param message       Mô tả chi tiết tình huống
     * @param victimCount   Số nạn nhân cần cứu
     * @param severity      Mức độ nguy hiểm: CRITICAL / HIGH / MEDIUM
     * @param latitude      Vĩ độ GPS
     * @param longitude     Kinh độ GPS
     * @return MeshPacket hoàn chỉnh, sẵn sàng gửi qua Socket
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

        MeshPacket packet = new MeshPacket(
                MeshPacket.TYPE_SOS_DATA,
                sourceNodeId,
                MeshPacket.NODE_BASE_STATION
        );

        MeshPacket.Payload payload = new MeshPacket.Payload();
        payload.setSenderName(senderName);
        payload.setAlertType(alertType);
        payload.setMessage(message);
        payload.setVictimCount(victimCount);
        payload.setSeverity(severity);
        payload.setLocation(new MeshPacket.Location(latitude, longitude));

        packet.setPayload(payload);
        packet.computeAndSetChecksum(GSON);

        return packet;
    }

    // =========================================================
    // DISPATCH_CMD — Lệnh chỉ đạo từ Base Station về nạn nhân
    // =========================================================

    /**
     * Tạo gói tin lệnh điều phối từ Base Station gửi ngược về node nạn nhân.
     *
     * @param destinationNodeId ID node nạn nhân cần nhận lệnh (ví dụ: "NODE_A_VICTIM")
     * @param commandMessage    Nội dung lệnh chỉ đạo từ chỉ huy
     * @param severity          Mức độ ưu tiên của lệnh
     * @return MeshPacket hoàn chỉnh loại DISPATCH_CMD
     */
    public static MeshPacket createDispatchCommand(
            String destinationNodeId,
            String commandMessage,
            String severity) {

        MeshPacket packet = new MeshPacket(
                MeshPacket.TYPE_DISPATCH_CMD,
                MeshPacket.NODE_BASE_STATION,
                destinationNodeId
        );

        MeshPacket.Payload payload = new MeshPacket.Payload();
        payload.setSenderName("BASE_STATION_COMMANDER");
        payload.setAlertType("DISPATCH");
        payload.setMessage(commandMessage);
        payload.setSeverity(severity);
        payload.setVictimCount(0);
        payload.setLocation(new MeshPacket.Location(0, 0));

        packet.setPayload(payload);
        packet.computeAndSetChecksum(GSON);

        return packet;
    }

    // =========================================================
    // ACK — Xác nhận nhận gói tin
    // =========================================================

    /**
     * Tạo gói tin ACK xác nhận đã nhận gói tin SOS.
     *
     * @param myNodeId          ID của node gửi ACK
     * @param originalPacketId  UUID của gói tin SOS gốc cần xác nhận
     * @param destinationNodeId ID node cần nhận ACK
     * @return MeshPacket hoàn chỉnh loại ACK
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
        payload.setSeverity(MeshPacket.SEVERITY_MEDIUM);
        payload.setVictimCount(0);
        payload.setLocation(new MeshPacket.Location(0, 0));

        packet.setPayload(payload);
        packet.computeAndSetChecksum(GSON);

        return packet;
    }

    // =========================================================
    // HEARTBEAT — Kiểm tra node còn sống không
    // =========================================================

    /**
     * Tạo gói tin HEARTBEAT để kiểm tra kết nối.
     *
     * @param sourceNodeId ID node gửi heartbeat
     * @return MeshPacket hoàn chỉnh loại HEARTBEAT
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
        payload.setLocation(new MeshPacket.Location(0, 0));

        packet.setPayload(payload);
        packet.computeAndSetChecksum(GSON);

        return packet;
    }

    /**
     * Trả về instance Gson dùng chung — tránh tạo Gson mới mỗi lần gọi.
     * @return Gson instance
     */
    public static Gson getGson() {
        return GSON;
    }
}