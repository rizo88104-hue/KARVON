package uz.karvon.common.infrastructure.web;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;

import java.time.Duration;
import java.util.List;

/**
 * {@code karvon.security.*}: request-level protection in front of the API.
 *
 * @param corsAllowedOrigins site origins allowed to call the API from a browser (e.g. https://karvon.uz); empty = none
 * @param paymeAllowedIps    IPs or CIDR ranges allowed to call /api/v1/payments/payme; empty = any (dev and test only)
 * @param clickAllowedIps    IPs or CIDR ranges allowed to call /api/v1/payments/click/**; empty = any (dev and test only)
 * @param maxJsonBody        largest non-multipart request body (multipart limits are spring.servlet.multipart.*)
 * @param metricsToken       Bearer token for /actuator/prometheus; empty = the endpoint is closed
 */
@ConfigurationProperties("karvon.security")
public record WebSecurityProperties(@DefaultValue List<String> corsAllowedOrigins,
                                    @DefaultValue List<String> paymeAllowedIps,
                                    @DefaultValue List<String> clickAllowedIps,
                                    @DefaultValue("1MB") DataSize maxJsonBody,
                                    @DefaultValue("") String metricsToken,
                                    @DefaultValue RateLimit rateLimit) {

    /**
     * Token buckets per client IP and endpoint group, shared by all API instances through Redis.
     */
    public record RateLimit(@DefaultValue("true") boolean enabled,
                            @DefaultValue({"10", "1m"}) Limit auth,
                            @DefaultValue({"120", "1m"}) Limit catalog,
                            @DefaultValue({"300", "1m"}) Limit payments) {
    }

    /** {@code capacity} requests per {@code period}, refilled gradually. */
    public record Limit(long capacity, Duration period) {
    }
}
