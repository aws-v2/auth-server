package org.serwin.auth_server.service;

import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.serwin.auth_server.config.RateLimitingConfig;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RateLimitingServiceTest {

    @Mock private CacheManager cacheManager;
    @Mock private RateLimitingConfig rateLimitingConfig;
    @Mock private Cache cache;
    @Mock private Bucket bucket;
    @Mock private ConsumptionProbe probe;

    @InjectMocks
    private RateLimitingService rateLimitingService;

    private static final String KEY = "192.168.1.1";
    private static final String CACHE_NAME = "rateLimitBuckets";

    @BeforeEach
    void setUp() {
        // Most tests use a working cache — override per-test where needed
        when(cacheManager.getCache(CACHE_NAME)).thenReturn(cache);
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private void stubBucketInCache(RateLimitingService.BucketType type) {
        String cacheKey = type.name() + ":" + KEY;
        when(cache.get(cacheKey, Bucket.class)).thenReturn(bucket);
    }

    private void stubNoBucketInCache(RateLimitingService.BucketType type) {
        String cacheKey = type.name() + ":" + KEY;
        when(cache.get(cacheKey, Bucket.class)).thenReturn(null);
    }

    private void stubNewBucketFor(RateLimitingService.BucketType type) {
        switch (type) {
            case AUTH   -> when(rateLimitingConfig.createAuthBucket()).thenReturn(bucket);
            case API    -> when(rateLimitingConfig.createApiBucket()).thenReturn(bucket);
            case STRICT -> when(rateLimitingConfig.createStrictBucket()).thenReturn(bucket);
        }
    }

    private void stubProbeAllowed(long remaining) {
        when(probe.isConsumed()).thenReturn(true);
        when(probe.getRemainingTokens()).thenReturn(remaining);
        when(bucket.tryConsumeAndReturnRemaining(1)).thenReturn(probe);
    }

    private void stubProbeDenied(long nanosToWait) {
        when(probe.isConsumed()).thenReturn(false);
        when(probe.getNanosToWaitForRefill()).thenReturn(nanosToWait);
        when(bucket.tryConsumeAndReturnRemaining(1)).thenReturn(probe);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // isAllowed
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    class IsAllowed {

        // ── cache unavailable ─────────────────────────────────────────────────

        @Test
        void nullCache_returnsTrue() {
            when(cacheManager.getCache(CACHE_NAME)).thenReturn(null);

            boolean result = rateLimitingService.isAllowed(KEY, RateLimitingService.BucketType.AUTH);

            assertThat(result).isTrue();
            verifyNoInteractions(bucket, rateLimitingConfig);
        }

        @Test
        void nullCache_doesNotInteractWithBucketOrConfig() {
            when(cacheManager.getCache(CACHE_NAME)).thenReturn(null);

            rateLimitingService.isAllowed(KEY, RateLimitingService.BucketType.API);

            verifyNoInteractions(rateLimitingConfig, bucket);
        }

        // ── existing bucket in cache ──────────────────────────────────────────

        @Test
        void existingBucket_allowed_returnsTrue() {
            stubBucketInCache(RateLimitingService.BucketType.AUTH);
            stubProbeAllowed(9);

            boolean result = rateLimitingService.isAllowed(KEY, RateLimitingService.BucketType.AUTH);

            assertThat(result).isTrue();
        }

        @Test
        void existingBucket_rateLimited_returnsFalse() {
            stubBucketInCache(RateLimitingService.BucketType.AUTH);
            stubProbeDenied(5_000_000_000L);

            boolean result = rateLimitingService.isAllowed(KEY, RateLimitingService.BucketType.AUTH);

            assertThat(result).isFalse();
        }

        @Test
        void existingBucket_consumesExactlyOneToken() {
            stubBucketInCache(RateLimitingService.BucketType.AUTH);
            stubProbeAllowed(4);

            rateLimitingService.isAllowed(KEY, RateLimitingService.BucketType.AUTH);

            verify(bucket).tryConsumeAndReturnRemaining(1);
        }

        @Test
        void existingBucket_doesNotCreateNewBucket() {
            stubBucketInCache(RateLimitingService.BucketType.AUTH);
            stubProbeAllowed(5);

            rateLimitingService.isAllowed(KEY, RateLimitingService.BucketType.AUTH);

            verifyNoInteractions(rateLimitingConfig);
            verify(cache, never()).put(anyString(), any());
        }

        // ── no bucket in cache → creates new one ──────────────────────────────

        @Test
        void noBucketInCache_createsAuthBucketAndStoresInCache() {
            stubNoBucketInCache(RateLimitingService.BucketType.AUTH);
            stubNewBucketFor(RateLimitingService.BucketType.AUTH);
            stubProbeAllowed(10);

            rateLimitingService.isAllowed(KEY, RateLimitingService.BucketType.AUTH);

            verify(rateLimitingConfig).createAuthBucket();
            verify(cache).put("AUTH:" + KEY, bucket);
        }

        @Test
        void noBucketInCache_createsApiBucketAndStoresInCache() {
            stubNoBucketInCache(RateLimitingService.BucketType.API);
            stubNewBucketFor(RateLimitingService.BucketType.API);
            stubProbeAllowed(10);

            rateLimitingService.isAllowed(KEY, RateLimitingService.BucketType.API);

            verify(rateLimitingConfig).createApiBucket();
            verify(cache).put("API:" + KEY, bucket);
        }

        @Test
        void noBucketInCache_createsStrictBucketAndStoresInCache() {
            stubNoBucketInCache(RateLimitingService.BucketType.STRICT);
            stubNewBucketFor(RateLimitingService.BucketType.STRICT);
            stubProbeAllowed(3);

            rateLimitingService.isAllowed(KEY, RateLimitingService.BucketType.STRICT);

            verify(rateLimitingConfig).createStrictBucket();
            verify(cache).put("STRICT:" + KEY, bucket);
        }

        @Test
        void noBucketInCache_thenAllowed_returnsTrue() {
            stubNoBucketInCache(RateLimitingService.BucketType.AUTH);
            stubNewBucketFor(RateLimitingService.BucketType.AUTH);
            stubProbeAllowed(10);

            boolean result = rateLimitingService.isAllowed(KEY, RateLimitingService.BucketType.AUTH);

            assertThat(result).isTrue();
        }

        @Test
        void noBucketInCache_thenRateLimited_returnsFalse() {
            stubNoBucketInCache(RateLimitingService.BucketType.STRICT);
            stubNewBucketFor(RateLimitingService.BucketType.STRICT);
            stubProbeDenied(10_000_000_000L);

            boolean result = rateLimitingService.isAllowed(KEY, RateLimitingService.BucketType.STRICT);

            assertThat(result).isFalse();
        }

        // ── cache key format ──────────────────────────────────────────────────

        @ParameterizedTest
        @EnumSource(RateLimitingService.BucketType.class)
        void cacheKeyFormat_isTypePrefixedColon(RateLimitingService.BucketType type) {
            String expectedKey = type.name() + ":" + KEY;
            when(cache.get(expectedKey, Bucket.class)).thenReturn(bucket);
            stubProbeAllowed(5);

            rateLimitingService.isAllowed(KEY, type);

            verify(cache).get(expectedKey, Bucket.class);
        }

        @Test
        void differentKeys_useSeparateCacheEntries() {
            String key1 = "10.0.0.1";
            String key2 = "10.0.0.2";

            when(cache.get("AUTH:" + key1, Bucket.class)).thenReturn(bucket);
            when(cache.get("AUTH:" + key2, Bucket.class)).thenReturn(bucket);
            stubProbeAllowed(5);

            rateLimitingService.isAllowed(key1, RateLimitingService.BucketType.AUTH);
            rateLimitingService.isAllowed(key2, RateLimitingService.BucketType.AUTH);

            verify(cache).get("AUTH:" + key1, Bucket.class);
            verify(cache).get("AUTH:" + key2, Bucket.class);
        }

        @Test
        void sameKeyDifferentBucketTypes_useSeparateCacheEntries() {
            when(cache.get("AUTH:" + KEY, Bucket.class)).thenReturn(bucket);
            when(cache.get("API:" + KEY, Bucket.class)).thenReturn(bucket);
            stubProbeAllowed(5);

            rateLimitingService.isAllowed(KEY, RateLimitingService.BucketType.AUTH);
            rateLimitingService.isAllowed(KEY, RateLimitingService.BucketType.API);

            verify(cache).get("AUTH:" + KEY, Bucket.class);
            verify(cache).get("API:" + KEY, Bucket.class);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // getRemainingTokens
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    class GetRemainingTokens {

        // ── cache unavailable ─────────────────────────────────────────────────

        @Test
        void nullCache_returnsNegativeOne() {
            when(cacheManager.getCache(CACHE_NAME)).thenReturn(null);

            long result = rateLimitingService.getRemainingTokens(KEY, RateLimitingService.BucketType.AUTH);

            assertThat(result).isEqualTo(-1L);
        }

        // ── existing bucket in cache ──────────────────────────────────────────

        @Test
        void existingBucket_returnsAvailableTokens() {
            stubBucketInCache(RateLimitingService.BucketType.AUTH);
            when(bucket.getAvailableTokens()).thenReturn(7L);

            long result = rateLimitingService.getRemainingTokens(KEY, RateLimitingService.BucketType.AUTH);

            assertThat(result).isEqualTo(7L);
        }

        @Test
        void existingBucket_zeroTokens_returnsZero() {
            stubBucketInCache(RateLimitingService.BucketType.API);
            when(bucket.getAvailableTokens()).thenReturn(0L);

            long result = rateLimitingService.getRemainingTokens(KEY, RateLimitingService.BucketType.API);

            assertThat(result).isEqualTo(0L);
        }

        @Test
        void existingBucket_doesNotCreateNewBucket() {
            stubBucketInCache(RateLimitingService.BucketType.AUTH);
            when(bucket.getAvailableTokens()).thenReturn(5L);

            rateLimitingService.getRemainingTokens(KEY, RateLimitingService.BucketType.AUTH);

            verifyNoInteractions(rateLimitingConfig);
            verify(cache, never()).put(anyString(), any());
        }

        // ── no bucket in cache → creates fresh bucket, does NOT store it ──────

        @Test
        void noBucketInCache_returnsFullCapacityOfNewAuthBucket() {
            stubNoBucketInCache(RateLimitingService.BucketType.AUTH);
            Bucket freshBucket = mock(Bucket.class);
            when(rateLimitingConfig.createAuthBucket()).thenReturn(freshBucket);
            when(freshBucket.getAvailableTokens()).thenReturn(100L);

            long result = rateLimitingService.getRemainingTokens(KEY, RateLimitingService.BucketType.AUTH);

            assertThat(result).isEqualTo(100L);
        }

        @Test
        void noBucketInCache_createsApiBucketForCapacityCheck() {
            stubNoBucketInCache(RateLimitingService.BucketType.API);
            Bucket freshBucket = mock(Bucket.class);
            when(rateLimitingConfig.createApiBucket()).thenReturn(freshBucket);
            when(freshBucket.getAvailableTokens()).thenReturn(50L);

            long result = rateLimitingService.getRemainingTokens(KEY, RateLimitingService.BucketType.API);

            assertThat(result).isEqualTo(50L);
            verify(rateLimitingConfig).createApiBucket();
        }

        @Test
        void noBucketInCache_createsStrictBucketForCapacityCheck() {
            stubNoBucketInCache(RateLimitingService.BucketType.STRICT);
            Bucket freshBucket = mock(Bucket.class);
            when(rateLimitingConfig.createStrictBucket()).thenReturn(freshBucket);
            when(freshBucket.getAvailableTokens()).thenReturn(5L);

            long result = rateLimitingService.getRemainingTokens(KEY, RateLimitingService.BucketType.STRICT);

            assertThat(result).isEqualTo(5L);
            verify(rateLimitingConfig).createStrictBucket();
        }

        @Test
        void noBucketInCache_doesNotPersistFreshBucketToCache() {
            // getRemainingTokens() must NOT store the bucket — only isAllowed() does
            stubNoBucketInCache(RateLimitingService.BucketType.AUTH);
            Bucket freshBucket = mock(Bucket.class);
            when(rateLimitingConfig.createAuthBucket()).thenReturn(freshBucket);
            when(freshBucket.getAvailableTokens()).thenReturn(100L);

            rateLimitingService.getRemainingTokens(KEY, RateLimitingService.BucketType.AUTH);

            verify(cache, never()).put(anyString(), any());
        }

        // ── parameterized across all bucket types ─────────────────────────────

        @ParameterizedTest
        @EnumSource(RateLimitingService.BucketType.class)
        void existingBucket_allTypes_returnsCorrectTokenCount(RateLimitingService.BucketType type) {
            String cacheKey = type.name() + ":" + KEY;
            when(cache.get(cacheKey, Bucket.class)).thenReturn(bucket);
            when(bucket.getAvailableTokens()).thenReturn(42L);

            long result = rateLimitingService.getRemainingTokens(KEY, type);

            assertThat(result).isEqualTo(42L);
        }
    }
}