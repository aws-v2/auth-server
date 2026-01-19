package org.serwin.auth_server.dto;

import lombok.Builder;
import lombok.Data;

public class ApiKeyResponse {
    private String id;
    private String accessKeyId;
    private String secretAccessKey; // Only on creation
    private String name;
    private String description;
    private String[] allowedActions;
    private String[] allowedResources;
    private boolean enabled;
    private String createdAt;
    private String expiresAt;
    private String lastUsedAt;
    private String warning; // "Save this secret - cannot be retrieved again"

    public ApiKeyResponse() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getAccessKeyId() {
        return accessKeyId;
    }

    public void setAccessKeyId(String accessKeyId) {
        this.accessKeyId = accessKeyId;
    }

    public String getSecretAccessKey() {
        return secretAccessKey;
    }

    public void setSecretAccessKey(String secretAccessKey) {
        this.secretAccessKey = secretAccessKey;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String[] getAllowedActions() {
        return allowedActions;
    }

    public void setAllowedActions(String[] allowedActions) {
        this.allowedActions = allowedActions;
    }

    public String[] getAllowedResources() {
        return allowedResources;
    }

    public void setAllowedResources(String[] allowedResources) {
        this.allowedResources = allowedResources;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(String expiresAt) {
        this.expiresAt = expiresAt;
    }

    public String getLastUsedAt() {
        return lastUsedAt;
    }

    public void setLastUsedAt(String lastUsedAt) {
        this.lastUsedAt = lastUsedAt;
    }

    public String getWarning() {
        return warning;
    }

    public void setWarning(String warning) {
        this.warning = warning;
    }
}
