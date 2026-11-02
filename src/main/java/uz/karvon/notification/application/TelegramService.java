package uz.karvon.notification.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import uz.karvon.common.application.BusinessException;
import uz.karvon.notification.api.dto.TelegramLinkResponse;
import uz.karvon.notification.api.dto.TelegramUpdate;
import uz.karvon.notification.infrastructure.TelegramProperties;
import uz.karvon.user.application.NotificationContact;
import uz.karvon.user.application.UserService;
import uz.karvon.user.domain.Language;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Linking a Telegram chat to a customer:
 * <ol>
 *   <li>GET /api/v1/me/telegram-link stores a random one-time token in Redis ({@code telegram:link:{token}} -> user id)
 *       and returns https://t.me/{bot}?start={token};</li>
 *   <li>the customer presses Start, Telegram calls our webhook with "/start {token}";</li>
 *   <li>the token is consumed (GETDEL) and the chat is linked to that user.</li>
 * </ol>
 */
@Service
public class TelegramService {

    static final String LINK_KEY = "telegram:link:";
    private static final Pattern TOKEN = Pattern.compile("[A-Za-z0-9_-]{16,64}");
    private static final int TOKEN_BYTES = 24;

    private static final Logger log = LoggerFactory.getLogger(TelegramService.class);

    private final StringRedisTemplate redis;
    private final UserService users;
    private final TelegramSender telegram;
    private final NotificationTemplates templates;
    private final TelegramProperties properties;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public TelegramService(StringRedisTemplate redis, UserService users, TelegramSender telegram,
                           NotificationTemplates templates, TelegramProperties properties, Clock clock) {
        this.redis = redis;
        this.users = users;
        this.telegram = telegram;
        this.templates = templates;
        this.properties = properties;
        this.clock = clock;
    }

    public TelegramLinkResponse createLink(UUID userId) {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        redis.opsForValue().set(LINK_KEY + token, userId.toString(), properties.linkTtl());
        return new TelegramLinkResponse("https://t.me/%s?start=%s".formatted(properties.botUsername(), token),
                Instant.now(clock).plus(properties.linkTtl()));
    }

    /** The X-Telegram-Bot-Api-Secret-Token header must equal TELEGRAM_WEBHOOK_SECRET. */
    public void verifyWebhookSecret(String secret) {
        byte[] expected = properties.webhookSecret().getBytes(StandardCharsets.UTF_8);
        if (secret == null || !MessageDigest.isEqual(expected, secret.getBytes(StandardCharsets.UTF_8))) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "TELEGRAM_WEBHOOK_UNAUTHORIZED",
                    "error.telegram.webhookUnauthorized");
        }
    }

    /** Handles "/start {token}"; every other update is ignored. */
    public void handle(TelegramUpdate update) {
        if (update == null || update.message() == null || update.message().chat() == null
                || update.message().chat().id() == null || update.message().text() == null) {
            return;
        }
        long chatId = update.message().chat().id();
        String text = update.message().text().trim();
        if (!text.equals("/start") && !text.startsWith("/start ")) {
            return;
        }
        String token = text.substring("/start".length()).trim();
        if (token.isEmpty()) {
            reply(chatId, "telegram.start_hint", Language.UZ);
            return;
        }
        String userId = TOKEN.matcher(token).matches() ? redis.opsForValue().getAndDelete(LINK_KEY + token) : null;
        if (userId == null) {
            reply(chatId, "telegram.link_invalid", Language.UZ);
            return;
        }
        NotificationContact contact;
        try {
            contact = users.linkTelegram(UUID.fromString(userId), chatId);
        } catch (BusinessException userGone) {
            reply(chatId, "telegram.link_invalid", Language.UZ);
            return;
        }
        log.info("Telegram chat linked to user {}", contact.userId());
        reply(chatId, "telegram.linked", contact.language());
    }

    private void reply(long chatId, String templateKey, Language language) {
        try {
            telegram.send(chatId, templates.render(templateKey, List.of(), language));
        } catch (RuntimeException ex) {
            log.warn("Could not answer Telegram chat {}: {}", chatId, ex.getMessage());
        }
    }
}
