package org.serwin.auth_server.dto;

import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserDto {
    private UUID id;
    private String email;
    private boolean mfaEnabled;
    private boolean emailVerified;
    private LocalDateTime createdAt;

 
    private String verificationToken;

    

 
    public UserDto(UUID id, String email, boolean mfaEnabled, boolean emailVerified, LocalDateTime createdAt) {
        this.id = id;
        this.email = email;
        this.mfaEnabled = mfaEnabled;
        this.emailVerified = emailVerified;
        this.createdAt = createdAt;
    }

 

}