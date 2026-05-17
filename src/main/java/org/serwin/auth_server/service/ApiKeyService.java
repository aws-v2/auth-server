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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

@Service
@RequiredArgsConstructor
@Slf4j
public class ApiKeyService {
    private static final Logger auditLog = LoggerFactory.getLogger("AUDIT");
    @Value("${jwt.secret:367566B5970}")
    private String secret;
    private final ApiKeyRepository apiKeyRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;


@PostConstruct
public void debugSecret() {
    log.info("[SECRET CHECK] class={} length={} first8='{}' last8='{}'",
        getClass().getSimpleName(),
        secret.length(),
        secret.substring(0, Math.min(8, secret.length())),
        secret.substring(Math.max(0, secret.length() - 8))
    );
}
 public String generateApiKey(String userId, CreateApiKeyRequest request) {
    debugSecret();
    try {
        User user = userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new RuntimeException("User not found"));

        String keyId = UUID.randomUUID().toString().replace("-", "");

        // payload: userId:keyId:role
        String payload = userId + ":" + keyId + ":" + user.getRole();

        String encodedPayload = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));

        String signature = hmacSign(encodedPayload);

        String apiKeyValue = "ak_" + encodedPayload + "." + signature;

        ApiKey apiKey = new ApiKey();
        apiKey.setUser(user);
        apiKey.setApiKey(apiKeyValue);
        apiKey.setName(request.getName());
        apiKey.setEnabled(true);
        apiKey.setCreatedAt(LocalDateTime.now());

        apiKeyRepository.save(apiKey);

        return apiKeyValue;

    } catch (Exception e) {
        log.error("Failed to generate API key", e);
        throw new RuntimeException("Failed to generate API key", e);
    }
}
private String hmacSign(String data) {
    try {

        Mac mac = Mac.getInstance("HmacSHA256");

        SecretKeySpec secretKeySpec = new SecretKeySpec(
                secret.getBytes(StandardCharsets.UTF_8),
                "HmacSHA256"
        );

        mac.init(secretKeySpec);

        byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));

        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(hash);

    } catch (Exception e) {
        throw new RuntimeException("Failed to sign API key", e);
    }
}

 

    @Transactional(readOnly = true)
    public List<ApiKeyResponse> listUserApiKeys(String email) {
        log.debug("Listing API keys for user: {}", email);

        User user = userRepository.findById(UUID.fromString(email))
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
        auditLog.info("API_KEY_REVOKED - email={}, apiKey={}, keyId={}", email, apiKey.getApiKey(), id);
        log.info("API key revoked successfully: {} for user: {}", apiKey.getApiKey(), email);
        return apiKey.getApiKey();
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
        auditLog.info("API_KEY_DELETED - email={}, apiKey={}, keyId={}", email, apiKey.getApiKey(), id);
        log.info("API key deleted successfully: {} for user: {}", apiKey.getApiKey(), email);
    }

    @Transactional
    public void updateLastUsed(String apiKeyVal) {
        log.trace("Updating last used timestamp for API key: {}", apiKeyVal);
        apiKeyRepository.findByApiKey(apiKeyVal).ifPresent(key -> {
            apiKeyRepository.save(key);
            log.debug("Updated last used timestamp for API key: {}", apiKeyVal);
        });
    }

    @Transactional(readOnly = true)
    public AccessKeyResolveResponse resolveApiKey(String apiKeyVal) {
        log.debug("Resolving API key: {}", apiKeyVal);

 return apiKeyRepository.findByApiKey(apiKeyVal)
        .map(key -> {
            log.debug("Resolved apiKey: {} to userId: {}", apiKeyVal, key.getUser().getId());

            return AccessKeyResolveResponse.builder()
                    .userId(key.getUser().getId().toString())
                    .enabled(key.isEnabled())
                    .build();
        })
        .orElseGet(() -> {
            log.warn("API key not found: {}", apiKeyVal);
            return AccessKeyResolveResponse.builder().build();
        });
}

private ApiKeyResponse mapToResponse(ApiKey key) {
    ApiKeyResponse response = new ApiKeyResponse();

    response.setId(key.getId().toString());
    response.setApiKey(key.getApiKey());
    response.setName(key.getName());
    response.setEnabled(key.isEnabled());
    response.setCreatedAt(
            key.getCreatedAt() != null
                    ? key.getCreatedAt().toString()
                    : null
    );

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
