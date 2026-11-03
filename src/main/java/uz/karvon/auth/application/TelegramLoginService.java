package uz.karvon.auth.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import uz.karvon.common.application.BusinessException;
import uz.karvon.common.domain.PhoneNumbers;
import uz.karvon.notification.application.TelegramBotClient;
import uz.karvon.notification.application.TelegramBotClient.Keyboard;
import uz.karvon.notification.application.TemplateRenderer;
import uz.karvon.notification.infrastructure.TelegramProperties;
import uz.karvon.user.api.dto.UserResponse;
import uz.karvon.user.application.UserService;
import uz.karvon.user.domain.Language;
import uz.karvon.user.domain.UserStatus;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Login codes over Telegram. A bot cannot write to a phone number first, so a phone without a linked chat gets a
 * one-time deep link https://t.me/{bot}?start={token}:
 * <ol>
 *   <li>POST /auth/otp/request keeps {@code telegram:login:{token}} -> phone ({@code loginTtl}, 10 min).</li>
 *   <li>/start {token} in the bot: the chat is remembered ({@code telegram:login-chat:{chatId}} -> token) and the
 *       bot shows the button "Telefon raqamni yuborish" (request_contact).</li>
 *   <li>The contact: only the sender's own contact (contact.user_id == from.id) with the phone of the token is
 *       accepted. Then the chat is linked to that phone (a new customer is created) and the code is sent.</li>
 * </ol>
 * The code is checked by POST /auth/otp/verify as before, with the same limits. The bot speaks Uzbek here.
 */
@Service
public class TelegramLoginService {

    private static final Logger log = LoggerFactory.getLogger(TelegramLoginService.class);

    private final StringRedisTemplate redis;
    private final TelegramBotClient telegram;
    private final TemplateRenderer renderer;
    private final TelegramProperties properties;
    private final UserService users;
    private final OtpService otp;
    private final SecureRandom random = new SecureRandom();

    public TelegramLoginService(StringRedisTemplate redis, TelegramBotClient telegram, TemplateRenderer renderer,
                                TelegramProperties properties, UserService users, OtpService otp) {
        this.redis = redis;
        this.telegram = telegram;
        this.renderer = renderer;
        this.properties = properties;
        this.users = users;
        this.otp = otp;
    }

    /** A one-time deep link for logging in with this (normalized) phone. */
    public String createLink(String phone) {
        String bot = properties.botUsername();
        if (bot == null || bot.isBlank()) {
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "TELEGRAM_NOT_CONFIGURED",
                    "error.telegram.notConfigured");
        }
        byte[] bytes = new byte[24];
        random.nextBytes(bytes);
        // URL-safe Base64 without padding: Telegram allows A-Z, a-z, 0-9, _ and - in the start parameter.
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        redis.opsForValue().set(tokenKey(token), phone, properties.loginTtl());
        return "https://t.me/" + bot + "?start=" + token;
    }

    public long linkTtlSeconds() {
        return properties.loginTtl().toSeconds();
    }

    /** Sends a code to a linked chat; throws {@link uz.karvon.notification.application.TelegramSendException}. */
    public void sendCode(long chatId, String code) {
        telegram.sendMessage(chatId, OtpService.codeText(code), Keyboard.REMOVE);
    }

    /** "/start {token}". Returns false when the token is not a login token (it may be a cabinet link token). */
    public boolean onStart(long chatId, String token) {
        if (redis.opsForValue().get(tokenKey(token)) == null) {
            return false;
        }
        redis.opsForValue().set(chatKey(chatId), token, properties.loginTtl());
        reply(chatId, "telegram.login.share_phone",
                new Keyboard.ShareContact(text("telegram.login.share_button")));
        return true;
    }

    /** A shared contact, answered in the chat. {@code fromId} is the sender's Telegram user id. */
    public void onContact(long chatId, Long fromId, String contactPhone, Long contactUserId) {
        String token = redis.opsForValue().get(chatKey(chatId));
        if (token == null) {
            reply(chatId, "telegram.login.no_request", Keyboard.REMOVE);
            return;
        }
        String phone = redis.opsForValue().get(tokenKey(token));
        if (phone == null) {
            redis.delete(chatKey(chatId));
            reply(chatId, "telegram.login.expired", Keyboard.REMOVE);
            return;
        }
        if (fromId == null || !Objects.equals(contactUserId, fromId)) {
            reply(chatId, "telegram.login.not_own_contact",
                    new Keyboard.ShareContact(text("telegram.login.share_button")));
            return;
        }
        Optional<String> shared = PhoneNumbers.normalize(contactPhone == null ? null
                : contactPhone.startsWith("+") ? contactPhone : "+" + contactPhone);
        if (shared.isEmpty() || !shared.get().equals(phone)) {
            log.info("Telegram login: shared phone does not match {}", PhoneNumbers.mask(phone));
            reply(chatId, "telegram.login.wrong_phone", Keyboard.REMOVE);
            return;
        }
        redis.delete(List.of(tokenKey(token), chatKey(chatId)));
        linkAndSendCode(chatId, phone);
    }

    /**
     * Links the chat to the phone's user (a new CUSTOMER if there is none) and sends a code there. Also used by the
     * dev stand-in for the bot (DevOtpController).
     */
    public void linkAndSendCode(long chatId, String phone) {
        UserResponse user = users.findOrCreateCustomer(phone);
        if (user.status() == UserStatus.BLOCKED) {
            reply(chatId, "telegram.login.blocked", Keyboard.REMOVE);
            return;
        }
        users.linkTelegram(user.id(), chatId);
        log.info("Telegram login: chat linked for {}", PhoneNumbers.mask(phone));
        String code;
        try {
            code = otp.newCode(phone);
        } catch (BusinessException ex) {
            // The phone is blocked after wrong codes.
            reply(chatId, "telegram.login.try_later", Keyboard.REMOVE);
            return;
        }
        try {
            sendCode(chatId, code);
        } catch (RuntimeException ex) {
            log.warn("Telegram login: the code was not sent to the chat of {}: {}", PhoneNumbers.mask(phone),
                    ex.getMessage());
        }
    }

    private void reply(long chatId, String templateKey, Keyboard keyboard) {
        try {
            telegram.sendMessage(chatId, text(templateKey), keyboard);
        } catch (RuntimeException ex) {
            log.warn("Could not answer Telegram chat {}: {}", chatId, ex.getMessage());
        }
    }

    private String text(String templateKey) {
        return renderer.render(templateKey, List.of(), Language.UZ);
    }

    private static String tokenKey(String token) {
        return "telegram:login:" + token;
    }

    private static String chatKey(long chatId) {
        return "telegram:login-chat:" + chatId;
    }
}
