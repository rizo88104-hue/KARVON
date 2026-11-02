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
 * Dev and test: nothing is sent to Telegram, the text is logged and kept in memory per chat.
 */
@Component
@Profile({"dev", "test"})
public class FakeTelegramBotClient implements TelegramBotClient {

    private static final Logger log = LoggerFactory.getLogger(FakeTelegramBotClient.class);

    private final Map<Long, List<String>> messages = new ConcurrentHashMap<>();

    @Override
    public void sendMessage(long chatId, String text) {
        log.info("[FAKE TELEGRAM] to chat {}: {}", chatId, text);
        messages.computeIfAbsent(chatId, id -> new CopyOnWriteArrayList<>()).add(text);
    }

    /** Everything "sent" to this chat, oldest first. */
    public List<String> messages(long chatId) {
        return List.copyOf(messages.getOrDefault(chatId, List.of()));
    }
}
