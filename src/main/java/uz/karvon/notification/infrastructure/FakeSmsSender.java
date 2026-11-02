package uz.karvon.notification.infrastructure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import uz.karvon.notification.application.SmsSender;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * Dev and test: nothing is sent, the text is logged and the messages per phone (the last 50) are kept in memory.
 */
@Component
@Profile({"dev", "test"})
public class FakeSmsSender implements SmsSender {

    private static final Logger log = LoggerFactory.getLogger(FakeSmsSender.class);
    private static final int KEEP = 50;

    private final Map<String, List<String>> messages = new ConcurrentHashMap<>();

    @Override
    public void send(String phone, String text) {
        log.info("[FAKE SMS] to {}: {}", phone, text);
        List<String> list = messages.computeIfAbsent(phone, key -> new ArrayList<>());
        synchronized (list) {
            list.add(text);
            if (list.size() > KEEP) {
                list.removeFirst();
            }
        }
    }

    public Optional<String> lastMessage(String phone) {
        return lastMessage(phone, text -> true);
    }

    /** The newest message to this phone that matches, e.g. the last login code among order notifications. */
    public Optional<String> lastMessage(String phone, Predicate<String> filter) {
        return messages(phone).reversed().stream().filter(filter).findFirst();
    }

    /** Oldest first. */
    public List<String> messages(String phone) {
        List<String> list = messages.getOrDefault(phone, List.of());
        synchronized (list) {
            return List.copyOf(list);
        }
    }
}
