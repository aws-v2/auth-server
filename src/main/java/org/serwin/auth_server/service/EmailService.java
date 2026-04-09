package org.serwin.auth_server.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Value("${app.frontend.url:http://localhost:5173}")
    private String frontendUrl;

    public void sendVerificationEmail(String toEmail, String verificationToken) {
        log.debug("Preparing verification email for: {}", toEmail);
        String verificationLink = frontendUrl + "/verify-email?token=" + verificationToken;
        String subject = "SERWIN - Email Verification Required";
        String htmlContent = getEmailVerificationTemplate(toEmail, verificationLink);
        sendHtmlEmail(toEmail, subject, htmlContent);
    }

    private String getEmailVerificationTemplate(String email, String verificationLink) {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
            </head>
            <body style="background-color: #f3f4f6; font-family: 'Courier New', Courier, monospace; margin: 0; padding: 40px 20px; color: #000000;">
                <div style="max-width: 600px; margin: 0 auto; background-color: #ffffff; border: 4px solid #000000; box-shadow: 12px 12px 0px 0px #000000; padding: 40px;">
                    <div style="text-align: center; border-bottom: 4px solid #000000; padding-bottom: 20px; margin-bottom: 30px;">
                        <h1 style="font-size: 48px; margin: 0; text-transform: uppercase; font-weight: 900; letter-spacing: -2px; font-family: 'Impact', sans-serif;">SERWIN</h1>
                    </div>
                    <div style="font-size: 16px; line-height: 1.5; margin-bottom: 30px;">
                        <p style="font-weight: bold; font-size: 18px; margin: 0 0 20px 0;">[ ACTION REQUIRED: EMAIL VERIFICATION ]</p>
                        <p style="margin: 0 0 20px 0;">Welcome to the SERWIN Cloud Platform. To complete your registration and activate your account, please verify your email address.</p>
                        <p style="margin: 0 0 20px 0;">Account registered: <span style="background-color: #000000; color: #ffffff; padding: 2px 4px;">%s</span></p>
                    </div>
                    <div style="text-align: center; margin: 40px 0;">
                        <a href="%s" style="background-color: #000000; color: #ffffff !important; padding: 20px 40px; text-decoration: none; font-weight: bold; font-size: 20px; display: inline-block; border: none;">VERIFY_EMAIL</a>
                    </div>
                    <div style="border-top: 2px solid #eeeeee; padding-top: 20px; font-size: 12px; opacity: 0.6; margin-bottom: 30px;">
                        <p style="margin: 0;">If you did not initiate this registration, please disregard this transmission. Unauthorized access attempts are logged.</p>
                    </div>
                    <div style="text-align: center; font-size: 10px; color: #666666; margin-top: 50px; text-transform: uppercase; letter-spacing: 2px;">
                        &copy; 2026 SERWIN CLOUD TECHNOLOGIES // TERMINAL SECURITY DIVISION
                    </div>
                </div>
            </body>
            </html>
            """.formatted(email, verificationLink);
    }

    public void sendPasswordResetEmail(String toEmail, String resetToken) {
        log.debug("Preparing password reset email for: {}", toEmail);
        String resetLink = frontendUrl + "/reset-password?token=" + resetToken;
        String subject = "SERWIN - Password Reset Request";
        String htmlContent = getPasswordResetTemplate(toEmail, resetLink);
        sendHtmlEmail(toEmail, subject, htmlContent);
    }

    private String getPasswordResetTemplate(String email, String resetLink) {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
            </head>
            <body style="background-color: #f3f4f6; font-family: 'Courier New', Courier, monospace; margin: 0; padding: 40px 20px; color: #000000;">
                <div style="max-width: 600px; margin: 0 auto; background-color: #ffffff; border: 4px solid #000000; box-shadow: 12px 12px 0px 0px #000000; padding: 40px;">
                    <div style="text-align: center; border-bottom: 4px solid #000000; padding-bottom: 20px; margin-bottom: 30px;">
                        <h1 style="font-size: 48px; margin: 0; text-transform: uppercase; font-weight: 900; letter-spacing: -2px; font-family: 'Impact', sans-serif;">SERWIN</h1>
                    </div>
                    <div style="font-size: 16px; line-height: 1.5; margin-bottom: 30px;">
                        <p style="font-weight: bold; font-size: 18px; margin: 0 0 20px 0;">[ ACTION REQUIRED: PASSWORD RESET ]</p>
                        <p style="margin: 0 0 20px 0;">We received a request to access your account credentials. If this was you, please use the secure terminal link below to reset your password.</p>
                        <p style="margin: 0 0 20px 0;">This request was initiated for: <span style="background-color: #000000; color: #ffffff; padding: 2px 4px;">%s</span></p>
                    </div>
                    <div style="text-align: center; margin: 40px 0;">
                        <a href="%s" style="background-color: #000000; color: #ffffff !important; padding: 20px 40px; text-decoration: none; font-weight: bold; font-size: 20px; display: inline-block; border: none;">RESET_CREDENTIALS</a>
                    </div>
                    <div style="border-top: 2px solid #eeeeee; padding-top: 20px; font-size: 12px; opacity: 0.6; margin-bottom: 30px;">
                        <p style="margin: 0;">If you didn't request this, you can safely ignore this transmission. This link will expire in 2 hours for security reasons.</p>
                    </div>
                    <div style="text-align: center; font-size: 10px; color: #666666; margin-top: 50px; text-transform: uppercase; letter-spacing: 2px;">
                        &copy; 2026 SERWIN CLOUD TECHNOLOGIES // TERMINAL SECURITY DIVISION
                    </div>
                </div>
            </body>
            </html>
            """.formatted(email, resetLink);
    }

    public void sendHtmlEmail(String toEmail, String subject, String htmlContent) {
        try {
            log.debug("Sending email to: {} with subject: {}", toEmail, subject);
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.info("Email sent successfully to: {} - Subject: {}", toEmail, subject);
        } catch (MessagingException e) {
            log.error("Failed to send email to: {} - Subject: {} - Error: {}", toEmail, subject, e.getMessage(), e);
            throw new RuntimeException("Failed to send email: " + e.getMessage(), e);
        }
    }

}
