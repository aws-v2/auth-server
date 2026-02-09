package org.serwin.auth_server.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
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

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ApiKeyController.class)
@Import(SecurityConfig.class)
class ApiKeyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ApiKeyService apiKeyService;

    @MockBean
    private NatsService natsService;

    @MockBean
    private JwtUtil jwtUtil;

    @MockBean
    private org.serwin.auth_server.repository.ClientRepository clientRepository;

    @MockBean
    private org.serwin.auth_server.service.RateLimitingService rateLimitingService;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        when(rateLimitingService.isAllowed(any(), any())).thenReturn(true);
    }

    @Test
    @WithMockUser(username = "test@example.com")
    void createApiKey_success() throws Exception {
        CreateApiKeyRequest request = new CreateApiKeyRequest();
        request.setName("Test Key");

        ApiKeyResponse response = ApiKeyResponse.builder()
                .accessKeyId("AKIA...")
                .secretAccessKey("secret")
                .name("Test Key")
                .build();

        when(apiKeyService.generateApiKey(eq("test@example.com"), any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/auth/api-keys")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessKeyId").value("AKIA..."))
                .andExpect(jsonPath("$.name").value("Test Key"));

        verify(natsService, times(1)).publish(eq("apikey"), eq("created"), any());
    }

    @Test
    @WithMockUser(username = "test@example.com")
    void listApiKeys_success() throws Exception {
        ApiKeyResponse key = ApiKeyResponse.builder()
                .accessKeyId("AKIA...")
                .name("Test Key")
                .build();

        when(apiKeyService.listUserApiKeys("test@example.com")).thenReturn(List.of(key));

        mockMvc.perform(get("/api/v1/auth/api-keys"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].accessKeyId").value("AKIA..."))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @WithMockUser(username = "test@example.com")
    void revokeApiKey_success() throws Exception {
        UUID keyId = UUID.randomUUID();
        when(apiKeyService.revokeApiKey(eq(keyId), eq("test@example.com"))).thenReturn("AKIA...");

        mockMvc.perform(delete("/api/v1/auth/api-keys/" + keyId)
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("API key revoked"));

        verify(natsService, times(1)).publish(eq("apikey"), eq("revoked"), any());
    }

    @Test
    void createApiKey_unauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/auth/api-keys")
                .with(csrf()))
                .andExpect(status().isForbidden());
    }
}