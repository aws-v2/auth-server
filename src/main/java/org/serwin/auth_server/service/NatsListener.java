package org.serwin.auth_server.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class NatsListener {

    private final NatsService natsService;

    @Value("${spring.profiles.active:dev}")
    private String env;

    @PostConstruct
    public void setupListeners() {
        if (natsService.getConnection() == null) {
            log.warn("NATS connection not available, listener setup skipped");
            return;
        }

        // Placeholder for future ecosystem events the Auth Server might need to react
        // to
        // Example: Listening to gateway signals for automatic token revocation on
        // attack detection
        log.info("Auth Server NATS listeners initialized for environment: {}", env);
    }
}
