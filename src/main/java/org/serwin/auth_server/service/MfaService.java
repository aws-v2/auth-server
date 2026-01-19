package org.serwin.auth_server.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.warrenstrange.googleauth.GoogleAuthenticator;
import com.warrenstrange.googleauth.GoogleAuthenticatorKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;

@Service
@Slf4j
public class MfaService {
    private final GoogleAuthenticator gAuth = new GoogleAuthenticator();

    public String generateSecretKey() {
        log.debug("Generating new MFA secret key");
        GoogleAuthenticatorKey key = gAuth.createCredentials();
        log.debug("MFA secret key generated successfully");
        return key.getKey();
    }

    public String generateQRCode(String email, String secret, String issuer) throws WriterException, IOException {
        log.debug("Generating QR code for email: {}", email);
        String otpAuthUrl = String.format("otpauth://totp/%s:%s?secret=%s&issuer=%s",
            issuer, email, secret, issuer);

        QRCodeWriter qrCodeWriter = new QRCodeWriter();
        BitMatrix bitMatrix = qrCodeWriter.encode(otpAuthUrl, BarcodeFormat.QR_CODE, 200, 200);

        ByteArrayOutputStream pngOutputStream = new ByteArrayOutputStream();
        MatrixToImageWriter.writeToStream(bitMatrix, "PNG", pngOutputStream);
        byte[] pngData = pngOutputStream.toByteArray();

        log.debug("QR code generated successfully for email: {}", email);
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(pngData);
    }

    public boolean verifyCode(String secret, String code) {
        try {
            log.debug("Verifying MFA code");
            boolean isValid = gAuth.authorize(secret, Integer.parseInt(code));
            if (isValid) {
                log.debug("MFA code verification successful");
            } else {
                log.debug("MFA code verification failed - invalid code");
            }
            return isValid;
        } catch (NumberFormatException e) {
            log.warn("MFA code verification failed - invalid format: {}", e.getMessage());
            return false;
        }
    }
}
