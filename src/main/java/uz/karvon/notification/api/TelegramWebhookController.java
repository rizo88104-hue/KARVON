package uz.karvon.notification.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uz.karvon.notification.api.dto.TelegramUpdate;
import uz.karvon.notification.application.TelegramService;

/**
 * Called by Telegram for every update of the bot (register it with setWebhook and secret_token).
 * Answers 200 for any update it accepted, so Telegram does not send it again.
 */
@Tag(name = "Telegram")
@RestController
@RequestMapping("/api/v1/telegram/webhook")
@RequiredArgsConstructor
public class TelegramWebhookController {

    static final String SECRET_HEADER = "X-Telegram-Bot-Api-Secret-Token";

    private final TelegramService telegram;

    @PostMapping
    public ResponseEntity<Void> update(@RequestHeader(value = SECRET_HEADER, required = false) String secret,
                                       @RequestBody TelegramUpdate update) {
        telegram.requireWebhookSecret(secret);
        telegram.onUpdate(update);
        return ResponseEntity.ok().build();
    }
}
