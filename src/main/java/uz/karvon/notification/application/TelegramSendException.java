package uz.karvon.notification.application;

/**
 * Telegram is down or refused the message. The message never contains the bot token.
 */
public class TelegramSendException extends RuntimeException {

    public TelegramSendException(String message) {
        super(message);
    }
}
