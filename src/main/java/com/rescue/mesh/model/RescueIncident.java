package com.rescue.mesh.model;

/**
 * Entity quản trị vòng đời cứu nạn — ánh xạ 3 trạng thái phân tán (Đề 54).
 *
 * Cơ sở lý thuyết:
 *   - REQ (Request) = PENDING: Nạn nhân phát SOS, hệ thống tiếp nhận.
 *   - ACQ (Acquire) = DISPATCHED: Trạm trưởng phân bổ lực lượng cứu hộ.
 *   - REL (Release) = RESOLVED: Cứu nạn hoàn tất, giải phóng tài nguyên.
 *
 * Class này độc lập với MeshPacket — chỉ chứa thông tin nghiệp vụ cứu nạn.
 */
public class RescueIncident {

    /** Trạng thái vòng đời cứu nạn theo Đề 54 */
    public enum RescueStatus {
        PENDING,     // REQ — Mới nhận SOS, chưa xử lý
        DISPATCHED,  // ACQ — Đang điều phối lực lượng cứu hộ
        RESOLVED     // REL — Đã cứu nạn xong, giải phóng tài nguyên
    }

    private String nodeId;
    private String fullName;
    private String phoneNumber;
    private String bloodType;
    private String medicalNotes;
    private int victimCount;
    private String alertType;
    private String severity;
    private double latitude;
    private double longitude;
    private RescueStatus status;
    private long firstSeenTimestamp;
    private long lastUpdatedTimestamp;

    // =========================================================
    // CONSTRUCTORS
    // =========================================================

    public RescueIncident() {
        this.status = RescueStatus.PENDING;
        this.firstSeenTimestamp = System.currentTimeMillis();
        this.lastUpdatedTimestamp = this.firstSeenTimestamp;
    }

    /**
     * Tạo RescueIncident từ MeshPacket SOS.
     */
    public static RescueIncident fromSosPacket(MeshPacket packet) {
        RescueIncident incident = new RescueIncident();
        incident.nodeId = packet.getSourceNodeId();

        if (packet.getPayload() != null) {
            MeshPacket.Payload p = packet.getPayload();
            incident.fullName = p.getSenderName();
            incident.victimCount = p.getVictimCount();
            incident.alertType = p.getAlertType();
            incident.severity = p.getSeverity();
            incident.phoneNumber = p.getPhoneNumber() != null ? p.getPhoneNumber() : "0900000000";
            incident.bloodType = p.getBloodType() != null ? p.getBloodType() : "O+";
            incident.medicalNotes = p.getMedicalNotes() != null ? p.getMedicalNotes() : p.getMessage();

            if (p.getLocation() != null) {
                incident.latitude = p.getLocation().getLatitude();
                incident.longitude = p.getLocation().getLongitude();
            }
        }

        incident.firstSeenTimestamp = packet.getTimestamp();
        incident.lastUpdatedTimestamp = System.currentTimeMillis();
        return incident;
    }

    // =========================================================
    // STATE TRANSITIONS (Đề 54)
    // =========================================================

    /**
     * Chuyển sang trạng thái DISPATCHED (ACQ).
     * Chỉ hợp lệ khi đang ở trạng thái PENDING.
     */
    public boolean dispatch() {
        if (status == RescueStatus.PENDING) {
            status = RescueStatus.DISPATCHED;
            lastUpdatedTimestamp = System.currentTimeMillis();
            return true;
        }
        return false;
    }

    /**
     * Chuyển sang trạng thái RESOLVED (REL).
     * Hợp lệ khi đang ở PENDING hoặc DISPATCHED.
     */
    public boolean resolve() {
        if (status == RescueStatus.PENDING || status == RescueStatus.DISPATCHED) {
            status = RescueStatus.RESOLVED;
            lastUpdatedTimestamp = System.currentTimeMillis();
            return true;
        }
        return false;
    }

    // =========================================================
    // GETTERS & SETTERS
    // =========================================================

    public String getNodeId() { return nodeId; }
    public void setNodeId(String nodeId) { this.nodeId = nodeId; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }

    public String getBloodType() { return bloodType; }
    public void setBloodType(String bloodType) { this.bloodType = bloodType; }

    public String getMedicalNotes() { return medicalNotes; }
    public void setMedicalNotes(String medicalNotes) { this.medicalNotes = medicalNotes; }

    public int getVictimCount() { return victimCount; }
    public void setVictimCount(int victimCount) { this.victimCount = victimCount; }

    public String getAlertType() { return alertType; }
    public void setAlertType(String alertType) { this.alertType = alertType; }

    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }

    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }

    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }

    public RescueStatus getStatus() { return status; }
    public void setStatus(RescueStatus status) { this.status = status; }

    public long getFirstSeenTimestamp() { return firstSeenTimestamp; }
    public void setFirstSeenTimestamp(long ts) { this.firstSeenTimestamp = ts; }

    public long getLastUpdatedTimestamp() { return lastUpdatedTimestamp; }
    public void setLastUpdatedTimestamp(long ts) { this.lastUpdatedTimestamp = ts; }

    @Override
    public String toString() {
        return "RescueIncident{node='" + nodeId + "', status=" + status
                + ", severity='" + severity + "', victims=" + victimCount + "}";
    }
}
