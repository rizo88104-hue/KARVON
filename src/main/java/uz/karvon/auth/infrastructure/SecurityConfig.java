package uz.karvon.auth.infrastructure;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.header.writers.DelegatingRequestMatcherHeaderWriter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.NegatedRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.util.ArrayList;
import java.util.List;

/**
 * Stateless JWT security. Access tokens come in the "Authorization: Bearer" header.
 * Staff endpoints (/api/v1/admin/**) need a staff role; finer rules use @PreAuthorize.
 * 401 and 403 from the filter chain go through GlobalExceptionHandler, so they are ProblemDetails too.
 * Request-level protection (request id, body size, payment IP lists, rate limits, metrics token) is in
 * uz.karvon.common.infrastructure.web and runs before this chain.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    static final String[] PUBLIC_ENDPOINTS = {
            "/actuator/health", "/actuator/health/**",
            // Guarded by MetricsTokenFilter (Bearer METRICS_TOKEN), not by user login.
            "/actuator/prometheus",
            // springdoc is switched off in production (application-prod.yml).
            "/swagger-ui", "/swagger-ui/**", "/swagger-ui.html",
            "/v3/api-docs", "/v3/api-docs/**",
            "/api/v1/ping",
            "/api/v1/auth/**",
            "/api/v1/settings/pricing",
            "/api/v1/pricing/estimate",
            "/api/v1/catalog/**",
            // Site content: home page banners, the top bar ticker and text pages.
            "/api/v1/banners", "/api/v1/ticker", "/api/v1/pages/**",
            // Payme and Click call these; they check their own credentials (Basic auth / sign) and PaymentIpFilter.
            "/api/v1/payments/payme", "/api/v1/payments/click/**",
            // Telegram calls this; it checks the secret header itself.
            "/api/v1/telegram/webhook",
            "/error"
    };

    /** Exist only in the dev and test profiles; never opened anywhere else, even if a bean slipped through. */
    static final String[] DEV_ONLY_ENDPOINTS = {
            "/api/v1/dev/**",
            // Local file storage (photos); production serves presigned S3 links instead.
            "/api/v1/files/**",
    };

    static final String[] STAFF_ROLES = {"OPERATOR", "BUYER", "ADMIN"};

    private static final DefaultBearerTokenResolver USER_TOKENS = new DefaultBearerTokenResolver();

    /** The API answers JSON only; Swagger UI (dev) needs its own scripts and styles. */
    static final String API_CSP = "default-src 'none'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'";
    static final String PERMISSIONS_POLICY = "camera=(), microphone=(), geolocation=(), payment=(), usb=()";

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtDecoder jwtDecoder,
            JwtUserAuthenticator authenticator,
            Environment environment,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver) throws Exception {
        AuthenticationEntryPoint unauthorized = (request, response, ex) ->
                exceptionResolver.resolveException(request, response, null, ex);
        AccessDeniedHandler forbidden = (request, response, ex) ->
                exceptionResolver.resolveException(request, response, null, ex);
        List<String> open = new ArrayList<>(List.of(PUBLIC_ENDPOINTS));
        if (environment.acceptsProfiles(Profiles.of("dev", "test"))) {
            open.addAll(List.of(DEV_ONLY_ENDPOINTS));
        }
        var docs = new OrRequestMatcher(
                PathPatternRequestMatcher.withDefaults().matcher("/swagger-ui/**"),
                PathPatternRequestMatcher.withDefaults().matcher("/swagger-ui.html"),
                PathPatternRequestMatcher.withDefaults().matcher("/v3/api-docs/**"));
        return http
                .csrf(AbstractHttpConfigurer::disable)
                // Uses the CorsConfigurationSource bean (allowed site origins from CORS_ALLOWED_ORIGINS).
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(headers -> headers
                        // Defaults stay on: X-Content-Type-Options, X-Frame-Options DENY, Cache-Control, HSTS (https).
                        .referrerPolicy(referrer -> referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                        .addHeaderWriter(new StaticHeadersWriter("Permissions-Policy", PERMISSIONS_POLICY))
                        .addHeaderWriter(new DelegatingRequestMatcherHeaderWriter(new NegatedRequestMatcher(docs),
                                new StaticHeadersWriter("Content-Security-Policy", API_CSP))))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(open.toArray(String[]::new)).permitAll()
                        .requestMatchers("/api/v1/admin/**").hasAnyRole(STAFF_ROLES)
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth
                        // The scrape token of /actuator/prometheus is not a JWT (MetricsTokenFilter checks it).
                        .bearerTokenResolver(request -> "/actuator/prometheus".equals(request.getRequestURI())
                                ? null : USER_TOKENS.resolve(request))
                        .jwt(jwt -> jwt.decoder(jwtDecoder).jwtAuthenticationConverter(authenticator::authenticate))
                        .authenticationEntryPoint(unauthorized)
                        .accessDeniedHandler(forbidden))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint(unauthorized)
                        .accessDeniedHandler(forbidden))
                .build();
    }
}
