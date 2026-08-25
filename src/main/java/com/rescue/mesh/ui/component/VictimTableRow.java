package com.rescue.mesh.ui.component;

import com.rescue.mesh.model.MeshPacket;
import javafx.beans.property.SimpleStringProperty;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Model cho TableView hiển thị danh sách nạn nhân trên Dashboard.
 *
 * Design Pattern:
 *   - Adapter Pattern: chuyển MeshPacket (network model) sang dạng
 *     phù hợp cho JavaFX TableView (UI model)
 *   - JavaFX Data Binding: dùng SimpleStringProperty để PropertyValueFactory
 *     tự động bind data vào cột TableView
 *
 * Quy ước đặt tên (JavaFX PropertyValueFactory yêu cầu):
 *   Cho cột "sourceNode":
 *     - Getter:   getSourceNode()        → trả về String
 *     - Property: sourceNodeProperty()   → trả về StringProperty
 *
 * Thread Safety:
 *   - Object này được tạo trong background thread (khi nhận SOS)
 *     nhưng add vào ObservableList qua Platform.runLater()
 *   - Sau khi add, chỉ JavaFX Application Thread đọc → an toàn
 */
public class VictimTableRow {

    /** Số thứ tự trong bảng */
    private final SimpleStringProperty stt;

    /** ID node nguồn gửi SOS */
    private final SimpleStringProperty sourceNode;

    /** Loại tình huống (MEDICAL / FLOOD_TRAPPED / LANDSLIDE) */
    private final SimpleStringProperty alertType;

    /** Mức độ nguy hiểm (CRITICAL / HIGH / MEDIUM) */
    private final SimpleStringProperty severity;

    /** Số nạn nhân cần cứu */
    private final SimpleStringProperty victimCount;

    /** Tọa độ GPS (latitude, longitude) */
    private final SimpleStringProperty coordinates;

    /** Thời điểm nhận được SOS */
    private final SimpleStringProperty time;

    /** Tin nhắn mô tả tình huống */
    private final SimpleStringProperty message;

    /** Lịch sử đường đi của gói tin qua các relay node */
    private final SimpleStringProperty routeHistory;

    /** Trạng thái xử lý (PENDING / DISPATCHED / RESOLVED) */
    private final SimpleStringProperty status;

    /** Gói tin gốc — lưu để tham chiếu khi gửi DISPATCH_CMD */
    private MeshPacket originalPacket;

    /** Format thời gian hiển thị trên bảng */
    private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("HH:mm:ss");

    // =========================================================
    // CONSTRUCTORS
    // =========================================================

    /**
     * Constructor đầy đủ — tạo từng field.
     */
    public VictimTableRow(String stt, String sourceNode, String alertType,
                          String severity, String victimCount, String coordinates,
                          String time, String message, String routeHistory,
                          String status, MeshPacket originalPacket) {
        this.stt          = new SimpleStringProperty(stt);
        this.sourceNode   = new SimpleStringProperty(sourceNode);
        this.alertType    = new SimpleStringProperty(alertType);
        this.severity     = new SimpleStringProperty(severity);
        this.victimCount  = new SimpleStringProperty(victimCount);
        this.coordinates  = new SimpleStringProperty(coordinates);
        this.time         = new SimpleStringProperty(time);
        this.message      = new SimpleStringProperty(message);
        this.routeHistory = new SimpleStringProperty(routeHistory);
        this.status       = new SimpleStringProperty(status);
        this.originalPacket = originalPacket;
    }

    // =========================================================
    // FACTORY METHOD
    // =========================================================

