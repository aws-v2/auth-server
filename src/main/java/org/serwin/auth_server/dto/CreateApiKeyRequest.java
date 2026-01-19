package org.serwin.auth_server.dto;

import lombok.Data;

public class CreateApiKeyRequest {
    private String name;
    private String description;
    private String[] allowedActions;
    private String[] allowedResources;
    private String expiresAt; // ISO-8601 format

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

    public String getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(String expiresAt) {
        this.expiresAt = expiresAt;
    }
}
