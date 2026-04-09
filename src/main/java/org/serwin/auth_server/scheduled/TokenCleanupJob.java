package org.serwin.auth_server.scheduled;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.serwin.auth_server.repository.TokenBlacklistRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class TokenCleanupJob {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(TokenCleanupJob.class);

    private final TokenBlacklistRepository tokenBlacklistRepository;

    @Scheduled(cron = "${scheduled.token-cleanup.cron}")
    @Transactional
    public void cleanupExpiredTokens() {
        try {
            tokenBlacklistRepository.deleteExpiredTokens(LocalDateTime.now());
            log.info("Cleaned up expired tokens from blacklist");
        } catch (Exception e) {
            log.error("Error cleaning up tokens: {}", e.getMessage());
        }
    }
}
