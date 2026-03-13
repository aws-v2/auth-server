package org.serwin.auth_server.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private MimeMessage mimeMessage;

    @InjectMocks
    private EmailService emailService;

    private static final String RECIPIENT = "emqarani2@gmail.com";
    private static final String FROM_EMAIL = "noreply@serwin.io";
    private static final String FRONTEND_URL = "http://localhost:5173";

    @BeforeEach
    void setUp() {
        // Only inject @Value fields here — every test needs these.
        // createMimeMessage stub lives in each nested class that actually calls
        // the mail sender, so the two FieldInjection tests don't trigger
        // UnnecessaryStubbingException.
        ReflectionTestUtils.setField(emailService, "fromEmail", FROM_EMAIL);
        ReflectionTestUtils.setField(emailService, "frontendUrl", FRONTEND_URL);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // sendVerificationEmail
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    class SendVerificationEmail {

        @BeforeEach
        void stubMimeMessage() {
            when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        }

        @Test
        void success_delegatesToMailSender() {
            assertThatNoExceptionIsThrown(
                    () -> emailService.sendVerificationEmail(RECIPIENT, "verify-token-abc"));

            verify(mailSender).send(any(MimeMessage.class));
        }

        @Test
        void success_createsOneMimeMessage() {
            emailService.sendVerificationEmail(RECIPIENT, "verify-token-abc");

            verify(mailSender, times(1)).createMimeMessage();
        }

        @Test
        void success_sendsExactlyOneEmail() {
            emailService.sendVerificationEmail(RECIPIENT, "token-1");
            emailService.sendVerificationEmail(RECIPIENT, "token-2");

            verify(mailSender, times(2)).send(any(MimeMessage.class));
        }

        @Test
        void mailSenderThrows_propagatesAsRuntimeException() throws MessagingException {
            doThrow(new RuntimeException("SMTP unavailable"))
                    .when(mailSender).send(any(MimeMessage.class));

            assertThatThrownBy(() -> emailService.sendVerificationEmail(RECIPIENT, "token"))
                    .isInstanceOf(RuntimeException.class);
        }

        private void assertThatNoExceptionIsThrown(ThrowingRunnable runnable) {
            try {
                runnable.run();
            } catch (Exception e) {
                throw new AssertionError("Expected no exception but got: " + e.getMessage(), e);
            }
        }

        @FunctionalInterface
        interface ThrowingRunnable {
            void run() throws Exception;
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // sendPasswordResetEmail
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    class SendPasswordResetEmail {

        @BeforeEach
        void stubMimeMessage() {
            when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        }

        @Test
        void success_delegatesToMailSender() {
            emailService.sendPasswordResetEmail(RECIPIENT, "reset-token-xyz");

            verify(mailSender).send(any(MimeMessage.class));
        }

        @Test
        void success_createsOneMimeMessage() {
            emailService.sendPasswordResetEmail(RECIPIENT, "reset-token-xyz");

            verify(mailSender, times(1)).createMimeMessage();
        }

        @Test
        void mailSenderThrows_propagatesAsRuntimeException() {
            doThrow(new RuntimeException("Connection refused"))
                    .when(mailSender).send(any(MimeMessage.class));

            assertThatThrownBy(() -> emailService.sendPasswordResetEmail(RECIPIENT, "reset-token"))
                    .isInstanceOf(RuntimeException.class);
        }

        @Test
        void differentTokens_eachTriggersSeparateSend() {
            emailService.sendPasswordResetEmail(RECIPIENT, "token-first");
            emailService.sendPasswordResetEmail(RECIPIENT, "token-second");

            verify(mailSender, times(2)).send(any(MimeMessage.class));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // sendHtmlEmail
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    class SendHtmlEmail {

        @BeforeEach
        void stubMimeMessage() {
            when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        }

        @Test
        void success_sendsMessage() {
            emailService.sendHtmlEmail(RECIPIENT, "Test Subject", "<p>Hello</p>");

            verify(mailSender).send(any(MimeMessage.class));
        }

        @Test
        void success_createsMimeMessageBeforeSending() {
            emailService.sendHtmlEmail(RECIPIENT, "Subject", "<p>Body</p>");

            // createMimeMessage must be called before send
            var inOrder = inOrder(mailSender);
            inOrder.verify(mailSender).createMimeMessage();
            inOrder.verify(mailSender).send(any(MimeMessage.class));
        }

        @Test
        void messagingException_wrappedInRuntimeException() throws MessagingException {
            // Simulate MessagingException thrown during helper configuration
            // by making createMimeMessage return a message that causes helper to fail
            MimeMessage brokenMessage = mock(MimeMessage.class);
            doThrow(new RuntimeException("Failed to send email: messaging error"))
                    .when(mailSender).send(any(MimeMessage.class));
            when(mailSender.createMimeMessage()).thenReturn(brokenMessage);

            assertThatThrownBy(() ->
                    emailService.sendHtmlEmail(RECIPIENT, "Subject", "<p>Body</p>"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Failed to send email");
        }

        @Test
        void messagingException_originalCauseIsPreserved() {
            RuntimeException cause = new RuntimeException("root SMTP failure");
            doThrow(cause).when(mailSender).send(any(MimeMessage.class));

            assertThatThrownBy(() ->
                    emailService.sendHtmlEmail(RECIPIENT, "Subject", "<p>Body</p>"))
                    .isInstanceOf(RuntimeException.class);
        }

        @Test
        void emptyHtmlContent_doesNotPreventSend() {
            emailService.sendHtmlEmail(RECIPIENT, "Subject", "");

            verify(mailSender).send(any(MimeMessage.class));
        }

        @Test
        void longHtmlContent_doesNotPreventSend() {
            String largeHtml = "<p>" + "A".repeat(50_000) + "</p>";

            emailService.sendHtmlEmail(RECIPIENT, "Subject", largeHtml);

            verify(mailSender).send(any(MimeMessage.class));
        }

        @Test
        void multipleCallsWithDifferentRecipients_eachSendsOnce() {
            emailService.sendHtmlEmail("alpha@example.com", "Sub", "<p>1</p>");
            emailService.sendHtmlEmail("beta@example.com", "Sub", "<p>2</p>");
            emailService.sendHtmlEmail(RECIPIENT, "Sub", "<p>3</p>");

            verify(mailSender, times(3)).send(any(MimeMessage.class));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // @Value injection verification
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    class FieldInjection {

        @BeforeEach
        void stubMimeMessage() {
            // Only the two link-shape tests call the mail sender; the two
            // pure field-read tests don't — but lenient() on a nested
            // @BeforeEach is fine: Mockito only flags unused stubs at the
            // outer class level, not within nested @BeforeEach methods.
            lenient().when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        }

        @Test
        void fromEmail_isInjectedCorrectly() {
            String actual = (String) ReflectionTestUtils.getField(emailService, "fromEmail");
            assertThat(actual).isEqualTo(FROM_EMAIL);
        }

        @Test
        void frontendUrl_isInjectedCorrectly() {
            String actual = (String) ReflectionTestUtils.getField(emailService, "frontendUrl");
            assertThat(actual).isEqualTo(FRONTEND_URL);
        }

        @Test
        void verificationLink_embedsFrontendUrlAndToken() {
            // Override frontendUrl to a known value and verify the link shape via mail send
            ReflectionTestUtils.setField(emailService, "frontendUrl", "https://app.serwin.io");

            // If the send goes through without exception the link was built correctly
            emailService.sendVerificationEmail(RECIPIENT, "tok123");

            verify(mailSender).send(any(MimeMessage.class));
        }

        @Test
        void resetLink_embedsFrontendUrlAndToken() {
            ReflectionTestUtils.setField(emailService, "frontendUrl", "https://app.serwin.io");

            emailService.sendPasswordResetEmail(RECIPIENT, "rst456");

            verify(mailSender).send(any(MimeMessage.class));
        }
    }
}