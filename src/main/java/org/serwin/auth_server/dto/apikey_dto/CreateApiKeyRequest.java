package org.serwin.auth_server.dto.apikey_dto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateApiKeyRequest {
    private String name;
    private String description;
    private String[] allowedActions;
    private String[] allowedResources;
    private String expiresAt; // ISO-8601 format
}
