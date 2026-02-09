package org.serwin.auth_server.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.serwin.auth_server.dto.ApiKeyResponse;
import org.serwin.auth_server.dto.CreateApiKeyRequest;
import org.serwin.auth_server.service.ApiKeyService;
import org.serwin.auth_server.service.NatsService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth/api-keys")
@RequiredArgsConstructor
@Slf4j
public class ApiKeyController {

    private final ApiKeyService apiKeyService;
    private final NatsService natsService;

    @PostMapping
    public ResponseEntity<?> createApiKey(@RequestBody CreateApiKeyRequest request) {
        log.info("API key creation request for name: {}", request.getName());
        try {
            String email = getCurrentUserEmail();
            ApiKeyResponse response = apiKeyService.generateApiKey(email, request);

            // Publish event: apikey.created
            natsService.publish("apikey", "created", Map.of(
                    "email", email,
                    "accessKeyId", response.getAccessKeyId(),
                    "keyName", request.getName(),
                    "timestamp", LocalDateTime.now().toString()));

            log.info("API key created successfully: {}", response.getAccessKeyId());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("API key creation failed - Error: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping
    public ResponseEntity<?> listApiKeys() {
        try {
            String email = getCurrentUserEmail();
            log.debug("Listing API keys for user: {}", email);
            List<ApiKeyResponse> keys = apiKeyService.listUserApiKeys(email);
            return ResponseEntity.ok(keys);
        } catch (Exception e) {
            log.error("Failed to list API keys - Error: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{keyId}")
    public ResponseEntity<?> revokeApiKey(@PathVariable(value = "keyId") String keyId) {
        log.info("API key revocation request for keyId: {}", keyId);
        try {
            String email = getCurrentUserEmail();
            String accessKeyId = apiKeyService.revokeApiKey(UUID.fromString(keyId), email);

            // Publish event: apikey.revoked
            natsService.publish("apikey", "revoked", Map.of(
                    "email", email,
                    "accessKeyId", accessKeyId,
                    "keyId", keyId,
                    "timestamp", LocalDateTime.now().toString()));

            log.info("API key revoked successfully: {}", accessKeyId);
            return ResponseEntity.ok(Map.of("message", "API key revoked"));
        } catch (Exception e) {
            log.error("API key revocation failed for keyId: {} - Error: {}", keyId, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    private String getCurrentUserEmail() {
        Authentication authentication = SecurityContextHolder.getContext()
                .getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new RuntimeException("User not authenticated");
        }
        return authentication.getName();
    }
}
