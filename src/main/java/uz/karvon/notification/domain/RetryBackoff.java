package uz.karvon.notification.domain;

import java.time.Duration;

/**
 * Delay before the next attempt: {@code base} after the first failure, doubled after each next one, at most {@code max}.
 */
public record RetryBackoff(Duration base, Duration max) {

    /** {@code attempts}: how many sends have been tried so far (at least 1). */
    public Duration delayAfter(int attempts) {
        int doublings = Math.min(Math.max(attempts, 1) - 1, 30);
        Duration delay = base.multipliedBy(1L << doublings);
        return delay.compareTo(max) > 0 ? max : delay;
    }
}
