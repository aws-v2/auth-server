package org.serwin.auth_server.controller;

import com.google.zxing.WriterException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.serwin.auth_server.dto.*;
import org.serwin.auth_server.service.AuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Slf4j
@RefreshScope
public class AuthController {

    private final AuthService authService;
    private static final Logger auditLog = LoggerFactory.getLogger("AUDIT");

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest request) {
        log.info("Registration attempt for email: {}", request.getEmail());
        try {
            LoginResponse response = authService.register(request);
            auditLog.info("USER_REGISTERED - email={}", request.getEmail());
            log.info("Registration successful for email: {}", request.getEmail());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Registration failed for email: {} - Error: {}", request.getEmail(), e.getMessage());
            auditLog.warn("REGISTRATION_FAILED - email={}, reason={}", request.getEmail(), e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        log.info("Login attempt for email: {}", request.getEmail());
        try {
            LoginResponse response = authService.login(request);
            auditLog.info("USER_LOGIN - email={}, mfaRequired={}", request.getEmail(), response.isMfaRequired());
            log.info("Login successful for email: {}", request.getEmail());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.warn("Login failed for email: {} - Error: {}", request.getEmail(), e.getMessage());
            auditLog.warn("LOGIN_FAILED - email={}, reason={}", request.getEmail(), e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/mfa/verify")
    public ResponseEntity<?> verifyMfa(@RequestBody MfaVerifyRequest request) {
        log.info("MFA verification attempt for email: {}", request.getEmail());
        try {
            LoginResponse response = authService.verifyMfa(request);
            auditLog.info("MFA_VERIFIED - email={}", request.getEmail());
            log.info("MFA verification successful for email: {}", request.getEmail());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.warn("MFA verification failed for email: {} - Error: {}", request.getEmail(), e.getMessage());
            auditLog.warn("MFA_VERIFICATION_FAILED - email={}, reason={}", request.getEmail(), e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/mfa/enable")
    public ResponseEntity<?> enableMfa() {
        try {
            String email = getCurrentUserEmail();
            log.info("MFA enable request for email: {}", email);
            Map<String, Object> response = authService.enableMfa(email);
            auditLog.info("MFA_ENABLED - email={}", email);
            log.info("MFA enabled successfully for email: {}", email);
            return ResponseEntity.ok(response);
        } catch (WriterException | IOException e) {
            log.error("Failed to generate QR code for MFA - Error: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to generate QR code"));
        } catch (Exception e) {
            log.error("MFA enable failed - Error: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/mfa/disable")
    public ResponseEntity<?> disableMfa(@RequestBody Map<String, String> request) {
        try {
            String email = getCurrentUserEmail();
            String code = request.get("code");
            log.info("MFA disable request for email: {}", email);
            Map<String, String> response = authService.disableMfa(email, code);
            auditLog.info("MFA_DISABLED - email={}", email);
            log.info("MFA disabled successfully for email: {}", email);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.warn("MFA disable failed for current user - Error: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@RequestBody ForgotPasswordRequest request) {
        log.info("Password reset request for email: {}", request.getEmail());
        try {
            Map<String, String> response = authService.forgotPassword(request);
            auditLog.info("PASSWORD_RESET_REQUESTED - email={}", request.getEmail());
            log.info("Password reset email sent to: {}", request.getEmail());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Password reset request failed for email: {} - Error: {}", request.getEmail(), e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestBody ResetPasswordRequest request) {
        log.info("Password reset confirmation attempt with token");
        try {
            Map<String, String> response = authService.resetPassword(request);
            auditLog.info("PASSWORD_RESET_COMPLETED - token={}", request.getToken().substring(0, 8) + "...");
            log.info("Password reset completed successfully");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Password reset failed - Error: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/verify-email")
    public ResponseEntity<?> verifyEmail(@RequestParam String token) {
        log.info("Email verification attempt with token: {}...", token.substring(0, 8));
        try {
            Map<String, String> response = authService.verifyEmail(token);
            auditLog.info("EMAIL_VERIFIED - token={}", token.substring(0, 8) + "...");
            log.info("Email verification successful");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.warn("Email verification failed - Error: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<?> resendVerification(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        log.info("Resend verification email request for: {}", email);
        try {
            Map<String, String> response = authService.resendVerificationEmail(email);
            log.info("Verification email resent to: {}", email);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Resend verification failed for email: {} - Error: {}", email, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    private final org.serwin.auth_server.service.TokenBlacklistService tokenBlacklistService;
    private final org.serwin.auth_server.service.NatsService natsService;

    @PostMapping("/logout")
    public ResponseEntity<?> logout(jakarta.servlet.http.HttpServletRequest request) {
        try {
            String token = extractTokenFromRequest(request);
            String email = getCurrentUserEmail();
            log.info("Logout request for email: {}", email);

            tokenBlacklistService.blacklistToken(token, email, "User logout");

            // Publish event
            natsService.publish("auth.token.blacklisted", Map.of(
                    "email", email,
                    "tokenHash", tokenBlacklistService.hashToken(token),
                    "reason", "User logout",
                    "timestamp", java.time.LocalDateTime.now().toString()));

            auditLog.info("USER_LOGOUT - email={}", email);
            log.info("User logged out successfully: {}", email);
            return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
        } catch (Exception e) {
            log.error("Logout failed - Error: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    private String extractTokenFromRequest(jakarta.servlet.http.HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        throw new RuntimeException("No token found in request");
    }

    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser() {
        try {
            String email = getCurrentUserEmail();
            log.debug("Fetching current user info for: {}", email);
            UserDto user = authService.getCurrentUser(email);
            return ResponseEntity.ok(user);
        } catch (Exception e) {
            log.error("Failed to fetch current user - Error: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    private String getCurrentUserEmail() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new RuntimeException("User not authenticated");
        }
        return authentication.getName();
    }
}
