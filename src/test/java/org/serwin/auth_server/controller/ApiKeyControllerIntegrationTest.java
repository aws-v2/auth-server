package org.serwin.auth_server.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.serwin.auth_server.BaseIntegrationTest;
import org.serwin.auth_server.dto.CreateApiKeyRequest;
import org.serwin.auth_server.repository.UserRepository;
import org.serwin.auth_server.entities.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ApiKeyControllerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @WithMockUser(username = "test@example.com")
    void createAndListApiKey_success() throws Exception {
        // Setup user
        User user = new User();
        user.setEmail("test@example.com");
        user.setPassword("password");
        user.setEnabled(true);
        user.setEmailVerified(true);
        userRepository.save(user);

        CreateApiKeyRequest request = new CreateApiKeyRequest();
        request.setName("Integration Key");

        // Create
        mockMvc.perform(post("/api/v1/auth/api-keys")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessKeyId").exists())
                .andExpect(jsonPath("$.name").value("Integration Key"));

        // List
        mockMvc.perform(get("/api/v1/auth/api-keys"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Integration Key"));
    }
}
