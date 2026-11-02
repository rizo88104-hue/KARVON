package uz.karvon.common.infrastructure.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * /actuator/prometheus needs "Authorization: Bearer METRICS_TOKEN" (constant-time compare). Without a configured
 * token the endpoint is closed for everyone. The scraper (Prometheus, Grafana agent) is not a user, so this sits
 * outside the JWT security.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 40)
public class MetricsTokenFilter extends OncePerRequestFilter {

    static final String PATH = "/actuator/prometheus";

    private final byte[] expected;
    private final ProblemResponses problems;

    public MetricsTokenFilter(WebSecurityProperties properties, ProblemResponses problems) {
        String token = properties.metricsToken();
        this.expected = token.isBlank() ? null : ("Bearer " + token).getBytes(StandardCharsets.UTF_8);
        this.problems = problems;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().equals(PATH);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        boolean ok = expected != null && header != null
                && MessageDigest.isEqual(expected, header.getBytes(StandardCharsets.UTF_8));
        if (!ok) {
            problems.write(request, response, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "error.http.401");
            return;
        }
        chain.doFilter(request, response);
    }
}
