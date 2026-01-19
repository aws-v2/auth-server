# Auth Server Enhancement Plan
## Dual Authentication Mode (JWT + API Keys)

---

## Objective

Enhance the existing Auth Server to support both UI authentication (JWT) and SDK authentication (API Keys), 

**Scope:** Auth Server only. IAM Service and Storage Server already exist and handle their responsibilities correctly.

---

## Current State Analysis

### Existing Capabilities
- JWT token generation (HS256, 24-hour expiry)
- User registration with email verification
- Login with MFA support (TOTP)
- Password reset flow
- Current JWT claims: `userId`, `email`, `exp`, `iat`

### Missing Capabilities
- API Key generation and management
- API Key validation endpoint (for NATS requests)
- JWT token blacklist (for logout)
- NATS producer/consumer infrastructure
- Token validation endpoint (for blacklist checks)

---

## Architecture Overview

### Auth Server's Role in the System

```
┌─────────────────────────────────────────────────────────────┐
│                     REQUEST FLOWS                            │
└─────────────────────────────────────────────────────────────┘

UI User Flow (JWT):
Browser → API Gateway [validates JWT locally]
        → Storage Server → [NATS] → IAM Service (authorization)
                        → MinIO

SDK Flow (API Key):
Script → API Gateway → [NATS] → Auth Server (validate API key)
       → Storage Server → [NATS] → IAM Service (authorization)
                       → MinIO

Logout Flow (JWT Blacklist):
Browser → Auth Server [blacklist token]
        → [NATS publish] → event: auth.token.blacklisted
        → API Gateway can check blacklist for critical operations
```

### Auth Server Responsibilities

1. **API Key Management**
   - Generate API Key + Secret for users
   - Store keys with scopes (actions/resources)
   - List/revoke API keys
   - Validate API keys

2. **JWT Enhancements**
   - Blacklist tokens on logout
   - Provide token validation endpoint (for blacklist checks)
   - Publish authentication events


   - **Consume:** `auth.validate.apikey` - Validate API key requests
   - **Consume:** `auth.validate.token` - Check if JWT is blacklisted
   - **Publish:** `auth.token.blacklisted` - Token blacklisted event
   - **Publish:** `auth.apikey.created` - API key created event
   - **Publish:** `user.registered`, `auth.login.success` - User events

---

## Implementation Plan

### Phase 1: Database Schema (Files to Create)

**1.1 API Keys Table**

Create migration script: `src/main/resources/db/migration/V2__create_api_keys_table.sql`

```sql
CREATE TABLE api_keys (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,

    -- Credentials
    access_key_id VARCHAR(20) NOT NULL UNIQUE,
    secret_key_hash VARCHAR(255) NOT NULL,

    -- Metadata
    name VARCHAR(100) NOT NULL,
    description TEXT,

    -- Scoping
    allowed_actions TEXT[],
    allowed_resources TEXT[],

    -- Status
    enabled BOOLEAN DEFAULT TRUE,
    last_used_at TIMESTAMP,

    -- Lifecycle
    created_at TIMESTAMP DEFAULT NOW(),
    expires_at TIMESTAMP,

    CONSTRAINT check_access_key_format CHECK (access_key_id ~ '^AKIA[A-Z0-9]{16}$')
);

CREATE INDEX idx_api_keys_user_id ON api_keys(user_id);
CREATE INDEX idx_api_keys_access_key_id ON api_keys(access_key_id);
CREATE INDEX idx_api_keys_enabled ON api_keys(enabled);
```

**1.2 Token Blacklist Table**

Create migration script: `src/main/resources/db/migration/V3__create_token_blacklist_table.sql`

```sql
CREATE TABLE token_blacklist (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    user_id UUID NOT NULL REFERENCES users(id),
    reason VARCHAR(255),
    blacklisted_at TIMESTAMP DEFAULT NOW(),
    expires_at TIMESTAMP NOT NULL,

    INDEX idx_token_hash (token_hash),
    INDEX idx_user_id (user_id),
    INDEX idx_expires_at (expires_at)
);
```

---

### Phase 2: Entity and Repository Layer

**2.1 Create ApiKey Entity**

File: `src/main/java/org/serwin/auth_server/entities/ApiKey.java`

