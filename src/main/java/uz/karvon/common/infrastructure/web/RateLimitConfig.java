package uz.karvon.common.infrastructure.web;

import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

import java.time.Duration;

/**
 * Bucket4j token buckets stored in Redis, through the Lettuce client that Spring Data Redis already created
 * (same host, password and TLS settings). Off with karvon.security.rate-limit.enabled=false.
 */
@Configuration
@ConditionalOnProperty(name = "karvon.security.rate-limit.enabled", havingValue = "true", matchIfMissing = true)
public class RateLimitConfig {

    @Bean(destroyMethod = "close")
    StatefulRedisConnection<byte[], byte[]> rateLimitRedisConnection(LettuceConnectionFactory factory) {
        if (!(factory.getNativeClient() instanceof RedisClient client)) {
            throw new IllegalStateException("Rate limiting supports a standalone Redis only");
        }
        return client.connect(ByteArrayCodec.INSTANCE);
    }

    @Bean
    ProxyManager<byte[]> rateLimitBuckets(StatefulRedisConnection<byte[], byte[]> rateLimitRedisConnection) {
        return LettuceBasedProxyManager.builderFor(rateLimitRedisConnection)
                // A bucket key disappears from Redis once the bucket would be full again (plus a margin).
                .withExpirationStrategy(ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(Duration.ofSeconds(10)))
                .build();
    }
}
