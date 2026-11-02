package uz.karvon.common.infrastructure.web;

import io.github.bucket4j.BandwidthBuilder;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * Per-IP rate limits for /api/v1/auth/**, /api/v1/catalog/** and /api/v1/payments/** (429 RATE_LIMITED with
 * Retry-After). Buckets live in Redis, so the limit holds across API instances. If Redis is unavailable the request
 * is let through and the problem is logged: an outage of the limiter must not take the shop down.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 30)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    private enum Group { AUTH, CATALOG, PAYMENTS }

    private final ProxyManager<byte[]> buckets;
    private final WebSecurityProperties.RateLimit limits;
    private final ProblemResponses problems;

    public RateLimitFilter(ObjectProvider<ProxyManager<byte[]>> buckets, WebSecurityProperties properties,
                           ProblemResponses problems) {
        this.buckets = buckets.getIfAvailable();
        this.limits = properties.rateLimit();
        this.problems = problems;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return buckets == null || !limits.enabled() || group(request.getRequestURI()) == null;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Group group = group(request.getRequestURI());
        WebSecurityProperties.Limit limit = switch (group) {
            case AUTH -> limits.auth();
            case CATALOG -> limits.catalog();
            case PAYMENTS -> limits.payments();
        };
        byte[] key = ("rl:" + group.name().toLowerCase() + ":" + request.getRemoteAddr()).getBytes(StandardCharsets.UTF_8);
        ConsumptionProbe probe;
        try {
            probe = buckets.builder().build(key, () -> configuration(limit)).tryConsumeAndReturnRemaining(1);
        } catch (RuntimeException ex) {
            log.warn("Rate limiter unavailable, request allowed", ex);
            chain.doFilter(request, response);
            return;
        }
        if (probe.isConsumed()) {
            response.setHeader("X-RateLimit-Remaining", String.valueOf(probe.getRemainingTokens()));
            chain.doFilter(request, response);
            return;
        }
        long seconds = Math.max(1, TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill()) + 1);
        response.setHeader("Retry-After", String.valueOf(seconds));
        problems.write(request, response, HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", "error.request.rateLimited", seconds);
    }

    private static BucketConfiguration configuration(WebSecurityProperties.Limit limit) {
        return BucketConfiguration.builder()
                .addLimit(BandwidthBuilder.builder().capacity(limit.capacity()).refillGreedy(limit.capacity(), limit.period()).build())
                .build();
    }

    private static Group group(String path) {
        if (path.startsWith("/api/v1/auth/")) {
            return Group.AUTH;
        }
        if (path.startsWith("/api/v1/catalog/")) {
            return Group.CATALOG;
        }
        if (path.startsWith("/api/v1/payments/")) {
            return Group.PAYMENTS;
        }
        return null;
    }
}
