package uz.karvon.notification.infrastructure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import uz.karvon.notification.application.TelegramBotClient;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Test, and dev without TELEGRAM_BOT_TOKEN (registered by {@link DevTelegramConfig}): nothing is sent to Telegram,
 * the text is logged and kept in memory per chat together with the keyboard it carried.
 */
@Component
@Profile("test")
public class FakeTelegramBotClient implements TelegramBotClient {

    private static final Logger log = LoggerFactory.getLogger(FakeTelegramBotClient.class);

    private final Map<Long, List<Sent>> messages = new ConcurrentHashMap<>();

    /** One "sent" message. */
    public record Sent(String text, Keyboard keyboard) {
    }

    @Override
    public void sendMessage(long chatId, String text, Keyboard keyboard) {
        log.info("[FAKE TELEGRAM] to chat {}: {}", chatId, text);
        messages.computeIfAbsent(chatId, id -> new CopyOnWriteArrayList<>()).add(new Sent(text, keyboard));
    }

    /** The texts "sent" to this chat, oldest first. */
    public List<String> messages(long chatId) {
        return sent(chatId).stream().map(Sent::text).toList();
    }

    /** Everything "sent" to this chat with its keyboard, oldest first. */
    public List<Sent> sent(long chatId) {
        return List.copyOf(messages.getOrDefault(chatId, List.of()));
    }
}
