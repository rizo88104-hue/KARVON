package uz.karvon.notification.domain;

import java.util.EnumSet;
import java.util.Set;

/**
 * Which channels a notification goes to:
 * <ul>
 *   <li>Telegram, if a chat is linked and the customer did not turn Telegram off;</li>
 *   <li>SMS for important events, or for every event when Telegram is not used - unless the customer turned SMS off.</li>
 * </ul>
 * The result may be empty (the customer turned everything off).
 */
public final class ChannelRule {

    private ChannelRule() {
    }

    public static Set<NotificationChannel> channels(boolean telegramLinked, boolean telegramEnabled, boolean smsEnabled,
                                                    boolean important) {
        Set<NotificationChannel> channels = EnumSet.noneOf(NotificationChannel.class);
        boolean telegram = telegramLinked && telegramEnabled;
        if (telegram) {
            channels.add(NotificationChannel.TELEGRAM);
        }
        if (smsEnabled && (important || !telegram)) {
            channels.add(NotificationChannel.SMS);
        }
        return channels;
    }
}
