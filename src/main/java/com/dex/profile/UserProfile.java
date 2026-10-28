package com.dex.profile;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

/**
 * Encapsulates the local user's personal profile settings for DEX / X.
 * Keeps user identity isolated per installation and avoids hardcoded defaults.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserProfile {

    @JsonProperty("profileId")
    private String profileId;

    @JsonProperty("userName")
    private String userName;

    @JsonProperty("setupCompleted")
    private boolean setupCompleted;

    @JsonProperty("createdAt")
    private long createdAt;

    @JsonProperty("updatedAt")
    private long updatedAt;

    public UserProfile() {
        this.profileId = UUID.randomUUID().toString();
        this.userName = "";
        this.setupCompleted = false;
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = System.currentTimeMillis();
    }

    public UserProfile(String userName, boolean setupCompleted) {
        this.profileId = UUID.randomUUID().toString();
        this.userName = userName != null ? userName.trim() : "";
        this.setupCompleted = setupCompleted;
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = System.currentTimeMillis();
    }

    public String getProfileId() {
        return profileId;
    }

    public void setProfileId(String profileId) {
        this.profileId = profileId;
    }

    public String getUserName() {
        return userName != null ? userName : "";
    }

    public void setUserName(String userName) {
        this.userName = userName != null ? userName.trim() : "";
        this.updatedAt = System.currentTimeMillis();
    }

    public boolean isSetupCompleted() {
        return setupCompleted;
    }

    public void setSetupCompleted(boolean setupCompleted) {
        this.setupCompleted = setupCompleted;
        this.updatedAt = System.currentTimeMillis();
    }

    public boolean hasCustomName() {
        return userName != null && !userName.isBlank();
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }
}
