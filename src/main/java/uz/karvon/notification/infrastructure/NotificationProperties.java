package uz.karvon.notification.infrastructure;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;
import uz.karvon.notification.domain.RetryBackoff;

import java.time.Duration;

/**
 * Sending and retrying notifications.
 *
 * @param maxRetries   how many times a FAILED notification is sent again automatically (after the first attempt)
 * @param retryBase    delay before the first retry; doubled for each next one, at most {@code retryMax}
 * @param sendingLease how long a notification being sent is not picked up by the retry job
 * @param batchSize    notifications retried per job run
 * @param threads      sending threads; {@code queueCapacity} messages may wait for them
 */
@Validated
@ConfigurationProperties("karvon.notifications")
public record NotificationProperties(
        @DefaultValue("5") @Min(0) int maxRetries,
        @DefaultValue("1m") Duration retryBase,
        @DefaultValue("1h") Duration retryMax,
        @DefaultValue("5m") Duration sendingLease,
        @DefaultValue("100") @Min(1) int batchSize,
        @DefaultValue("2") @Min(1) int threads,
        @DefaultValue("1000") @Min(1) int queueCapacity) {

    public RetryBackoff backoff() {
        return new RetryBackoff(retryBase, retryMax);
    }
}
