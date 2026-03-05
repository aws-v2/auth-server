package org.serwin.auth_server.service;

import com.google.zxing.WriterException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.serwin.auth_server.dto.*;
import org.serwin.auth_server.entities.PasswordResetToken;
import org.serwin.auth_server.entities.User;
import org.serwin.auth_server.repository.PasswordResetTokenRepository;
import org.serwin.auth_server.repository.UserRepository;
import org.serwin.auth_server.util.JwtUtil;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final MfaService mfaService;
    private final EmailService emailService;
    private final PasswordResetTokenRepository resetTokenRepository;
    private final NatsService natsService;

    public LoginResponse register(RegisterRequest request) {
        log.debug("Processing registration for email: {}", request.getEmail());

        if (!Objects.equals(request.getPassword(), request.getConfirmPassword())) {
            log.warn("Password mismatch during registration for email: {}", request.getEmail());
            throw new RuntimeException("Password Mismatch");
        }

        if (userRepository.existsByEmail(request.getEmail())) {
            log.warn("Registration attempt with existing email: {}", request.getEmail());
            throw new RuntimeException("Email already exists");
        }

        // Generate verification token
        String verificationToken = UUID.randomUUID().toString();
        log.debug("Generated verification token for email: {}", request.getEmail());

        User user = new User();
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setMfaEnabled(false);
        user.setEmailVerified(false);
        user.setVerificationToken(verificationToken);

        userRepository.save(user);
        log.info("User (Tenant) created successfully with email: {}", request.getEmail());

        // Publish Registration Events to NATS
        try {
            // 1. Publish TenantCreated event (formal)
            TenantCreatedEvent tenantEvent = TenantCreatedEvent.builder()
                    .event_id(UUID.randomUUID())
                    .event_type("tenant.created")
                    .tenant_id(user.getId())
                    .tenant_name(user.getEmail())
                    .created_at(java.time.OffsetDateTime.now().toString())
                    .build();
 
            // 2. Publish UserRegistered event (standard/example)
            natsService.publish("user", "registered", Map.of(
                    "email", user.getEmail(),
                    "userId", user.getId().toString(),
                    "mfaEnabled", false,
                    "registrationType", "Standard",
                    "timestamp", java.time.OffsetDateTime.now().toString()));
            log.info("Published user.registered event for user: {}", user.getEmail());
        } catch (Exception e) {
            log.error("Failed to publish registration events to NATS: {}", e.getMessage());
        }

        // Send verification email
        try {
            emailService.sendVerificationEmail(user.getEmail(), verificationToken);
            log.debug("Verification email sent to: {}", user.getEmail());
        } catch (Exception e) {
            log.error("Failed to send verification email to: {} - Error: {}", user.getEmail(), e.getMessage(), e);
        }

        String token = jwtUtil.generateToken(user.getEmail(), user.getId());

        LoginResponse response = new LoginResponse();
        response.setToken(token);
        response.setEmail(user.getEmail());
        response.setMfaEnabled(false);
        response.setMessage("Registration successful. Please check your email to verify your account.");
        return response;
    }

    public LoginResponse login(LoginRequest request) {
        log.debug("Processing login for email: {}", request.getEmail());

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> {
                    log.warn("Login attempt with non-existent email: {}", request.getEmail());
                    return new RuntimeException("Invalid credentials");
                });

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            log.warn("Invalid password for email: {}", request.getEmail());
            throw new RuntimeException("Invalid credentials");
        }

        if (user.isMfaEnabled()) {
            log.debug("MFA required for email: {}", request.getEmail());
            LoginResponse response = new LoginResponse();
            response.setEmail(user.getEmail());
            response.setMfaEnabled(true);
            response.setMfaRequired(true);
            response.setMessage("MFA verification required");
            return response;
        }

        String token = jwtUtil.generateToken(user.getEmail(), user.getId());
        log.info("Login successful for email: {}", request.getEmail());

        LoginResponse response = new LoginResponse();
        response.setToken(token);
        response.setEmail(user.getEmail());
        response.setMfaEnabled(false);
        response.setMessage("Login successful");
        return response;
    }

    public Map<String, Object> enableMfa(String email) throws WriterException, IOException {
        log.debug("Enabling MFA for email: {}", email);

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> {
                    log.error("User not found while enabling MFA: {}", email);
                    return new RuntimeException("User not found");
                });

        String secret = mfaService.generateSecretKey();
        user.setMfaSecret(secret);
        user.setMfaEnabled(true);
        userRepository.save(user);
        log.info("MFA enabled for user: {}", email);

        String qrCode = mfaService.generateQRCode(email, secret, "AWS-Clone IAM");

        Map<String, Object> response = new HashMap<>();
        response.put("secret", secret);
        response.put("qrCode", qrCode);
        response.put("message", "MFA enabled successfully. Scan the QR code with your authenticator app.");

        return response;
    }

    public LoginResponse verifyMfa(MfaVerifyRequest request) {
        log.debug("Verifying MFA for request: {}",
                request.getEmail() != null ? request.getEmail() : request.getUserId());

        User user;

        // Support both email and userId
        if (request.getEmail() != null && !request.getEmail().isEmpty()) {
            user = userRepository.findByEmail(request.getEmail())
                    .orElseThrow(() -> {
                        log.warn("User not found during MFA verification: {}", request.getEmail());
                        return new RuntimeException("User not found");
                    });
        } else if (request.getUserId() != null && !request.getUserId().isEmpty()) {
            user = userRepository.findById(UUID.fromString(request.getUserId()))
                    .orElseThrow(() -> {
                        log.warn("User not found during MFA verification with userId: {}", request.getUserId());
                        return new RuntimeException("User not found");
                    });
        } else {
            log.error("MFA verification attempted without email or userId");
            throw new RuntimeException("Email or userId is required");
        }

        if (!user.isMfaEnabled()) {
            log.warn("MFA verification attempted for user without MFA enabled: {}", user.getEmail());
            throw new RuntimeException("MFA is not enabled for this user");
        }

        if (!mfaService.verifyCode(user.getMfaSecret(), request.getCode())) {
            log.warn("Invalid MFA code for user: {}", user.getEmail());
            throw new RuntimeException("Invalid MFA code");
        }

        String token = jwtUtil.generateToken(user.getEmail(), user.getId());
        log.info("MFA verification successful for user: {}", user.getEmail());

        LoginResponse response = new LoginResponse();
        response.setToken(token);
        response.setEmail(user.getEmail());
        response.setMfaEnabled(true);
        response.setMessage("MFA verification successful");
        return response;
    }

    public Map<String, String> disableMfa(String email, String code) {
        log.debug("Disabling MFA for email: {}", email);

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> {
                    log.error("User not found while disabling MFA: {}", email);
                    return new RuntimeException("User not found");
                });

        if (!user.isMfaEnabled()) {
            log.warn("MFA disable attempted for user without MFA enabled: {}", email);
            throw new RuntimeException("MFA is not enabled for this user");
        }

        if (!mfaService.verifyCode(user.getMfaSecret(), code)) {
            log.warn("Invalid MFA code during disable attempt for user: {}", email);
            throw new RuntimeException("Invalid MFA code");
        }

        user.setMfaEnabled(false);
        user.setMfaSecret(null);
        userRepository.save(user);
        log.info("MFA disabled for user: {}", email);

        return Map.of("message", "MFA disabled successfully");
    }

    @Transactional
    public Map<String, String> forgotPassword(ForgotPasswordRequest request) {
        log.debug("Processing forgot password request for email: {}", request.getEmail());

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> {
                    log.warn("Forgot password request for non-existent user: {}", request.getEmail());
                    return new RuntimeException("User not found");
                });

        // Delete any existing reset tokens for this user
        resetTokenRepository.deleteByUser(user);
        log.debug("Deleted existing reset tokens for user: {}", user.getEmail());

        // Generate a new reset token
        String resetToken = UUID.randomUUID().toString();

        PasswordResetToken passwordResetToken = new PasswordResetToken();
        passwordResetToken.setToken(resetToken);
        passwordResetToken.setUser(user);
        passwordResetToken.setUsed(false);

        resetTokenRepository.save(passwordResetToken);
        log.debug("Created new password reset token for user: {}", user.getEmail());

        // Send the email
        try {
            emailService.sendPasswordResetEmail(user.getEmail(), resetToken);
            log.info("Password reset email sent to: {}", user.getEmail());
            return Map.of(
                    "message", "Password reset email sent successfully",
                    "email", user.getEmail());
        } catch (Exception e) {
            log.error("Failed to send password reset email to: {} - Error: {}", user.getEmail(), e.getMessage(), e);
            throw new RuntimeException("Failed to send password reset email: " + e.getMessage());
        }
    }

    @Transactional
    public Map<String, String> resetPassword(ResetPasswordRequest request) {
        log.debug("Processing password reset with token");

        if (!Objects.equals(request.getNewPassword(), request.getConfirmPassword())) {
            log.warn("Password mismatch during reset");
            throw new RuntimeException("Passwords do not match");
        }

        PasswordResetToken resetToken = resetTokenRepository.findByToken(request.getToken())
                .orElseThrow(() -> {
                    log.warn("Invalid or expired reset token used");
                    return new RuntimeException("Invalid or expired reset token");
                });

        if (resetToken.isUsed()) {
            log.warn("Attempt to use already used reset token");
            throw new RuntimeException("Reset token has already been used");
        }

        if (resetToken.isExpired()) {
            log.warn("Attempt to use expired reset token");
            throw new RuntimeException("Reset token has expired");
        }

        User user = resetToken.getUser();
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        resetToken.setUsed(true);
        resetTokenRepository.save(resetToken);
        log.info("Password reset successfully for user: {}", user.getEmail());

        return Map.of("message", "Password reset successfully");
    }

    public Map<String, String> verifyEmail(String token) {
        log.debug("Processing email verification with token");

        User user = userRepository.findAll().stream()
                .filter(u -> token.equals(u.getVerificationToken()))
                .findFirst()
                .orElseThrow(() -> {
                    log.warn("Invalid verification token used");
                    return new RuntimeException("Invalid verification token");
                });

        if (user.isEmailVerified()) {
            log.warn("Email verification attempted for already verified user: {}", user.getEmail());
            throw new RuntimeException("Email already verified");
        }

        user.setEmailVerified(true);
        user.setVerificationToken(null);
        userRepository.save(user);
        log.info("Email verified successfully for user: {}", user.getEmail());

        return Map.of("message", "Email verified successfully");
    }

    public Map<String, String> resendVerificationEmail(String email) {
        log.debug("Resending verification email for: {}", email);

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> {
                    log.warn("Resend verification email requested for non-existent user: {}", email);
                    return new RuntimeException("User not found");
                });

        if (user.isEmailVerified()) {
            log.warn("Resend verification email attempted for already verified user: {}", email);
            throw new RuntimeException("Email already verified");
        }

        // Generate new verification token
        String verificationToken = UUID.randomUUID().toString();
        user.setVerificationToken(verificationToken);
        userRepository.save(user);
        log.debug("Generated new verification token for user: {}", email);

        // Send verification email
        try {
            emailService.sendVerificationEmail(user.getEmail(), verificationToken);
            log.info("Verification email resent successfully to: {}", email);
            return Map.of("message", "Verification email sent successfully");
        } catch (Exception e) {
            log.error("Failed to resend verification email to: {} - Error: {}", email, e.getMessage(), e);
            throw new RuntimeException("Failed to send verification email: " + e.getMessage());
        }
    }

    public UserDto getCurrentUser(String email) {
        log.debug("Fetching user details for: {}", email);

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> {
                    log.error("User not found when fetching current user: {}", email);
                    return new RuntimeException("User not found");
                });

        UserDto userDto = new UserDto();
        userDto.setId(user.getId());
        userDto.setEmail(user.getEmail());
        userDto.setMfaEnabled(user.isMfaEnabled());
        userDto.setEmailVerified(user.isEmailVerified());
        userDto.setCreatedAt(user.getCreatedAt());

        // ✅ Only include verification code if NOT verified
        if (!user.isEmailVerified()) {
            userDto.setVerificationToken(user.getVerificationToken());
        }

        return userDto;
    }
}
