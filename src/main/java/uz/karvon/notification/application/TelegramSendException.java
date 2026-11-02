package uz.karvon.notification.application;

/**
 * Telegram is down or refused the message (e.g. the user blocked the bot). The message never contains the bot token.
 */
public class TelegramSendException extends RuntimeException {

    public TelegramSendException(String message) {
        super(message);
    }
}
