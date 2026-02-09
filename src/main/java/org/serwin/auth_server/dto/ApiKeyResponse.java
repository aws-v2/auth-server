package org.serwin.auth_server.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
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
}
