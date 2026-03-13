package org.serwin.auth_server.service;

import com.google.zxing.WriterException;
import com.warrenstrange.googleauth.GoogleAuthenticator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Base64;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for MfaService.
 *
 * GoogleAuthenticator is constructed internally (not injected), so these tests
 * exercise the real library. verifyCode() is tested using a real secret + a live
 * TOTP code generated in the same test — this is the standard pattern when the
 * authenticator cannot be mocked.
 */
class MfaServiceTest {

    private MfaService mfaService;

    @BeforeEach
    void setUp() {
        mfaService = new MfaService();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // generateSecretKey
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    class GenerateSecretKey {

        @Test
        void returnsNonNullSecret() {
            String secret = mfaService.generateSecretKey();
            assertThat(secret).isNotNull();
        }

        @Test
        void returnsNonBlankSecret() {
            String secret = mfaService.generateSecretKey();
            assertThat(secret).isNotBlank();
        }

        @Test
        void secretIsBase32Encoded() {
            // GoogleAuthenticator secrets are Base32 — uppercase letters A-Z and digits 2-7
            String secret = mfaService.generateSecretKey();
            assertThat(secret).matches("[A-Z2-7]+");
        }

        @Test
        void secretHasMinimumLength() {
            // RFC 6238 recommends at least 16 Base32 chars (80 bits)
            String secret = mfaService.generateSecretKey();
            assertThat(secret.length()).isGreaterThanOrEqualTo(16);
        }

        @Test
        void eachCallGeneratesUniqueSecret() {
            String first = mfaService.generateSecretKey();
            String second = mfaService.generateSecretKey();
            String third = mfaService.generateSecretKey();

            // Probability of collision is astronomically small — this is a sanity guard
            assertThat(first).isNotEqualTo(second);
            assertThat(second).isNotEqualTo(third);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // generateQRCode
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    class GenerateQRCode {

        private static final String EMAIL = "emqarani2@gmail.com";
        private static final String ISSUER = "AWS-Clone IAM";

        @Test
        void returnsNonNullResult() throws WriterException, IOException {
            String secret = mfaService.generateSecretKey();
            String result = mfaService.generateQRCode(EMAIL, secret, ISSUER);
            assertThat(result).isNotNull();
        }

        @Test
        void resultStartsWithDataUriPrefix() throws WriterException, IOException {
            String secret = mfaService.generateSecretKey();
            String result = mfaService.generateQRCode(EMAIL, secret, ISSUER);
            assertThat(result).startsWith("data:image/png;base64,");
        }

        @Test
        void base64PayloadIsValidAndDecodable() throws WriterException, IOException {
            String secret = mfaService.generateSecretKey();
            String result = mfaService.generateQRCode(EMAIL, secret, ISSUER);

            String base64Part = result.substring("data:image/png;base64,".length());
            assertThatCode(() -> Base64.getDecoder().decode(base64Part))
                    .doesNotThrowAnyException();
        }

        @Test
        void decodedPayloadIsPngImage() throws WriterException, IOException {
            String secret = mfaService.generateSecretKey();
            String result = mfaService.generateQRCode(EMAIL, secret, ISSUER);

            String base64Part = result.substring("data:image/png;base64,".length());
            byte[] pngBytes = Base64.getDecoder().decode(base64Part);

            // PNG magic bytes: 0x89 0x50 0x4E 0x47 0x0D 0x0A 0x1A 0x0A
            assertThat(pngBytes).hasSizeGreaterThan(8);
            assertThat(pngBytes[0] & 0xFF).isEqualTo(0x89);
            assertThat(pngBytes[1] & 0xFF).isEqualTo(0x50); // 'P'
            assertThat(pngBytes[2] & 0xFF).isEqualTo(0x4E); // 'N'
            assertThat(pngBytes[3] & 0xFF).isEqualTo(0x47); // 'G'
        }

        @Test
        void resultIsNonEmptyAfterPrefix() throws WriterException, IOException {
            String secret = mfaService.generateSecretKey();
            String result = mfaService.generateQRCode(EMAIL, secret, ISSUER);

            String base64Part = result.substring("data:image/png;base64,".length());
            assertThat(base64Part).isNotBlank();
        }

        @Test
        void differentEmailsProduceDifferentQrCodes() throws WriterException, IOException {
            String secret = mfaService.generateSecretKey();
            String qr1 = mfaService.generateQRCode("emqarani2@gmail.com", secret, ISSUER);
            String qr2 = mfaService.generateQRCode("other@gmail.com", secret, ISSUER);

            assertThat(qr1).isNotEqualTo(qr2);
        }

        @Test
        void differentSecretsProduceDifferentQrCodes() throws WriterException, IOException {
            String secret1 = mfaService.generateSecretKey();
            String secret2 = mfaService.generateSecretKey();

            String qr1 = mfaService.generateQRCode(EMAIL, secret1, ISSUER);
            String qr2 = mfaService.generateQRCode(EMAIL, secret2, ISSUER);

            assertThat(qr1).isNotEqualTo(qr2);
        }

        @Test
        void differentIssuersProduceDifferentQrCodes() throws WriterException, IOException {
            String secret = mfaService.generateSecretKey();
            String qr1 = mfaService.generateQRCode(EMAIL, secret, "Issuer-A");
            String qr2 = mfaService.generateQRCode(EMAIL, secret, "Issuer-B");

            assertThat(qr1).isNotEqualTo(qr2);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // verifyCode
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    class VerifyCode {

        /**
         * Generates a live TOTP code using the same GoogleAuthenticator instance
         * that MfaService uses internally, so we can test the "valid code" path
         * without mocking the authenticator.
         */
        private int liveCode(String secret) {
            return new GoogleAuthenticator().getTotpPassword(secret);
        }

        @Test
        void validCodeAndSecret_returnsTrue() {
            String secret = mfaService.generateSecretKey();
            int code = liveCode(secret);

            boolean result = mfaService.verifyCode(secret, String.valueOf(code));

            assertThat(result).isTrue();
        }

        @Test
        void wrongCode_returnsFalse() {
            String secret = mfaService.generateSecretKey();
            // 000000 is almost certainly not the current TOTP window
            boolean result = mfaService.verifyCode(secret, "000000");

            assertThat(result).isFalse();
        }

        @Test
        void allZeroCode_returnsFalse() {
            String secret = mfaService.generateSecretKey();
            boolean result = mfaService.verifyCode(secret, "000000");
            assertThat(result).isFalse();
        }

        @Test
        void nonNumericCode_returnsFalse() {
            // Triggers the NumberFormatException catch block → returns false
            String secret = mfaService.generateSecretKey();
            boolean result = mfaService.verifyCode(secret, "ABCDEF");
            assertThat(result).isFalse();
        }

        @Test
        void codeWithSpaces_returnsFalse() {
            String secret = mfaService.generateSecretKey();
            boolean result = mfaService.verifyCode(secret, "123 456");
            assertThat(result).isFalse();
        }

        @Test
        void emptyCode_returnsFalse() {
            String secret = mfaService.generateSecretKey();
            boolean result = mfaService.verifyCode(secret, "");
            assertThat(result).isFalse();
        }

        @Test
        void nullCode_returnsFalse() {
            // parseInt(null) throws NumberFormatException → caught → returns false
            String secret = mfaService.generateSecretKey();
            boolean result = mfaService.verifyCode(secret, null);
            assertThat(result).isFalse();
        }

        @Test
        void codeWithDecimalPoint_returnsFalse() {
            String secret = mfaService.generateSecretKey();
            boolean result = mfaService.verifyCode(secret, "123.456");
            assertThat(result).isFalse();
        }

        @Test
        void negativeNumberString_returnsFalse() {
            String secret = mfaService.generateSecretKey();
            // Negative integers are not valid TOTP codes
            boolean result = mfaService.verifyCode(secret, "-123456");
            assertThat(result).isFalse();
        }

        @Test
        void validCodeWithDifferentSecret_returnsFalse() {
            String correctSecret = mfaService.generateSecretKey();
            String wrongSecret = mfaService.generateSecretKey();

            int code = liveCode(correctSecret);

            // Code for correctSecret must not validate against wrongSecret
            boolean result = mfaService.verifyCode(wrongSecret, String.valueOf(code));
            assertThat(result).isFalse();
        }
    }
}