package com.ecommerce.server.cache;

import com.ecommerce.server.dto.product.ProductResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Locale;

/**
 * Redis-backed cache for product search results.
 *
 * <p>Entries are keyed by the normalized search parameters and a cache
 * generation. Writes bump the generation, which immediately makes all
 * previously cached searches unreachable; the orphaned entries then expire
 * through their TTL.
 *
 * <p>Redis is treated as an optional dependency: when it is unavailable the
 * lookup reports a miss and searches fall back to the database. Failures are
 * logged once and Redis is not retried for a short cooldown to avoid paying
 * the connection timeout on every request.
 */
@Component
public class ProductSearchCache {
    private static final Logger log = LoggerFactory.getLogger(ProductSearchCache.class);

    private static final String CACHE_PREFIX = "product-search";
    private static final String GENERATION_KEY = CACHE_PREFIX + ":generation";
    private static final long RETRY_COOLDOWN_NANOS = Duration.ofSeconds(30).toNanos();
    private static final TypeReference<List<ProductResponse>> RESULT_LIST =
            new TypeReference<>() {};

    private final StringRedisTemplate redisTemplate;
    private final JsonMapper jsonMapper;
    private final boolean enabled;
    private final Duration ttl;

    private volatile long retryAfterNanos;

    public ProductSearchCache(
            StringRedisTemplate redisTemplate,
            JsonMapper jsonMapper,
            @Value("${app.cache.product-search.enabled:true}") boolean enabled,
            @Value("${app.cache.product-search.ttl:60s}") Duration ttl
    ) {
        this.redisTemplate = redisTemplate;
        this.jsonMapper = jsonMapper;
        this.enabled = enabled;
        this.ttl = ttl;
    }

    /**
     * Looks up a cached search.
     *
     * <p>The returned lookup always carries the cache key when the cache is
     * usable, even on a miss, so the caller can store its result without
     * resolving the key a second time (which could race with an invalidation).
     */
    public CacheLookup lookup(
            String q,
            Long categoryId,
            BigDecimal minPrice,
            BigDecimal maxPrice
    ) {
        if (!enabled || coolingDown()) {
            return CacheLookup.miss();
        }

        try {
            String key = buildKey(q, categoryId, minPrice, maxPrice);
            String json = redisTemplate.opsForValue().get(key);

            if (json == null) {
                return new CacheLookup(key, null);
            }

            return new CacheLookup(key, jsonMapper.readValue(json, RESULT_LIST));
        } catch (Exception ex) {
            recordFailure("read", ex);
            return CacheLookup.miss();
        }
    }

    public void put(String key, List<ProductResponse> results) {
        if (!enabled || key == null) {
            return;
        }

        try {
            String json = jsonMapper.writeValueAsString(results);
            redisTemplate.opsForValue().set(key, json, ttl);
        } catch (Exception ex) {
            recordFailure("write", ex);
        }
    }

    /**
     * Invalidates every cached search by moving to the next generation.
     */
    public void invalidate() {
        if (!enabled) {
            return;
        }

        try {
            redisTemplate.opsForValue().increment(GENERATION_KEY);
        } catch (Exception ex) {
            recordFailure("invalidate", ex);
        }
    }

    private String buildKey(
            String q,
            Long categoryId,
            BigDecimal minPrice,
            BigDecimal maxPrice
    ) {
        return CACHE_PREFIX
                + ":g" + generation()
                + ":q=" + normalizeQuery(q)
                + ":c=" + (categoryId == null ? "" : categoryId)
                + ":min=" + normalizePrice(minPrice)
                + ":max=" + normalizePrice(maxPrice);
    }

    private long generation() {
        String value = redisTemplate.opsForValue().get(GENERATION_KEY);
        return value == null ? 0L : Long.parseLong(value);
    }

    private static String normalizeQuery(String q) {
        return q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizePrice(BigDecimal price) {
        return price == null ? "" : price.stripTrailingZeros().toPlainString();
    }

    private boolean coolingDown() {
        return System.nanoTime() < retryAfterNanos;
    }

    private void recordFailure(String operation, Exception ex) {
        long now = System.nanoTime();
        boolean alreadyCoolingDown = now < retryAfterNanos;
        retryAfterNanos = now + RETRY_COOLDOWN_NANOS;

        if (alreadyCoolingDown) {
            log.debug("Product search cache {} failed: {}", operation, ex.getMessage());
        } else {
            log.warn(
                    "Product search cache {} failed: {}. Falling back to the database "
                            + "and retrying Redis in {}s.",
                    operation,
                    ex.getMessage(),
                    RETRY_COOLDOWN_NANOS / 1_000_000_000L
            );
        }
    }

    /**
     * Result of a cache lookup. {@code results} is {@code null} on a miss and
     * {@code key} is {@code null} when the cache is disabled or unavailable.
     */
    public record CacheLookup(String key, List<ProductResponse> results) {
        public static CacheLookup miss() {
            return new CacheLookup(null, null);
        }

        public boolean isHit() {
            return results != null;
        }
    }
}
