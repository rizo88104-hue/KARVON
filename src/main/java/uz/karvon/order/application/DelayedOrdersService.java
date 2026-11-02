package uz.karvon.order.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.karvon.order.api.dto.DelayedOrderResponse;
import uz.karvon.order.domain.OrderStatus;
import uz.karvon.order.infrastructure.DelayedOrderQueries;
import uz.karvon.settings.application.SlaSettingsService;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Orders that stay in a status longer than the SLA allows (e.g. PAID over 48 hours). The time in a status counts
 * from the last time the order entered it. Limits for names that are not order statuses are ignored.
 */
@Service
@RequiredArgsConstructor
public class DelayedOrdersService {

    private final SlaSettingsService sla;
    private final DelayedOrderQueries queries;
    private final Clock clock;

    /** The most overdue first. */
    @Transactional(readOnly = true)
    public List<DelayedOrderResponse> delayed() {
        Instant now = Instant.now(clock);
        List<DelayedOrderResponse> delayed = new ArrayList<>();
        for (Map.Entry<String, Integer> limit : sla.current().limits().entrySet()) {
            OrderStatus status;
            try {
                status = OrderStatus.valueOf(limit.getKey());
            } catch (IllegalArgumentException ex) {
                continue;
            }
            Duration max = Duration.ofHours(limit.getValue());
            for (DelayedOrderQueries.Row row : queries.inStatusSince(status, now.minus(max))) {
                long hours = Duration.between(row.since(), now).toHours();
                delayed.add(new DelayedOrderResponse(row.id(), row.number(), row.status(), row.since(), hours,
                        limit.getValue(), hours - limit.getValue(), row.buyerId()));
            }
        }
        delayed.sort(Comparator.comparingLong(DelayedOrderResponse::overdueHours).reversed());
        return delayed;
    }
}
