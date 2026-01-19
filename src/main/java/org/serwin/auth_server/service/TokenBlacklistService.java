package org.serwin.auth_server.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.serwin.auth_server.entities.TokenBlacklist;
import org.serwin.auth_server.entities.User;
import org.serwin.auth_server.repository.TokenBlacklistRepository;
import org.serwin.auth_server.repository.UserRepository;
import org.serwin.auth_server.util.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.time.LocalDateTime;
import java.util.Date;

@Service
@RequiredArgsConstructor
@Slf4j
public class TokenBlacklistService {
    private static final Logger auditLog = LoggerFactory.getLogger("AUDIT");

    private final TokenBlacklistRepository tokenBlacklistRepository;
    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;

    @Transactional
    public void blacklistToken(String token, String email, String reason) {
        log.debug("Blacklisting token for user: {}, reason: {}", email, reason);

        if (isTokenBlacklisted(token)) {
            log.debug("Token already blacklisted for user: {}", email);
            return;
        }

        String tokenHash = jwtUtil.getTokenHash(token);
        Date expirationDate = jwtUtil.extractExpiration(token);
        LocalDateTime expiresAt = LocalDateTime.ofInstant(expirationDate.toInstant(), ZoneId.systemDefault());

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> {
                    log.error("User not found while blacklisting token: {}", email);
                    return new RuntimeException("User not found");
                });

        TokenBlacklist blacklistEntry = new TokenBlacklist();
        blacklistEntry.setTokenHash(tokenHash);
        blacklistEntry.setUser(user);
        blacklistEntry.setReason(reason);
        blacklistEntry.setExpiresAt(expiresAt);

        tokenBlacklistRepository.save(blacklistEntry);
        auditLog.info("TOKEN_BLACKLISTED - email={}, reason={}", email, reason);
        log.info("Token blacklisted successfully for user: {}", email);
    }

    @Transactional(readOnly = true)
    public boolean isTokenBlacklisted(String token) {
        String tokenHash = jwtUtil.getTokenHash(token);
        boolean isBlacklisted = tokenBlacklistRepository.existsByTokenHash(tokenHash);
        if (isBlacklisted) {
            log.debug("Token found in blacklist");
        }
        return isBlacklisted;
    }

    public String hashToken(String token) {
        return jwtUtil.getTokenHash(token);
    }
}
