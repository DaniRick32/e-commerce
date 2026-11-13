package com.ecommerce.server.cache;

import com.ecommerce.server.dto.product.ProductResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductSearchCacheTest {
    private static final Duration TTL = Duration.ofSeconds(60);

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    private ProductSearchCache cache;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        cache = new ProductSearchCache(redisTemplate, jsonMapper, true, TTL);
    }

    @Test
    void buildsNormalizedKeyForCacheMiss() {
        when(valueOperations.get(anyString())).thenReturn(null);

        ProductSearchCache.CacheLookup lookup =
                cache.lookup("  Phone ", 7L, new BigDecimal("10.00"), null);

        assertFalse(lookup.isHit());
        assertNull(lookup.results());
        assertEquals(
                "product-search:g0:q=phone:c=7:min=10:max=",
                lookup.key()
        );
    }

    @Test
    void returnsCachedResults() {
        ProductResponse product = new ProductResponse(
                1L,
                "Phone",
                "A phone",
                new BigDecimal("9.99"),
                3,
                7L,
                "Tech",
                LocalDateTime.of(2026, 1, 2, 3, 4)
        );
        String json = jsonMapper.writeValueAsString(List.of(product));

        when(valueOperations.get("product-search:generation")).thenReturn("3");
        when(valueOperations.get(
                "product-search:g3:q=phone:c=:min=:max="
        )).thenReturn(json);

        ProductSearchCache.CacheLookup lookup =
                cache.lookup("phone", null, null, null);

        assertTrue(lookup.isHit());
        assertEquals(1, lookup.results().size());
        assertEquals(1L, lookup.results().get(0).id());
        assertEquals(0, new BigDecimal("9.99").compareTo(lookup.results().get(0).price()));
    }

    @Test
    void fallsBackToDatabaseWhenRedisFails() {
        when(valueOperations.get(anyString()))
                .thenThrow(new RedisConnectionFailureException("Redis is down"));

        ProductSearchCache.CacheLookup lookup =
                cache.lookup("phone", null, null, null);

        assertFalse(lookup.isHit());
        assertNull(lookup.key());
    }

    @Test
    void putsResultsWithTtl() {
        List<ProductResponse> results = List.of(new ProductResponse(
                1L,
                "Phone",
                null,
                new BigDecimal("9.99"),
                3,
                null,
                null,
                LocalDateTime.of(2026, 1, 2, 3, 4)
        ));

        cache.put("product-search:g0:q=phone:c=:min=:max=", results);

        verify(valueOperations).set(
                eq("product-search:g0:q=phone:c=:min=:max="),
                anyString(),
                eq(TTL)
        );
    }

    @Test
    void invalidateAdvancesCacheGeneration() {
        cache.invalidate();

        verify(valueOperations).increment("product-search:generation");
    }

    @Test
    void disabledCacheNeverTouchesRedis() {
        ProductSearchCache disabled =
                new ProductSearchCache(redisTemplate, jsonMapper, false, TTL);

        ProductSearchCache.CacheLookup lookup =
                disabled.lookup("phone", null, null, null);

        assertFalse(lookup.isHit());
        assertNull(lookup.key());

        disabled.put("key", List.of());
        disabled.invalidate();

        verifyNoInteractions(redisTemplate);
    }
}
