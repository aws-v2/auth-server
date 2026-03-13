package org.serwin.auth_server.service;

import com.google.zxing.WriterException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.serwin.auth_server.dto.*;
import org.serwin.auth_server.entities.PasswordResetToken;
import org.serwin.auth_server.entities.User;
import org.serwin.auth_server.repository.PasswordResetTokenRepository;
import org.serwin.auth_server.repository.UserRepository;
import org.serwin.auth_server.util.JwtUtil;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtUtil jwtUtil;
    @Mock private MfaService mfaService;
    @Mock private EmailService emailService;
    @Mock private PasswordResetTokenRepository resetTokenRepository;
    @Mock private NatsService natsService;

    @InjectMocks
    private AuthService authService;

    private static final String EMAIL = "test@example.com";
    private static final String PASSWORD = "password123";
    private static final String ENCODED_PASSWORD = "encodedPassword";

    private User baseUser;

    @BeforeEach
    void setUp() {
        baseUser = new User();
        baseUser.setId(UUID.randomUUID());
        baseUser.setEmail(EMAIL);
        baseUser.setPassword(ENCODED_PASSWORD);
        baseUser.setMfaEnabled(false);
        baseUser.setEmailVerified(false);
        baseUser.setVerificationToken("existing-token");
    }

    // ── shared stubs ──────────────────────────────────────────────────────────

    private void stubUserSave() {
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            if (u.getId() == null) u.setId(UUID.randomUUID());
            return u;
        });
    }

    private RegisterRequest registerRequest(String email, String pw, String confirm) {
        RegisterRequest r = new RegisterRequest();
        r.setEmail(email);
        r.setPassword(pw);
        r.setConfirmPassword(confirm);
        return r;
    }

    private LoginRequest loginRequest(String email, String pw) {
        LoginRequest r = new LoginRequest();
        r.setEmail(email);
        r.setPassword(pw);
        return r;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // register
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    class Register {

        @Test
        void success_returnsTokenAndEmail() {
            when(userRepository.existsByEmail(EMAIL)).thenReturn(false);
            when(passwordEncoder.encode(PASSWORD)).thenReturn(ENCODED_PASSWORD);
            when(jwtUtil.generateToken(eq(EMAIL), any())).thenReturn("mock-token");
            stubUserSave();

            LoginResponse response = authService.register(registerRequest(EMAIL, PASSWORD, PASSWORD));

            assertThat(response.getToken()).isEqualTo("mock-token");
            assertThat(response.getEmail()).isEqualTo(EMAIL);
            assertThat(response.isMfaEnabled()).isFalse();
            assertThat(response.getMessage()).contains("Registration successful");
        }

        @Test
        void success_persistsUserWithHashedPasswordAndVerificationToken() {
            when(userRepository.existsByEmail(EMAIL)).thenReturn(false);
            when(passwordEncoder.encode(PASSWORD)).thenReturn(ENCODED_PASSWORD);
            when(jwtUtil.generateToken(any(), any())).thenReturn("token");
            stubUserSave();

            authService.register(registerRequest(EMAIL, PASSWORD, PASSWORD));

            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(captor.capture());

            User saved = captor.getValue();
            assertThat(saved.getEmail()).isEqualTo(EMAIL);
            assertThat(saved.getPassword()).isEqualTo(ENCODED_PASSWORD);
            assertThat(saved.isEmailVerified()).isFalse();
            assertThat(saved.isMfaEnabled()).isFalse();
            assertThat(saved.getVerificationToken()).isNotNull().isNotBlank();
        }

        @Test
        void success_sendsVerificationEmail() {
            when(userRepository.existsByEmail(EMAIL)).thenReturn(false);
            when(passwordEncoder.encode(PASSWORD)).thenReturn(ENCODED_PASSWORD);
            when(jwtUtil.generateToken(any(), any())).thenReturn("token");
            stubUserSave();

            authService.register(registerRequest(EMAIL, PASSWORD, PASSWORD));

            verify(emailService).sendVerificationEmail(eq(EMAIL), anyString());
        }

        @Test
        void success_publishesNatsUserRegisteredEvent() {
            when(userRepository.existsByEmail(EMAIL)).thenReturn(false);
            when(passwordEncoder.encode(PASSWORD)).thenReturn(ENCODED_PASSWORD);
            when(jwtUtil.generateToken(any(), any())).thenReturn("token");
            stubUserSave();

            authService.register(registerRequest(EMAIL, PASSWORD, PASSWORD));

            verify(natsService).publish(
                    eq("user"),
                    eq("registered"),
                    argThat(payload -> {
                        @SuppressWarnings("unchecked")
                        var map = (Map<String, Object>) payload;
                        return map.containsKey("tenant_email")
                                && map.containsKey("tenant_id")
                                && map.containsKey("timestamp");
                    })
            );
        }

        @Test
        void success_emailFailure_doesNotPreventRegistration() {
            // Email sending is wrapped in try/catch — failure must not bubble up
            when(userRepository.existsByEmail(EMAIL)).thenReturn(false);
            when(passwordEncoder.encode(PASSWORD)).thenReturn(ENCODED_PASSWORD);
            when(jwtUtil.generateToken(any(), any())).thenReturn("token");
            stubUserSave();
            doThrow(new RuntimeException("SMTP unavailable"))
                    .when(emailService).sendVerificationEmail(any(), any());

            assertDoesNotThrow(() -> authService.register(registerRequest(EMAIL, PASSWORD, PASSWORD)));
            verify(userRepository).save(any(User.class));
        }

        @Test
        void success_natsFailure_doesNotPreventRegistration() {
            when(userRepository.existsByEmail(EMAIL)).thenReturn(false);
            when(passwordEncoder.encode(PASSWORD)).thenReturn(ENCODED_PASSWORD);
            when(jwtUtil.generateToken(any(), any())).thenReturn("token");
            stubUserSave();
            doThrow(new RuntimeException("NATS down"))
                    .when(natsService).publish(any(), any(), any());

            assertDoesNotThrow(() -> authService.register(registerRequest(EMAIL, PASSWORD, PASSWORD)));
        }

        @Test
        void passwordMismatch_throwsWithMessage() {
            assertThatThrownBy(() -> authService.register(registerRequest(EMAIL, PASSWORD, "different")))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Password Mismatch");

            verifyNoInteractions(userRepository, emailService, natsService);
        }

        @Test
        void duplicateEmail_throwsWithMessage() {
            when(userRepository.existsByEmail(EMAIL)).thenReturn(true);

            assertThatThrownBy(() -> authService.register(registerRequest(EMAIL, PASSWORD, PASSWORD)))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Email already exists");

            verify(userRepository, never()).save(any());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // login
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    class Login {

        @Test
        void success_returnsToken() {
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(baseUser));
            when(passwordEncoder.matches(PASSWORD, ENCODED_PASSWORD)).thenReturn(true);
            when(jwtUtil.generateToken(EMAIL, baseUser.getId())).thenReturn("jwt");

            LoginResponse response = authService.login(loginRequest(EMAIL, PASSWORD));

            assertThat(response.getToken()).isEqualTo("jwt");
            assertThat(response.getEmail()).isEqualTo(EMAIL);
            assertThat(response.isMfaRequired()).isFalse();
            assertThat(response.getMessage()).contains("Login successful");
        }

        @Test
        void mfaEnabled_returnsNoTokenAndMfaRequiredTrue() {
            baseUser.setMfaEnabled(true);
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(baseUser));
            when(passwordEncoder.matches(PASSWORD, ENCODED_PASSWORD)).thenReturn(true);

            LoginResponse response = authService.login(loginRequest(EMAIL, PASSWORD));

            assertThat(response.isMfaRequired()).isTrue();
            assertThat(response.isMfaEnabled()).isTrue();
            assertThat(response.getToken()).isNull();
            assertThat(response.getMessage()).contains("MFA");
            verifyNoInteractions(jwtUtil);
        }

        @Test
        void userNotFound_throwsInvalidCredentials() {
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.login(loginRequest(EMAIL, PASSWORD)))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Invalid credentials");
        }

        @Test
        void wrongPassword_throwsInvalidCredentials() {
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(baseUser));
            when(passwordEncoder.matches("wrong", ENCODED_PASSWORD)).thenReturn(false);

            assertThatThrownBy(() -> authService.login(loginRequest(EMAIL, "wrong")))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Invalid credentials");

            verifyNoInteractions(jwtUtil);
        }

        @Test
        void userNotFound_doesNotRevealUserExistence() {
            // Both "user not found" and "bad password" must produce identical error message
            when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(baseUser));
            when(passwordEncoder.matches("bad", ENCODED_PASSWORD)).thenReturn(false);

            Throwable notFound = catchThrowable(
                    () -> authService.login(loginRequest("nobody@example.com", PASSWORD)));
            Throwable badPass = catchThrowable(
                    () -> authService.login(loginRequest(EMAIL, "bad")));

            assertThat(notFound).hasMessage("Invalid credentials");
            assertThat(badPass).hasMessage("Invalid credentials");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // enableMfa
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    class EnableMfa {

        @Test
        void success_returnsSecretAndQrCode() throws WriterException, IOException {
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(baseUser));
            when(mfaService.generateSecretKey()).thenReturn("TOTP-SECRET");
            when(mfaService.generateQRCode(EMAIL, "TOTP-SECRET", "AWS-Clone IAM"))
                    .thenReturn("data:image/png;base64,qr==");
            when(userRepository.save(any())).thenReturn(baseUser);

            Map<String, Object> result = authService.enableMfa(EMAIL);

            assertThat(result).containsKey("secret").containsKey("qrCode").containsKey("message");
            assertThat(result.get("secret")).isEqualTo("TOTP-SECRET");
            assertThat(result.get("qrCode")).isEqualTo("data:image/png;base64,qr==");
        }

        @Test
        void success_persistsSecretAndEnablesFlag() throws WriterException, IOException {
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(baseUser));
            when(mfaService.generateSecretKey()).thenReturn("SECRET-KEY");
            when(mfaService.generateQRCode(any(), any(), any())).thenReturn("qr");
            when(userRepository.save(any())).thenReturn(baseUser);

            authService.enableMfa(EMAIL);

            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(captor.capture());

            assertThat(captor.getValue().getMfaSecret()).isEqualTo("SECRET-KEY");
            assertThat(captor.getValue().isMfaEnabled()).isTrue();
        }

        @Test
        void userNotFound_throwsRuntimeException() {
            when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.enableMfa("ghost@example.com"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("User not found");
        }

        @Test
        void qrCodeGenerationFails_propagatesWriterException() throws WriterException, IOException {
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(baseUser));
            when(mfaService.generateSecretKey()).thenReturn("SECRET");
            when(mfaService.generateQRCode(any(), any(), any())).thenThrow(new WriterException("QR fail"));
            when(userRepository.save(any())).thenReturn(baseUser);

            assertThatThrownBy(() -> authService.enableMfa(EMAIL))
                    .isInstanceOf(WriterException.class);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // verifyMfa
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    class VerifyMfa {

        private User mfaUser() {
            User u = new User();
            u.setId(UUID.randomUUID());
            u.setEmail(EMAIL);
            u.setPassword(ENCODED_PASSWORD);
            u.setMfaEnabled(true);
            u.setMfaSecret("MFA-SECRET");
            return u;
        }

        private MfaVerifyRequest emailRequest(String email, String code) {
            MfaVerifyRequest r = new MfaVerifyRequest();
            r.setEmail(email);
            r.setCode(code);
            return r;
        }

        private MfaVerifyRequest userIdRequest(String userId, String code) {
            MfaVerifyRequest r = new MfaVerifyRequest();
            r.setUserId(userId);
            r.setCode(code);
            return r;
        }

        @Test
        void success_viaEmail_returnsFullToken() {
            User u = mfaUser();
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(u));
            when(mfaService.verifyCode("MFA-SECRET", "123456")).thenReturn(true);
            when(jwtUtil.generateToken(EMAIL, u.getId())).thenReturn("full-jwt");

            LoginResponse response = authService.verifyMfa(emailRequest(EMAIL, "123456"));

            assertThat(response.getToken()).isEqualTo("full-jwt");
            assertThat(response.getEmail()).isEqualTo(EMAIL);
            assertThat(response.isMfaEnabled()).isTrue();
        }

        @Test
        void success_viaUserId_returnsFullToken() {
            User u = mfaUser();
            when(userRepository.findById(u.getId())).thenReturn(Optional.of(u));
            when(mfaService.verifyCode("MFA-SECRET", "654321")).thenReturn(true);
            when(jwtUtil.generateToken(EMAIL, u.getId())).thenReturn("full-jwt");

            LoginResponse response = authService.verifyMfa(userIdRequest(u.getId().toString(), "654321"));

            assertThat(response.getToken()).isEqualTo("full-jwt");
        }

        @Test
        void invalidCode_throwsRuntimeException() {
            User u = mfaUser();
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(u));
            when(mfaService.verifyCode("MFA-SECRET", "000000")).thenReturn(false);

            assertThatThrownBy(() -> authService.verifyMfa(emailRequest(EMAIL, "000000")))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Invalid MFA code");

            verifyNoInteractions(jwtUtil);
        }

        @Test
        void mfaNotEnabled_throwsRuntimeException() {
            baseUser.setMfaEnabled(false);
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(baseUser));

            assertThatThrownBy(() -> authService.verifyMfa(emailRequest(EMAIL, "123456")))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("MFA is not enabled for this user");
        }

        @Test
        void userNotFoundByEmail_throwsRuntimeException() {
            when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.verifyMfa(emailRequest("ghost@example.com", "123456")))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("User not found");
        }

        @Test
        void userNotFoundByUserId_throwsRuntimeException() {
            UUID id = UUID.randomUUID();
            when(userRepository.findById(id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.verifyMfa(userIdRequest(id.toString(), "123456")))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("User not found");
        }

        @Test
        void noEmailOrUserId_throwsRequiredFieldException() {
            MfaVerifyRequest r = new MfaVerifyRequest();
            r.setCode("123456");
            // both email and userId are null/empty

            assertThatThrownBy(() -> authService.verifyMfa(r))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Email or userId is required");
        }

        @Test
        void emptyEmailAndEmptyUserId_throwsRequiredFieldException() {
            MfaVerifyRequest r = new MfaVerifyRequest();
            r.setEmail("");
            r.setUserId("");
            r.setCode("123456");

            assertThatThrownBy(() -> authService.verifyMfa(r))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Email or userId is required");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // disableMfa
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    class DisableMfa {

        @Test
        void success_disablesMfaAndClearsSecret() {
            baseUser.setMfaEnabled(true);
            baseUser.setMfaSecret("OLD-SECRET");
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(baseUser));
            when(mfaService.verifyCode("OLD-SECRET", "123456")).thenReturn(true);
            when(userRepository.save(any())).thenReturn(baseUser);

            Map<String, String> result = authService.disableMfa(EMAIL, "123456");

            assertThat(result.get("message")).isEqualTo("MFA disabled successfully");

            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(captor.capture());
            assertThat(captor.getValue().isMfaEnabled()).isFalse();
            assertThat(captor.getValue().getMfaSecret()).isNull();
        }

        @Test
        void invalidCode_throwsRuntimeException() {
            baseUser.setMfaEnabled(true);
            baseUser.setMfaSecret("SECRET");
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(baseUser));
            when(mfaService.verifyCode("SECRET", "bad")).thenReturn(false);

            assertThatThrownBy(() -> authService.disableMfa(EMAIL, "bad"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Invalid MFA code");

            verify(userRepository, never()).save(any());
        }

        @Test
        void mfaNotEnabled_throwsRuntimeException() {
            baseUser.setMfaEnabled(false);
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(baseUser));

            assertThatThrownBy(() -> authService.disableMfa(EMAIL, "123456"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("MFA is not enabled for this user");
        }

        @Test
        void userNotFound_throwsRuntimeException() {
            when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.disableMfa("ghost@example.com", "123456"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("User not found");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // forgotPassword
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    class ForgotPassword {

        @Test
        void success_deletesOldTokensCreatesNewAndSendsEmail() {
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(baseUser));

            ForgotPasswordRequest request = new ForgotPasswordRequest();
            request.setEmail(EMAIL);

            Map<String, String> result = authService.forgotPassword(request);

            assertThat(result.get("message")).isEqualTo("Password reset email sent successfully");
            assertThat(result.get("email")).isEqualTo(EMAIL);

            verify(resetTokenRepository).deleteByUser(baseUser);
            verify(resetTokenRepository).save(any(PasswordResetToken.class));
            verify(emailService).sendPasswordResetEmail(eq(EMAIL), anyString());
        }

        @Test
        void success_tokenPersistedCorrectly() {
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(baseUser));

            ForgotPasswordRequest request = new ForgotPasswordRequest();
            request.setEmail(EMAIL);

            authService.forgotPassword(request);

            ArgumentCaptor<PasswordResetToken> captor = ArgumentCaptor.forClass(PasswordResetToken.class);
            verify(resetTokenRepository).save(captor.capture());

            PasswordResetToken saved = captor.getValue();
            assertThat(saved.getToken()).isNotNull().isNotBlank();
            assertThat(saved.getUser()).isEqualTo(baseUser);
            assertThat(saved.isUsed()).isFalse();
        }

        @Test
        void userNotFound_throwsRuntimeException() {
            when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

            ForgotPasswordRequest request = new ForgotPasswordRequest();
            request.setEmail("ghost@example.com");

            assertThatThrownBy(() -> authService.forgotPassword(request))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("User not found");

            verifyNoInteractions(resetTokenRepository, emailService);
        }

        @Test
        void emailSendFailure_throwsRuntimeException() {
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(baseUser));
            doThrow(new RuntimeException("SMTP error"))
                    .when(emailService).sendPasswordResetEmail(any(), any());

            ForgotPasswordRequest request = new ForgotPasswordRequest();
            request.setEmail(EMAIL);

            assertThatThrownBy(() -> authService.forgotPassword(request))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Failed to send password reset email");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // resetPassword
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    class ResetPassword {

        private PasswordResetToken validToken() {
            PasswordResetToken t = new PasswordResetToken();
            t.setToken("valid-reset-token");
            t.setUser(baseUser);
            t.setUsed(false);
            // expiryDate must be non-null: isExpired() calls LocalDateTime.now().isAfter(expiryDate)
            // and throws NPE if it is null. @PrePersist sets it automatically in production,
            // but unit tests never persist the entity so it must be set explicitly here.
            t.setExpiryDate(java.time.LocalDateTime.now().plusHours(2));
            return t;
        }

        private ResetPasswordRequest resetRequest(String token, String pw, String confirm) {
            ResetPasswordRequest r = new ResetPasswordRequest();
            r.setToken(token);
            r.setNewPassword(pw);
            r.setConfirmPassword(confirm);
            return r;
        }

        @Test
        void success_updatesPasswordAndMarksTokenUsed() {
            PasswordResetToken token = validToken();
            when(resetTokenRepository.findByToken("valid-reset-token")).thenReturn(Optional.of(token));
            when(passwordEncoder.encode("newPass123!")).thenReturn("encoded-new");
            when(userRepository.save(any())).thenReturn(baseUser);
            when(resetTokenRepository.save(any())).thenReturn(token);

            Map<String, String> result = authService.resetPassword(
                    resetRequest("valid-reset-token", "newPass123!", "newPass123!"));

            assertThat(result.get("message")).isEqualTo("Password reset successfully");

            ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(userCaptor.capture());
            assertThat(userCaptor.getValue().getPassword()).isEqualTo("encoded-new");

            ArgumentCaptor<PasswordResetToken> tokenCaptor = ArgumentCaptor.forClass(PasswordResetToken.class);
            verify(resetTokenRepository).save(tokenCaptor.capture());
            assertThat(tokenCaptor.getValue().isUsed()).isTrue();
        }

        @Test
        void passwordMismatch_throwsWithMessage() {
            assertThatThrownBy(() -> authService.resetPassword(
                    resetRequest("any-token", "abc", "xyz")))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Passwords do not match");

            verifyNoInteractions(resetTokenRepository);
        }

        @Test
        void invalidToken_throwsRuntimeException() {
            when(resetTokenRepository.findByToken("bad-token")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.resetPassword(
                    resetRequest("bad-token", "pass", "pass")))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Invalid or expired reset token");
        }

        @Test
        void alreadyUsedToken_throwsRuntimeException() {
            PasswordResetToken token = validToken();
            token.setUsed(true);
            when(resetTokenRepository.findByToken("used-token")).thenReturn(Optional.of(token));

            assertThatThrownBy(() -> authService.resetPassword(
                    resetRequest("used-token", "pass", "pass")))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Reset token has already been used");

            verify(userRepository, never()).save(any());
        }

        @Test
        void expiredToken_throwsRuntimeException() {
            PasswordResetToken token = mock(PasswordResetToken.class);
            when(token.isUsed()).thenReturn(false);
            when(token.isExpired()).thenReturn(true);
            when(resetTokenRepository.findByToken("expired-token")).thenReturn(Optional.of(token));

            assertThatThrownBy(() -> authService.resetPassword(
                    resetRequest("expired-token", "pass", "pass")))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Reset token has expired");

            verify(userRepository, never()).save(any());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // verifyEmail
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    class VerifyEmail {

        @Test
        void success_setsVerifiedAndClearsToken() {
            baseUser.setVerificationToken("my-verify-token");
            baseUser.setEmailVerified(false);
            when(userRepository.findAll()).thenReturn(List.of(baseUser));
            when(userRepository.save(any())).thenReturn(baseUser);

            Map<String, String> result = authService.verifyEmail("my-verify-token");

            assertThat(result.get("message")).isEqualTo("Email verified successfully");

            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(captor.capture());
            assertThat(captor.getValue().isEmailVerified()).isTrue();
            assertThat(captor.getValue().getVerificationToken()).isNull();
        }

        @Test
        void tokenNotFound_throwsRuntimeException() {
            when(userRepository.findAll()).thenReturn(List.of(baseUser)); // token doesn't match

            assertThatThrownBy(() -> authService.verifyEmail("wrong-token"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Invalid verification token");
        }

        @Test
        void alreadyVerified_throwsRuntimeException() {
            baseUser.setVerificationToken("my-token");
            baseUser.setEmailVerified(true);
            when(userRepository.findAll()).thenReturn(List.of(baseUser));

            assertThatThrownBy(() -> authService.verifyEmail("my-token"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Email already verified");

            verify(userRepository, never()).save(any());
        }

        @Test
        void noUsersInSystem_throwsRuntimeException() {
            when(userRepository.findAll()).thenReturn(List.of());

            assertThatThrownBy(() -> authService.verifyEmail("any-token"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Invalid verification token");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // resendVerificationEmail
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    class ResendVerificationEmail {

        @Test
        void success_generatesNewTokenAndSendsEmail() {
            baseUser.setEmailVerified(false);
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(baseUser));
            when(userRepository.save(any())).thenReturn(baseUser);

            Map<String, String> result = authService.resendVerificationEmail(EMAIL);

            assertThat(result.get("message")).isEqualTo("Verification email sent successfully");
            verify(emailService).sendVerificationEmail(eq(EMAIL), anyString());
        }

        @Test
        void success_replacesOldToken() {
            baseUser.setEmailVerified(false);
            baseUser.setVerificationToken("old-token");
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(baseUser));
            when(userRepository.save(any())).thenReturn(baseUser);

            authService.resendVerificationEmail(EMAIL);

            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(captor.capture());
            assertThat(captor.getValue().getVerificationToken())
                    .isNotNull()
                    .isNotEqualTo("old-token");
        }

        @Test
        void alreadyVerified_throwsRuntimeException() {
            baseUser.setEmailVerified(true);
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(baseUser));

            assertThatThrownBy(() -> authService.resendVerificationEmail(EMAIL))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Email already verified");

            verify(emailService, never()).sendVerificationEmail(any(), any());
        }

        @Test
        void userNotFound_throwsRuntimeException() {
            when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.resendVerificationEmail("ghost@example.com"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("User not found");
        }

        @Test
        void emailSendFailure_throwsRuntimeException() {
            baseUser.setEmailVerified(false);
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(baseUser));
            when(userRepository.save(any())).thenReturn(baseUser);
            doThrow(new RuntimeException("SMTP down"))
                    .when(emailService).sendVerificationEmail(any(), any());

            assertThatThrownBy(() -> authService.resendVerificationEmail(EMAIL))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Failed to send verification email");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // getCurrentUser
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    class GetCurrentUser {

        @Test
        void success_returnsFullUserDto() {
            baseUser.setEmailVerified(true);
            baseUser.setMfaEnabled(true);
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(baseUser));

            UserDto dto = authService.getCurrentUser(EMAIL);

            assertThat(dto.getEmail()).isEqualTo(EMAIL);
            assertThat(dto.getId()).isEqualTo(baseUser.getId());
            assertThat(dto.isEmailVerified()).isTrue();
            assertThat(dto.isMfaEnabled()).isTrue();
        }

        @Test
        void unverifiedUser_includesVerificationToken() {
            baseUser.setEmailVerified(false);
            baseUser.setVerificationToken("verify-me");
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(baseUser));

            UserDto dto = authService.getCurrentUser(EMAIL);

            assertThat(dto.isEmailVerified()).isFalse();
            assertThat(dto.getVerificationToken()).isEqualTo("verify-me");
        }

        @Test
        void verifiedUser_doesNotExposeVerificationToken() {
            baseUser.setEmailVerified(true);
            baseUser.setVerificationToken("stale-token");
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(baseUser));

            UserDto dto = authService.getCurrentUser(EMAIL);

            assertThat(dto.isEmailVerified()).isTrue();
            assertThat(dto.getVerificationToken()).isNull();
        }

        @Test
        void userNotFound_throwsRuntimeException() {
            when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.getCurrentUser("ghost@example.com"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("User not found");
        }
    }
}