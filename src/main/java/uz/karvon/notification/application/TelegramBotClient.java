package uz.karvon.notification.application;

/**
 * Sends a text message to a Telegram chat through the Bot API.
 * Throws {@link TelegramSendException} if Telegram did not accept it.
 */
public interface TelegramBotClient {

    void sendMessage(long chatId, String text);
}
