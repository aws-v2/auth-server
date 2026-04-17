package org.serwin.auth_server.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import io.nats.client.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.serwin.auth_server.dto.AccessKeyResolveRequest;
import org.serwin.auth_server.dto.AccessKeyResolveResponse;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NatsListenerTest {

    @Mock private NatsService natsService;
    @Mock private ApiKeyService apiKeyService;
    @Mock private ObjectMapper objectMapper;
    @Mock private Connection connection;
    @Mock private Dispatcher dispatcher;

    @InjectMocks
    private NatsListener natsListener;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(natsListener, "env", "dev");
    }

    // ── helper: capture and invoke the dispatcher's MessageHandler ────────────

    /**
     * Captures the MessageHandler lambda passed to createDispatcher(), then
     * invokes it with a mock Message so we can test the handler's logic directly.
     */
    private io.nats.client.MessageHandler captureHandler() {
        when(natsService.getConnection()).thenReturn(connection);
        when(connection.createDispatcher(any(io.nats.client.MessageHandler.class))).thenAnswer(inv -> {
            // store the handler but return the dispatcher mock
            return dispatcher;
        });

        natsListener.setupListeners();

        ArgumentCaptor<io.nats.client.MessageHandler> captor =
                ArgumentCaptor.forClass(io.nats.client.MessageHandler.class);
        verify(connection).createDispatcher(captor.capture());
        return captor.getValue();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // setupListeners — connection lifecycle
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    class SetupListeners {

        @Test
        void nullConnection_skipsSetupWithoutException() {
            when(natsService.getConnection()).thenReturn(null);

            assertThatCode(() -> natsListener.setupListeners()).doesNotThrowAnyException();

            verifyNoInteractions(connection, dispatcher);
        }

        @Test
        void validConnection_createsDispatcher() {
            when(natsService.getConnection()).thenReturn(connection);
            when(connection.createDispatcher(any())).thenReturn(dispatcher);

            natsListener.setupListeners();

            verify(connection).createDispatcher(any(io.nats.client.MessageHandler.class));
        }

        @Test
        void validConnection_subscribesBothSubjects() {
            when(natsService.getConnection()).thenReturn(connection);
            when(connection.createDispatcher(any())).thenReturn(dispatcher);

            natsListener.setupListeners();

            verify(dispatcher).subscribe("dev.lambda.v1.apikey.resolve");
            verify(dispatcher).subscribe("dev.auth.v1.apikey.resolve");
        }

        @Test
        void lambdaSubject_embedsEnvPrefix() {
            ReflectionTestUtils.setField(natsListener, "env", "prod");
            when(natsService.getConnection()).thenReturn(connection);
            when(connection.createDispatcher(any())).thenReturn(dispatcher);

            natsListener.setupListeners();

            verify(dispatcher).subscribe("prod.lambda.v1.apikey.resolve");
        }

        @Test
        void authSubject_embedsEnvPrefix() {
            ReflectionTestUtils.setField(natsListener, "env", "staging");
            when(natsService.getConnection()).thenReturn(connection);
            when(connection.createDispatcher(any())).thenReturn(dispatcher);

            natsListener.setupListeners();

            verify(dispatcher).subscribe("staging.auth.v1.apikey.resolve");
        }

        @Test
        void exactlyTwoSubscriptions_noExtras() {
            when(natsService.getConnection()).thenReturn(connection);
            when(connection.createDispatcher(any())).thenReturn(dispatcher);

            natsListener.setupListeners();

            verify(dispatcher, times(2)).subscribe(anyString());
        }

        @Test
        void connectionFetchedExactlyOnce() {
            when(natsService.getConnection()).thenReturn(connection);
            when(connection.createDispatcher(any())).thenReturn(dispatcher);

            natsListener.setupListeners();

            verify(natsService, times(1)).getConnection();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // MessageHandler — happy path
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    class MessageHandler {

        @Test
        void validMessage_resolvesApiKeyAndReplies() throws Exception {
            AccessKeyResolveRequest request = new AccessKeyResolveRequest();
            request.setAccessKeyId("AKIA-TEST");

            AccessKeyResolveResponse response = AccessKeyResolveResponse.builder()
                    .userId("user-123")
                    .secretKeyHash("hashed")
                    .enabled(true)
                    .build();

            byte[] requestBytes = new byte[]{1, 2, 3};
            byte[] responseBytes = new byte[]{4, 5, 6};
            String replyTo = "reply.subject";

            Message message = mock(Message.class);
            when(message.getData()).thenReturn(requestBytes);
            when(message.getReplyTo()).thenReturn(replyTo);
            when(objectMapper.readValue(requestBytes, AccessKeyResolveRequest.class)).thenReturn(request);
            when(apiKeyService.resolveApiKey("AKIA-TEST")).thenReturn(response);
            when(objectMapper.writeValueAsBytes(response)).thenReturn(responseBytes);

            io.nats.client.MessageHandler handler = captureHandler();
            handler.onMessage(message);

            verify(apiKeyService).resolveApiKey("AKIA-TEST");
            verify(connection).publish(replyTo, responseBytes);
        }

        @Test
        void validMessage_publishesResponseToReplyToSubject() throws Exception {
            AccessKeyResolveRequest request = new AccessKeyResolveRequest();
            request.setAccessKeyId("AKIA-XYZ");

            AccessKeyResolveResponse response = AccessKeyResolveResponse.builder().build();
            byte[] raw = "{}".getBytes();
            byte[] responseBytes = "{\"userId\":null}".getBytes();

            Message message = mock(Message.class);
            when(message.getData()).thenReturn(raw);
            when(message.getReplyTo()).thenReturn("_INBOX.abc123");
            when(objectMapper.readValue(raw, AccessKeyResolveRequest.class)).thenReturn(request);
            when(apiKeyService.resolveApiKey(anyString())).thenReturn(response);
            when(objectMapper.writeValueAsBytes(response)).thenReturn(responseBytes);

            io.nats.client.MessageHandler handler = captureHandler();
            handler.onMessage(message);

            verify(connection).publish(eq("_INBOX.abc123"), eq(responseBytes));
        }

        @Test
        void validMessage_callsResolveApiKeyWithCorrectAccessKeyId() throws Exception {
            AccessKeyResolveRequest request = new AccessKeyResolveRequest();
            request.setAccessKeyId("AKIA-SPECIFIC");

            Message message = mock(Message.class);
            when(message.getData()).thenReturn(new byte[0]);
            when(message.getReplyTo()).thenReturn("reply");
            when(objectMapper.readValue(new byte[0], AccessKeyResolveRequest.class)).thenReturn(request);
            when(apiKeyService.resolveApiKey("AKIA-SPECIFIC"))
                    .thenReturn(AccessKeyResolveResponse.builder().build());
            when(objectMapper.writeValueAsBytes(any())).thenReturn(new byte[0]);

            io.nats.client.MessageHandler handler = captureHandler();
            handler.onMessage(message);

            verify(apiKeyService).resolveApiKey("AKIA-SPECIFIC");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // MessageHandler — error paths (must NOT throw; errors are caught internally)
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    class MessageHandlerErrorPaths {

        @Test
        void deserializationFailure_doesNotThrow() throws Exception {
            Message message = mock(Message.class);
            when(message.getData()).thenReturn("bad-json".getBytes());
            when(objectMapper.readValue(any(byte[].class), eq(AccessKeyResolveRequest.class)))
                    .thenThrow(new com.fasterxml.jackson.core.JsonProcessingException("parse error") {});

            io.nats.client.MessageHandler handler = captureHandler();

            assertThatCode(() -> handler.onMessage(message)).doesNotThrowAnyException();
        }

        @Test
        void deserializationFailure_doesNotPublishReply() throws Exception {
            Message message = mock(Message.class);
            when(message.getData()).thenReturn("bad-json".getBytes());
            when(objectMapper.readValue(any(byte[].class), eq(AccessKeyResolveRequest.class)))
                    .thenThrow(new com.fasterxml.jackson.core.JsonProcessingException("parse error") {});

            io.nats.client.MessageHandler handler = captureHandler();
            handler.onMessage(message);

            verify(connection, never()).publish(anyString(), any(byte[].class));
            verifyNoInteractions(apiKeyService);
        }

        @Test
        void apiKeyServiceThrows_doesNotThrow() throws Exception {
            AccessKeyResolveRequest request = new AccessKeyResolveRequest();
            request.setAccessKeyId("AKIA-BAD");

            Message message = mock(Message.class);
            when(message.getData()).thenReturn(new byte[0]);
            when(objectMapper.readValue(any(byte[].class), eq(AccessKeyResolveRequest.class))).thenReturn(request);
            when(apiKeyService.resolveApiKey("AKIA-BAD"))
                    .thenThrow(new RuntimeException("DB connection lost"));

            io.nats.client.MessageHandler handler = captureHandler();

            assertThatCode(() -> handler.onMessage(message)).doesNotThrowAnyException();
        }

        @Test
        void apiKeyServiceThrows_doesNotPublishReply() throws Exception {
            AccessKeyResolveRequest request = new AccessKeyResolveRequest();
            request.setAccessKeyId("AKIA-ERR");

            Message message = mock(Message.class);
            when(message.getData()).thenReturn(new byte[0]);
            when(objectMapper.readValue(any(byte[].class), eq(AccessKeyResolveRequest.class))).thenReturn(request);
            when(apiKeyService.resolveApiKey("AKIA-ERR"))
                    .thenThrow(new RuntimeException("failure"));

            io.nats.client.MessageHandler handler = captureHandler();
            handler.onMessage(message);

            verify(connection, never()).publish(anyString(), any(byte[].class));
        }

        @Test
        void serializationFailure_doesNotThrow() throws Exception {
            AccessKeyResolveRequest request = new AccessKeyResolveRequest();
            request.setAccessKeyId("AKIA-SER");

            AccessKeyResolveResponse response = AccessKeyResolveResponse.builder().build();

            Message message = mock(Message.class);
            when(message.getData()).thenReturn(new byte[0]);
            when(objectMapper.readValue(any(byte[].class), eq(AccessKeyResolveRequest.class))).thenReturn(request);
            when(apiKeyService.resolveApiKey("AKIA-SER")).thenReturn(response);
            when(objectMapper.writeValueAsBytes(response))
                    .thenThrow(new com.fasterxml.jackson.core.JsonProcessingException("serialize error") {});

            io.nats.client.MessageHandler handler = captureHandler();

            assertThatCode(() -> handler.onMessage(message)).doesNotThrowAnyException();
        }

        @Test
        void serializationFailure_doesNotPublishReply() throws Exception {
            AccessKeyResolveRequest request = new AccessKeyResolveRequest();
            request.setAccessKeyId("AKIA-SER2");

            AccessKeyResolveResponse response = AccessKeyResolveResponse.builder().build();

            Message message = mock(Message.class);
            when(message.getData()).thenReturn(new byte[0]);
            when(objectMapper.readValue(any(byte[].class), eq(AccessKeyResolveRequest.class))).thenReturn(request);
            when(apiKeyService.resolveApiKey("AKIA-SER2")).thenReturn(response);
            when(objectMapper.writeValueAsBytes(response))
                    .thenThrow(new com.fasterxml.jackson.core.JsonProcessingException("serialize error") {});

            io.nats.client.MessageHandler handler = captureHandler();
            handler.onMessage(message);

            verify(connection, never()).publish(anyString(), any(byte[].class));
        }
    }
}