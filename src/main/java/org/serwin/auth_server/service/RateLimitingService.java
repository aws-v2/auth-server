package org.serwin.auth_server.service;

import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.serwin.auth_server.config.RateLimitingConfig;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class RateLimitingService {

    private final CacheManager cacheManager;
    private final RateLimitingConfig rateLimitingConfig;

    /**
     * Check if a request should be rate limited
     * @param key Unique key for the rate limit (e.g., IP address or user ID)
     * @param bucketType Type of bucket (auth, api, strict)
     * @return true if allowed, false if rate limited
     */
    public boolean isAllowed(String key, BucketType bucketType) {
        Cache cache = cacheManager.getCache("rateLimitBuckets");
        if (cache == null) {
            log.warn("Rate limit cache not available, allowing request to go one ");
            return true;
        }

        String cacheKey = bucketType.name() + ":" + key;
        Bucket bucket = cache.get(cacheKey, Bucket.class);

        if (bucket == null) {
            bucket = createBucket(bucketType);
            cache.put(cacheKey, bucket);
            log.debug("Created new rate limit bucket for key: {}", cacheKey);
        }

        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        boolean allowed = probe.isConsumed();

        if (!allowed) {
            log.warn("Rate limit exceeded for key: {} (type: {}). Retry after: {} seconds",
                    key, bucketType, probe.getNanosToWaitForRefill() / 1_000_000_000);
        } else {
            log.trace("Rate limit check passed for key: {} (type: {}). Remaining: {}",
                    key, bucketType, probe.getRemainingTokens());
        }

        return allowed;
    }

    /**
     * Get remaining tokens for a key
     */
    public long getRemainingTokens(String key, BucketType bucketType) {
        Cache cache = cacheManager.getCache("rateLimitBuckets");
        if (cache == null) {
            return -1;
        }

        String cacheKey = bucketType.name() + ":" + key;
        Bucket bucket = cache.get(cacheKey, Bucket.class);

        if (bucket == null) {
            return createBucket(bucketType).getAvailableTokens();
        }

        return bucket.getAvailableTokens();
    }

    private Bucket createBucket(BucketType bucketType) {
        return switch (bucketType) {
            case AUTH -> rateLimitingConfig.createAuthBucket();
            case API -> rateLimitingConfig.createApiBucket();
            case STRICT -> rateLimitingConfig.createStrictBucket();
        };
    }

    public enum BucketType {
        AUTH,    // For login, register, password reset
        API,     // For general API calls
        STRICT   // For sensitive operations like MFA
    }
}
