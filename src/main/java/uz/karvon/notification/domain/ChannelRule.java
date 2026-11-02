package uz.karvon.notification.domain;

import java.util.EnumSet;
import java.util.Set;

/**
 * Which channels a message goes to. If Telegram is linked (and allowed), it is used for everything and SMS is added
 * only for important messages; without Telegram, SMS is used. A channel the user turned off is never used.
 */
public final class ChannelRule {

    private ChannelRule() {
    }

    public static Set<NotificationChannel> channels(boolean important, boolean telegramLinked,
                                                    NotificationPreferences preferences) {
        Set<NotificationChannel> channels = EnumSet.noneOf(NotificationChannel.class);
        boolean telegram = telegramLinked && preferences.telegram();
        if (telegram) {
            channels.add(NotificationChannel.TELEGRAM);
        }
        if (preferences.sms() && (important || !telegram)) {
            channels.add(NotificationChannel.SMS);
        }
        return channels;
    }
}
