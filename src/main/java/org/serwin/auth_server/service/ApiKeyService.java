package org.serwin.auth_server.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.serwin.auth_server.dto.ApiKeyResponse;
import org.serwin.auth_server.dto.AccessKeyResolveResponse;
import org.serwin.auth_server.dto.CreateApiKeyRequest;
import org.serwin.auth_server.entities.ApiKey;
import org.serwin.auth_server.entities.User;
import org.serwin.auth_server.repository.ApiKeyRepository;
import org.serwin.auth_server.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ApiKeyService {
    private static final Logger auditLog = LoggerFactory.getLogger("AUDIT");

    private final ApiKeyRepository apiKeyRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public ApiKeyResponse generateApiKey(String email, CreateApiKeyRequest request) {
        log.info("Generating API key for user: {}, name: {}", email, request.getName());

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> {
                    log.error("User not found while generating API key: {}", email);
                    return new RuntimeException("User not found");
                });

        if (!user.isEmailVerified()) {
            log.warn("API key creation attempted by unverified user: {}", email);
            throw new RuntimeException("Email must be verified to create API keys");
        }

        // Generate Access Key ID
        String accessKeyId = "AKIA" + generateRandomAlphanumeric(16);
        while (apiKeyRepository.existsByAccessKeyId(accessKeyId)) {
            log.debug("Access key ID collision, regenerating");
            accessKeyId = "AKIA" + generateRandomAlphanumeric(16);
        }

        // Generate Secret Key
        byte[] secretBytes = new byte[30];
        new SecureRandom().nextBytes(secretBytes);
        String secretAccessKey = Base64.getEncoder().encodeToString(secretBytes);

        // Hash Secret
        String secretHash = passwordEncoder.encode(secretAccessKey);

        LocalDateTime expiresAt = null;
        if (request.getExpiresAt() != null) {
            expiresAt = LocalDateTime.parse(request.getExpiresAt(), DateTimeFormatter.ISO_DATE_TIME);
            log.debug("API key will expire at: {}", expiresAt);
        }

        ApiKey apiKey = new ApiKey();
        apiKey.setUser(user);
        apiKey.setAccessKeyId(accessKeyId);
        apiKey.setSecretKeyHash(secretHash);
        apiKey.setName(request.getName());
        apiKey.setDescription(request.getDescription());
        apiKey.setAllowedActions(request.getAllowedActions());
        apiKey.setAllowedResources(request.getAllowedResources());
        apiKey.setExpiresAt(expiresAt);

        apiKeyRepository.save(apiKey);
        auditLog.info("API_KEY_CREATED - email={}, accessKeyId={}, name={}", email, accessKeyId, request.getName());
        log.info("API key created successfully for user: {}, accessKeyId: {}", email, accessKeyId);

        ApiKeyResponse response = new ApiKeyResponse();
        response.setId(apiKey.getId().toString());
        response.setAccessKeyId(accessKeyId);
        response.setSecretAccessKey(secretAccessKey);
        response.setUserId(user.getId().toString()); // Set User ID
        response.setSecretKeyHash(secretHash); // Set Hash for NATS
        response.setName(apiKey.getName());

        response.setDescription(apiKey.getDescription());
        response.setAllowedActions(apiKey.getAllowedActions());
        response.setAllowedResources(apiKey.getAllowedResources());
        response.setEnabled(apiKey.isEnabled());
        response.setCreatedAt(apiKey.getCreatedAt().toString());
        response.setExpiresAt(expiresAt != null ? expiresAt.toString() : null);
        response.setWarning("Save this secret key now. You cannot retrieve it again.");
        return response;
    }

    @Transactional(readOnly = true)
    public List<ApiKeyResponse> listUserApiKeys(String email) {
        log.debug("Listing API keys for user: {}", email);

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> {
                    log.error("User not found while listing API keys: {}", email);
                    return new RuntimeException("User not found");
                });

        List<ApiKeyResponse> keys = apiKeyRepository.findByUser(user).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());

        log.debug("Found {} API keys for user: {}", keys.size(), email);
        return keys;
    }

    @Transactional
    public String revokeApiKey(UUID id, String email) {
        log.info("Revoking API key: {} for user: {}", id, email);

        ApiKey apiKey = apiKeyRepository.findById(id)
                .orElseThrow(() -> {
                    log.error("API Key not found: {}", id);
                    return new RuntimeException("API Key not found");
                });

        if (!apiKey.getUser().getEmail().equals(email)) {
            log.warn("Unauthorized attempt to revoke API key: {} by user: {}", id, email);
            throw new RuntimeException("Not authorized to revoke this key");
        }

        apiKey.setEnabled(false);
        apiKeyRepository.save(apiKey);
        auditLog.info("API_KEY_REVOKED - email={}, accessKeyId={}, keyId={}", email, apiKey.getAccessKeyId(), id);
        log.info("API key revoked successfully: {} for user: {}", apiKey.getAccessKeyId(), email);
        return apiKey.getAccessKeyId();
    }

    @Transactional
    public void deleteApiKey(UUID id, String email) {
        log.info("Deleting API key: {} for user: {}", id, email);

        ApiKey apiKey = apiKeyRepository.findById(id)
                .orElseThrow(() -> {
                    log.error("API Key not found: {}", id);
                    return new RuntimeException("API Key not found");
                });

        if (!apiKey.getUser().getEmail().equals(email)) {
            log.warn("Unauthorized attempt to delete API key: {} by user: {}", id, email);
            throw new RuntimeException("Not authorized to delete this key");
        }

        apiKeyRepository.delete(apiKey);
        auditLog.info("API_KEY_DELETED - email={}, accessKeyId={}, keyId={}", email, apiKey.getAccessKeyId(), id);
        log.info("API key deleted successfully: {} for user: {}", apiKey.getAccessKeyId(), email);
    }

    @Transactional
    public void updateLastUsed(String accessKeyId) {
        log.trace("Updating last used timestamp for API key: {}", accessKeyId);
        apiKeyRepository.findByAccessKeyId(accessKeyId).ifPresent(key -> {
            key.setLastUsedAt(LocalDateTime.now());
            apiKeyRepository.save(key);
            log.debug("Updated last used timestamp for API key: {}", accessKeyId);
        });
    }

    @Transactional(readOnly = true)
    public AccessKeyResolveResponse resolveApiKey(String accessKeyId) {
        log.debug("Resolving API key: {}", accessKeyId);

        return apiKeyRepository.findByAccessKeyId(accessKeyId)
                .map(key -> {
                    log.debug("Resolved accessKeyId: {} to userId: {}", accessKeyId, key.getUser().getId());
                    return AccessKeyResolveResponse.builder()
                            .userId(key.getUser().getId().toString())
                            .secretKeyHash(key.getSecretKeyHash())
                            .enabled(key.isEnabled() && !key.isExpired())
                            .build();
                })
                .orElseGet(() -> {
                    log.warn("API key not found: {}", accessKeyId);
                    return AccessKeyResolveResponse.builder().build();
                });
    }

    private ApiKeyResponse mapToResponse(ApiKey key) {
        ApiKeyResponse response = new ApiKeyResponse();
        response.setId(key.getId().toString());
        response.setAccessKeyId(key.getAccessKeyId());
        // No secret key
        response.setName(key.getName());
        response.setDescription(key.getDescription());
        response.setAllowedActions(key.getAllowedActions());
        response.setAllowedResources(key.getAllowedResources());
        response.setEnabled(key.isEnabled());
        response.setCreatedAt(key.getCreatedAt().toString());
        response.setExpiresAt(key.getExpiresAt() != null ? key.getExpiresAt().toString() : null);
        response.setLastUsedAt(key.getLastUsedAt() != null ? key.getLastUsedAt().toString() : null);
        return response;
    }

    private String generateRandomAlphanumeric(int length) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }
}
