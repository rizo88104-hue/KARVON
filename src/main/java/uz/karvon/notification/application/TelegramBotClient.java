package uz.karvon.notification.application;

/**
 * Sends a text message to a Telegram chat through the Bot API.
 * Throws {@link TelegramSendException} if Telegram did not accept it.
 */
public interface TelegramBotClient {

    default void sendMessage(long chatId, String text) {
        sendMessage(chatId, text, Keyboard.NONE);
    }

    /** With a reply keyboard: a "share my phone number" button, or removal of the keyboard shown before. */
    void sendMessage(long chatId, String text, Keyboard keyboard);

    sealed interface Keyboard permits Keyboard.Unchanged, Keyboard.ShareContact, Keyboard.Remove {

        Keyboard NONE = new Unchanged();
        Keyboard REMOVE = new Remove();

        /** No reply_markup: the chat keeps what it shows. */
        record Unchanged() implements Keyboard {
        }

        /** One button that sends the user's own phone number (request_contact). */
        record ShareContact(String buttonText) implements Keyboard {
        }

        record Remove() implements Keyboard {
        }
    }
}
