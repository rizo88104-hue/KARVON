package uz.karvon.auth.application;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import uz.karvon.auth.domain.RefreshToken;
import uz.karvon.auth.infrastructure.AuthProperties;
import uz.karvon.auth.infrastructure.JwtConfig;
import uz.karvon.auth.infrastructure.RefreshTokenRepository;
import uz.karvon.user.api.dto.UserResponse;
import uz.karvon.user.domain.Language;
import uz.karvon.user.domain.Role;
import uz.karvon.user.domain.UserStatus;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class TokenServiceTest {

    private static final AuthProperties PROPERTIES = properties("unit-test-jwt-secret-0123456789abcdefghij");

    private final Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
    private final JwtConfig config = new JwtConfig();
    private final JwtDecoder decoder = config.jwtDecoder(PROPERTIES);
    private final RefreshTokenRepository refreshTokens = mock(RefreshTokenRepository.class);
    private final TokenService service = new TokenService(config.jwtEncoder(PROPERTIES), refreshTokens, PROPERTIES,
            Clock.fixed(now, ZoneOffset.UTC));

    private final UserResponse user = new UserResponse(UUID.randomUUID(), "+998901234567", null, Language.UZ,
            UserStatus.ACTIVE, Set.of(Role.CUSTOMER, Role.BUYER), now);

    @Test
    void accessTokenHasUserIdRolesAndLivesFifteenMinutes() {
        Jwt jwt = decoder.decode(service.issue(user).accessToken());

        assertThat(jwt.getSubject()).isEqualTo(user.id().toString());
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("BUYER", "CUSTOMER");
        assertThat(jwt.getIssuedAt()).isEqualTo(now);
        assertThat(jwt.getExpiresAt()).isEqualTo(now.plus(Duration.ofMinutes(15)));
        assertThat(jwt.getHeaders()).containsEntry("alg", "HS256");
    }

    @Test
    void refreshTokenIsStoredOnlyAsHashForThirtyDays() {
        String refreshToken = service.issue(user).refreshToken();

        ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokens).save(saved.capture());
        assertThat(saved.getValue().getTokenHash())
                .isEqualTo(TokenService.hash(refreshToken))
                .isNotEqualTo(refreshToken)
                .hasSize(64);
        assertThat(saved.getValue().getUserId()).isEqualTo(user.id());
        assertThat(saved.getValue().getExpiresAt()).isEqualTo(now.plus(Duration.ofDays(30)));
    }

    @Test
    void everyRefreshTokenIsDifferent() {
        assertThat(service.issue(user).refreshToken()).isNotEqualTo(service.issue(user).refreshToken());
    }

    @Test
    void tokenSignedWithAnotherSecretIsRejected() {
        AuthProperties other = properties("another-jwt-secret-0123456789abcdefghijkl");
        String foreign = new TokenService(config.jwtEncoder(other), refreshTokens, other, Clock.systemUTC())
                .issue(user).accessToken();

        assertThatThrownBy(() -> decoder.decode(foreign)).isInstanceOf(JwtException.class);
    }

    @Test
    void shortSecretIsRefusedAtStartup() {
        assertThatThrownBy(() -> config.jwtEncoder(properties("too-short")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET");
    }

    private static AuthProperties properties(String secret) {
        return new AuthProperties(secret, Duration.ofMinutes(15), Duration.ofDays(30),
                new AuthProperties.Otp(Duration.ofMinutes(2), 3, Duration.ofMinutes(10), 5, Duration.ofMinutes(15)));
    }
}
