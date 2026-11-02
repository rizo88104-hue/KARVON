package uz.karvon.user.application;

import uz.karvon.user.domain.Language;

import java.util.UUID;

/**
 * How to reach a user: phone (E.164), language of the texts and the linked Telegram chat (null if not linked).
 */
public record UserContact(UUID id, String phone, Language language, Long telegramChatId) {

    public boolean telegramLinked() {
        return telegramChatId != null;
    }
}
