package uz.karvon.settings.infrastructure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import tools.jackson.databind.json.JsonMapper;
import uz.karvon.settings.api.dto.PricingSettingsResponse;
import uz.karvon.settings.application.PricingSettingsChangedEvent;

import java.time.Duration;
import java.util.Optional;

/**
 * The active pricing settings in Redis as JSON. Cleared after the transaction that saves a new version commits,
 * so readers never put the old version back. Redis problems are logged and treated as a cache miss.
 */
@Component
public class PricingSettingsCache {

    static final String KEY = "settings:pricing:current";
    /** Safety net only; a change clears the key at once. */
    private static final Duration TTL = Duration.ofMinutes(10);

    private static final Logger log = LoggerFactory.getLogger(PricingSettingsCache.class);

    private final StringRedisTemplate redis;
    private final JsonMapper json;

    public PricingSettingsCache(StringRedisTemplate redis, JsonMapper json) {
        this.redis = redis;
        this.json = json;
    }

    public Optional<PricingSettingsResponse> get() {
        try {
            String value = redis.opsForValue().get(KEY);
            return value == null ? Optional.empty() : Optional.of(json.readValue(value, PricingSettingsResponse.class));
        } catch (RuntimeException ex) {
            log.warn("Could not read pricing settings from Redis, using the database", ex);
            return Optional.empty();
        }
    }

    public void put(PricingSettingsResponse settings) {
        try {
            redis.opsForValue().set(KEY, json.writeValueAsString(settings), TTL);
        } catch (RuntimeException ex) {
            log.warn("Could not cache pricing settings in Redis", ex);
        }
    }

    @TransactionalEventListener(fallbackExecution = true)
    public void onChanged(PricingSettingsChangedEvent event) {
        try {
            redis.delete(KEY);
        } catch (RuntimeException ex) {
            log.error("Could not clear cached pricing settings; old values stay up to {}", TTL, ex);
        }
    }
}
