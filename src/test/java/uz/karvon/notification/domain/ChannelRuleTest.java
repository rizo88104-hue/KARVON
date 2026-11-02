package uz.karvon.notification.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static uz.karvon.notification.domain.NotificationChannel.SMS;
import static uz.karvon.notification.domain.NotificationChannel.TELEGRAM;

class ChannelRuleTest {

    private static final NotificationPreferences BOTH = NotificationPreferences.DEFAULT;

    @Test
    void telegramLinkedGetsEverythingThereAndSmsOnlyForImportantMessages() {
        assertThat(ChannelRule.channels(false, true, BOTH)).containsExactly(TELEGRAM);
        assertThat(ChannelRule.channels(true, true, BOTH)).containsExactlyInAnyOrder(TELEGRAM, SMS);
    }

    @Test
    void withoutTelegramEverythingGoesBySms() {
        assertThat(ChannelRule.channels(false, false, BOTH)).containsExactly(SMS);
        assertThat(ChannelRule.channels(true, false, BOTH)).containsExactly(SMS);
    }

    @Test
    void telegramTurnedOffCountsAsNotLinked() {
        NotificationPreferences smsOnly = new NotificationPreferences(true, false);

        assertThat(ChannelRule.channels(false, true, smsOnly)).containsExactly(SMS);
    }

    @Test
    void smsTurnedOffIsNeverUsed() {
        NotificationPreferences telegramOnly = new NotificationPreferences(false, true);

        assertThat(ChannelRule.channels(true, true, telegramOnly)).containsExactly(TELEGRAM);
        assertThat(ChannelRule.channels(true, false, telegramOnly)).isEmpty();
        assertThat(ChannelRule.channels(true, true, new NotificationPreferences(false, false))).isEmpty();
    }
}
