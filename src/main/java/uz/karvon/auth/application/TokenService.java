package uz.karvon.auth.application;

import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import uz.karvon.auth.domain.RefreshToken;
import uz.karvon.auth.infrastructure.AuthProperties;
import uz.karvon.auth.infrastructure.JwtConfig;
import uz.karvon.auth.infrastructure.RefreshTokenRepository;
import uz.karvon.user.api.dto.UserResponse;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Issues the access JWT (sub = user id, roles) and an opaque random refresh token.
 */
@Service
@RequiredArgsConstructor
public class TokenService {

    private static final int REFRESH_TOKEN_BYTES = 32;

    private final JwtEncoder jwtEncoder;
    private final RefreshTokenRepository refreshTokens;
    private final AuthProperties properties;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    /** Must run inside a transaction: the refresh token is saved. */
    public TokenPair issue(UserResponse user) {
        Instant now = Instant.now(clock);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(JwtConfig.ISSUER)
                .subject(user.id().toString())
                .issuedAt(now)
                .expiresAt(now.plus(properties.accessTokenTtl()))
                .claim(JwtConfig.ROLES_CLAIM, user.roles().stream().map(Enum::name).sorted().toList())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String accessToken = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();

        byte[] bytes = new byte[REFRESH_TOKEN_BYTES];
        random.nextBytes(bytes);
        String refreshToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        refreshTokens.save(new RefreshToken(user.id(), hash(refreshToken), now.plus(properties.refreshTokenTtl())));
        return new TokenPair(accessToken, refreshToken);
    }

    /** SHA-256 hex. The refresh token is 256 random bits, so a plain hash cannot be reversed. */
    public static String hash(String refreshToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(refreshToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    public record TokenPair(String accessToken, String refreshToken) {
    }
}
