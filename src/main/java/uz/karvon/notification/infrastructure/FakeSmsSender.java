package uz.karvon.notification.infrastructure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import uz.karvon.notification.application.SmsSendException;
import uz.karvon.notification.application.SmsSender;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Dev and test while SMS is turned on (karvon.sms.enabled=true): nothing is sent, the text is logged and every
 * message per phone is kept in memory.
 * Tests can make the next sends to a phone fail with {@link #failNext}.
 */
@Component
@Profile({"dev", "test"})
@ConditionalOnProperty(name = SmsProperties.ENABLED, havingValue = "true")
public class FakeSmsSender implements SmsSender {

    private static final Logger log = LoggerFactory.getLogger(FakeSmsSender.class);

    private final Map<String, List<String>> messages = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> failures = new ConcurrentHashMap<>();

    @Override
    public void send(String phone, String text) {
        AtomicInteger failing = failures.get(phone);
        if (failing != null && failing.getAndDecrement() > 0) {
            log.info("[FAKE SMS] to {} failed on purpose: {}", phone, text);
            throw new SmsSendException(new IllegalStateException("Fake SMS failure"));
        }
        log.info("[FAKE SMS] to {}: {}", phone, text);
        messages.computeIfAbsent(phone, key -> new CopyOnWriteArrayList<>()).add(text);
    }

    public Optional<String> lastMessage(String phone) {
        List<String> sent = messages(phone);
        return sent.isEmpty() ? Optional.empty() : Optional.of(sent.getLast());
    }

    /** Everything "sent" to this phone, oldest first. */
    public List<String> messages(String phone) {
        return List.copyOf(messages.getOrDefault(phone, List.of()));
    }

    /** The next {@code times} sends to this phone throw {@link SmsSendException}. */
    public void failNext(String phone, int times) {
        failures.put(phone, new AtomicInteger(times));
    }
}
