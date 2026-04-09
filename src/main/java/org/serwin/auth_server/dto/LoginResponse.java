package org.serwin.auth_server.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {
    private String token;
    private String email;
    private boolean mfaEnabled;
    private boolean mfaRequired;
    private String qrCode; // For MFA enabling
    private String message;
}
