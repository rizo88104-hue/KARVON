package uz.karvon.notification.domain;

import java.util.EnumSet;
import java.util.Set;

/**
 * Which channels a message goes to. If Telegram is linked (and allowed), it is used for everything; SMS is added only
 * for important messages, and is used instead of Telegram without it. SMS needs SMS turned on for the whole shop
 * ({@code smsEnabled}, karvon.sms.enabled) and allowed by the user. A channel the user turned off is never used.
 * When nothing is left the message is kept in-app (shown on the site).
 */
public final class ChannelRule {

    private ChannelRule() {
    }

    public static Set<NotificationChannel> channels(boolean important, boolean telegramLinked,
                                                    NotificationPreferences preferences, boolean smsEnabled) {
        Set<NotificationChannel> channels = EnumSet.noneOf(NotificationChannel.class);
        boolean telegram = telegramLinked && preferences.telegram();
        if (telegram) {
            channels.add(NotificationChannel.TELEGRAM);
        }
        if (smsEnabled && preferences.sms() && (important || !telegram)) {
            channels.add(NotificationChannel.SMS);
        }
        if (channels.isEmpty()) {
            channels.add(NotificationChannel.IN_APP);
        }
        return channels;
    }
}
