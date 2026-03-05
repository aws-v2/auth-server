package org.serwin.auth_server.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantCreatedEvent {
    private UUID event_id;
    private String event_type;
    private UUID tenant_id;
    private String tenant_name;
    private String created_at; // RFC3339 timestamp
}
