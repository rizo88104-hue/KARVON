package uz.karvon.auth.infrastructure;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/**
 * HS256 access tokens signed with JWT_SECRET.
 */
@Configuration
@EnableConfigurationProperties(AuthProperties.class)
public class JwtConfig {

    public static final String ISSUER = "karvon";
    public static final String ROLES_CLAIM = "roles";

    /** HS256 needs a key of at least 256 bits. */
    static final int MIN_SECRET_BYTES = 32;

    @Bean
    public JwtEncoder jwtEncoder(AuthProperties properties) {
        return NimbusJwtEncoder.withSecretKey(secretKey(properties)).build();
    }

    @Bean
    public JwtDecoder jwtDecoder(AuthProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(secretKey(properties))
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(ISSUER));
        return decoder;
    }

    static SecretKey secretKey(AuthProperties properties) {
        byte[] bytes = properties.jwtSecret().getBytes(StandardCharsets.UTF_8);
        if (bytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("JWT_SECRET must be at least " + MIN_SECRET_BYTES + " bytes long");
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }
}
