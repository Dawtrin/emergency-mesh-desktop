package com.rescue.mesh.controller;

import com.rescue.mesh.db.VictimDao;
import com.rescue.mesh.service.InterAgencyService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * REST API điều phối Cổng Tác Chiến Liên Ngành 2 Chiều (115 / 114 / 113).
 *
 * Endpoints:
 *   POST /api/interagency/{agencyType}/dispatch — Chuyển tiếp hồ sơ sang 115/114/113
 *   POST /api/interagency/ticket/create         — Tạo Phiếu điều phối điện tử chi tiết
 *   POST /api/interagency/ticket/{id}/advance   — Chuyển bước tiến trình (EN_ROUTE, ON_SCENE, HANDOVER)
 *   GET  /api/interagency/tickets               — Danh sách phiếu điều phối với tiến độ 4 bước
 *   GET  /api/interagency/directory             — Danh bạ khẩn cấp dã chiến
 *   GET  /api/interagency/history               — Lịch sử chuyển tiếp (backward compat)
 *   GET  /api/interagency/radio-logs            — Nhật ký đàm thoại VHF CH16
 *   POST /api/interagency/radio-logs            — Bắn khẩu lệnh mới qua bộ đàm
 *   POST /api/facilities/{facilityId}/capacity  — Tăng / giảm giường cấp cứu dã chiến
 */
@RestController
@RequestMapping("/api/interagency")
public class InterAgencyApiController {

    private final InterAgencyService interAgencyService;
    private final VictimDao victimDao;
    private final SimpMessagingTemplate messagingTemplate;

