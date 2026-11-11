package com.rescue.mesh.model;

/**
 * Entity ánh xạ bảng `interagency_dispatches` — nhật ký chuyển tiếp liên ngành.
 * Theo FINAL spec §5.1.
 */
public class InterAgencyDispatch {

    private long id;
    private String victimNodeId;
    private String agencyType;   // EMS_115, RESCUE_114, POLICE_113
    private String agencyName;
    private String payloadSent;
    private long dispatchedAt;
    private String status;       // SENT, ACKNOWLEDGED, FAILED

    public InterAgencyDispatch() {
        this.status = "SENT";
        this.dispatchedAt = System.currentTimeMillis();
    }

    public InterAgencyDispatch(String victimNodeId, String agencyType, String agencyName, String payloadSent) {
        this();
        this.victimNodeId = victimNodeId;
        this.agencyType = agencyType;
        this.agencyName = agencyName;
        this.payloadSent = payloadSent;
    }

    // =========================================================
    // GETTERS & SETTERS
    // =========================================================

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getVictimNodeId() { return victimNodeId; }
    public void setVictimNodeId(String victimNodeId) { this.victimNodeId = victimNodeId; }

    public String getAgencyType() { return agencyType; }
    public void setAgencyType(String agencyType) { this.agencyType = agencyType; }

    public String getAgencyName() { return agencyName; }
    public void setAgencyName(String agencyName) { this.agencyName = agencyName; }

    public String getPayloadSent() { return payloadSent; }
    public void setPayloadSent(String payloadSent) { this.payloadSent = payloadSent; }

    public long getDispatchedAt() { return dispatchedAt; }
    public void setDispatchedAt(long dispatchedAt) { this.dispatchedAt = dispatchedAt; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    @Override
    public String toString() {
        return "InterAgencyDispatch{id=" + id + ", victim='" + victimNodeId
                + "', agency='" + agencyType + "', status='" + status + "'}";
    }
}
