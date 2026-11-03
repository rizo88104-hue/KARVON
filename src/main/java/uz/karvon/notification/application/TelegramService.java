package uz.karvon.notification.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import uz.karvon.auth.application.TelegramLoginService;
import uz.karvon.common.application.BusinessException;
import uz.karvon.common.domain.Money;
import uz.karvon.notification.api.dto.TelegramLinkResponse;
import uz.karvon.notification.api.dto.TelegramUpdate;
import uz.karvon.notification.infrastructure.TelegramProperties;
import uz.karvon.order.api.dto.OrderSummaryResponse;
import uz.karvon.order.application.OrderService;
import uz.karvon.order.domain.OrderTab;
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
import java.util.Optional;
import java.util.UUID;

/**
 * The bot. Every update arrives here, from the webhook (prod) or from long polling (dev):
 * <ul>
 *   <li>/start {token}: a login token ({@link TelegramLoginService}) or a cabinet link token (below);</li>
 *   <li>a shared contact: the login step that links the chat and sends the code;</li>
 *   <li>/buyurtmalarim: the last 5 orders of the linked user with their statuses.</li>
 * </ul>
 * Cabinet linking: a signed-in user asks for a one-time deep link https://t.me/{bot}?start={token}; the token is kept
 * in Redis ({@code telegram:link:{token}} -> user id, lives {@code linkTtl}) and works once.
 */
@Service
public class TelegramService {

    static final String ORDERS_COMMAND = "/buyurtmalarim";
    static final int ORDERS_SHOWN = 5;

    private static final Logger log = LoggerFactory.getLogger(TelegramService.class);
    private static final String START = "/start";

    private final StringRedisTemplate redis;
    private final UserService users;
    private final TelegramBotClient telegram;
    private final TemplateRenderer renderer;
    private final TelegramProperties properties;
    private final TelegramLoginService login;
    private final OrderService orders;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public TelegramService(StringRedisTemplate redis, UserService users, TelegramBotClient telegram,
                           TemplateRenderer renderer, TelegramProperties properties, TelegramLoginService login,
                           OrderService orders, Clock clock) {
        this.redis = redis;
        this.users = users;
        this.telegram = telegram;
        this.renderer = renderer;
        this.properties = properties;
        this.login = login;
        this.orders = orders;
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

    /** One update of the bot; anything it does not know is ignored. */
    public void onUpdate(TelegramUpdate update) {
        TelegramUpdate.Message message = update == null ? null : update.message();
        if (message == null || message.chat() == null || message.chat().id() == null) {
            return;
        }
        long chatId = message.chat().id();
        TelegramUpdate.From from = message.from();
        if (message.contact() != null) {
            login.onContact(chatId, from == null ? null : from.id(), message.contact().phoneNumber(),
                    message.contact().userId());
            return;
        }
        onMessage(chatId, message.text(), from == null ? null : from.languageCode());
    }

    /**
     * A text message to the bot. {@code languageCode} is the Telegram app language, used until the chat is linked.
     */
    public void onMessage(long chatId, String text, String languageCode) {
        if (text == null) {
            return;
        }
        Language guess = languageCode != null && languageCode.toLowerCase().startsWith("ru") ? Language.RU : Language.UZ;
        String command = text.trim();
        if (command.equals(ORDERS_COMMAND) || command.startsWith(ORDERS_COMMAND + "@")) {
            replyWithOrders(chatId, guess);
            return;
        }
        if (!(command.equals(START) || command.startsWith(START + " "))) {
            return;
        }
        String token = command.substring(START.length()).trim();
        if (token.isEmpty()) {
            reply(chatId, "telegram.start", guess);
            return;
        }
        if (login.onStart(chatId, token)) {
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

    private void replyWithOrders(long chatId, Language guess) {
        Optional<UserContact> user = users.contactByTelegramChat(chatId);
        if (user.isEmpty()) {
            reply(chatId, "telegram.orders.not_linked", guess);
            return;
        }
        Language language = user.get().language();
        List<OrderSummaryResponse> last = orders.list(user.get().id(), OrderTab.ALL, 0, ORDERS_SHOWN).content();
        if (last.isEmpty()) {
            reply(chatId, "telegram.orders.empty", language);
            return;
        }
        StringBuilder text = new StringBuilder(renderer.render("telegram.orders.title", List.of(), language));
        for (OrderSummaryResponse order : last) {
            text.append('\n').append(renderer.render("telegram.orders.line", List.of(order.number(),
                    renderer.render("order.status_label." + order.status().name(), List.of(), language),
                    Money.formatUzsNumber(order.totalUzs())), language));
        }
        send(chatId, text.toString());
    }

    private void reply(long chatId, String templateKey, Language language) {
        send(chatId, renderer.render(templateKey, List.of(), language));
    }

    private void send(long chatId, String text) {
        try {
            telegram.sendMessage(chatId, text);
        } catch (RuntimeException ex) {
            log.warn("Could not answer Telegram chat {}: {}", chatId, ex.getMessage());
        }
    }

    private static String key(String token) {
        return "telegram:link:" + token;
    }
}
