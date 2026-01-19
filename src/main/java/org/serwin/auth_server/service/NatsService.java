package org.serwin.auth_server.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import io.nats.client.Nats;
import io.nats.client.Options;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Duration;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
@Slf4j
public class NatsService {

    @Value("${nats.url:nats://localhost:4222}")
    private String natsUrl;

    @Value("${nats.username:}")
    private String username;

    @Value("${nats.password:}")
    private String password;

    private final ObjectMapper objectMapper;
    private Connection natsConnection;

    @PostConstruct
    public void init() {
        try {
            Options.Builder builder = new Options.Builder()
                    .server(natsUrl)
                    .connectionName("auth-server")
                    .maxReconnects(-1)
                    .reconnectWait(Duration.ofSeconds(2));

            if (username != null && !username.isEmpty()) {
                builder.userInfo(username, password);
            }

            natsConnection = Nats.connect(builder.build());
            log.info("Connected to NATS at {}", natsUrl);
        } catch (IOException | InterruptedException e) {
            log.error("Failed to connect to NATS: {}", e.getMessage());
            // Don't throw exception to allow app to start even if NATS is down,
            // but functionality will be degraded
        }
    }

    public <T> void publish(String subject, T payload) {
        if (natsConnection == null || natsConnection.getStatus() != Connection.Status.CONNECTED) {
            log.warn("NATS not connected, skipping publish to {}", subject);
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(payload);
            natsConnection.publish(subject, json.getBytes());
        } catch (Exception e) {
            log.error("Failed to publish to {}: {}", subject, e.getMessage());
        }
    }

    public <T, R> void subscribe(String subject, Class<T> requestType, Function<T, R> handler) {
        if (natsConnection == null) {
            log.warn("NATS not connected, cannot subscribe to {}", subject);
            return;
        }

        Dispatcher dispatcher = natsConnection.createDispatcher(msg -> {
            try {
                T request = objectMapper.readValue(msg.getData(), requestType);
                R response = handler.apply(request);

                if (msg.getReplyTo() != null) {
                    String responseJson = objectMapper.writeValueAsString(response);
                    natsConnection.publish(msg.getReplyTo(), responseJson.getBytes());
                }
            } catch (Exception e) {
                log.error("Error handling NATS message on {}: {}", subject, e.getMessage());
            }
        });
        dispatcher.subscribe(subject);
        log.info("Subscribed to NATS subject: {}", subject);
    }
}
