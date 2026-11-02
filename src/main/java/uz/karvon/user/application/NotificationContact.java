package uz.karvon.user.application;

import uz.karvon.user.domain.Language;

import java.util.UUID;

/**
 * Where and in which language a user gets notifications. {@code telegramChatId} is null until Telegram is linked.
 */
public record NotificationContact(UUID userId, String phone, Language language, Long telegramChatId,
                                  boolean smsEnabled, boolean telegramEnabled) {

    public boolean telegramLinked() {
        return telegramChatId != null;
    }
}
