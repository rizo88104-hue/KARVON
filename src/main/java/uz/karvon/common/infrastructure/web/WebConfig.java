package uz.karvon.common.infrastructure.web;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.time.Duration;
import java.util.List;

/**
 * CORS: only the site origins from karvon.security.cors-allowed-origins (CORS_ALLOWED_ORIGINS) may call the API
 * from a browser. The site itself calls the API through its own server (Next.js rewrite), so the list is mostly a
 * safety net for direct browser calls; with an empty list no cross-origin call is allowed.
 */
@Configuration
@EnableConfigurationProperties(WebSecurityProperties.class)
public class WebConfig {

    @Bean
    CorsConfigurationSource corsConfigurationSource(WebSecurityProperties properties) {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        List<String> origins = properties.corsAllowedOrigins().stream().map(String::trim).filter(o -> !o.isEmpty()).toList();
        if (origins.isEmpty()) {
            return source;
        }
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(origins);
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept-Language", RequestIdFilter.HEADER));
        cors.setExposedHeaders(List.of(RequestIdFilter.HEADER, "Retry-After", "Content-Disposition"));
        cors.setAllowCredentials(false);
        cors.setMaxAge(Duration.ofHours(1));
        source.registerCorsConfiguration("/api/**", cors);
        return source;
    }
}