    /**
     * Tạo VictimTableRow từ MeshPacket — Adapter Pattern.
     * Trích xuất thông tin từ gói tin SOS và format cho TableView.
     *
     * @param packet Gói tin SOS đã nhận
     * @param index  Số thứ tự (bắt đầu từ 1)
     * @return VictimTableRow sẵn sàng add vào TableView
     */
    public static VictimTableRow fromMeshPacket(MeshPacket packet, int index) {
        String sourceNode   = packet.getSourceNodeId() != null ? packet.getSourceNodeId() : "N/A";
        String alertType    = "N/A";
        String severity     = "N/A";
        String victimCount  = "0";
        String coordinates  = "N/A";
        String message      = "N/A";

        if (packet.getPayload() != null) {
            MeshPacket.Payload payload = packet.getPayload();

            if (payload.getAlertType() != null) {
                alertType = payload.getAlertType();
            }
            if (payload.getSeverity() != null) {
                severity = payload.getSeverity();
            }
            victimCount = String.valueOf(payload.getVictimCount());

            if (payload.getLocation() != null) {
                coordinates = String.format("%.4f, %.4f",
                        payload.getLocation().getLatitude(),
                        payload.getLocation().getLongitude());
            }
            if (payload.getMessage() != null) {
                message = payload.getMessage();
            }
        }

        String time = TIME_FORMAT.format(new Date(packet.getTimestamp()));

        String routeHistory = packet.getRouteHistory() != null
                ? packet.getRouteHistory().toString()
                : "[]";

        return new VictimTableRow(
                String.valueOf(index),
                sourceNode,
                alertType,
                severity,
                victimCount,
                coordinates,
                time,
                message,
                routeHistory,
                "PENDING",
                packet
        );
    }

    // =========================================================
    // GETTERS — cho PropertyValueFactory
    // =========================================================

    public String getStt()          { return stt.get(); }
    public String getSourceNode()   { return sourceNode.get(); }
    public String getAlertType()    { return alertType.get(); }
    public String getSeverity()     { return severity.get(); }
    public String getVictimCount()  { return victimCount.get(); }
    public String getCoordinates()  { return coordinates.get(); }
    public String getTime()         { return time.get(); }
    public String getMessage()      { return message.get(); }
    public String getRouteHistory() { return routeHistory.get(); }
    public String getStatus()       { return status.get(); }
    public MeshPacket getOriginalPacket() { return originalPacket; }

    // =========================================================
    // PROPERTY GETTERS — cho JavaFX Data Binding
    // =========================================================

    public SimpleStringProperty sttProperty()          { return stt; }
    public SimpleStringProperty sourceNodeProperty()   { return sourceNode; }
    public SimpleStringProperty alertTypeProperty()    { return alertType; }
    public SimpleStringProperty severityProperty()     { return severity; }
    public SimpleStringProperty victimCountProperty()  { return victimCount; }
    public SimpleStringProperty coordinatesProperty()  { return coordinates; }
    public SimpleStringProperty timeProperty()         { return time; }
    public SimpleStringProperty messageProperty()      { return message; }
    public SimpleStringProperty routeHistoryProperty() { return routeHistory; }
    public SimpleStringProperty statusProperty()       { return status; }

    // =========================================================
    // SETTERS
    // =========================================================

    public void setStt(String stt)                   { this.stt.set(stt); }
    public void setSourceNode(String sourceNode)     { this.sourceNode.set(sourceNode); }
    public void setAlertType(String alertType)       { this.alertType.set(alertType); }
    public void setSeverity(String severity)         { this.severity.set(severity); }
    public void setVictimCount(String victimCount)   { this.victimCount.set(victimCount); }
    public void setCoordinates(String coordinates)   { this.coordinates.set(coordinates); }
    public void setTime(String time)                 { this.time.set(time); }
    public void setMessage(String message)           { this.message.set(message); }
    public void setRouteHistory(String routeHistory) { this.routeHistory.set(routeHistory); }
    public void setStatus(String status)             { this.status.set(status); }
    public void setOriginalPacket(MeshPacket packet) { this.originalPacket = packet; }

    // =========================================================
    // UTILITY
    // =========================================================

    @Override
    public String toString() {
        return "VictimTableRow{"
                + "stt=" + stt.get()
                + ", source=" + sourceNode.get()
                + ", alert=" + alertType.get()
                + ", severity=" + severity.get()
                + ", victims=" + victimCount.get()
                + '}';
    }
}
