package uz.karvon.auth.application;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import uz.karvon.auth.infrastructure.AuthProperties;
import uz.karvon.common.application.BusinessException;
import uz.karvon.notification.application.SmsSender;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * One-time login codes, kept in Redis:
 * <ul>
 *   <li>{@code otp:code:{phone}} - HMAC of the code, lives {@code codeTtl}</li>
 *   <li>{@code otp:requests:{phone}} - codes requested in the current {@code requestWindow}</li>
 *   <li>{@code otp:attempts:{phone}} - wrong codes entered</li>
 *   <li>{@code otp:blocked:{phone}} - set for {@code blockDuration} after {@code maxWrongAttempts} wrong codes</li>
 * </ul>
 * The code is hashed with HMAC-SHA256 keyed by JWT_SECRET: a plain hash of a 5-digit code could be reversed
 * by trying all 100 000 values.
 */
@Service
public class OtpService {

    static final String SMS_TEXT = "Karvon: tasdiqlash kodi %s. Kodni hech kimga bermang.";

    private static final int CODE_BOUND = 100_000;

    private final StringRedisTemplate redis;
    private final SmsSender sms;
    private final AuthProperties.Otp settings;
    private final SecretKeySpec hmacKey;
    private final SecureRandom random = new SecureRandom();

    public OtpService(StringRedisTemplate redis, SmsSender sms, AuthProperties properties) {
        this.redis = redis;
        this.sms = sms;
        this.settings = properties.otp();
        this.hmacKey = new SecretKeySpec(properties.jwtSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    /** Generates and sends a code. Returns how many seconds it is valid. */
    public long request(String phone) {
        ensureNotBlocked(phone);
        long requests = incrementWithTtl(requestsKey(phone), settings.requestWindow());
        if (requests > settings.maxRequests()) {
            throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS, "OTP_TOO_MANY_REQUESTS",
                    "error.otp.tooManyRequests", minutesLeft(requestsKey(phone)));
        }
        String code = "%05d".formatted(random.nextInt(CODE_BOUND));
        redis.opsForValue().set(codeKey(phone), hash(phone, code), settings.codeTtl());
        sms.send(phone, SMS_TEXT.formatted(code));
        return settings.codeTtl().toSeconds();
    }

    /** Checks the code and consumes it. Throws if the code is wrong, expired or the phone is blocked. */
    public void verify(String phone, String code) {
        ensureNotBlocked(phone);
        String expected = redis.opsForValue().get(codeKey(phone));
        if (expected == null) {
            throw new BusinessException("OTP_EXPIRED", "error.otp.expired");
        }
        if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                hash(phone, code).getBytes(StandardCharsets.UTF_8))) {
            long wrong = incrementWithTtl(attemptsKey(phone), settings.blockDuration());
            if (wrong >= settings.maxWrongAttempts()) {
                redis.opsForValue().set(blockedKey(phone), "1", settings.blockDuration());
                redis.delete(List.of(codeKey(phone), attemptsKey(phone)));
                throw blocked(phone);
            }
            throw new BusinessException("OTP_INVALID", "error.otp.invalid", settings.maxWrongAttempts() - wrong);
        }
        redis.delete(List.of(codeKey(phone), attemptsKey(phone)));
    }

    private void ensureNotBlocked(String phone) {
        if (Boolean.TRUE.equals(redis.hasKey(blockedKey(phone)))) {
            throw blocked(phone);
        }
    }

    private BusinessException blocked(String phone) {
        return new BusinessException(HttpStatus.TOO_MANY_REQUESTS, "OTP_BLOCKED", "error.otp.blocked",
                minutesLeft(blockedKey(phone)));
    }

    /** INCR, and start the TTL when the counter is created. */
    private long incrementWithTtl(String key, Duration ttl) {
        Long value = redis.opsForValue().increment(key);
        if (value != null && value == 1) {
            redis.expire(key, ttl);
        }
        return value == null ? 0 : value;
    }

    private long minutesLeft(String key) {
        Long seconds = redis.getExpire(key, TimeUnit.SECONDS);
        return seconds == null || seconds <= 0 ? 1 : (seconds + 59) / 60;
    }

    private String hash(String phone, String code) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(hmacKey);
            return HexFormat.of().formatHex(mac.doFinal((phone + ":" + code).getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("HmacSHA256 is not available", ex);
        }
    }

    private static String codeKey(String phone) {
        return "otp:code:" + phone;
    }

    private static String requestsKey(String phone) {
        return "otp:requests:" + phone;
    }

    private static String attemptsKey(String phone) {
        return "otp:attempts:" + phone;
    }

    private static String blockedKey(String phone) {
        return "otp:blocked:" + phone;
    }
}
