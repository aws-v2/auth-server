error id: file://<HOME>/Documents/aws-v4/dev/Java/auth-server%20(copy)/src/main/java/org/serwin/auth_server/messaging/NatsListener.java:_empty_/InstanceTokenRequest#getInstanceId#
file://<HOME>/Documents/aws-v4/dev/Java/auth-server%20(copy)/src/main/java/org/serwin/auth_server/messaging/NatsListener.java
empty definition using pc, found symbol in pc: _empty_/InstanceTokenRequest#getInstanceId#
empty definition using semanticdb
empty definition using fallback
non-local guesses:

offset: 2945
uri: file://<HOME>/Documents/aws-v4/dev/Java/auth-server%20(copy)/src/main/java/org/serwin/auth_server/messaging/NatsListener.java
text:
```scala
package org.serwin.auth_server.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import io.nats.client.Message;
import io.nats.client.MessageHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.serwin.auth_server.dto.events_dto.InstanceTokenRequest;
import org.serwin.auth_server.dto.events_dto.InstanceTokenResponse;
import org.serwin.auth_server.util.JwtUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class NatsListener {

    private final NatsService natsService;
    private final ObjectMapper objectMapper;
    private final JwtUtil jwtUtil;

    @Value("${nats.prefix}")
    private String natsPrefix;

    private Dispatcher dispatcher;

    @EventListener(ApplicationReadyEvent.class)
    public void setupListeners() {
        Connection conn = natsService.getConnection();
        if (conn == null) {
            log.warn("NATS connection not available, listener setup skipped");
            return;
        }

        // One shared dispatcher — each subject gets its own handler via subscribe(subject, handler)
        dispatcher = conn.createDispatcher(msg ->
                log.warn("[NATS] Received message on unhandled default subject={}", msg.getSubject()));

        // Current listener: token generation
        addListener(subject("iam.token.generate"), this::handleTokenGenerate);

        // Future listeners go here, e.g.:
        // addListener(subject("iam.some.other.event"), this::handleSomeOtherEvent);

        log.info("Auth Server NATS listeners initialized for environment: {}", natsPrefix);
    }

    /**
     * Registers a handler for a subject on the shared dispatcher.
     * Call this again (with a different subject/handler) to listen to more subjects.
     */
    private void addListener(String subject, MessageHandler handler) {
        dispatcher.subscribe(subject, handler);
        log.info("[NATS] Subscribed to subject={}", subject);
    }

    private String subject(String suffix) {
        return String.format("%s.%s", natsPrefix, suffix);
    }

    private void handleTokenGenerate(Message msg) {
        String subject = msg.getSubject();
        String replyTo = msg.getReplyTo();

        try {
            InstanceTokenRequest request = objectMapper.readValue(msg.getData(), InstanceTokenRequest.class);

            log.info("[NATS] [REQUEST] subject={} user_id={} instance_id={}",
                    subject, request.getUserID(), request.getInsta@@nceId());

            String token = generateToken(request);
            InstanceTokenResponse response = new InstanceTokenResponse(token, null);

            reply(replyTo, response, subject);

        } catch (Exception e) {
            log.error("[NATS] Failed to process token request", e);
            reply(replyTo, new InstanceTokenResponse(null, e.getMessage()), subject);
        }
    }

    private void reply(String replyTo, InstanceTokenResponse response, String subject) {
        if (replyTo == null || replyTo.isBlank()) {
            log.warn("[NATS] No reply subject set for request on subject={}", subject);
            return;
        }
        try {
            natsService.publish(replyTo, response);
            log.debug("[NATS] Replied on replyTo={}", replyTo);
        } catch (Exception e) {
            log.error("[NATS] Failed to send response to replyTo={}", replyTo, e);
        }
    }

    private String generateToken(InstanceTokenRequest request) {
        Map<String, Object> extraClaims = new HashMap<>();

        if (request.getPayload() != null && !request.getPayload().isEmpty()) {
            try {
                byte[] decoded = Base64.getUrlDecoder().decode(request.getPayload());
                Map<String, Object> payloadClaims = objectMapper.readValue(decoded, Map.class);
                extraClaims.putAll(payloadClaims);
            } catch (Exception e) {
                log.error("Failed to decode presigned payload", e);
            }
        }

        extraClaims.put("correlationId", UUID.randomUUID().toString());
        extraClaims.put("userId", request.getUserID());
        return jwtUtil.generateTokenWithClaims(request.getUserID(), extraClaims);
    }
}
```


#### Short summary: 

empty definition using pc, found symbol in pc: _empty_/InstanceTokenRequest#getInstanceId#