```java
@Entity
@Table(name = "api_keys")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApiKey {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "access_key_id", unique = true, nullable = false, length = 20)
    private String accessKeyId;

    @Column(name = "secret_key_hash", nullable = false)
    private String secretKeyHash;

    @Column(nullable = false, length = 100)
    private String name;

    @Column
    private String description;

    @Column(name = "allowed_actions", columnDefinition = "text[]")
    private String[] allowedActions;

    @Column(name = "allowed_resources", columnDefinition = "text[]")
    private String[] allowedResources;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public boolean isExpired() {
        return expiresAt != null && LocalDateTime.now().isAfter(expiresAt);
    }
}
```

**2.2 Create TokenBlacklist Entity**

File: `src/main/java/org/serwin/auth_server/entities/TokenBlacklist.java`

```java
@Entity
@Table(name = "token_blacklist")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TokenBlacklist {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "token_hash", unique = true, nullable = false)
    private String tokenHash;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column
    private String reason;

    @Column(name = "blacklisted_at")
    private LocalDateTime blacklistedAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @PrePersist
    protected void onCreate() {
        blacklistedAt = LocalDateTime.now();
    }
}
```

**2.3 Create Repositories**

File: `src/main/java/org/serwin/auth_server/repository/ApiKeyRepository.java`

```java
@Repository
public interface ApiKeyRepository extends JpaRepository<ApiKey, UUID> {
    Optional<ApiKey> findByAccessKeyId(String accessKeyId);
    List<ApiKey> findByUserAndEnabledTrue(User user);
    List<ApiKey> findByUser(User user);
    boolean existsByAccessKeyId(String accessKeyId);
}
```

File: `src/main/java/org/serwin/auth_server/repository/TokenBlacklistRepository.java`

```java
@Repository
public interface TokenBlacklistRepository extends JpaRepository<TokenBlacklist, UUID> {
    Optional<TokenBlacklist> findByTokenHash(String tokenHash);
    boolean existsByTokenHash(String tokenHash);

    @Modifying
    @Query("DELETE FROM TokenBlacklist t WHERE t.expiresAt < :now")
    void deleteExpiredTokens(@Param("now") LocalDateTime now);
}
```

---

### Phase 3: Service Layer

**3.1 Create ApiKeyService**

File: `src/main/java/org/serwin/auth_server/service/ApiKeyService.java`

**Key Methods:**
- `generateApiKey(userId, name, description, scopes, expiresAt)` - Generate access key + secret
- `validateApiKey(accessKeyId, secretAccessKey)` - Validate and return user context
- `listUserApiKeys(userId)` - Get user's API keys (without secrets)
- `revokeApiKey(apiKeyId, userId)` - Disable API key
- `updateLastUsed(accessKeyId)` - Track usage

**API Key Generation Algorithm:**
```java
// Access Key ID: AKIA + 16 random alphanumeric
String accessKeyId = "AKIA" + generateRandomAlphanumeric(16);

// Secret: 40 characters Base64
byte[] secretBytes = new byte[30];
new SecureRandom().nextBytes(secretBytes);
String secretAccessKey = Base64.getEncoder().encodeToString(secretBytes);

// Store hash, return secret ONCE
String secretHash = passwordEncoder.encode(secretAccessKey);
```

**3.2 Create TokenBlacklistService**

File: `src/main/java/org/serwin/auth_server/service/TokenBlacklistService.java`

**Key Methods:**
- `blacklistToken(token, userId, reason)` - Add token to blacklist
- `isTokenBlacklisted(token)` - Check if token is blacklisted
- `cleanupExpiredTokens()` - Scheduled job to remove expired entries

**3.3 Modify JwtService**

File: `src/main/java/org/serwin/auth_server/service/JwtService.java` (existing)

**Add Methods:**
```java
public String getTokenHash(String token) {
    // SHA-256 hash of token for blacklist lookup
    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
    return Hex.encodeHexString(hash);
}

public LocalDateTime getTokenExpiration(String token) {
    Date expiration = extractExpiration(token);
    return expiration.toInstant()
        .atZone(ZoneId.systemDefault())
        .toLocalDateTime();
}
```

---

### Phase 4: NATS Integration

**4.1 Add NATS Dependency**

File: `pom.xml` (modify)

```xml
<dependency>
    <groupId>io.nats</groupId>
    <artifactId>jnats</artifactId>
    <version>2.16.14</version>
</dependency>
```

**4.2 Create NatsConfig**

File: `src/main/java/org/serwin/auth_server/config/NatsConfig.java`

