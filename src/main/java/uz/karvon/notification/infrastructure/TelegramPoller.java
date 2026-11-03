package uz.karvon.notification.infrastructure;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import uz.karvon.notification.api.dto.TelegramUpdate;
import uz.karvon.notification.application.TelegramService;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Dev with a real bot (TELEGRAM_BOT_TOKEN set): a webhook cannot reach localhost, so updates are fetched with
 * long polling (getUpdates) on one background thread and handled like webhook calls. Prod uses the webhook.
 * Telegram refuses getUpdates while a webhook is set for the bot (HTTP 409): use a separate dev bot. The token is
 * part of the URL and is never logged.
 */
public class TelegramPoller implements SmartLifecycle {

    static final int POLL_SECONDS = 25;

    private static final Logger log = LoggerFactory.getLogger(TelegramPoller.class);

    private final RestClient http;
    private final String token;
    private final TelegramService bot;
    private volatile boolean running;
    private Thread thread;
    private long offset;

    public TelegramPoller(TelegramProperties properties, TelegramService bot) {
        this.token = properties.botToken();
        this.bot = bot;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.timeout());
        // Longer than the long poll itself.
        requestFactory.setReadTimeout(Duration.ofSeconds(POLL_SECONDS + 10));
        this.http = RestClient.builder().requestFactory(requestFactory)
                .baseUrl(properties.apiUrl() + "/bot" + token).build();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Updates(boolean ok, List<TelegramUpdate> result) {
    }

    @Override
    public void start() {
        running = true;
        thread = new Thread(this::loop, "telegram-poller");
        thread.setDaemon(true);
        thread.start();
        log.info("Telegram bot: long polling started (dev)");
    }

    @Override
    public void stop() {
        running = false;
        if (thread != null) {
            thread.interrupt();
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    private void loop() {
        while (running) {
            try {
                for (TelegramUpdate update : poll()) {
                    if (update.updateId() != null) {
                        offset = Math.max(offset, update.updateId() + 1);
                    }
                    handle(update);
                }
            } catch (RestClientResponseException ex) {
                if (ex.getStatusCode().value() == HttpStatus.CONFLICT.value()) {
                    log.warn("Telegram bot: getUpdates refused because a webhook is set for this bot. Long polling "
                            + "needs a dev bot without a webhook (deleteWebhook); retrying in 30 s");
                    pause(Duration.ofSeconds(30));
                } else {
                    log.warn("Telegram bot: getUpdates answered {}; retrying in 5 s", ex.getStatusCode().value());
                    pause(Duration.ofSeconds(5));
                }
            } catch (RuntimeException ex) {
                if (running) {
                    log.warn("Telegram bot: getUpdates failed ({}); retrying in 5 s", hideToken(ex.getMessage()));
                    pause(Duration.ofSeconds(5));
                }
            }
        }
    }

    private List<TelegramUpdate> poll() {
        Updates updates = http.post().uri("/getUpdates")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("offset", offset, "timeout", POLL_SECONDS, "allowed_updates", List.of("message")))
                .retrieve()
                .body(Updates.class);
        return updates == null || updates.result() == null ? List.of() : updates.result();
    }

    private void handle(TelegramUpdate update) {
        try {
            bot.onUpdate(update);
        } catch (RuntimeException ex) {
            log.warn("Telegram bot: update {} failed: {}", update.updateId(), ex.getMessage(), ex);
        }
    }

    private void pause(Duration duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            running = false;
        }
    }

    private String hideToken(String text) {
        return text == null ? "" : text.replace(token, "***");
    }
}
