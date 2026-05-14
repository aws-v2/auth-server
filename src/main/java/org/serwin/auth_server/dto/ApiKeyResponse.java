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

    private String apiKey;
 
    private String name;

    private boolean enabled;

    private String createdAt;

    private String userId;

    private String warning;
}