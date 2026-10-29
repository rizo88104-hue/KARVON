package uz.karvon.auth.application;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.karvon.auth.api.dto.AuthResponse;
import uz.karvon.auth.domain.RefreshToken;
import uz.karvon.auth.infrastructure.RefreshTokenRepository;
import uz.karvon.common.application.BusinessException;
import uz.karvon.common.domain.PhoneNumbers;
import uz.karvon.user.api.dto.UserResponse;
import uz.karvon.user.application.UserBlockedEvent;
import uz.karvon.user.application.UserService;
import uz.karvon.user.domain.UserStatus;

import java.time.Clock;
import java.time.Instant;

/**
 * Phone login and sessions. Refresh tokens are rotated: every refresh revokes the used token.
 * A revoked token used again means it was stolen or copied, so all sessions of that user are ended.
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final OtpService otpService;
    private final TokenService tokenService;
    private final UserService userService;
    private final RefreshTokenRepository refreshTokens;
    private final Clock clock;

    public long requestOtp(String rawPhone) {
        String phone = normalize(rawPhone);
        userService.findByPhone(phone).ifPresent(AuthService::ensureActive);
        return otpService.request(phone);
    }

    @Transactional
    public AuthResponse verifyOtp(String rawPhone, String code) {
        String phone = normalize(rawPhone);
        otpService.verify(phone, code);
        UserResponse user = userService.findOrCreateCustomer(phone);
        ensureActive(user);
        return respond(user);
    }

    /** {@code noRollbackFor}: revoking sessions after token reuse must be saved even though the call fails. */
    @Transactional(noRollbackFor = BusinessException.class)
    public AuthResponse refresh(String refreshToken) {
        Instant now = Instant.now(clock);
        RefreshToken stored = refreshTokens.findByTokenHash(TokenService.hash(refreshToken))
                .orElseThrow(AuthService::invalidRefreshToken);
        if (stored.isRevoked()) {
            log.warn("Revoked refresh token reused for user {}; ending all sessions", stored.getUserId());
            refreshTokens.revokeAllForUser(stored.getUserId(), now);
            throw invalidRefreshToken();
        }
        if (stored.isExpired(now)) {
            throw invalidRefreshToken();
        }
        stored.revoke(now);
        UserResponse user = userService.findById(stored.getUserId()).orElseThrow(AuthService::invalidRefreshToken);
        ensureActive(user);
        return respond(user);
    }

    /** Ends the session of this refresh token. Unknown or already revoked tokens are ignored. */
    @Transactional
    public void logout(String refreshToken) {
        refreshTokens.findByTokenHash(TokenService.hash(refreshToken))
                .ifPresent(token -> token.revoke(Instant.now(clock)));
    }

    @EventListener
    @Transactional
    public void onUserBlocked(UserBlockedEvent event) {
        refreshTokens.revokeAllForUser(event.userId(), Instant.now(clock));
    }

    private AuthResponse respond(UserResponse user) {
        TokenService.TokenPair tokens = tokenService.issue(user);
        return new AuthResponse(tokens.accessToken(), tokens.refreshToken(), user);
    }

    private static void ensureActive(UserResponse user) {
        if (user.status() == UserStatus.BLOCKED) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "USER_BLOCKED", "error.user.blocked");
        }
    }

    private static String normalize(String rawPhone) {
        return PhoneNumbers.normalize(rawPhone)
                .orElseThrow(() -> new BusinessException("PHONE_INVALID", "error.phone.invalid"));
    }

    private static BusinessException invalidRefreshToken() {
        return new BusinessException(HttpStatus.UNAUTHORIZED, "REFRESH_TOKEN_INVALID", "error.auth.refreshTokenInvalid");
    }
}
