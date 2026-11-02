package uz.karvon.notification.application;

/**
 * Sends a text message to a Telegram chat through our bot.
 * Throws {@link TelegramSendException} if Telegram did not accept it.
 */
public interface TelegramSender {

    void send(long chatId, String text);
}
