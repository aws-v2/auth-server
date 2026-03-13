package org.serwin.auth_server.service;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.serwin.auth_server.dto.AccessKeyResolveResponse;
import org.serwin.auth_server.dto.ApiKeyResponse;
import org.serwin.auth_server.dto.CreateApiKeyRequest;
import org.serwin.auth_server.entities.ApiKey;
import org.serwin.auth_server.entities.User;
import org.serwin.auth_server.repository.ApiKeyRepository;
import org.serwin.auth_server.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApiKeyServiceTest {

    @Mock private ApiKeyRepository apiKeyRepository;
    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;

    @InjectMocks
    private ApiKeyService apiKeyService;

    // ── helpers ────────────────────────────────────────────────────────────────

    private User verifiedUser(String email) {
        User u = new User();
        u.setId(UUID.randomUUID());
        u.setEmail(email);
        u.setEmailVerified(true);
        return u;
    }

    private User unverifiedUser(String email) {
        User u = new User();
        u.setId(UUID.randomUUID());
        u.setEmail(email);
        u.setEmailVerified(false);
        return u;
    }

    private ApiKey savedApiKey(User owner, String accessKeyId) {
        ApiKey key = new ApiKey();
        key.setId(UUID.randomUUID());
        key.setUser(owner);
        key.setAccessKeyId(accessKeyId);
        key.setSecretKeyHash("hashed-secret");
        key.setName("Test Key");
        key.setEnabled(true);
        key.setCreatedAt(LocalDateTime.now());
        return key;
    }

    /** Configures the save stub to inject id + createdAt, mimicking JPA behaviour. */
    private void stubSave() {
        when(apiKeyRepository.save(any(ApiKey.class))).thenAnswer(inv -> {
            ApiKey k = inv.getArgument(0);
            if (k.getId() == null) k.setId(UUID.randomUUID());
            if (k.getCreatedAt() == null) k.setCreatedAt(LocalDateTime.now());
            return k;
        });
    }

    private CreateApiKeyRequest basicRequest(String name) {
        CreateApiKeyRequest r = new CreateApiKeyRequest();
        r.setName(name);
        return r;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // generateApiKey
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    class GenerateApiKey {

        @Test
        void success_returnsResponseWithExpectedFields() {
            User user = verifiedUser("user@example.com");
            when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
            when(apiKeyRepository.existsByAccessKeyId(any())).thenReturn(false);
            when(passwordEncoder.encode(any())).thenReturn("encoded-secret");
            stubSave();

            ApiKeyResponse response = apiKeyService.generateApiKey("user@example.com", basicRequest("My Key"));

            assertNotNull(response.getAccessKeyId());
            assertNotNull(response.getSecretAccessKey());
            assertTrue(response.getAccessKeyId().startsWith("AKIA"));
            assertEquals("My Key", response.getName());
            assertEquals("encoded-secret", response.getSecretKeyHash());
            assertEquals("Save this secret key now. You cannot retrieve it again.", response.getWarning());
            assertTrue(response.isEnabled());
        }

        @Test
        void success_accessKeyIdIs20CharsTotal() {
            // "AKIA" + 16 random alphanumeric = 20 chars
            User user = verifiedUser("user@example.com");
            when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
            when(apiKeyRepository.existsByAccessKeyId(any())).thenReturn(false);
            when(passwordEncoder.encode(any())).thenReturn("hash");
            stubSave();

            ApiKeyResponse response = apiKeyService.generateApiKey("user@example.com", basicRequest("Key"));

            assertEquals(20, response.getAccessKeyId().length());
            assertTrue(response.getAccessKeyId().matches("AKIA[A-Z0-9]{16}"));
        }

        @Test
        void success_secretKeyIsNot_storedInPlainText() {
            // The response secretAccessKey must differ from the stored secretKeyHash
            User user = verifiedUser("user@example.com");
            when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
            when(apiKeyRepository.existsByAccessKeyId(any())).thenReturn(false);
            when(passwordEncoder.encode(any())).thenReturn("bcrypt-hash");
            stubSave();

            ApiKeyResponse response = apiKeyService.generateApiKey("user@example.com", basicRequest("Key"));

            assertNotEquals(response.getSecretAccessKey(), response.getSecretKeyHash());
            assertEquals("bcrypt-hash", response.getSecretKeyHash());
        }

        @Test
        void success_persistsCorrectFieldsViaArgumentCaptor() {
            User user = verifiedUser("user@example.com");
            when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
            when(apiKeyRepository.existsByAccessKeyId(any())).thenReturn(false);
            when(passwordEncoder.encode(any())).thenReturn("hash");
            stubSave();

            CreateApiKeyRequest req = basicRequest("Prod");
            req.setDescription("Production key");

            apiKeyService.generateApiKey("user@example.com", req);

            ArgumentCaptor<ApiKey> captor = ArgumentCaptor.forClass(ApiKey.class);
            verify(apiKeyRepository).save(captor.capture());

            ApiKey saved = captor.getValue();
            assertEquals(user, saved.getUser());
            assertEquals("Prod", saved.getName());
            assertEquals("Production key", saved.getDescription());
            assertNotNull(saved.getAccessKeyId());
            assertEquals("hash", saved.getSecretKeyHash());
            assertNull(saved.getExpiresAt()); // no expiry set
        }

        @Test
        void withExpiryDate_parsesAndPersistsExpiresAt() {
            User user = verifiedUser("user@example.com");
            when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
            when(apiKeyRepository.existsByAccessKeyId(any())).thenReturn(false);
            when(passwordEncoder.encode(any())).thenReturn("hash");
            stubSave();

            CreateApiKeyRequest req = basicRequest("Expiring Key");
            req.setExpiresAt("2099-12-31T23:59:59");

            ApiKeyResponse response = apiKeyService.generateApiKey("user@example.com", req);

            assertNotNull(response.getExpiresAt());
            assertTrue(response.getExpiresAt().contains("2099-12-31"));

            ArgumentCaptor<ApiKey> captor = ArgumentCaptor.forClass(ApiKey.class);
            verify(apiKeyRepository).save(captor.capture());
            assertNotNull(captor.getValue().getExpiresAt());
        }

        @Test
        void withAllowedActionsAndResources_persistedCorrectly() {
            User user = verifiedUser("user@example.com");
            when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
            when(apiKeyRepository.existsByAccessKeyId(any())).thenReturn(false);
            when(passwordEncoder.encode(any())).thenReturn("hash");
            stubSave();

            CreateApiKeyRequest req = basicRequest("Scoped Key");
            req.setAllowedActions(List.of("read", "write").toArray(new String[0]));
            req.setAllowedResources(List.of("resource-1", "resource-2").toArray(new String[0]));

            apiKeyService.generateApiKey("user@example.com", req);

            ArgumentCaptor<ApiKey> captor = ArgumentCaptor.forClass(ApiKey.class);
            verify(apiKeyRepository).save(captor.capture());

            assertThat(captor.getValue().getAllowedActions()).containsExactly("read", "write");
            assertThat(captor.getValue().getAllowedResources()).containsExactly("resource-1", "resource-2");
        }

        @Test
        void accessKeyIdCollision_regeneratesUntilUnique() {
            User user = verifiedUser("user@example.com");
            when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
            // First two generated IDs collide, third is unique
            when(apiKeyRepository.existsByAccessKeyId(any()))
                    .thenReturn(true, true, false);
            when(passwordEncoder.encode(any())).thenReturn("hash");
            stubSave();

            ApiKeyResponse response = apiKeyService.generateApiKey("user@example.com", basicRequest("Key"));

            assertNotNull(response.getAccessKeyId());
            // existsByAccessKeyId called 3 times (2 collisions + 1 success)
            verify(apiKeyRepository, times(3)).existsByAccessKeyId(any());
        }

        @Test
        void userNotFound_throwsRuntimeException() {
            when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> apiKeyService.generateApiKey("ghost@example.com", basicRequest("Key")));

            assertEquals("User not found", ex.getMessage());
            verify(apiKeyRepository, never()).save(any());
        }

        @Test
        void unverifiedEmail_throwsRuntimeExceptionWithMessage() {
            User user = unverifiedUser("unverified@example.com");
            when(userRepository.findByEmail("unverified@example.com")).thenReturn(Optional.of(user));

            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> apiKeyService.generateApiKey("unverified@example.com", basicRequest("Key")));

            assertEquals("Email must be verified to create API keys", ex.getMessage());
            verify(apiKeyRepository, never()).save(any());
            verify(apiKeyRepository, never()).existsByAccessKeyId(any());
        }

        @Test
        void invalidExpiryDateFormat_throwsException() {
            User user = verifiedUser("user@example.com");
            when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
            when(apiKeyRepository.existsByAccessKeyId(any())).thenReturn(false);
            when(passwordEncoder.encode(any())).thenReturn("hash");

            CreateApiKeyRequest req = basicRequest("Key");
            req.setExpiresAt("not-a-date");

            assertThrows(Exception.class,
                    () -> apiKeyService.generateApiKey("user@example.com", req));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // listUserApiKeys
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    class ListUserApiKeys {

        @Test
        void success_returnsListOfMappedResponses() {
            User user = verifiedUser("user@example.com");
            ApiKey k1 = savedApiKey(user, "AKIA001");
            ApiKey k2 = savedApiKey(user, "AKIA002");
            k2.setName("Second Key");

            when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
            when(apiKeyRepository.findByUser(user)).thenReturn(List.of(k1, k2));

            List<ApiKeyResponse> result = apiKeyService.listUserApiKeys("user@example.com");

            assertEquals(2, result.size());
            assertThat(result).extracting(ApiKeyResponse::getAccessKeyId)
                    .containsExactlyInAnyOrder("AKIA001", "AKIA002");
        }

        @Test
        void success_mappedResponseDoesNotContainSecretKey() {
            User user = verifiedUser("user@example.com");
            ApiKey key = savedApiKey(user, "AKIA001");

            when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
            when(apiKeyRepository.findByUser(user)).thenReturn(List.of(key));

            List<ApiKeyResponse> result = apiKeyService.listUserApiKeys("user@example.com");

            assertNull(result.get(0).getSecretAccessKey(),
                    "Secret key must not be returned in list responses");
        }

        @Test
        void success_mappedResponseIncludesLastUsedAt() {
            User user = verifiedUser("user@example.com");
            ApiKey key = savedApiKey(user, "AKIA001");
            key.setLastUsedAt(LocalDateTime.of(2024, 6, 15, 10, 30));

            when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
            when(apiKeyRepository.findByUser(user)).thenReturn(List.of(key));

            List<ApiKeyResponse> result = apiKeyService.listUserApiKeys("user@example.com");

            assertNotNull(result.get(0).getLastUsedAt());
            assertTrue(result.get(0).getLastUsedAt().contains("2024-06-15"));
        }

        @Test
        void success_keyWithNullLastUsedAt_mapsToNull() {
            User user = verifiedUser("user@example.com");
            ApiKey key = savedApiKey(user, "AKIA001");
            key.setLastUsedAt(null);

            when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
            when(apiKeyRepository.findByUser(user)).thenReturn(List.of(key));

            List<ApiKeyResponse> result = apiKeyService.listUserApiKeys("user@example.com");

            assertNull(result.get(0).getLastUsedAt());
        }

        @Test
        void success_keyWithNullExpiresAt_mapsToNull() {
            User user = verifiedUser("user@example.com");
            ApiKey key = savedApiKey(user, "AKIA001");
            key.setExpiresAt(null);

            when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
            when(apiKeyRepository.findByUser(user)).thenReturn(List.of(key));

            List<ApiKeyResponse> result = apiKeyService.listUserApiKeys("user@example.com");

            assertNull(result.get(0).getExpiresAt());
        }

        @Test
        void userHasNoKeys_returnsEmptyList() {
            User user = verifiedUser("user@example.com");
            when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
            when(apiKeyRepository.findByUser(user)).thenReturn(List.of());

            List<ApiKeyResponse> result = apiKeyService.listUserApiKeys("user@example.com");

            assertNotNull(result);
            assertTrue(result.isEmpty());
        }

        @Test
        void userNotFound_throwsRuntimeException() {
            when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> apiKeyService.listUserApiKeys("ghost@example.com"));

            assertEquals("User not found", ex.getMessage());
            verifyNoInteractions(apiKeyRepository);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // revokeApiKey
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    class RevokeApiKey {

        @Test
        void success_disablesKeyAndReturnsAccessKeyId() {
            UUID keyId = UUID.randomUUID();
            User user = verifiedUser("user@example.com");
            ApiKey key = savedApiKey(user, "AKIA-REVOKE");

            when(apiKeyRepository.findById(keyId)).thenReturn(Optional.of(key));

            String returned = apiKeyService.revokeApiKey(keyId, "user@example.com");

            assertEquals("AKIA-REVOKE", returned);
            assertFalse(key.isEnabled());
            verify(apiKeyRepository).save(key);
        }

        @Test
        void success_alreadyDisabledKey_canBeRevokedAgain() {
            UUID keyId = UUID.randomUUID();
            User user = verifiedUser("user@example.com");
            ApiKey key = savedApiKey(user, "AKIA-ALREADY");
            key.setEnabled(false);

            when(apiKeyRepository.findById(keyId)).thenReturn(Optional.of(key));

            String returned = apiKeyService.revokeApiKey(keyId, "user@example.com");

            assertEquals("AKIA-ALREADY", returned);
            assertFalse(key.isEnabled());
            verify(apiKeyRepository).save(key);
        }

        @Test
        void keyNotFound_throwsRuntimeException() {
            UUID keyId = UUID.randomUUID();
            when(apiKeyRepository.findById(keyId)).thenReturn(Optional.empty());

            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> apiKeyService.revokeApiKey(keyId, "user@example.com"));

            assertEquals("API Key not found", ex.getMessage());
            verify(apiKeyRepository, never()).save(any());
        }

        @Test
        void keyBelongsToDifferentUser_throwsUnauthorized() {
            UUID keyId = UUID.randomUUID();
            User owner = verifiedUser("owner@example.com");
            ApiKey key = savedApiKey(owner, "AKIA-OTHER");

            when(apiKeyRepository.findById(keyId)).thenReturn(Optional.of(key));

            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> apiKeyService.revokeApiKey(keyId, "attacker@example.com"));

            assertEquals("Not authorized to revoke this key", ex.getMessage());
            verify(apiKeyRepository, never()).save(any());
        }

        @Test
        void revokedKey_isStillPersisted_notDeleted() {
            UUID keyId = UUID.randomUUID();
            User user = verifiedUser("user@example.com");
            ApiKey key = savedApiKey(user, "AKIA-KEEP");

            when(apiKeyRepository.findById(keyId)).thenReturn(Optional.of(key));

            apiKeyService.revokeApiKey(keyId, "user@example.com");

            verify(apiKeyRepository).save(key);
            verify(apiKeyRepository, never()).delete(any());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // deleteApiKey
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    class DeleteApiKey {

        @Test
        void success_deletesKey() {
            UUID keyId = UUID.randomUUID();
            User user = verifiedUser("user@example.com");
            ApiKey key = savedApiKey(user, "AKIA-DEL");

            when(apiKeyRepository.findById(keyId)).thenReturn(Optional.of(key));

            apiKeyService.deleteApiKey(keyId, "user@example.com");

            verify(apiKeyRepository).delete(key);
            verify(apiKeyRepository, never()).save(any());
        }

        @Test
        void keyNotFound_throwsRuntimeException() {
            UUID keyId = UUID.randomUUID();
            when(apiKeyRepository.findById(keyId)).thenReturn(Optional.empty());

            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> apiKeyService.deleteApiKey(keyId, "user@example.com"));

            assertEquals("API Key not found", ex.getMessage());
            verify(apiKeyRepository, never()).delete(any());
        }

        @Test
        void keyBelongsToDifferentUser_throwsUnauthorized() {
            UUID keyId = UUID.randomUUID();
            User owner = verifiedUser("owner@example.com");
            ApiKey key = savedApiKey(owner, "AKIA-OWN");

            when(apiKeyRepository.findById(keyId)).thenReturn(Optional.of(key));

            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> apiKeyService.deleteApiKey(keyId, "attacker@example.com"));

            assertEquals("Not authorized to delete this key", ex.getMessage());
            verify(apiKeyRepository, never()).delete(any());
        }

        @Test
        void delete_doesNotDisableKey_justRemoves() {
            UUID keyId = UUID.randomUUID();
            User user = verifiedUser("user@example.com");
            ApiKey key = savedApiKey(user, "AKIA-GONE");

            when(apiKeyRepository.findById(keyId)).thenReturn(Optional.of(key));

            apiKeyService.deleteApiKey(keyId, "user@example.com");

            // Should delete, not save (i.e. not the revoke path)
            verify(apiKeyRepository).delete(key);
            verify(apiKeyRepository, never()).save(any());
            assertTrue(key.isEnabled(), "enabled flag should not have been touched");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // updateLastUsed
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    class UpdateLastUsed {

        @Test
        void keyExists_updatesLastUsedAt() {
            User user = verifiedUser("user@example.com");
            ApiKey key = savedApiKey(user, "AKIA-USED");
            key.setLastUsedAt(null);

            when(apiKeyRepository.findByAccessKeyId("AKIA-USED")).thenReturn(Optional.of(key));

            apiKeyService.updateLastUsed("AKIA-USED");

            assertNotNull(key.getLastUsedAt());
            assertThat(key.getLastUsedAt()).isBeforeOrEqualTo(LocalDateTime.now());
            verify(apiKeyRepository).save(key);
        }

        @Test
        void keyExists_overwritesPreviousLastUsedAt() {
            User user = verifiedUser("user@example.com");
            ApiKey key = savedApiKey(user, "AKIA-USED");
            LocalDateTime previousTime = LocalDateTime.now().minusDays(5);
            key.setLastUsedAt(previousTime);

            when(apiKeyRepository.findByAccessKeyId("AKIA-USED")).thenReturn(Optional.of(key));

            apiKeyService.updateLastUsed("AKIA-USED");

            assertThat(key.getLastUsedAt()).isAfter(previousTime);
            verify(apiKeyRepository).save(key);
        }

        @Test
        void keyNotFound_doesNothingGracefully() {
            when(apiKeyRepository.findByAccessKeyId("AKIA-MISSING")).thenReturn(Optional.empty());

            // Should not throw
            assertDoesNotThrow(() -> apiKeyService.updateLastUsed("AKIA-MISSING"));
            verify(apiKeyRepository, never()).save(any());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // resolveApiKey
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    class ResolveApiKey {

        @Test
        void keyExists_activeAndNotExpired_returnsEnabledResponse() {
            User user = verifiedUser("user@example.com");
            ApiKey key = savedApiKey(user, "AKIA-RESOLVE");
            key.setEnabled(true);
            // isExpired() returns false when expiresAt is null or in the future
            key.setExpiresAt(LocalDateTime.now().plusYears(1));

            when(apiKeyRepository.findByAccessKeyId("AKIA-RESOLVE")).thenReturn(Optional.of(key));

            AccessKeyResolveResponse response = apiKeyService.resolveApiKey("AKIA-RESOLVE");

            assertEquals(user.getId().toString(), response.getUserId());
            assertEquals(key.getSecretKeyHash(), response.getSecretKeyHash());
            assertTrue(response.isEnabled());
        }

        @Test
        void keyExists_butDisabled_returnsEnabledFalse() {
            User user = verifiedUser("user@example.com");
            ApiKey key = savedApiKey(user, "AKIA-DISABLED");
            key.setEnabled(false);

            when(apiKeyRepository.findByAccessKeyId("AKIA-DISABLED")).thenReturn(Optional.of(key));

            AccessKeyResolveResponse response = apiKeyService.resolveApiKey("AKIA-DISABLED");

            assertFalse(response.isEnabled());
            assertEquals(user.getId().toString(), response.getUserId());
        }

        @Test
        void keyExists_butExpired_returnsEnabledFalse() {
            User user = verifiedUser("user@example.com");
            ApiKey key = savedApiKey(user, "AKIA-EXPIRED");
            key.setEnabled(true);
            key.setExpiresAt(LocalDateTime.now().minusDays(1)); // expired yesterday

            when(apiKeyRepository.findByAccessKeyId("AKIA-EXPIRED")).thenReturn(Optional.of(key));

            AccessKeyResolveResponse response = apiKeyService.resolveApiKey("AKIA-EXPIRED");

            // enabled = key.isEnabled() && !key.isExpired() → true && !true → false
            assertFalse(response.isEnabled());
        }

        @Test
        void keyNotFound_returnsEmptyResponse() {
            when(apiKeyRepository.findByAccessKeyId("AKIA-GONE")).thenReturn(Optional.empty());

            AccessKeyResolveResponse response = apiKeyService.resolveApiKey("AKIA-GONE");

            assertNotNull(response);
            assertNull(response.getUserId());
            assertNull(response.getSecretKeyHash());
        }

        @Test
        void keyFound_secretKeyHashIsFromEntity() {
            User user = verifiedUser("user@example.com");
            ApiKey key = savedApiKey(user, "AKIA-HASH");
            key.setSecretKeyHash("$2a$12$specific-bcrypt-hash");

            when(apiKeyRepository.findByAccessKeyId("AKIA-HASH")).thenReturn(Optional.of(key));

            AccessKeyResolveResponse response = apiKeyService.resolveApiKey("AKIA-HASH");

            assertEquals("$2a$12$specific-bcrypt-hash", response.getSecretKeyHash());
        }
    }
}