```java
@Configuration
public class NatsConfig {
    @Value("${nats.url:nats://localhost:4222}")
    private String natsUrl;

    @Value("${nats.username:}")
    private String username;

    @Value("${nats.password:}")
    private String password;

    @Bean
    public Connection natsConnection() throws IOException, InterruptedException {
        Options.Builder builder = new Options.Builder()
            .server(natsUrl)
            .connectionName("auth-server")
            .maxReconnects(-1)
            .reconnectWait(Duration.ofSeconds(1));

        if (!username.isEmpty()) {
            builder.userInfo(username, password);
        }

        return Nats.connect(builder.build());
    }
}
```

**4.3 Create NatsService**

File: `src/main/java/org/serwin/auth_server/service/NatsService.java`

```java
@Service
@RequiredArgsConstructor
public class NatsService {
    private final Connection natsConnection;
    private final ObjectMapper objectMapper;

    public <T> void publish(String subject, T payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            natsConnection.publish(subject, json.getBytes());
        } catch (Exception e) {
            log.error("Failed to publish to {}: {}", subject, e.getMessage());
        }
    }

    public <T, R> void subscribe(String subject, Class<T> requestType,
                                 Function<T, R> handler) {
        Dispatcher dispatcher = natsConnection.createDispatcher(msg -> {
            try {
                T request = objectMapper.readValue(msg.getData(), requestType);
                R response = handler.apply(request);
                String responseJson = objectMapper.writeValueAsString(response);
                natsConnection.publish(msg.getReplyTo(), responseJson.getBytes());
            } catch (Exception e) {
                log.error("Error handling NATS message: {}", e.getMessage());
            }
        });
        dispatcher.subscribe(subject);
    }
}
```

**4.4 Create NATS Message Listeners**

File: `src/main/java/org/serwin/auth_server/nats/AuthNatsListener.java`

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthNatsListener {
    private final ApiKeyService apiKeyService;
    private final TokenBlacklistService tokenBlacklistService;
    private final NatsService natsService;

    @PostConstruct
    public void initialize() {
        // Subscribe to API key validation requests
        natsService.subscribe(
            "auth.validate.apikey",
            ApiKeyValidationRequest.class,
            this::handleApiKeyValidation
        );

        // Subscribe to token blacklist checks
        natsService.subscribe(
            "auth.validate.token",
            TokenValidationRequest.class,
            this::handleTokenValidation
        );

        log.info("NATS listeners initialized for auth validation");
    }

    private ApiKeyValidationResponse handleApiKeyValidation(
        ApiKeyValidationRequest request
    ) {
        try {
            ApiKeyValidationResult result = apiKeyService.validateApiKey(
                request.getAccessKeyId(),
                request.getSecretAccessKey()
            );

            if (result.isValid()) {
                apiKeyService.updateLastUsed(request.getAccessKeyId());
            }

            return ApiKeyValidationResponse.builder()
                .valid(result.isValid())
                .userId(result.getUserId())
                .userName(result.getUserName())
                .scopes(result.getScopes())
                .build();
        } catch (Exception e) {
            log.error("API key validation error: {}", e.getMessage());
            return ApiKeyValidationResponse.builder()
                .valid(false)
                .error(e.getMessage())
                .build();
        }
    }

    private TokenValidationResponse handleTokenValidation(
        TokenValidationRequest request
    ) {
        boolean isBlacklisted = tokenBlacklistService.isTokenBlacklisted(
            request.getToken()
        );

        return TokenValidationResponse.builder()
            .valid(!isBlacklisted)
            .blacklisted(isBlacklisted)
            .build();
    }
}
```

---

### Phase 5: DTOs for NATS Messages

**5.1 API Key DTOs**

File: `src/main/java/org/serwin/auth_server/dto/ApiKeyValidationRequest.java`

```java
@Data
public class ApiKeyValidationRequest {
    private String accessKeyId;
    private String secretAccessKey;
    private Map<String, String> requestContext; // IP, User-Agent, etc.
}
```

File: `src/main/java/org/serwin/auth_server/dto/ApiKeyValidationResponse.java`

```java
@Data
@Builder
public class ApiKeyValidationResponse {
    private boolean valid;
    private String userId;
    private String userName;
    private ApiKeyScopes scopes;
    private String error;
}

