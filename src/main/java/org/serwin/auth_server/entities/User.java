package org.serwin.auth_server.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

import org.serwin.auth_server.enums.Role;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

@Column(unique = true, nullable = false)
@NotBlank
@Email(message = "Invalid email format")
private String email;

    @Column(nullable = false)
    private String password;

    @Column(name = "mfa_enabled")
    private boolean mfaEnabled = false;

    @Column(name = "mfa_secret")
    private String mfaSecret;

    @Column(name = "email_verified")
    private boolean emailVerified = false;

    @Column(name = "role")
    private Role role = Role.USER;

    @Column(name = "verification_token")
    private String verificationToken;

    @Column(name = "enabled")
    private boolean enabled = true;

    @Column(name = "created_at")
    private java.time.LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = java.time.LocalDateTime.now();
    }
}
