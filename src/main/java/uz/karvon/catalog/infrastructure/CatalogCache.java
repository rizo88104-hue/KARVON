package uz.karvon.catalog.infrastructure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;
import uz.karvon.catalog.domain.ProductDetail;
import uz.karvon.catalog.domain.SearchResult;

import java.time.Duration;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Provider answers in Redis (Chinese, before translation), so the paid Taobao API is asked as little as possible.
 * Search results live {@code karvon.catalog.cache.search-ttl} (30 min), products
 * {@code karvon.catalog.cache.product-ttl} (2 h). Redis problems are logged and the provider is asked directly.
 */
@Component
public class CatalogCache {

    static final String SEARCH_PREFIX = "catalog:search:";
    static final String PRODUCT_PREFIX = "catalog:product:";

    private static final Logger log = LoggerFactory.getLogger(CatalogCache.class);

    private final StringRedisTemplate redis;
    private final JsonMapper json;
    private final Duration searchTtl;
    private final Duration productTtl;

    public CatalogCache(StringRedisTemplate redis, JsonMapper json,
                        @Value("${karvon.catalog.cache.search-ttl:30m}") Duration searchTtl,
                        @Value("${karvon.catalog.cache.product-ttl:2h}") Duration productTtl) {
        this.redis = redis;
        this.json = json;
        this.searchTtl = searchTtl;
        this.productTtl = productTtl;
    }

    /** {@code key} identifies the query (it is hashed by the caller or used as is). */
    public SearchResult search(String key, Supplier<SearchResult> loader) {
        return cached(SEARCH_PREFIX + key, SearchResult.class, searchTtl, loader);
    }

    public ProductDetail product(String externalId, Supplier<ProductDetail> loader) {
        return cached(PRODUCT_PREFIX + externalId, ProductDetail.class, productTtl, loader);
    }

    private <T> T cached(String key, Class<T> type, Duration ttl, Supplier<T> loader) {
        Optional<T> hit = read(key, type);
        if (hit.isPresent()) {
            return hit.get();
        }
        T value = loader.get();
        write(key, value, ttl);
        return value;
    }

    private <T> Optional<T> read(String key, Class<T> type) {
        try {
            String value = redis.opsForValue().get(key);
            return value == null ? Optional.empty() : Optional.of(json.readValue(value, type));
        } catch (RuntimeException ex) {
            log.warn("Could not read {} from Redis", key, ex);
            return Optional.empty();
        }
    }

    private void write(String key, Object value, Duration ttl) {
        try {
            redis.opsForValue().set(key, json.writeValueAsString(value), ttl);
        } catch (RuntimeException ex) {
            log.warn("Could not cache {} in Redis", key, ex);
        }
    }
}
