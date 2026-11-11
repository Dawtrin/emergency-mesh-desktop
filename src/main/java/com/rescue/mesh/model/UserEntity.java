package com.rescue.mesh.model;

/**
 * Entity đại diện cho Cán bộ trực ban / Người dùng hệ thống Web C2.
 * Vai trò (RBAC): ADMIN, COMMANDER, DISPATCHER, OBSERVER.
 */
public class UserEntity {

    private Long id;
    private String username;
    private String displayName;
    private String passwordHash;
    private String role; // ADMIN, COMMANDER, DISPATCHER, OBSERVER
    private boolean active;
    private int failedLogins;
    private Long lockedUntil;
    private boolean mustChangePassword;
    private Long lastLoginAt;
    private long createdAt;

    public UserEntity() {
        this.active = true;
        this.createdAt = System.currentTimeMillis();
    }

    public UserEntity(String username, String displayName, String passwordHash, String role) {
        this.username = username;
        this.displayName = displayName;
        this.passwordHash = passwordHash;
        this.role = role != null ? role.toUpperCase() : "OBSERVER";
        this.active = true;
        this.createdAt = System.currentTimeMillis();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role != null ? role.toUpperCase() : "OBSERVER"; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public int getFailedLogins() { return failedLogins; }
    public void setFailedLogins(int failedLogins) { this.failedLogins = failedLogins; }

    public Long getLockedUntil() { return lockedUntil; }
    public void setLockedUntil(Long lockedUntil) { this.lockedUntil = lockedUntil; }

    public boolean isMustChangePassword() { return mustChangePassword; }
    public void setMustChangePassword(boolean mustChangePassword) { this.mustChangePassword = mustChangePassword; }

    public Long getLastLoginAt() { return lastLoginAt; }
    public void setLastLoginAt(Long lastLoginAt) { this.lastLoginAt = lastLoginAt; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public boolean isLocked() {
        return lockedUntil != null && lockedUntil > System.currentTimeMillis();
    }
}
