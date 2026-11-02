package uz.karvon.notification.infrastructure;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * The notification thread pool. When the queue is full the calling thread sends itself, so nothing is dropped.
 * {@code async=false} (tests) sends in the calling thread right after the commit.
 * The retry job interval is karvon.notification.retry-check-ms.
 */
@Validated
@ConfigurationProperties("karvon.notification")
public record NotificationProperties(
        @DefaultValue("true") boolean async,
        @DefaultValue("2") @Min(1) int threads,
        @DefaultValue("4") @Min(1) int maxThreads,
        @DefaultValue("1000") @Min(1) int queueCapacity) {
}
