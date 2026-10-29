package uz.karvon.notification.application;

/**
 * Sends an SMS. {@code phone} is E.164 (+998XXXXXXXXX).
 * Throws {@link SmsSendException} if the provider did not accept the message.
 */
public interface SmsSender {

    void send(String phone, String text);
}
