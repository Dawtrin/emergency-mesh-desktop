package com.rescue.mesh.service;

import com.google.gson.Gson;
import com.rescue.mesh.model.MeshPacket;
import com.rescue.mesh.util.PacketFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Bộ Giả Lập Bơm Gói Tin Demo 1-Chạm (Scenario Simulator).
 *
 * Tạo dữ liệu SOS giả lập và bơm trực tiếp vào TCP port 8888
 * để toàn bộ bản đồ, biểu đồ và còi hú nhảy số biểu diễn trước Hội đồng.
 *
 * Vùng giả lập: Khu vực Đà Nẵng (16.0° - 16.1°N, 108.15° - 108.25°E).
 */
public class ScenarioSimulatorService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ScenarioSimulatorService.class);
    private static final Gson GSON = new Gson();
    private final Random random = new Random();
    private final com.rescue.mesh.db.DatabaseManager databaseManager;
    private int nodeCounter = 0;

    private static final String[] NAMES_FLOOD = {
            "Nguyễn Văn Hùng", "Trần Thị Mai", "Lê Văn Sơn",
            "Phạm Thị Hoa", "Hoàng Văn Đức"
    };
    private static final String[] NAMES_MEDICAL = {
            "Đỗ Thị Lan", "Vũ Văn Minh", "Bùi Thị Ngọc"
    };
    private static final String[] BLOOD_TYPES = {"O+", "A+", "B+", "AB-", "O-", "A-"};
    private static final String[] MESSAGES_FLOOD = {
            "Nước ngập tới mái nhà, cần cứu gấp!",
            "Gia đình 5 người mắc kẹt trên tầng 2",
            "Bà cụ 80 tuổi không thể di chuyển, nước dâng nhanh",
            "Trẻ em và phụ nữ mang thai cần sơ tán ngay",
            "Nhà sập 1 phần, đang trú trên mái tôn"
    };
    private static final String[] MESSAGES_MEDICAL = {
            "Cần gấp 2 bịch máu AB-, bệnh nhân mất máu nặng",
            "Chấn thương cột sống, cần cáng và nẹp chuyên dụng",
            "Bệnh nhân tiểu đường hạ đường huyết, cần insulin"
    };

    public ScenarioSimulatorService(com.rescue.mesh.db.DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    /**
     * Bơm N ca lũ lụt giả lập vào TCP mesh.
     * @return Danh sách node_id đã bơm
     */
    public List<String> injectFloodVictims(int count, int meshPort) {
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String nodeId = "SIM_FLOOD_" + (++nodeCounter);
            String name = NAMES_FLOOD[random.nextInt(NAMES_FLOOD.length)];
            String message = MESSAGES_FLOOD[random.nextInt(MESSAGES_FLOOD.length)];
            String severity = random.nextFloat() < 0.4 ? "CRITICAL" : "HIGH";
            double lat = 16.02 + random.nextDouble() * 0.06;
            double lon = 108.16 + random.nextDouble() * 0.08;
            int victims = 1 + random.nextInt(6);

            MeshPacket packet = PacketFactory.createSosPacket(
                    nodeId, name, "FLOOD_TRAPPED", message,
                    victims, severity, lat, lon
            );
            // Gán thông tin bổ sung & tính lại checksum
            packet.getPayload().setPhoneNumber("09" + (10000000 + random.nextInt(89999999)));
            packet.getPayload().setBloodType(BLOOD_TYPES[random.nextInt(BLOOD_TYPES.length)]);
            packet.computeAndSetChecksum(GSON);

            sendToLocalMesh(packet, meshPort);
            ids.add(nodeId);
            sleepQuietly(150);
        }
        LOGGER.info("[SIMULATOR] Đã bơm {} ca lũ lụt", count);
        return ids;
    }

    /**
     * Bơm 1 ca cấp cứu y tế (máu hiếm AB-) giả lập.
     */
    public String injectMedicalEmergency(int meshPort) {
        String nodeId = "SIM_MED_" + (++nodeCounter);
        String name = NAMES_MEDICAL[random.nextInt(NAMES_MEDICAL.length)];
        String message = MESSAGES_MEDICAL[random.nextInt(MESSAGES_MEDICAL.length)];
        double lat = 16.04 + random.nextDouble() * 0.03;
        double lon = 108.19 + random.nextDouble() * 0.04;

        MeshPacket packet = PacketFactory.createSosPacket(
                nodeId, name, "MEDICAL", message,
                1, "CRITICAL", lat, lon
        );
        packet.getPayload().setPhoneNumber("09" + (10000000 + random.nextInt(89999999)));
        packet.getPayload().setBloodType("AB-");
        packet.getPayload().setMedicalNotes("Cần gấp bịch máu AB-, bệnh nhân choáng mất máu");
        packet.computeAndSetChecksum(GSON);

        sendToLocalMesh(packet, meshPort);
        LOGGER.info("[SIMULATOR] Đã bơm ca cấp cứu y tế: {}", nodeId);
        return nodeId;
    }

    /**
     * Bơm gói tin qua 3 bước nhảy relay giả lập (multi-hop).
     */
    public String injectMultiHopPacket(int meshPort) {
        String nodeId = "SIM_MULTIHOP_" + (++nodeCounter);
        MeshPacket packet = PacketFactory.createSosPacket(
                nodeId, "Bùi Văn Trung", "FLOOD_TRAPPED",
                "Mắc kẹt xa trạm, gói tin qua 3 relay",
                3, "HIGH",
                16.08 + random.nextDouble() * 0.02,
                108.22 + random.nextDouble() * 0.03
        );
        // Giả lập multi-hop
        packet.addToRouteHistory("RELAY_NODE_A");
        packet.addToRouteHistory("RELAY_NODE_B");
        packet.addToRouteHistory("RELAY_NODE_C");
        packet.setHopCount(3);
        packet.computeAndSetChecksum(GSON);

        sendToLocalMesh(packet, meshPort);
        LOGGER.info("[SIMULATOR] Đã bơm gói multi-hop 3 bước: {}", nodeId);
        return nodeId;
    }

    /**
     * Bơm 7 Tình Huống Cứu Nạn Thực Tế Chuẩn Tác Chiến Tại TP. Đà Nẵng
     * Kèm 7 Phiếu Tác Chiến Liên Ngành (115/114/113) và 6 Điện Đàm VHF CH16.
     */
    public List<String> injectRealisticSituations(int meshPort) {
        List<String> nodeIds = new ArrayList<>();
        long now = System.currentTimeMillis();

        com.rescue.mesh.db.DatabaseManager db = this.databaseManager;

        // 1. Dữ liệu 7 nạn nhân thực tế
        Object[][] victimsData = {
            // nodeId, name, alert, sev, lat, lon, phone, blood, notes, status
            {"DN_HOAVANG_01", "Trần Văn Hùng (Gia đình 4 người)", "LŨ CÔ LẬP — CẦN OXY", "CRITICAL", 16.0042, 108.1528, "0905.234.111", "O+", "Người già 78 tuổi tai biến liệt nửa người, nước ngập 1.8m ngắt điện hoàn toàn, cần bình oxy thở khẩn cấp", "DISPATCHED"},
            {"DN_SONGHAN_02", "Nguyễn Văn Cường (3 ngư dân)", "LẬT TÀU CÁ CỬA BIỂN", "CRITICAL", 16.0825, 108.2210, "0913.882.233", "B+", "Tàu cá ĐNa-90214 bị lốc đánh lật, 3 người bám phao cứu sinh trôi dạt hạ lưu gần Cầu Thuận Phước", "DISPATCHED"},
            {"DN_HAICHAU_03", "Lê Quốc Bảo (2 công nhân)", "SẠT LỞ BỜ KÈ — ĐA CHẤN THƯƠNG", "CRITICAL", 16.0812, 108.2195, "0935.671.990", "A+", "Sạt lở bờ kè ven sông Như Nguyệt, đất đá đè ép, nghi vỡ xương chậu và chấn thương cột sống cổ", "DISPATCHED"},
            {"DN_CAMLE_04", "Hoàng Thị Bích Ngọc", "SẢN PHỤ CHUYỂN DẠ VỠ ỐI", "CRITICAL", 16.0285, 108.2098, "0988.332.115", "O+", "Thai 38 tuần chuyển dạ cấp, vỡ ối giờ thứ 2, đường Nguyễn Tri Phương ngập sâu 1.2m xe ô tô không vào được", "DISPATCHED"},
            {"DN_SONTRA_05", "Michael Harrison (Du khách)", "LẠC RỪNG — MẤT MÁU NẶNG", "CRITICAL", 16.1150, 108.2610, "+84.905.999.888", "O-", "Trượt ngã vực sâu 15m đường lên Đỉnh Bàn Cờ, vết thương đùi sâu mất máu dữ dội, nhóm máu O- cực hiếm", "RESOLVED"},
            {"DN_LIENCHIEU_06", "Phạm Đăng Khoa", "SẠT ĐÈO HẢI VÂN — KẸT CABIN", "HIGH", 16.1420, 108.1310, "0977.445.667", "B+", "Đá tảng lăn từ vách núi đè bẹp cabin xe tải khúc cua Làng Vân đèo Hải Vân, chân tài xế bị kẹt cứng", "PENDING"},
            {"DN_NGUHANHSON_07", "Gia đình bà Lê Thị Tám (6 người)", "NƯỚC LŨ CÔ LẬP GÁC MÁI", "HIGH", 16.0125, 108.2415, "0905.123.456", "A+", "Nước sông Đô Tỏa dâng ngập khu di tích K20, 6 người gồm 2 trẻ nhỏ đang ngồi trên xà gồ gác mái tôn", "PENDING"}
        };

        try (java.sql.Connection conn = db.getConnection()) {
            // A. Insert Victims
            String vSql = """
                INSERT OR REPLACE INTO victims (
                    node_id, full_name, phone_number, blood_type, medical_notes,
                    victim_count, alert_type, severity, latitude, longitude,
                    status, first_seen_timestamp, last_updated_timestamp, dispatched_team
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
            try (java.sql.PreparedStatement ps = conn.prepareStatement(vSql)) {
                for (Object[] row : victimsData) {
                    ps.setString(1, (String) row[0]);
                    ps.setString(2, (String) row[1]);
                    ps.setString(3, (String) row[6]); // phone_number
                    ps.setString(4, (String) row[7]); // blood_type
                    ps.setString(5, (String) row[8]); // medical_notes
                    ps.setInt(6, 1);                  // victim_count
                    ps.setString(7, (String) row[2]); // alert_type
                    ps.setString(8, (String) row[3]); // severity
                    ps.setDouble(9, (Double) row[4]); // lat
                    ps.setDouble(10, (Double) row[5]); // lon
                    ps.setString(11, (String) row[9]); // status
                    ps.setLong(12, now - 600000);     // first_seen
                    ps.setLong(13, now);              // last_updated
                    ps.setString(14, "ĐỘI PHẢN ỨNG NHANH C2");
                    ps.addBatch();
                    nodeIds.add((String) row[0]);
                }
                ps.executeBatch();
            }

            // B. Insert Matching 7 Inter-Agency Mission Tickets
            Object[][] ticketsData = {
                // vicId, agencyType, facName, reqVeh, specNeeds, status, respUnit, respLeader, phone, eta
                {"DN_HOAVANG_01", "EMS_115", "Bệnh viện Đà Nẵng", "Xe cấp cứu hồi sức chuyên dụng (115)", "Máy thở xách tay, 2 bình oxy 10L, cáng bạt dã chiến chống nước", "EN_ROUTE", "Xe cấp cứu 115-01", "BS. Nguyễn Văn Nam", "0905.115.001", 8},
                {"DN_SONGHAN_02", "RESCUE_114", "Đội Ca-nô PCCC & CNCH Sông Hàn", "Ca-nô cao tốc cứu nạn đường sông Sông Hàn (114)", "Phao cứu sinh quăng xa, súng bắn dây mồi, chăn ủ nhiệt y tế", "EN_ROUTE", "Ca-nô cao tốc Sông Hàn 02", "Đại úy Trần Đình Tuấn", "0905.114.002", 4},
                {"DN_HAICHAU_03", "RESCUE_114", "Đội PCCC & CNCH Hải Châu - Thanh Khê", "Xe cứu nạn cứu hộ lội nước bánh xích", "Kẹp nâng thủy lực dọn bê tông, nẹp cố định cột sống cứng", "ON_SCENE", "Xe cứu hộ xích 114-HC", "Thiếu tá Lê Hoàng Hải", "0905.114.003", 0},
                {"DN_CAMLE_04", "EMS_115", "Bệnh viện Phụ sản - Nhi Đà Nẵng", "Xuồng cao su động cơ chân vịt cạn luồn lách (114)", "Bộ dụng cụ đỡ đẻ dã chiến vô trùng, lồng ấp sơ sinh di động", "ON_SCENE", "Xuồng chuyên dụng + Kíp Sản", "BS. Trần Thị Mai", "0905.115.004", 0},
                {"DN_SONTRA_05", "EMS_115", "Bệnh viện Đa khoa Hoàn Mỹ Đà Nẵng", "Xe cấp cứu lưu động tiêu chuẩn", "Chuẩn bị sẵn 2 đơn vị hồng cầu khối O- nhóm hiếm, mổ cấp cứu", "HANDOVER", "Xe ngoại viện Hoàn Mỹ 01", "BS. Đặng Quốc Bảo", "0905.115.005", 0},
                {"DN_LIENCHIEU_06", "RESCUE_114", "Đội PCCC & CNCH Khu vực Liên Chiểu", "Xe cứu nạn cứu hộ lội nước bánh xích", "Kích bóng hơi chịu tải 20 tấn, kìm cắt thủy lực Lukas", "DISPATCHED", "Đội xe chuyên dụng KCN Hòa Khánh", "Trung tá Phạm Văn Đức", "0905.114.006", 12},
                {"DN_NGUHANHSON_07", "POLICE_113", "Công an Quận Ngũ Hành Sơn", "Tổ tuần tra bảo vệ hiện trường & rào chắn cầu", "Áo phao cứu sinh, lương khô, dây thừng kéo ca-nô K20", "DISPATCHED", "Tổ phản ứng nhanh CA-NHS", "Đại úy Võ Thành Công", "0905.113.007", 6}
            };

            String tSql = """
                INSERT INTO interagency_dispatches (
                    victim_node_id, agency_type, agency_name, payload_sent,
                    special_needs, status, dispatched_at, responder_unit,
                    responder_leader, contact_phone, eta_minutes, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
            try (java.sql.PreparedStatement ps = conn.prepareStatement(tSql)) {
                for (Object[] row : ticketsData) {
                    ps.setString(1, (String) row[0]);
                    ps.setString(2, (String) row[1]);
                    ps.setString(3, (String) row[2]);
                    ps.setString(4, (String) row[3]); // payload_sent / vehicle
                    ps.setString(5, (String) row[4]); // special_needs
                    ps.setString(6, (String) row[5]); // status
                    ps.setLong(7, now - 600000);
                    ps.setString(8, (String) row[6]); // responder_unit
                    ps.setString(9, (String) row[7]); // responder_leader
                    ps.setString(10, (String) row[8]); // contact_phone
                    ps.setInt(11, (Integer) row[9]);  // eta_minutes
                    ps.setLong(12, now);
                    ps.addBatch();
                }
                ps.executeBatch();
            }

            // C. Insert Realistic VHF CH16 Tactical Radio Logs
            String rSql = """
                INSERT INTO tactical_radio_logs (timestamp, channel, sender, recipient, message, urgency)
                VALUES (?, ?, ?, ?, ?, ?)
            """;
            try (java.sql.PreparedStatement ps = conn.prepareStatement(rSql)) {
                Object[][] radioMessages = {
                    {"CH 16 - 156.800 MHz", "115-DANANG-01", "TRẠM CHỈ HUY", "Xe 115-01 đang rẽ vào đường tránh Túy Loan, nước ngập bánh xe, đang phối hợp ca-nô Hòa Vang đón cụ bà tai biến!", "CRITICAL", now - 400000},
                    {"CH 16 - 156.800 MHz", "CANO-114-SH02", "TRẠM CHỈ HUY", "Đã phát hiện phao cứu sinh 3 ngư dân cách chân Cầu Thuận Phước 300m về phía đông, đang tiếp cận vớt nạn nhân an toàn!", "CRITICAL", now - 320000},
                    {"CH 16 - 156.800 MHz", "CNCH-114-HC", "TRẠM CHỈ HUY", "Đội 114 đã đưa được kích thủy lực vào hiện trường Như Nguyệt, BS kíp C đang tiêm giảm đau và cố định nẹp cổ!", "CRITICAL", now - 250000},
                    {"CH 16 - 156.800 MHz", "115-SANNHI-04", "TRẠM CHỈ HUY", "BS Mai báo cáo: Đã tiếp cận sản phụ tại số nhà 142 Nguyễn Tri Phương bằng xuồng dã chiến, sản phụ an toàn, chuẩn bị đón bé gái!", "HIGH", now - 180000},
                    {"CH 16 - 156.800 MHz", "HOANMY-TRAUMA", "TRẠM CHỈ HUY", "Đã tiếp nhận bệnh nhân Michael Harrison (Sơn Trà) vào phòng mổ Hybrid, 2 bịch máu O- đã sẵn sàng!", "HIGH", now - 110000},
                    {"CH 16 - 156.800 MHz", "CA-NGUHANHSON", "TRẠM CHỈ HUY", "Tổ công tác 113 đã tiếp cận được khu vực K20 sông Đô Tỏa, đang chuyển 2 cháu nhỏ lên ca-nô!", "HIGH", now - 40000}
                };
                for (Object[] rm : radioMessages) {
                    ps.setLong(1, (Long) rm[5]);
                    ps.setString(2, (String) rm[0]);
                    ps.setString(3, (String) rm[1]);
                    ps.setString(4, (String) rm[2]);
                    ps.setString(5, (String) rm[3]);
                    ps.setString(6, (String) rm[4]);
                    ps.addBatch();
                }
                ps.executeBatch();
            }

            // D. Deduct 1 bed from Hoan My Hospital for the resolved handover
            try (java.sql.PreparedStatement ps = conn.prepareStatement("UPDATE emergency_facilities SET available_capacity = MAX(0, available_capacity - 1) WHERE facility_id = 'HOSP_06'")) {
                ps.executeUpdate();
            }

            LOGGER.info("[SIMULATOR] Đã nạp thành công 7 tình huống cứu hộ thực tế TP. Đà Nẵng, 7 phiếu liên ngành & 6 đàm thoại VHF CH16!");
        } catch (java.sql.SQLException e) {
            LOGGER.error("[SIMULATOR] Lỗi nạp 7 tình huống thực tế: {}", e.getMessage(), e);
        }

        return nodeIds;
    }

    /**
     * Gửi packet vào localhost:meshPort qua TCP.
     */
    private void sendToLocalMesh(MeshPacket packet, int meshPort) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("localhost", meshPort), 3000);
            PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);
            writer.println(packet.toJson(GSON));
        } catch (Exception e) {
            LOGGER.error("[SIMULATOR] Lỗi bơm gói tin: {}", e.getMessage());
        }
    }

    private void sleepQuietly(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
}
