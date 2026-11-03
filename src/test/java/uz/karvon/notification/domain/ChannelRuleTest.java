package uz.karvon.notification.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static uz.karvon.notification.domain.NotificationChannel.IN_APP;
import static uz.karvon.notification.domain.NotificationChannel.SMS;
import static uz.karvon.notification.domain.NotificationChannel.TELEGRAM;

class ChannelRuleTest {

    private static final NotificationPreferences BOTH = NotificationPreferences.DEFAULT;
    private static final boolean SMS_ON = true;
    private static final boolean SMS_OFF = false;

    // --- SMS turned off for the shop (karvon.sms.enabled=false, the current setting)

    @Test
    void smsOffEverythingGoesOnlyByTelegram() {
        assertThat(ChannelRule.channels(false, true, BOTH, SMS_OFF)).containsExactly(TELEGRAM);
        assertThat(ChannelRule.channels(true, true, BOTH, SMS_OFF)).containsExactly(TELEGRAM);
    }

    @Test
    void smsOffWithoutTelegramTheMessageIsKeptInApp() {
        assertThat(ChannelRule.channels(false, false, BOTH, SMS_OFF)).containsExactly(IN_APP);
        assertThat(ChannelRule.channels(true, false, BOTH, SMS_OFF)).containsExactly(IN_APP);
    }

    @Test
    void smsOffAndTelegramTurnedOffByTheUserIsInApp() {
        NotificationPreferences smsOnly = new NotificationPreferences(true, false);

        assertThat(ChannelRule.channels(true, true, smsOnly, SMS_OFF)).containsExactly(IN_APP);
    }

    // --- SMS turned on (later): the earlier rules

    @Test
    void telegramLinkedGetsEverythingThereAndSmsOnlyForImportantMessages() {
        assertThat(ChannelRule.channels(false, true, BOTH, SMS_ON)).containsExactly(TELEGRAM);
        assertThat(ChannelRule.channels(true, true, BOTH, SMS_ON)).containsExactlyInAnyOrder(TELEGRAM, SMS);
    }

    @Test
    void withoutTelegramEverythingGoesBySms() {
        assertThat(ChannelRule.channels(false, false, BOTH, SMS_ON)).containsExactly(SMS);
        assertThat(ChannelRule.channels(true, false, BOTH, SMS_ON)).containsExactly(SMS);
    }

    @Test
    void telegramTurnedOffCountsAsNotLinked() {
        NotificationPreferences smsOnly = new NotificationPreferences(true, false);

        assertThat(ChannelRule.channels(false, true, smsOnly, SMS_ON)).containsExactly(SMS);
    }

    @Test
    void smsTurnedOffByTheUserIsNeverUsedAndNothingLeftMeansInApp() {
        NotificationPreferences telegramOnly = new NotificationPreferences(false, true);

        assertThat(ChannelRule.channels(true, true, telegramOnly, SMS_ON)).containsExactly(TELEGRAM);
        assertThat(ChannelRule.channels(true, false, telegramOnly, SMS_ON)).containsExactly(IN_APP);
        assertThat(ChannelRule.channels(true, true, new NotificationPreferences(false, false), SMS_ON))
                .containsExactly(IN_APP);
    }
}
