package uz.karvon.notification.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import uz.karvon.common.application.BusinessException;
import uz.karvon.notification.api.dto.TelegramLinkResponse;
import uz.karvon.notification.infrastructure.TelegramProperties;
import uz.karvon.user.application.UserContact;
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

/**
 * Linking a Telegram chat to a user. The cabinet asks for a one-time deep link https://t.me/{bot}?start={token};
 * the token is kept in Redis ({@code telegram:link:{token}} -> user id, lives {@code linkTtl}). When the user presses
 * Start, Telegram calls the webhook with "/start {token}", and the chat is linked. A token works once.
 */
@Service
public class TelegramService {

    private static final Logger log = LoggerFactory.getLogger(TelegramService.class);
    private static final String START = "/start";

    private final StringRedisTemplate redis;
    private final UserService users;
    private final TelegramBotClient telegram;
    private final TemplateRenderer renderer;
    private final TelegramProperties properties;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public TelegramService(StringRedisTemplate redis, UserService users, TelegramBotClient telegram,
                           TemplateRenderer renderer, TelegramProperties properties, Clock clock) {
        this.redis = redis;
        this.users = users;
        this.telegram = telegram;
        this.renderer = renderer;
        this.properties = properties;
        this.clock = clock;
    }

    public TelegramLinkResponse createLink(UUID userId) {
        if (properties.botUsername() == null || properties.botUsername().isBlank()) {
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "TELEGRAM_NOT_CONFIGURED",
                    "error.telegram.notConfigured");
        }
        byte[] bytes = new byte[24];
        random.nextBytes(bytes);
        // URL-safe Base64 without padding: Telegram allows A-Z, a-z, 0-9, _ and - in the start parameter.
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        redis.opsForValue().set(key(token), userId.toString(), properties.linkTtl());
        return new TelegramLinkResponse("https://t.me/" + properties.botUsername() + "?start=" + token,
                Instant.now(clock).plus(properties.linkTtl()));
    }

    /** The webhook must carry the secret set with setWebhook (header X-Telegram-Bot-Api-Secret-Token). */
    public void requireWebhookSecret(String header) {
        String secret = properties.webhookSecret();
        boolean valid = secret != null && !secret.isBlank() && header != null
                && MessageDigest.isEqual(secret.getBytes(StandardCharsets.UTF_8), header.getBytes(StandardCharsets.UTF_8));
        if (!valid) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "TELEGRAM_WEBHOOK_FORBIDDEN", "error.telegram.webhookForbidden");
        }
    }

    /**
     * A text message to the bot. Only "/start {token}" does something; the answer goes back to the chat.
     * {@code languageCode} is the Telegram app language, used until the chat is linked.
     */
    public void onMessage(long chatId, String text, String languageCode) {
        if (text == null || !(text.equals(START) || text.startsWith(START + " "))) {
            return;
        }
        String token = text.substring(START.length()).trim();
        Language guess = languageCode != null && languageCode.toLowerCase().startsWith("ru") ? Language.RU : Language.UZ;
        if (token.isEmpty()) {
            reply(chatId, "telegram.start", guess);
            return;
        }
        String userId = redis.opsForValue().getAndDelete(key(token));
        if (userId == null) {
            reply(chatId, "telegram.link_invalid", guess);
            return;
        }
        UserContact contact = users.linkTelegram(UUID.fromString(userId), chatId);
        log.info("Telegram chat linked to user {}", contact.id());
        reply(chatId, "telegram.linked", contact.language());
    }

    private void reply(long chatId, String templateKey, Language language) {
        try {
            telegram.sendMessage(chatId, renderer.render(templateKey, List.of(), language));
        } catch (RuntimeException ex) {
            log.warn("Could not answer Telegram chat {}: {}", chatId, ex.getMessage());
        }
    }

    private static String key(String token) {
        return "telegram:link:" + token;
    }
}
