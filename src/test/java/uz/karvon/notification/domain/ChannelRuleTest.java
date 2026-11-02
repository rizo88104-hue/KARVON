package uz.karvon.notification.domain;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.EnumSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static uz.karvon.notification.domain.NotificationChannel.SMS;
import static uz.karvon.notification.domain.NotificationChannel.TELEGRAM;

class ChannelRuleTest {

    @ParameterizedTest(name = "linked={0} telegram={1} sms={2} important={3} -> {4}")
    @CsvSource({
            // Telegram linked: Telegram always, SMS only for important events
            "true,  true,  true,  false, TELEGRAM",
            "true,  true,  true,  true,  TELEGRAM+SMS",
            // Telegram not linked: everything by SMS
            "false, true,  true,  false, SMS",
            "false, true,  true,  true,  SMS",
            // Telegram linked but turned off counts as not linked
            "true,  false, true,  false, SMS",
            "true,  false, true,  true,  SMS",
            // SMS turned off
            "true,  true,  false, true,  TELEGRAM",
            "false, true,  false, true,  NONE",
            "true,  false, false, true,  NONE"
    })
    void channels(boolean linked, boolean telegramEnabled, boolean smsEnabled, boolean important, String expected) {
        assertThat(ChannelRule.channels(linked, telegramEnabled, smsEnabled, important)).isEqualTo(parse(expected));
    }

    private static Set<NotificationChannel> parse(String expected) {
        Set<NotificationChannel> channels = EnumSet.noneOf(NotificationChannel.class);
        if (expected.contains("TELEGRAM")) {
            channels.add(TELEGRAM);
        }
        if (expected.contains("SMS")) {
            channels.add(SMS);
        }
        return channels;
    }
}