@Data
@Builder
class ApiKeyScopes {
    private String[] allowedActions;
    private String[] allowedResources;
}
```

File: `src/main/java/org/serwin/auth_server/dto/CreateApiKeyRequest.java`

```java
@Data
public class CreateApiKeyRequest {
    private String name;
    private String description;
    private String[] allowedActions;
    private String[] allowedResources;
    private String expiresAt; // ISO-8601 format
}
```

File: `src/main/java/org/serwin/auth_server/dto/ApiKeyResponse.java`

```java
@Data
@Builder
public class ApiKeyResponse {
    private String id;
    private String accessKeyId;
    private String secretAccessKey; // Only on creation
    private String name;
    private String description;
    private String[] allowedActions;
    private String[] allowedResources;
    private boolean enabled;
    private String createdAt;
    private String expiresAt;
    private String lastUsedAt;
    private String warning; // "Save this secret - cannot be retrieved again"
}
```

**5.2 Token Validation DTOs**

File: `src/main/java/org/serwin/auth_server/dto/TokenValidationRequest.java`

```java
@Data
public class TokenValidationRequest {
    private String token;
}
```

File: `src/main/java/org/serwin/auth_server/dto/TokenValidationResponse.java`

```java
@Data
@Builder
public class TokenValidationResponse {
    private boolean valid;
    private boolean blacklisted;
}
```

---

### Phase 6: REST API Endpoints

**6.1 Modify AuthController**

File: `src/main/java/org/serwin/auth_server/controller/AuthController.java` (modify)

**Add Logout Endpoint:**

```java
@PostMapping("/logout")
public ResponseEntity<?> logout(HttpServletRequest request) {
    try {
        String token = extractTokenFromRequest(request);
        String email = getCurrentUserEmail();

        tokenBlacklistService.blacklistToken(token, email, "User logout");

        // Publish event
        natsService.publish("auth.token.blacklisted", Map.of(
            "email", email,
            "reason", "User logout",
            "timestamp", LocalDateTime.now()
        ));

        return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
    } catch (Exception e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(Map.of("error", e.getMessage()));
    }
}

private String extractTokenFromRequest(HttpServletRequest request) {
    String bearerToken = request.getHeader("Authorization");
    if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
        return bearerToken.substring(7);
    }
    throw new RuntimeException("No token found in request");
}
```

**6.2 Create ApiKeyController**

File: `src/main/java/org/serwin/auth_server/controller/ApiKeyController.java`

```java
@RestController
@RequestMapping("/auth/api-keys")
@RequiredArgsConstructor
public class ApiKeyController {
    private final ApiKeyService apiKeyService;
    private final NatsService natsService;

