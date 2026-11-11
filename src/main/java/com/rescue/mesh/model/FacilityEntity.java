package com.rescue.mesh.model;

/**
 * Entity ánh xạ bảng `emergency_facilities` — cơ sở cấp cứu & an ninh dã chiến.
 * Theo FINAL spec §5.1 — field khớp 1:1 cột bảng.
 */
public class FacilityEntity {

    private String facilityId;
    private String facilityName;
    private String facilityType;   // HOSPITAL, FIRE_STATION, POLICE_POST
    private double latitude;
    private double longitude;
    private String contactPhone;
    private int availableCapacity;

    public FacilityEntity() {
        this.availableCapacity = 10;
    }

    public FacilityEntity(String facilityId, String facilityName, String facilityType,
                           double latitude, double longitude, String contactPhone, int availableCapacity) {
        this.facilityId = facilityId;
        this.facilityName = facilityName;
        this.facilityType = facilityType;
        this.latitude = latitude;
        this.longitude = longitude;
        this.contactPhone = contactPhone;
        this.availableCapacity = availableCapacity;
    }

    // =========================================================
    // GETTERS & SETTERS
    // =========================================================

    public String getFacilityId() { return facilityId; }
    public void setFacilityId(String facilityId) { this.facilityId = facilityId; }

    public String getFacilityName() { return facilityName; }
    public void setFacilityName(String facilityName) { this.facilityName = facilityName; }

    public String getFacilityType() { return facilityType; }
    public void setFacilityType(String facilityType) { this.facilityType = facilityType; }

    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }

    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }

    public String getContactPhone() { return contactPhone; }
    public void setContactPhone(String contactPhone) { this.contactPhone = contactPhone; }

    public int getAvailableCapacity() { return availableCapacity; }
    public void setAvailableCapacity(int availableCapacity) { this.availableCapacity = availableCapacity; }

    @Override
    public String toString() {
        return "FacilityEntity{id='" + facilityId + "', name='" + facilityName
                + "', type='" + facilityType + "'}";
    }
}
