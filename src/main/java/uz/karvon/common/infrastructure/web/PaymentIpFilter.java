package uz.karvon.common.infrastructure.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Payme and Click callbacks are accepted only from their published IP addresses (karvon.security.payme-allowed-ips,
 * click-allowed-ips). An empty list allows any IP: that is for dev and test; production has no default, so the
 * lists must be set. The client IP is the real one behind nginx (server.forward-headers-strategy=native).
 * This is in addition to the providers' own checks (Payme Basic auth, Click signature).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class PaymentIpFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(PaymentIpFilter.class);

    static final String PAYME = "/api/v1/payments/payme";
    static final String CLICK = "/api/v1/payments/click/";

    private final IpRanges payme;
    private final IpRanges click;
    private final ProblemResponses problems;

    public PaymentIpFilter(WebSecurityProperties properties, ProblemResponses problems) {
        this.payme = IpRanges.parse(properties.paymeAllowedIps());
        this.click = IpRanges.parse(properties.clickAllowedIps());
        this.problems = problems;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        IpRanges allowed = path.equals(PAYME) || path.startsWith(PAYME + "/") ? payme
                : path.startsWith(CLICK) ? click
                : null;
        String ip = request.getRemoteAddr();
        if (allowed != null && !allowed.isEmpty() && !allowed.contains(ip)) {
            log.warn("Payment callback {} refused from IP {}", path, ip);
            problems.write(request, response, HttpStatus.FORBIDDEN, "IP_NOT_ALLOWED", "error.request.ipNotAllowed");
            return;
        }
        chain.doFilter(request, response);
    }
}
