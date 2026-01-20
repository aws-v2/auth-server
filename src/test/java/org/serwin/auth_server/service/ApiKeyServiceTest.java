package org.serwin.auth_server.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.serwin.auth_server.dto.ApiKeyResponse;
import org.serwin.auth_server.dto.CreateApiKeyRequest;
import org.serwin.auth_server.entities.ApiKey;
import org.serwin.auth_server.entities.User;
import org.serwin.auth_server.repository.ApiKeyRepository;
import org.serwin.auth_server.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApiKeyServiceTest {

    @Mock
    private ApiKeyRepository apiKeyRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private ApiKeyService apiKeyService;

    @Test
    void generateApiKey_ValidUser_ReturnsKey() {
        String email = "user@example.com";
        CreateApiKeyRequest request = new CreateApiKeyRequest();
        request.setName("Test Key");

        User user = new User();
        user.setEmail(email);
        user.setEmailVerified(true);

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(apiKeyRepository.existsByAccessKeyId(any())).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("encoded-secret");
        when(apiKeyRepository.save(any(ApiKey.class))).thenAnswer(invocation -> {
            ApiKey key = invocation.getArgument(0);
            key.setId(UUID.randomUUID());
            key.setCreatedAt(java.time.LocalDateTime.now());
            return key;
        });

        ApiKeyResponse response = apiKeyService.generateApiKey(email, request);

        assertNotNull(response.getAccessKeyId());
        assertNotNull(response.getSecretAccessKey());
        assertTrue(response.getAccessKeyId().startsWith("AKIA"));
        verify(apiKeyRepository).save(any(ApiKey.class));
    }

    @Test
    void generateApiKey_UnverifiedUser_ThrowsException() {
        String email = "unverified@example.com";
        CreateApiKeyRequest request = new CreateApiKeyRequest();

        User user = new User();
        user.setEmail(email);
        user.setEmailVerified(false);

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));

        assertThrows(RuntimeException.class, () -> apiKeyService.generateApiKey(email, request));
        verify(apiKeyRepository, never()).save(any(ApiKey.class));
    }

    @Test
    void revokeApiKey_ValidKey_Revokes() {
        UUID keyId = UUID.randomUUID();
        String email = "user@example.com";

        User user = new User();
        user.setEmail(email);

        ApiKey apiKey = new ApiKey();
        apiKey.setId(keyId);
        apiKey.setUser(user);
        apiKey.setEnabled(true);
        apiKey.setAccessKeyId("AKIA123");

        when(apiKeyRepository.findById(keyId)).thenReturn(Optional.of(apiKey));

        String revokedId = apiKeyService.revokeApiKey(keyId, email);

        assertEquals("AKIA1233", revokedId);
        assertFalse(apiKey.isEnabled());
        verify(apiKeyRepository).save(apiKey);
    }

    @Test
    void deleteApiKey_ValidKey_Deletes() {
        UUID keyId = UUID.randomUUID();
        String email = "user@example.com";

        User user = new User();
        user.setEmail(email);

        ApiKey apiKey = new ApiKey();
        apiKey.setId(keyId);
        apiKey.setUser(user);
        apiKey.setAccessKeyId("AKIA123");

        when(apiKeyRepository.findById(keyId)).thenReturn(Optional.of(apiKey));

        apiKeyService.deleteApiKey(keyId, email);

        verify(apiKeyRepository).delete(apiKey);
    }
}
