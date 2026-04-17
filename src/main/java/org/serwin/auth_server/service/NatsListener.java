package org.serwin.auth_server.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.serwin.auth_server.dto.AccessKeyResolveRequest;
import org.serwin.auth_server.dto.AccessKeyResolveResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class NatsListener {

    private final NatsService natsService;
    private final ApiKeyService apiKeyService;
    private final ObjectMapper objectMapper;

    @Value("${spring.profiles.active:dev}")
    private String env;

    @EventListener(ApplicationReadyEvent.class)
    public void setupListeners() {
        Connection conn = natsService.getConnection();
        if (conn == null) {
            log.warn("NATS connection not available, listener setup skipped");
            return;
        }

        // Listener for the subject used by lambda-server
        String lambdaSubject = String.format("%s.lambda.v1.apikey.resolve", env);
        // Listener for the standardized auth subject
        String authSubject = String.format("%s.auth.v1.apikey.resolve", env);

        Dispatcher dispatcher = conn.createDispatcher(msg -> {
            try {
                AccessKeyResolveRequest request = objectMapper.readValue(msg.getData(), AccessKeyResolveRequest.class);
                log.debug("Received Access Key resolution request for: {}", request.getAccessKeyId());

                AccessKeyResolveResponse response = apiKeyService.resolveApiKey(request.getAccessKeyId());
                byte[] responseData = objectMapper.writeValueAsBytes(response);

                conn.publish(msg.getReplyTo(), responseData);
                log.debug("Replied to resolution request for: {}", request.getAccessKeyId());
            } catch (Exception e) {
                log.error("Failed to handle Access Key resolution request: {}", e.getMessage());
            }
        });

        dispatcher.subscribe(lambdaSubject);
        dispatcher.subscribe(authSubject);
        log.info("Auth Server NATS listeners initialized for environment: {} on subjects: {}, {}", env, lambdaSubject,
                authSubject);
    }
}