    public InterAgencyApiController(InterAgencyService interAgencyService,
                                     VictimDao victimDao,
                                     SimpMessagingTemplate messagingTemplate) {
        this.interAgencyService = interAgencyService;
        this.victimDao = victimDao;
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Chuyển tiếp hồ sơ nạn nhân sang cơ quan liên ngành.
     * POST /api/interagency/{agencyType}/dispatch
     */
    @PostMapping(value = "/{agencyType}/dispatch",
                 consumes = MediaType.APPLICATION_JSON_VALUE,
                 produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> dispatch(
            @PathVariable String agencyType,
            @RequestBody Map<String, String> body) {
        String victimNodeId = body.get("victimNodeId");
        String agencyName = body.getOrDefault("agencyName", resolveDefaultAgency(agencyType));

        if (victimNodeId == null || victimNodeId.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "victimNodeId là bắt buộc"));
        }

        if (!Set.of("EMS_115", "RESCUE_114", "POLICE_113").contains(agencyType)) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "agencyType phải là EMS_115, RESCUE_114, hoặc POLICE_113"));
        }

        VictimDao.VictimRecord victim = victimDao.findById(victimNodeId);
        String payload = victim != null ? victim.toString() : "Node: " + victimNodeId;

        long id = interAgencyService.dispatchToAgency(victimNodeId, agencyType, agencyName, payload);

        // Cập nhật trạng thái victim trong DB sang DISPATCHED
        victimDao.updateStatus(victimNodeId, "DISPATCHED");

        // Broadcast qua WebSocket
        messagingTemplate.convertAndSend("/topic/interagency", Map.of(
                "type", "INTERAGENCY_DISPATCH",
                "victimNodeId", victimNodeId,
                "agencyType", agencyType,
                "agencyName", agencyName,
                "id", id,
                "timestamp", System.currentTimeMillis()
        ));

        messagingTemplate.convertAndSend("/topic/victims", Map.of(
                "type", "STATUS_CHANGE",
                "targetNodeId", victimNodeId,
                "status", "DISPATCHED",
                "timestamp", System.currentTimeMillis()
        ));

        return ResponseEntity.ok(Map.of(
                "status", "SENT",
                "id", id,
                "agencyType", agencyType,
                "agencyName", agencyName
        ));
    }

    /**
     * Tạo Phiếu Điều Phối Điện Tử Toàn Diện
     * POST /api/interagency/ticket/create
     */
    @PostMapping(value = "/ticket/create",
                 consumes = MediaType.APPLICATION_JSON_VALUE,
                 produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> createTicket(@RequestBody Map<String, String> body) {
        String victimNodeId = body.get("victimNodeId");
        String agencyType = body.getOrDefault("agencyType", "EMS_115");
        String agencyName = body.getOrDefault("agencyName", resolveDefaultAgency(agencyType));
        String requiredVehicle = body.getOrDefault("requiredVehicle", "Xe cấp cứu tiêu chuẩn");
        String specialNeeds = body.getOrDefault("specialNeeds", "Không có yêu cầu đặc biệt");
        String priority = body.getOrDefault("priority", "HIGH");

        if (victimNodeId == null || victimNodeId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "victimNodeId là bắt buộc"));
        }

        VictimDao.VictimRecord victim = victimDao.findById(victimNodeId);
        String payload = victim != null ? victim.toString() : "Node: " + victimNodeId;

        long id = interAgencyService.createMissionTicket(victimNodeId, agencyType, agencyName,
                payload, requiredVehicle, specialNeeds, priority);

        victimDao.updateStatus(victimNodeId, "DISPATCHED");

        // Ghi vào nhật ký bộ đàm VHF tự động
        String radioMsg = String.format("Trạm C2 điều động %s: Tiếp nhận ca %s (%s) → Phương tiện: %s",
                agencyName, victimNodeId, victim != null ? victim.fullName() : "Chưa rõ họ tên", requiredVehicle);
        interAgencyService.logRadioTransmission("CH 16 (156.8 MHz)", "C2-COMMAND", agencyType, radioMsg, "URGENT");

        messagingTemplate.convertAndSend("/topic/interagency", Map.of(
                "type", "TICKET_CREATED",
                "ticketId", id,
                "victimNodeId", victimNodeId,
                "timestamp", System.currentTimeMillis()
        ));

        return ResponseEntity.ok(Map.of("status", "CREATED", "ticketId", id));
    }

    /**
     * Chuyển bước tiến trình xử lý phiếu liên ngành (2 chiều)
     * POST /api/interagency/ticket/{id}/advance
     * Body: { "status": "EN_ROUTE" | "ON_SCENE" | "HANDOVER", "unit": "Xe 115-02", "leader": "BS. Tuấn", "phone": "...", "eta": 6 }
     */
    @PostMapping(value = "/ticket/{id}/advance",
                 consumes = MediaType.APPLICATION_JSON_VALUE,
                 produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> advanceTicket(
            @PathVariable long id,
            @RequestBody Map<String, Object> body) {
        String nextStatus = (String) body.getOrDefault("status", "EN_ROUTE");
        String unit = (String) body.getOrDefault("unit", "");
        String leader = (String) body.getOrDefault("leader", "");
        String phone = (String) body.getOrDefault("phone", "");
        Integer eta = body.get("eta") != null ? Integer.valueOf(body.get("eta").toString()) : null;

        boolean ok = interAgencyService.advanceTicketStatus(id, nextStatus, unit, leader, phone, eta);
        if (!ok) {
            return ResponseEntity.badRequest().body(Map.of("error", "Không thể cập nhật phiếu #" + id));
        }

        // Tự động thêm log đàm thoại vô tuyến tương ứng
        String statusText = switch (nextStatus) {
            case "EN_ROUTE" -> "XUẤT KÍCH: Đội " + unit + " (Đ/c " + leader + ") đã xuất phát, ETA ~" + eta + " phút";
            case "ON_SCENE" -> "TIẾP CẬN HIỆN TRƯỜNG: Đội " + unit + " đã tiếp cận tọa độ nạn nhân";
            case "HANDOVER" -> "BÀN GIAO HOÀN TẤT: Nạn nhân đã nhập viện an toàn, kết thúc nhiệm vụ";
            default -> "Cập nhật trạng thái: " + nextStatus;
        };
        interAgencyService.logRadioTransmission("CH 16 (156.8 MHz)", unit.isBlank() ? "RESCUE-UNIT" : unit, "C2-COMMAND", statusText, "ROUTINE");

        // Broadcast realtime
        messagingTemplate.convertAndSend("/topic/interagency", Map.of(
                "type", "TICKET_UPDATED",
                "ticketId", id,
                "status", nextStatus,
                "timestamp", System.currentTimeMillis()
        ));

        return ResponseEntity.ok(Map.of("status", "UPDATED", "ticketId", id, "nextStatus", nextStatus));
    }

    /** Danh sách toàn bộ phiếu điều phối */
    @GetMapping(value = "/tickets", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<Map<String, Object>> getTickets() {
        return interAgencyService.getTickets();
    }

    /** Lịch sử chuyển tiếp (tương thích backward) */
    @GetMapping(value = "/history", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<Map<String, Object>> getHistory() {
        return interAgencyService.getTickets();
    }

    /** Danh bạ khẩn cấp dã chiến */
    @GetMapping(value = "/directory", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<Map<String, Object>> getDirectory() {
        return List.of(
            Map.of("agency", "EMS_115", "name", "Trung tâm Cấp cứu 115 Đà Nẵng", "phone", "0236-3825-115", "vhf", "155.725 MHz", "address", "122 Hải Phòng, Hải Châu"),
            Map.of("agency", "RESCUE_114", "name", "Phòng Cảnh sát PCCC & CNCH 114 Đà Nẵng", "phone", "114", "vhf", "155.475 MHz", "address", "183 Phan Đăng Lưu"),
            Map.of("agency", "POLICE_113", "name", "TT Thông tin Chỉ huy 113 CATP", "phone", "113", "uhf", "462.5625 MHz", "address", "47 Lý Tự Trọng"),
            Map.of("agency", "RESCUE_114", "name", "Đội Ca-nô PCCC Sông Hàn", "phone", "0236-3910-114", "vhf", "CH 16 (156.8 MHz)", "address", "Bến du thuyền Sông Hàn"),
            Map.of("agency", "EMS_115", "name", "Bệnh viện Đà Nẵng (Khoa Cấp Cứu)", "phone", "0236-3821-118", "vhf", "155.725 MHz", "address", "124 Hải Phòng")
        );
    }

    /** Nhật ký đàm thoại VHF CH16 */
    @GetMapping(value = "/radio-logs", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<Map<String, Object>> getRadioLogs() {
        return interAgencyService.getRadioLogs();
    }

    /** Bắn khẩu lệnh qua Kênh Bộ đàm dã chiến */
    @PostMapping(value = "/radio-logs", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> postRadioLog(@RequestBody Map<String, String> body) {
        String channel = body.getOrDefault("channel", "CH 16 (156.8 MHz)");
        String sender = body.getOrDefault("sender", "C2-COMMAND");
        String recipient = body.getOrDefault("recipient", "ALL_STATIONS");
        String message = body.get("message");
        String urgency = body.getOrDefault("urgency", "ROUTINE");

        if (message == null || message.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "message là bắt buộc"));
        }

        interAgencyService.logRadioTransmission(channel, sender, recipient, message, urgency);

        messagingTemplate.convertAndSend("/topic/radio", Map.of(
                "channel", channel,
                "sender", sender,
                "recipient", recipient,
                "message", message,
                "urgency", urgency,
                "timestamp", System.currentTimeMillis()
        ));

        return ResponseEntity.ok(Map.of("status", "LOGGED"));
    }

    /** Tăng hoặc giảm sức chứa giường/ca dã chiến */
    @PostMapping(value = "/facilities/{facilityId}/capacity",
                 consumes = MediaType.APPLICATION_JSON_VALUE,
                 produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> updateCapacity(
            @PathVariable String facilityId,
            @RequestBody Map<String, Integer> body) {
        int delta = body.getOrDefault("delta", 0);
        boolean ok = interAgencyService.updateFacilityCapacity(facilityId, delta);
        if (ok) {
            messagingTemplate.convertAndSend("/topic/facilities", Map.of("facilityId", facilityId, "delta", delta));
            return ResponseEntity.ok(Map.of("status", "UPDATED", "facilityId", facilityId, "delta", delta));
        }
        return ResponseEntity.badRequest().body(Map.of("error", "Không thể đổi sức chứa"));
    }

    private String resolveDefaultAgency(String agencyType) {
        return switch (agencyType) {
            case "EMS_115" -> "Cấp cứu 115 Đà Nẵng";
            case "RESCUE_114" -> "Phòng CS PCCC & CNCH 114";
            case "POLICE_113" -> "Trung tâm Chỉ huy 113 CATP";
            default -> "Liên ngành";
        };
    }
}
