package org.serwin.auth_server.util;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.serwin.auth_server.enums.Role;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtUtilTest {

    private JwtUtil jwtUtil;
    private final String secret = "dGhpcy1pcy1hLXZlcnktc2VjcmV0LWtleS13aGljaC1pcy1hdGxlYXN0LTMyLWJ5dGVzLWxvbmc="; // "this-is-a-very-secret-key-which-is-atleast-32-bytes-long" base64
    private final long expiration = 3600000; // 1 hour

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret", secret);
        ReflectionTestUtils.setField(jwtUtil, "jwtExpiration", expiration);
    }

    @Test
    void generateToken_shouldCreateValidToken() {
        String username = "test@example.com";
        UUID userId = UUID.randomUUID();

        String token = jwtUtil.generateToken(username, userId, Role.USER);

        assertThat(token).isNotNull();
        assertThat(jwtUtil.extractUsername(token)).isEqualTo(username);
        assertThat(jwtUtil.extractUserId(token)).isEqualTo(userId.toString());
        assertThat(jwtUtil.isTokenValid(token)).isTrue();
    }

    @Test
    void isTokenValid_shouldReturnFalseForInvalidToken() {
        assertThat(jwtUtil.isTokenValid("invalid.token.here")).isFalse();
    }

    @Test
    void getTokenHash_shouldReturnConsistentHash() {
        String token = "some.jwt.token";
        String hash1 = jwtUtil.getTokenHash(token);
        String hash2 = jwtUtil.getTokenHash(token);

        assertThat(hash1).isEqualTo(hash2);
        assertThat(hash1).hasSize(64); // SHA-256 hex string
    }

    @Test
    void extractAllClaims_shouldExtractClaimsCorrectly() {
        String username = "test@example.com";
        UUID userId = UUID.randomUUID();
        String token = jwtUtil.generateToken(username, userId, Role.USER);

        Claims claims = jwtUtil.extractAllClaims(token);

        assertThat(claims.getSubject()).isEqualTo(username);
        assertThat(claims.get("userId", String.class)).isEqualTo(userId.toString());
    }
}
