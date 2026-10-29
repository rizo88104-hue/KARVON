package uz.karvon.auth.infrastructure;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Stateless JWT security. Access tokens come in the "Authorization: Bearer" header.
 * Staff endpoints (/api/v1/admin/**) need a staff role; finer rules use @PreAuthorize.
 * 401 and 403 from the filter chain go through GlobalExceptionHandler, so they are ProblemDetails too.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    static final String[] PUBLIC_ENDPOINTS = {
            "/actuator/health", "/actuator/health/**",
            "/swagger-ui", "/swagger-ui/**", "/swagger-ui.html",
            "/v3/api-docs", "/v3/api-docs/**",
            "/api/v1/ping",
            "/api/v1/auth/**",
            "/api/v1/settings/pricing",
            "/api/v1/pricing/estimate",
            // The controller exists only in the dev and test profiles.
            "/api/v1/dev/**",
            "/error"
    };

    static final String[] STAFF_ROLES = {"OPERATOR", "BUYER", "ADMIN"};

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtDecoder jwtDecoder,
            JwtUserAuthenticator authenticator,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver) throws Exception {
        AuthenticationEntryPoint unauthorized = (request, response, ex) ->
                exceptionResolver.resolveException(request, response, null, ex);
        AccessDeniedHandler forbidden = (request, response, ex) ->
                exceptionResolver.resolveException(request, response, null, ex);
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                        .requestMatchers("/api/v1/admin/**").hasAnyRole(STAFF_ROLES)
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(jwt -> jwt.decoder(jwtDecoder).jwtAuthenticationConverter(authenticator::authenticate))
                        .authenticationEntryPoint(unauthorized)
                        .accessDeniedHandler(forbidden))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint(unauthorized)
                        .accessDeniedHandler(forbidden))
                .build();
    }
}
