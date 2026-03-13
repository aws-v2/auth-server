package org.serwin.auth_server.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.serwin.auth_server.config.SecurityConfig;
import org.serwin.auth_server.dto.ApiKeyResponse;
import org.serwin.auth_server.dto.CreateApiKeyRequest;
import org.serwin.auth_server.service.ApiKeyService;
import org.serwin.auth_server.service.NatsService;
import org.serwin.auth_server.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ApiKeyController.class)
@Import(SecurityConfig.class)
class ApiKeyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean private ApiKeyService apiKeyService;
    @MockBean private NatsService natsService;
    @MockBean private JwtUtil jwtUtil;
    @MockBean private org.serwin.auth_server.repository.ClientRepository clientRepository;
    @MockBean private org.serwin.auth_server.service.RateLimitingService rateLimitingService;

    @Autowired
    private ObjectMapper objectMapper;

    // ── helpers ────────────────────────────────────────────────────────────────

    private static final String BASE_URL = "/api/v1/auth/api-keys";
    private static final String TEST_EMAIL = "test@example.com";

  private ApiKeyResponse buildApiKeyResponse(String accessKeyId, String name) {
    return ApiKeyResponse.builder()
            .accessKeyId(accessKeyId)
            .secretAccessKey("secret-value")
            .secretKeyHash("hashed-secret")
            .name(name)
            .userId(UUID.fromString("00000000-0000-0000-0000-000000000000").toString())
            .createdAt(LocalDateTime.now().toString())
            .build();
}

    private CreateApiKeyRequest buildCreateRequest(String name) {
        CreateApiKeyRequest req = new CreateApiKeyRequest();
        req.setName(name);
        return req;
    }

    @BeforeEach
    void setUp() {
        when(rateLimitingService.isAllowed(any(), any())).thenReturn(true);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // POST /api/v1/auth/api-keys
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    class CreateApiKey {

        @Test
        @WithMockUser(username = TEST_EMAIL)
        void success_returnsKeyAndPublishesEvent() throws Exception {
            ApiKeyResponse response = buildApiKeyResponse("AKIAIOSFODNN7EXAMPLE", "My Key");
            when(apiKeyService.generateApiKey(eq(TEST_EMAIL), any())).thenReturn(response);

            mockMvc.perform(post(BASE_URL)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(buildCreateRequest("My Key"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessKeyId").value("AKIAIOSFODNN7EXAMPLE"))
                    .andExpect(jsonPath("$.name").value("My Key"))
                    .andExpect(jsonPath("$.secretAccessKey").value("secret-value"));

            verify(natsService).publish(eq("apikey"), eq("created"), any());
        }

        @Test
        @WithMockUser(username = TEST_EMAIL)
        void success_natsPayloadContainsExpectedFields() throws Exception {
            ApiKeyResponse response = buildApiKeyResponse("AKIA123", "Prod Key");
            when(apiKeyService.generateApiKey(eq(TEST_EMAIL), any())).thenReturn(response);

            mockMvc.perform(post(BASE_URL)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(buildCreateRequest("Prod Key"))))
                    .andExpect(status().isOk());

            // Verify publish is called with the correct subject and action
            verify(natsService).publish(
                    eq("apikey"),
                    eq("created"),
                    argThat(payload -> {
                        @SuppressWarnings("unchecked")
                        var map = (java.util.Map<String, Object>) payload;
                        return map.containsKey("email")
                                && map.containsKey("accessKeyId")
                                && map.containsKey("keyName")
                                && map.containsKey("timestamp");
                    })
            );
        }

        @Test
        @WithMockUser(username = TEST_EMAIL)
        void serviceThrows_returns400WithErrorMessage() throws Exception {
            when(apiKeyService.generateApiKey(eq(TEST_EMAIL), any()))
                    .thenThrow(new RuntimeException("Key limit reached"));

            mockMvc.perform(post(BASE_URL)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(buildCreateRequest("Extra Key"))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("Key limit reached"));

            verifyNoInteractions(natsService);
        }

        @Test
        @WithMockUser(username = TEST_EMAIL)
        void duplicateKeyName_returns400() throws Exception {
            when(apiKeyService.generateApiKey(eq(TEST_EMAIL), any()))
                    .thenThrow(new RuntimeException("API key with this name already exists"));

            mockMvc.perform(post(BASE_URL)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(buildCreateRequest("Existing Key"))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("API key with this name already exists"));
        }

        @Test
        void unauthenticated_returns403() throws Exception {
            mockMvc.perform(post(BASE_URL)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(buildCreateRequest("Key"))))
                    .andExpect(status().isForbidden());

            verifyNoInteractions(apiKeyService, natsService);
        }


    }

    // ═══════════════════════════════════════════════════════════════════════════
    // GET /api/v1/auth/api-keys
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    class ListApiKeys {

        @Test
        @WithMockUser(username = TEST_EMAIL)
        void success_returnsListOfKeys() throws Exception {
            List<ApiKeyResponse> keys = List.of(
                    buildApiKeyResponse("AKIA001", "Key One"),
                    buildApiKeyResponse("AKIA002", "Key Two")
            );
            when(apiKeyService.listUserApiKeys(TEST_EMAIL)).thenReturn(keys);

            mockMvc.perform(get(BASE_URL))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].accessKeyId").value("AKIA001"))
                    .andExpect(jsonPath("$[0].name").value("Key One"))
                    .andExpect(jsonPath("$[1].accessKeyId").value("AKIA002"))
                    .andExpect(jsonPath("$[1].name").value("Key Two"));
        }

        @Test
        @WithMockUser(username = TEST_EMAIL)
        void noKeys_returnsEmptyList() throws Exception {
            when(apiKeyService.listUserApiKeys(TEST_EMAIL)).thenReturn(Collections.emptyList());

            mockMvc.perform(get(BASE_URL))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));
        }

        @Test
        @WithMockUser(username = TEST_EMAIL)
        void singleKey_returnsListWithOneElement() throws Exception {
            when(apiKeyService.listUserApiKeys(TEST_EMAIL))
                    .thenReturn(List.of(buildApiKeyResponse("AKIA999", "Solo Key")));

            mockMvc.perform(get(BASE_URL))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].name").value("Solo Key"));
        }

        @Test
        @WithMockUser(username = TEST_EMAIL)
        void serviceThrows_returns401WithError() throws Exception {
            when(apiKeyService.listUserApiKeys(TEST_EMAIL))
                    .thenThrow(new RuntimeException("Session expired"));

            mockMvc.perform(get(BASE_URL))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error").value("Session expired"));
        }

        @Test
        void unauthenticated_returns403() throws Exception {
            mockMvc.perform(get(BASE_URL))
                    .andExpect(status().isForbidden());

            verifyNoInteractions(apiKeyService);
        }

        @Test
        @WithMockUser(username = TEST_EMAIL)
        void responseDoesNotExposeSecretAccessKey() throws Exception {
            // Secret key should only be returned on creation, not on list
            ApiKeyResponse key = buildApiKeyResponse("AKIA001", "Key One");
            when(apiKeyService.listUserApiKeys(TEST_EMAIL)).thenReturn(List.of(key));

            mockMvc.perform(get(BASE_URL))
                    .andExpect(status().isOk())
                    // secretAccessKey field should not be present (or be null) in list response
                    // Adjust this assertion based on your DTO design
                    .andExpect(jsonPath("$[0].accessKeyId").exists())
                    .andExpect(jsonPath("$[0].name").exists());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // DELETE /api/v1/auth/api-keys/{keyId}
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    class RevokeApiKey {

        @Test
        @WithMockUser(username = TEST_EMAIL)
        void success_returnsMessageAndPublishesEvent() throws Exception {
            UUID keyId = UUID.randomUUID();
            when(apiKeyService.revokeApiKey(eq(keyId), eq(TEST_EMAIL)))
                    .thenReturn("AKIAIOSFODNN7EXAMPLE");

            mockMvc.perform(delete(BASE_URL + "/" + keyId).with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("API key revoked"));

            verify(natsService).publish(eq("apikey"), eq("revoked"), any());
        }

        @Test
        @WithMockUser(username = TEST_EMAIL)
        void success_natsPayloadContainsKeyDetails() throws Exception {
            UUID keyId = UUID.randomUUID();
            when(apiKeyService.revokeApiKey(eq(keyId), eq(TEST_EMAIL)))
                    .thenReturn("AKIA_REVOKED");

            mockMvc.perform(delete(BASE_URL + "/" + keyId).with(csrf()))
                    .andExpect(status().isOk());

            verify(natsService).publish(
                    eq("apikey"),
                    eq("revoked"),
                    argThat(payload -> {
                        @SuppressWarnings("unchecked")
                        var map = (java.util.Map<String, Object>) payload;
                        return map.containsKey("email")
                                && map.containsKey("accessKeyId")
                                && map.containsKey("keyId")
                                && map.containsKey("timestamp");
                    })
            );
        }

        @Test
        @WithMockUser(username = TEST_EMAIL)
        void keyNotFound_returns400() throws Exception {
            UUID keyId = UUID.randomUUID();
            when(apiKeyService.revokeApiKey(eq(keyId), eq(TEST_EMAIL)))
                    .thenThrow(new RuntimeException("API key not found"));

            mockMvc.perform(delete(BASE_URL + "/" + keyId).with(csrf()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("API key not found"));

            verifyNoInteractions(natsService);
        }

        @Test
        @WithMockUser(username = TEST_EMAIL)
        void keyBelongsToAnotherUser_returns400() throws Exception {
            UUID keyId = UUID.randomUUID();
            when(apiKeyService.revokeApiKey(eq(keyId), eq(TEST_EMAIL)))
                    .thenThrow(new RuntimeException("Access denied"));

            mockMvc.perform(delete(BASE_URL + "/" + keyId).with(csrf()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("Access denied"));
        }

        @Test
        @WithMockUser(username = TEST_EMAIL)
        void invalidUuidFormat_returns400() throws Exception {
            // A non-UUID path variable causes UUID.fromString() to throw
            mockMvc.perform(delete(BASE_URL + "/not-a-valid-uuid").with(csrf()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").exists());

            verifyNoInteractions(apiKeyService, natsService);
        }

        @Test
        void unauthenticated_returns403() throws Exception {
            UUID keyId = UUID.randomUUID();
            mockMvc.perform(delete(BASE_URL + "/" + keyId).with(csrf()))
                    .andExpect(status().isForbidden());

            verifyNoInteractions(apiKeyService, natsService);
        }


    }
}