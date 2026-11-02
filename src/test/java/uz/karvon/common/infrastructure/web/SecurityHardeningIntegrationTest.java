package uz.karvon.common.infrastructure.web;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import uz.karvon.AbstractIntegrationTest;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static uz.karvon.TestLogins.newPhone;

/**
 * Request-level protection with real Redis: rate limits (Bucket4j on Spring's Lettuce client), payment callback IP
 * lists, CORS, security headers, request id, body size limit, metrics token and health groups.
 */
@TestPropertySource(properties = {
        "karvon.security.rate-limit.enabled=true",
        "karvon.security.rate-limit.auth.capacity=3",
        "karvon.security.rate-limit.auth.period=1m",
        "karvon.security.payme-allowed-ips=203.0.113.0/24",
        "karvon.security.click-allowed-ips=203.0.113.0/24, 192.0.2.10",
        "karvon.security.cors-allowed-origins=https://shop.example",
        "karvon.security.metrics-token=test-metrics-token",
})
@ExtendWith(OutputCaptureExtension.class)
class SecurityHardeningIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvcTester mvc;

    @Test
    void authIsRateLimitedPerIp() {
        String ip = "198.51.100.7";
        for (int i = 0; i < 3; i++) {
            assertThat(otpRequest(ip)).hasStatusOk();
        }
        MvcTestResult limited = otpRequest(ip);

        assertThat(limited).hasStatus(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(limited).bodyJson().extractingPath("$.code").isEqualTo("RATE_LIMITED");
        assertThat(limited).bodyJson().extractingPath("$.detail").asString().contains("soniyadan keyin");
        assertThat(limited).headers().containsHeader("Retry-After");
        assertThat(otpRequest("198.51.100.8")).as("another client is not affected").hasStatusOk();
        assertThat(mvc.get().uri("/api/v1/catalog/categories").with(from(ip)).exchange())
                .as("other groups have their own bucket").hasStatusOk();
    }

    @Test
    void paymentCallbacksOnlyFromAllowedIps(CapturedOutput output) {
        MvcTestResult stranger = mvc.post().uri("/api/v1/payments/payme").with(from("198.51.100.9"))
                .header(RequestIdFilter.HEADER, "req-payme-refused-1")
                .contentType(MediaType.APPLICATION_JSON).content("{}").exchange();
        assertThat(stranger).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(stranger).bodyJson().extractingPath("$.code").isEqualTo("IP_NOT_ALLOWED");
        // Every log line of the request carries its id (MDC "requestId"; the JSON logs of prod have it as a field).
        assertThat(output.getOut().lines().filter(line -> line.contains("Payment callback")).findFirst())
                .hasValueSatisfying(line -> assertThat(line).contains("[req-payme-refused-1]"));

        // From Payme's range the request reaches the Merchant API, which refuses it by its own rules (no auth).
        MvcTestResult payme = mvc.post().uri("/api/v1/payments/payme").with(from("203.0.113.5"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"CheckPerformTransaction\",\"params\":{}}").exchange();
        assertThat(payme).hasStatusOk();
        assertThat(payme).bodyJson().extractingPath("$.error.code").isEqualTo(-32504);

        assertThat(mvc.post().uri("/api/v1/payments/click/prepare").with(from("198.51.100.9")).exchange())
                .hasStatus(HttpStatus.FORBIDDEN);
        assertThat(mvc.post().uri("/api/v1/payments/click/prepare").with(from("192.0.2.10")).exchange().getResponse().getStatus())
                .as("an allowed IP gets past the filter").isNotEqualTo(HttpStatus.FORBIDDEN.value());
    }

    @Test
    void corsOnlyForTheSiteOrigin() {
        MvcTestResult allowed = mvc.options().uri("/api/v1/catalog/categories")
                .header(HttpHeaders.ORIGIN, "https://shop.example")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET").exchange();
        assertThat(allowed).hasStatusOk();
        assertThat(allowed).headers().hasValue(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://shop.example");

        MvcTestResult other = mvc.options().uri("/api/v1/catalog/categories")
                .header(HttpHeaders.ORIGIN, "https://evil.example")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET").exchange();
        assertThat(other).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(other).headers().doesNotContainHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN);
    }

    @Test
    void securityHeadersAndRequestId() {
        MvcTestResult ping = mvc.get().uri("/api/v1/ping").exchange();

        assertThat(ping).hasStatusOk();
        assertThat(ping).headers().hasValue("X-Content-Type-Options", "nosniff");
        assertThat(ping).headers().hasValue("X-Frame-Options", "DENY");
        assertThat(ping).headers().hasValue("Referrer-Policy", "no-referrer");
        assertThat(ping.getResponse().getHeader("Content-Security-Policy")).startsWith("default-src 'none'");
        assertThat(ping.getResponse().getHeader("Permissions-Policy")).contains("camera=()");
        assertThat(ping.getResponse().getHeader(RequestIdFilter.HEADER)).hasSize(36);

        MvcTestResult traced = mvc.get().uri("/api/v1/ping").header(RequestIdFilter.HEADER, "nginx-req-12345").exchange();
        assertThat(traced).headers().hasValue(RequestIdFilter.HEADER, "nginx-req-12345");
        MvcTestResult unsafe = mvc.get().uri("/api/v1/ping").header(RequestIdFilter.HEADER, "bad id\r\nX-Evil: 1").exchange();
        assertThat(unsafe.getResponse().getHeader(RequestIdFilter.HEADER)).as("replaced, not echoed").hasSize(36);
    }

    @Test
    void tooLargeJsonBodyIsRefusedBeforeItIsRead() {
        byte[] body = new byte[1024 * 1024 + 1];
        java.util.Arrays.fill(body, (byte) ' ');
        MvcTestResult result = mvc.post().uri("/api/v1/auth/otp/request").with(from("198.51.100.20"))
                .contentType(MediaType.APPLICATION_JSON).content(body).exchange();

        assertThat(result).hasStatus(HttpStatus.CONTENT_TOO_LARGE);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("REQUEST_TOO_LARGE");
    }

    @Test
    void prometheusNeedsTheToken() {
        assertThat(mvc.get().uri("/actuator/prometheus").exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(mvc.get().uri("/actuator/prometheus").header(HttpHeaders.AUTHORIZATION, "Bearer wrong").exchange())
                .hasStatus(HttpStatus.UNAUTHORIZED);

        MvcTestResult metrics = mvc.get().uri("/actuator/prometheus")
                .header(HttpHeaders.AUTHORIZATION, "Bearer test-metrics-token").exchange();
        assertThat(metrics).hasStatusOk();
        assertThat(new String(metrics.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8))
                .contains("jvm_memory_used_bytes").contains("hikaricp_connections").contains("application=\"karvon-api\"");
    }

    @Test
    void healthGroupsCheckDatabaseRedisAndTaobao() {
        assertThat(mvc.get().uri("/actuator/health/readiness").exchange()).bodyJson().extractingPath("$.status").isEqualTo("UP");
        assertThat(mvc.get().uri("/actuator/health/liveness").exchange()).bodyJson().extractingPath("$.status").isEqualTo("UP");
        assertThat(mvc.get().uri("/actuator/health/dependencies").exchange()).bodyJson().extractingPath("$.status").isEqualTo("UP");
    }

    private MvcTestResult otpRequest(String ip) {
        return mvc.post().uri("/api/v1/auth/otp/request").with(from(ip)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"phone\":\"%s\"}".formatted(newPhone())).exchange();
    }

    private static RequestPostProcessor from(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }
}
