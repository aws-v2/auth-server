package org.serwin.auth_server;

import org.serwin.auth_server.service.NatsService;
import org.serwin.auth_server.service.RateLimitingService;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import org.junit.jupiter.api.BeforeEach;

@ActiveProfiles("test")
public abstract class BaseIntegrationTest {

    @MockBean
    protected NatsService natsService;

    @MockBean
    protected RateLimitingService rateLimitingService;

    @MockBean
    protected JavaMailSender mailSender;

    @BeforeEach
    void baseSetUp() {
        when(rateLimitingService.isAllowed(any(), any())).thenReturn(true);
        // Mock MimeMessage if needed, but for context load this is enough
        when(mailSender.createMimeMessage()).thenReturn(null); 
    }
}
