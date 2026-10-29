package uz.karvon.order.application;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Cancels orders that were not paid within the payment timeout (30 minutes). Each order is cancelled in its own
 * transaction, so one failure (e.g. a payment arriving at the same moment) does not stop the others.
 */
@Component
@RequiredArgsConstructor
public class UnpaidOrderCancelJob {

    private static final Logger log = LoggerFactory.getLogger(UnpaidOrderCancelJob.class);

    private final OrderService orders;

    /** Returns how many orders were cancelled. */
    @Scheduled(fixedDelayString = "${karvon.orders.auto-cancel-check-ms:60000}",
            initialDelayString = "${karvon.orders.auto-cancel-check-ms:60000}")
    public int cancelUnpaid() {
        int cancelled = 0;
        for (UUID orderId : orders.unpaidExpired()) {
            try {
                if (orders.cancelIfUnpaid(orderId)) {
                    cancelled++;
                }
            } catch (RuntimeException ex) {
                log.warn("Could not cancel unpaid order {}", orderId, ex);
            }
        }
        if (cancelled > 0) {
            log.info("Cancelled {} unpaid orders", cancelled);
        }
        return cancelled;
    }
}
