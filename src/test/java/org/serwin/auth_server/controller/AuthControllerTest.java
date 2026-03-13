package org.serwin.auth_server.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.serwin.auth_server.config.SecurityConfig;
import org.serwin.auth_server.dto.*;
import org.serwin.auth_server.service.AuthService;
import org.serwin.auth_server.service.NatsService;
import org.serwin.auth_server.service.TokenBlacklistService;
import org.serwin.auth_server.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean private AuthService authService;
    @MockBean private TokenBlacklistService tokenBlacklistService;
    @MockBean private NatsService natsService;
    @MockBean private JwtUtil jwtUtil;
    @MockBean private org.serwin.auth_server.repository.ClientRepository clientRepository;
    @MockBean private org.serwin.auth_server.repository.UserRepository userRepository;
    @MockBean private org.serwin.auth_server.service.RateLimitingService rateLimitingService;
    @MockBean private UserDetailsService userDetailsService;

    @Autowired
    private ObjectMapper objectMapper;

    // ── helpers ────────────────────────────────────────────────────────────────

    private RegisterRequest buildRegisterRequest(String email, String password, String confirm) {
        RegisterRequest r = new RegisterRequest();
        r.setEmail(email);
        r.setPassword(password);
        r.setConfirmPassword(confirm);
        r.setFirstName("Test");
        r.setLastName("User");
        return r;
    }

    private LoginResponse tokenResponse(String token) {
        return LoginResponse.builder().token(token).email("test@example.com").build();
    }

    @BeforeEach
    void setUp() {
        when(rateLimitingService.isAllowed(any(), any())).thenReturn(true);
        when(tokenBlacklistService.hashToken(anyString())).thenReturn("hashed-token");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // /register
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    class Register {

        @Test
        void success_returnsTokenAndPublishesEvent() throws Exception {
            when(authService.register(any())).thenReturn(tokenResponse("jwt-abc"));

            mockMvc.perform(post("/api/v1/auth/register")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    buildRegisterRequest("new@example.com", "pass123", "pass123"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.token").value("jwt-abc"))
                    .andExpect(jsonPath("$.email").value("test@example.com"));
        }

        @Test
        void passwordMismatch_returns400() throws Exception {
            when(authService.register(any()))
                    .thenThrow(new IllegalArgumentException("Passwords do not match"));

            mockMvc.perform(post("/api/v1/auth/register")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    buildRegisterRequest("new@example.com", "abc", "xyz"))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").exists());
        }

        @Test
        void duplicateEmail_returns400() throws Exception {
            when(authService.register(any()))
                    .thenThrow(new RuntimeException("Email already registered"));

            mockMvc.perform(post("/api/v1/auth/register")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    buildRegisterRequest("dup@example.com", "pass", "pass"))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("Email already registered"));
        }

    }

    // ═══════════════════════════════════════════════════════════════════════════
    // /login
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    class Login {

        @Test
        void success_returnsToken() throws Exception {
            when(authService.login(any())).thenReturn(tokenResponse("jwt-xyz"));

            LoginRequest req = new LoginRequest();
            req.setEmail("test@example.com");
            req.setPassword("correct");

            mockMvc.perform(post("/api/v1/auth/login")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.token").value("jwt-xyz"));
        }

        @Test
        void mfaRequired_returnsMfaFlagInResponse() throws Exception {
            LoginResponse mfaResponse = LoginResponse.builder()
                    .mfaRequired(true)
                    .email("test@example.com")
                    .build();
            when(authService.login(any())).thenReturn(mfaResponse);

            LoginRequest req = new LoginRequest();
            req.setEmail("mfa@example.com");
            req.setPassword("pass");

            mockMvc.perform(post("/api/v1/auth/login")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.mfaRequired").value(true));
        }

        @Test
        void invalidCredentials_returns401() throws Exception {
            when(authService.login(any()))
                    .thenThrow(new RuntimeException("Invalid credentials"));

            LoginRequest req = new LoginRequest();
            req.setEmail("wrong@example.com");
            req.setPassword("bad");

            mockMvc.perform(post("/api/v1/auth/login")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error").value("Invalid credentials"));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // /mfa/verify
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    class MfaVerify {

        @Test
        void success_returnsFullToken() throws Exception {
            when(authService.verifyMfa(any())).thenReturn(tokenResponse("full-jwt"));

            MfaVerifyRequest req = new MfaVerifyRequest();
            req.setEmail("test@example.com");
            req.setCode("123456");

            mockMvc.perform(post("/api/v1/auth/mfa/verify")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.token").value("full-jwt"));
        }

        @Test
        void invalidCode_returns401() throws Exception {
            when(authService.verifyMfa(any()))
                    .thenThrow(new RuntimeException("Invalid MFA code"));

            MfaVerifyRequest req = new MfaVerifyRequest();
            req.setEmail("test@example.com");
            req.setCode("000000");

            mockMvc.perform(post("/api/v1/auth/mfa/verify")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error").value("Invalid MFA code"));
        }

        @Test
        void expiredCode_returns401() throws Exception {
            when(authService.verifyMfa(any()))
                    .thenThrow(new RuntimeException("MFA code expired"));

            MfaVerifyRequest req = new MfaVerifyRequest();
            req.setEmail("test@example.com");
            req.setCode("999999");

            mockMvc.perform(post("/api/v1/auth/mfa/verify")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error").exists());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // /mfa/enable
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    class MfaEnable {

        @Test
        @WithMockUser(username = "test@example.com")
        void success_returnsQrCodeAndSecret() throws Exception {
            when(authService.enableMfa("test@example.com"))
                    .thenReturn(Map.of("qrCode", "data:image/png;base64,abc", "secret", "TOTP_SECRET"));

            mockMvc.perform(post("/api/v1/auth/mfa/enable").with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.qrCode").value("data:image/png;base64,abc"))
                    .andExpect(jsonPath("$.secret").value("TOTP_SECRET"));
        }

        @Test
        @WithMockUser(username = "test@example.com")
        void qrCodeGenerationFails_returns500() throws Exception {
            when(authService.enableMfa(anyString()))
                    .thenThrow(new com.google.zxing.WriterException("QR failure"));

            mockMvc.perform(post("/api/v1/auth/mfa/enable").with(csrf()))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.error").value("Failed to generate QR code"));
        }

        @Test
        @WithMockUser(username = "test@example.com")
        void mfaAlreadyEnabled_returns400() throws Exception {
            when(authService.enableMfa("test@example.com"))
                    .thenThrow(new RuntimeException("MFA already enabled"));

            mockMvc.perform(post("/api/v1/auth/mfa/enable").with(csrf()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("MFA already enabled"));
        }

        @Test
        void unauthenticated_returns403() throws Exception {
            mockMvc.perform(post("/api/v1/auth/mfa/enable").with(csrf()))
                    .andExpect(status().isForbidden());

            verifyNoInteractions(authService);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // /mfa/disable
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    class MfaDisable {

        @Test
        @WithMockUser(username = "test@example.com")
        void success_returnsConfirmation() throws Exception {
            when(authService.disableMfa("test@example.com", "123456"))
                    .thenReturn(Map.of("message", "MFA disabled successfully"));

            mockMvc.perform(post("/api/v1/auth/mfa/disable")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("code", "123456"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("MFA disabled successfully"));
        }

        @Test
        @WithMockUser(username = "test@example.com")
        void wrongCode_returns400() throws Exception {
            when(authService.disableMfa("test@example.com", "000000"))
                    .thenThrow(new RuntimeException("Invalid MFA code"));

            mockMvc.perform(post("/api/v1/auth/mfa/disable")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("code", "000000"))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("Invalid MFA code"));
        }

        @Test
        void unauthenticated_returns403() throws Exception {
            mockMvc.perform(post("/api/v1/auth/mfa/disable")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("code", "111111"))))
                    .andExpect(status().isForbidden());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // /forgot-password
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    class ForgotPassword {

        @Test
        void success_returnsMessage() throws Exception {
            when(authService.forgotPassword(any()))
                    .thenReturn(Map.of("message", "Reset email sent"));

            ForgotPasswordRequest req = new ForgotPasswordRequest();
            req.setEmail("user@example.com");

            mockMvc.perform(post("/api/v1/auth/forgot-password")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("Reset email sent"));
        }

        @Test
        void unknownEmail_returns400() throws Exception {
            when(authService.forgotPassword(any()))
                    .thenThrow(new RuntimeException("User not found"));

            ForgotPasswordRequest req = new ForgotPasswordRequest();
            req.setEmail("ghost@example.com");

            mockMvc.perform(post("/api/v1/auth/forgot-password")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("User not found"));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // /reset-password
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    class ResetPassword {

        @Test
        void success_returnsMessage() throws Exception {
            when(authService.resetPassword(any()))
                    .thenReturn(Map.of("message", "Password reset successfully"));

            ResetPasswordRequest req = new ResetPasswordRequest();
            req.setToken("valid-reset-token-1234");
            req.setNewPassword("newSecurePass1!");

            mockMvc.perform(post("/api/v1/auth/reset-password")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("Password reset successfully"));
        }

        @Test
        void invalidToken_returns400() throws Exception {
            when(authService.resetPassword(any()))
                    .thenThrow(new RuntimeException("Invalid or expired reset token"));

            ResetPasswordRequest req = new ResetPasswordRequest();
            req.setToken("bad-token-12345678");
            req.setNewPassword("newPass");

            mockMvc.perform(post("/api/v1/auth/reset-password")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("Invalid or expired reset token"));
        }

        @Test
        void expiredToken_returns400() throws Exception {
            when(authService.resetPassword(any()))
                    .thenThrow(new RuntimeException("Token has expired"));

            ResetPasswordRequest req = new ResetPasswordRequest();
            req.setToken("expired-token-12345");
            req.setNewPassword("newPass");

            mockMvc.perform(post("/api/v1/auth/reset-password")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").exists());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // /verify-email
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    class VerifyEmail {

        @Test
        void success_returnsConfirmation() throws Exception {
            when(authService.verifyEmail("valid-verify-token"))
                    .thenReturn(Map.of("message", "Email verified"));

            mockMvc.perform(get("/api/v1/auth/verify-email")
                            .param("token", "valid-verify-token"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("Email verified"));
        }

        @Test
        void invalidToken_returns400() throws Exception {
            when(authService.verifyEmail("bad-token"))
                    .thenThrow(new RuntimeException("Invalid verification token"));

            mockMvc.perform(get("/api/v1/auth/verify-email")
                            .param("token", "bad-token"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("Invalid verification token"));
        }

        @Test
        void alreadyVerified_returns400() throws Exception {
            when(authService.verifyEmail("used-token"))
                    .thenThrow(new RuntimeException("Email already verified"));

            mockMvc.perform(get("/api/v1/auth/verify-email")
                            .param("token", "used-token"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("Email already verified"));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // /resend-verification
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    class ResendVerification {

        @Test
        void success_returnsMessage() throws Exception {
            when(authService.resendVerificationEmail("unverified@example.com"))
                    .thenReturn(Map.of("message", "Verification email sent"));

            mockMvc.perform(post("/api/v1/auth/resend-verification")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("email", "unverified@example.com"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("Verification email sent"));
        }

        @Test
        void unknownEmail_returns400() throws Exception {
            when(authService.resendVerificationEmail("ghost@example.com"))
                    .thenThrow(new RuntimeException("User not found"));

            mockMvc.perform(post("/api/v1/auth/resend-verification")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("email", "ghost@example.com"))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("User not found"));
        }

        @Test
        void alreadyVerified_returns400() throws Exception {
            when(authService.resendVerificationEmail("verified@example.com"))
                    .thenThrow(new RuntimeException("Email already verified"));

            mockMvc.perform(post("/api/v1/auth/resend-verification")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("email", "verified@example.com"))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").exists());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // /logout
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    class Logout {

        @Test
        @WithMockUser(username = "test@example.com")
        void success_blacklistsTokenAndPublishesEvent() throws Exception {
            mockMvc.perform(post("/api/v1/auth/logout")
                            .with(csrf())
                            .header("Authorization", "Bearer my-valid-token"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("Logged out successfully"));

            verify(tokenBlacklistService).blacklistToken(eq("my-valid-token"), eq("test@example.com"), anyString());
            verify(natsService).publish(eq("token"), eq("blacklisted"), any());
        }

        @Test
        @WithMockUser(username = "test@example.com")
        void missingAuthorizationHeader_returns400() throws Exception {
            mockMvc.perform(post("/api/v1/auth/logout").with(csrf()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").exists());

            verifyNoInteractions(tokenBlacklistService);
        }

        @Test
        @WithMockUser(username = "test@example.com")
        void malformedAuthorizationHeader_returns400() throws Exception {
            // Header present but missing "Bearer " prefix
            mockMvc.perform(post("/api/v1/auth/logout")
                            .with(csrf())
                            .header("Authorization", "Basic dXNlcjpwYXNz"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").exists());
        }

        @Test
        void unauthenticated_returns403() throws Exception {
            mockMvc.perform(post("/api/v1/auth/logout")
                            .with(csrf())
                            .header("Authorization", "Bearer some-token"))
                    .andExpect(status().isForbidden());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // /me
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    class GetCurrentUser {

        @Test
        @WithMockUser(username = "test@example.com")
        void success_returnsUserDto() throws Exception {
            UserDto user = new UserDto();
            user.setEmail("test@example.com");
            when(authService.getCurrentUser("test@example.com")).thenReturn(user);

            mockMvc.perform(get("/api/v1/auth/me"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.email").value("test@example.com"));
        }

        @Test
        void unauthenticated_returns403() throws Exception {
            mockMvc.perform(get("/api/v1/auth/me"))
                    .andExpect(status().isForbidden());

            verifyNoInteractions(authService);
        }

        @Test
        @WithMockUser(username = "test@example.com")
        void serviceThrows_returns401() throws Exception {
            when(authService.getCurrentUser(anyString()))
                    .thenThrow(new RuntimeException("User session invalid"));

            mockMvc.perform(get("/api/v1/auth/me"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error").value("User session invalid"));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // /payment/verify
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    class PaymentVerify {

        private PaymentRequest buildPaymentRequest() {
            PaymentRequest req = new PaymentRequest();
            req.setCardholderName("John Doe");
            // populate other required fields as needed
            return req;
        }

        @Test
        @WithMockUser(username = "test@example.com")
        void success_returnsMessage() throws Exception {
            PaymentVerificationResponse natsResponse = new PaymentVerificationResponse();
            natsResponse.setStatus("SUCCESS");
            natsResponse.setMessage("Payment verified");

            when(natsService.getEnv()).thenReturn("dev");
            when(natsService.request(anyString(), any(), eq(PaymentVerificationResponse.class)))
                    .thenReturn(natsResponse);

            mockMvc.perform(post("/api/v1/auth/payment/verify")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(buildPaymentRequest())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("Payment verified"));
        }

        @Test
        @WithMockUser(username = "test@example.com")
        void natsReturnsFailureStatus_returns400() throws Exception {
            PaymentVerificationResponse natsResponse = new PaymentVerificationResponse();
            natsResponse.setStatus("FAILURE");
            natsResponse.setMessage("Card declined");

            when(natsService.getEnv()).thenReturn("dev");
            when(natsService.request(anyString(), any(), eq(PaymentVerificationResponse.class)))
                    .thenReturn(natsResponse);

            mockMvc.perform(post("/api/v1/auth/payment/verify")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(buildPaymentRequest())))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("Card declined"));
        }

        @Test
        @WithMockUser(username = "test@example.com")
        void natsReturnsNull_returns400WithDefaultMessage() throws Exception {
            when(natsService.getEnv()).thenReturn("dev");
            when(natsService.request(anyString(), any(), eq(PaymentVerificationResponse.class)))
                    .thenReturn(null);

            mockMvc.perform(post("/api/v1/auth/payment/verify")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(buildPaymentRequest())))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("Payment verification failed or timed out"));
        }

        @Test
        @WithMockUser(username = "test@example.com")
        void natsThrowsException_returns500() throws Exception {
            when(natsService.getEnv()).thenReturn("dev");
            when(natsService.request(anyString(), any(), eq(PaymentVerificationResponse.class)))
                    .thenThrow(new RuntimeException("NATS connection timeout"));

            mockMvc.perform(post("/api/v1/auth/payment/verify")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(buildPaymentRequest())))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.error").value("Internal server error during verification"));
        }

        @Test
        void unauthenticated_returns403() throws Exception {
            mockMvc.perform(post("/api/v1/auth/payment/verify")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(buildPaymentRequest())))
                    .andExpect(status().isForbidden());

            verifyNoInteractions(natsService);
        }
    }
}