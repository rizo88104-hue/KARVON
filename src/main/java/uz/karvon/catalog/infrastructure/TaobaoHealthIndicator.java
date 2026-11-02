package uz.karvon.catalog.infrastructure;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * "taobao" health from the circuit breaker of the real Taobao client, without calling Taobao: an open breaker
 * (half of the recent calls failed) is DOWN. The dev/test fake provider has no breaker and is always UP.
 * Part of the readiness group, so a load balancer can notice an unusable catalog.
 */
@Component("taobao")
public class TaobaoHealthIndicator implements HealthIndicator {

    private final CircuitBreaker breaker;

    public TaobaoHealthIndicator(ObjectProvider<CircuitBreaker> taobaoCircuitBreaker) {
        this.breaker = taobaoCircuitBreaker.getIfAvailable();
    }

    @Override
    public Health health() {
        if (breaker == null) {
            return Health.up().withDetail("provider", "fake").build();
        }
        CircuitBreaker.State state = breaker.getState();
        Health.Builder health = state == CircuitBreaker.State.OPEN || state == CircuitBreaker.State.FORCED_OPEN
                ? Health.down() : Health.up();
        return health.withDetail("circuitBreaker", state.name())
                .withDetail("failureRate", breaker.getMetrics().getFailureRate())
                .build();
    }
}
