package uz.karvon.notification.infrastructure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import uz.karvon.notification.application.SmsSender;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dev and test: nothing is sent, the text is logged and the last message per phone is kept in memory.
 */
@Component
@Profile({"dev", "test"})
public class FakeSmsSender implements SmsSender {

    private static final Logger log = LoggerFactory.getLogger(FakeSmsSender.class);

    private final Map<String, String> lastMessages = new ConcurrentHashMap<>();

    @Override
    public void send(String phone, String text) {
        log.info("[FAKE SMS] to {}: {}", phone, text);
        lastMessages.put(phone, text);
    }

    public Optional<String> lastMessage(String phone) {
        return Optional.ofNullable(lastMessages.get(phone));
    }
}
