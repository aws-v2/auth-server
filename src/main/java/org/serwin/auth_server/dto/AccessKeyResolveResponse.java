package org.serwin.auth_server.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccessKeyResolveResponse {
    private String userId;
    private String secretKeyHash;
    private boolean enabled;
}
