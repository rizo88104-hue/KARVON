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
 * @param siteUrl      the customer site, for links in messages (the order page)
 * @param reviewUrl    where "leave a rating" in the delivered message points; {@code {number}} is replaced by the
 *                     order number. Empty = the order page on the site
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
        @DefaultValue("1000") @Min(1) int queueCapacity,
        @DefaultValue("http://localhost:3000") String siteUrl,
        @DefaultValue("") String reviewUrl) {

    public RetryBackoff backoff() {
        return new RetryBackoff(retryBase, retryMax);
    }

    /** The "leave a rating" link of an order. */
    public String reviewLink(String orderNumber) {
        if (reviewUrl != null && !reviewUrl.isBlank()) {
            return reviewUrl.trim().replace("{number}", orderNumber);
        }
        String site = siteUrl.endsWith("/") ? siteUrl.substring(0, siteUrl.length() - 1) : siteUrl;
        return site + "/account/orders/" + orderNumber;
    }
}
