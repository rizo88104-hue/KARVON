package uz.karvon.notification.infrastructure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import uz.karvon.notification.application.TelegramSendException;
import uz.karvon.notification.application.TelegramSender;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Dev and test: nothing is sent, the text is logged and kept in memory per chat (the last 50).
 * {@link #failNext(int)} makes the next sends fail, to see retries.
 */
@Component
@Profile({"dev", "test"})
public class FakeTelegramSender implements TelegramSender {

    private static final Logger log = LoggerFactory.getLogger(FakeTelegramSender.class);
    private static final int KEEP = 50;

    private final Map<Long, List<String>> messages = new ConcurrentHashMap<>();
    private final AtomicInteger failures = new AtomicInteger();

    @Override
    public void send(long chatId, String text) {
        if (failures.getAndUpdate(left -> Math.max(0, left - 1)) > 0) {
            log.info("[FAKE TELEGRAM] to chat {} FAILED on purpose: {}", chatId, text);
            throw new TelegramSendException("Fake failure");
        }
        log.info("[FAKE TELEGRAM] to chat {}: {}", chatId, text);
        List<String> chat = messages.computeIfAbsent(chatId, id -> new ArrayList<>());
        synchronized (chat) {
            chat.add(text);
            if (chat.size() > KEEP) {
                chat.removeFirst();
            }
        }
    }

    public List<String> messages(long chatId) {
        List<String> chat = messages.getOrDefault(chatId, List.of());
        synchronized (chat) {
            return List.copyOf(chat);
        }
    }

    public void failNext(int count) {
        failures.set(count);
    }
}