    @PostMapping
    public ResponseEntity<?> createApiKey(@RequestBody CreateApiKeyRequest request) {
        try {
            String email = getCurrentUserEmail();
            ApiKeyResponse response = apiKeyService.generateApiKey(email, request);

            // Publish event
            natsService.publish("auth.apikey.created", Map.of(
                "email", email,
                "keyName", request.getName(),
                "timestamp", LocalDateTime.now()
            ));

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping
    public ResponseEntity<?> listApiKeys() {
        try {
            String email = getCurrentUserEmail();
            List<ApiKeyResponse> keys = apiKeyService.listUserApiKeys(email);
            return ResponseEntity.ok(keys);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{keyId}")
    public ResponseEntity<?> revokeApiKey(@PathVariable String keyId) {
        try {
            String email = getCurrentUserEmail();
            apiKeyService.revokeApiKey(UUID.fromString(keyId), email);

            // Publish event
            natsService.publish("auth.apikey.revoked", Map.of(
                "email", email,
                "keyId", keyId,
                "timestamp", LocalDateTime.now()
            ));

            return ResponseEntity.ok(Map.of("message", "API key revoked"));
        } catch (Exception e) {
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
```

---

### Phase 7: Configuration

**7.1 Update application.yml**

File: `src/main/resources/application.yml` (modify)

```yaml
# Existing config...

# NATS Configuration
nats:
  url: nats://localhost:4222
  username: auth-server
  password: ${NATS_PASSWORD:auth-secret}

# Scheduled tasks
scheduled:
  token-cleanup:
    cron: "0 0 * * * *"  # Every hour
```

**7.2 Create Scheduled Cleanup Job**

File: `src/main/java/org/serwin/auth_server/scheduled/TokenCleanupJob.java`

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class TokenCleanupJob {
    private final TokenBlacklistRepository tokenBlacklistRepository;

    @Scheduled(cron = "${scheduled.token-cleanup.cron}")
    public void cleanupExpiredTokens() {
        try {
            tokenBlacklistRepository.deleteExpiredTokens(LocalDateTime.now());
            log.info("Cleaned up expired tokens from blacklist");
        } catch (Exception e) {
            log.error("Error cleaning up tokens: {}", e.getMessage());
        }
    }
}
```

---

## Testing Strategy

### Unit Tests

**ApiKeyServiceTest.java:**
- Test API key generation format (AKIA prefix)
- Test secret validation (BCrypt)
- Test expiry logic
- Test scope validation

**TokenBlacklistServiceTest.java:**
- Test token blacklisting
- Test blacklist lookup
- Test cleanup job

### Integration Tests

**ApiKeyIntegrationTest.java:**
- End-to-end API key creation
- Validation via NATS
- Revocation flow

**AuthNatsListenerTest.java:**
- Test NATS request-reply for API key validation
- Test NATS request-reply for token blacklist check

### Manual Testing Checklist

1. **API Key Flow:**
   - ✅ Create API key via POST /auth/api-keys
   - ✅ Receive access key + secret (one time only)
   - ✅ API Gateway sends NATS request to auth.validate.apikey
   - ✅ Auth Server validates and responds with user context
   - ✅ Revoke API key
   - ✅ Next validation attempt fails

2. **JWT Flow:**
   - ✅ Login and receive JWT
   - ✅ Use JWT for authenticated requests
   - ✅ Logout (POST /auth/logout)
   - ✅ Token added to blacklist
   - ✅ NATS event published: auth.token.blacklisted
   - ✅ Next request with same JWT fails (if gateway checks blacklist)

3. **NATS Events:**
   - ✅ User registration publishes user.registered
   - ✅ Login publishes auth.login.success
   - ✅ API key creation publishes auth.apikey.created
   - ✅ Events are consumable by other services

---

## Implementation Checklist

### Files to Create (19 files)

**Entities:**
- [ ] `entities/ApiKey.java`
- [ ] `entities/TokenBlacklist.java`

**Repositories:**
- [ ] `repository/ApiKeyRepository.java`
- [ ] `repository/TokenBlacklistRepository.java`

**Services:**
- [ ] `service/ApiKeyService.java`
- [ ] `service/TokenBlacklistService.java`
- [ ] `service/NatsService.java`

**Controllers:**
- [ ] `controller/ApiKeyController.java`

**Config:**
- [ ] `config/NatsConfig.java`

**NATS:**
- [ ] `nats/AuthNatsListener.java`

**DTOs (8 files):**
- [ ] `dto/ApiKeyValidationRequest.java`
- [ ] `dto/ApiKeyValidationResponse.java`
- [ ] `dto/CreateApiKeyRequest.java`
- [ ] `dto/ApiKeyResponse.java`
- [ ] `dto/TokenValidationRequest.java`
- [ ] `dto/TokenValidationResponse.java`
- [ ] `dto/ApiKeyScopes.java`
- [ ] `dto/ApiKeyValidationResult.java`

**Scheduled:**
- [ ] `scheduled/TokenCleanupJob.java`

**Migrations:**
- [ ] `resources/db/migration/V2__create_api_keys_table.sql`
- [ ] `resources/db/migration/V3__create_token_blacklist_table.sql`

### Files to Modify (3 files)

- [ ] `service/JwtService.java` - Add hash/expiration methods
- [ ] `controller/AuthController.java` - Add logout endpoint
- [ ] `pom.xml` - Add NATS dependency
- [ ] `application.yml` - Add NATS config

---

## NATS Message Contracts

### Messages Auth Server Consumes (Request-Reply)

**Subject:** `auth.validate.apikey`
**Request:**
```json
{
  "accessKeyId": "AKIAIOSFODNN7EXAMPLE",
  "secretAccessKey": "wJalrXUt...",
  "requestContext": {
    "ipAddress": "1.2.3.4",
    "userAgent": "aws-sdk-go/1.0"
  }
}
```
**Response:**
```json
{
  "valid": true,
  "userId": "uuid",
  "userName": "user@example.com",
  "scopes": {
    "allowedActions": ["s3:*"],
    "allowedResources": ["arn:aws:s3:::*"]
  }
}
```

**Subject:** `auth.validate.token`
**Request:**
```json
{
  "token": "eyJhbGc..."
}
```
**Response:**
```json
{
  "valid": true,
  "blacklisted": false
}
```

### Messages Auth Server Publishes (Pub-Sub Events)

**Subject:** `user.registered`
```json
{
  "userId": "uuid",
  "email": "user@example.com",
  "timestamp": "2026-01-07T10:30:00Z"
}
```

**Subject:** `auth.login.success`
```json
{
  "userId": "uuid",
  "email": "user@example.com",
  "mfaUsed": false,
  "timestamp": "2026-01-07T10:30:00Z"
}
```

**Subject:** `auth.token.blacklisted`
```json
{
  "email": "user@example.com",
  "reason": "User logout",
  "timestamp": "2026-01-07T10:30:00Z"
}
```

**Subject:** `auth.apikey.created`
```json
{
  "email": "user@example.com",
  "keyName": "Production Backup",
  "timestamp": "2026-01-07T10:30:00Z"
}
```

---

## Success Criteria

✅ **API Key Management:**
- Users can create API keys via UI
- Keys generate with AKIA prefix + 40-char secret
- Secrets are hashed (BCrypt) before storage
- Keys can be listed (without secrets)
- Keys can be revoked instantly

✅ **API Key Validation:**
- Auth Server responds to `auth.validate.apikey` NATS requests
- Validation checks: enabled, not expired, secret match
- Updates `last_used_at` on successful validation
- Returns user context + scopes for authorization

✅ **JWT Logout:**
- Logout endpoint blacklists JWT
- Token blacklist checked via `auth.validate.token`
- NATS event published on blacklist

✅ **NATS Infrastructure:**
- Connection established on startup
- Request-reply pattern works for validation
- Pub-sub pattern works for events
- Graceful error handling

✅ **Events Published:**
- `user.registered` on registration
- `auth.login.success` on login
- `auth.token.blacklisted` on logout
- `auth.apikey.created` on key creation

---

## Next Steps After Implementation

1. **Test with API Gateway:**
   - Send API key validation requests via NATS
   - Verify responses match expected format

2. **Test with Storage Server:**
   - Storage Server receives validated user context
   - Storage Server can extract scopes for pre-filtering

3. **Test with IAM Service:**
   - IAM receives authorization requests
   - IAM evaluates policies correctly

4. **Performance Testing:**
   - Measure API key validation latency (~25ms target)
   - Test concurrent NATS requests (100+ req/sec)

5. **Security Audit:**
   - Verify secrets are never logged
   - Verify BCrypt salt rounds (12+)
   - Verify NATS authentication enabled

---

## Architecture Diagram

```
┌──────────────────────────────────────────────────────────────┐
│                      AUTH SERVER SCOPE                        │
└──────────────────────────────────────────────────────────────┘

┌─────────────┐
│   Browser   │ (UI User)
└──────┬──────┘
       │ POST /auth/login
       │ POST /auth/logout
       │ POST /auth/api-keys
       ▼
┌─────────────────────────────────────────────────┐
│            Auth Server (Spring Boot)            │
│                                                 │
│  REST API:                                      │
│  • POST /auth/register                          │
│  • POST /auth/login → JWT                       │
│  • POST /auth/logout → Blacklist JWT            │
│  • POST /auth/api-keys → Generate API Key       │
│  • GET  /auth/api-keys → List Keys              │
│  • DELETE /auth/api-keys/{id} → Revoke          │
│                                                 │
│  NATS Listeners:                                │
│  • auth.validate.apikey (Request-Reply)         │
│  • auth.validate.token (Request-Reply)          │
│                                                 │
│  NATS Publishers:                               │
│  • user.registered                              │
│  • auth.login.success                           │
│  • auth.token.blacklisted                       │
│  • auth.apikey.created                          │
│                                                 │
│  Database:                                      │
│  • users                                        │
│  • api_keys                                     │
│  • token_blacklist                              │
│  • password_reset_tokens                        │
└─────────────┬───────────────────────────────────┘
              │
              ▼
       ┌─────────────┐
       │    NATS     │ (Message Broker)
       └─────────────┘
              │
      ┌───────┴───────┐
      ▼               ▼
┌────────────┐  ┌────────────┐
│API Gateway │  │IAM Service │ (Existing)
│ (Existing) │  │ (Existing) │
└────────────┘  └────────────┘
```

This plan focuses Auth Server on its core responsibilities: authentication, API key management, and NATS integration with existing services.
