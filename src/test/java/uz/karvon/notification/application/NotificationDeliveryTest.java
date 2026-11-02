package uz.karvon.notification.application;

import org.junit.jupiter.api.Test;
import uz.karvon.common.infrastructure.I18nConfig;
import uz.karvon.notification.domain.NotificationChannel;
import uz.karvon.user.application.UserContact;
import uz.karvon.user.application.UserService;
import uz.karvon.user.domain.Language;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class NotificationDeliveryTest {

    private static final UUID ID = UUID.randomUUID();
    private static final UUID USER = UUID.randomUUID();
    private static final String PHONE = "+998901234567";

    private final NotificationService outbox = mock(NotificationService.class);
    private final UserService users = mock(UserService.class);
    private final SmsSender sms = mock(SmsSender.class);
    private final TelegramBotClient telegram = mock(TelegramBotClient.class);
    private final NotificationDelivery delivery = new NotificationDelivery(outbox, users,
            new TemplateRenderer(new I18nConfig().messageSource()), sms, telegram);

    @Test
    void smsInTheUsersLanguageThenSent() {
        claimed(NotificationChannel.SMS);
        when(users.contact(USER)).thenReturn(Optional.of(new UserContact(USER, PHONE, Language.RU, null)));

        assertThat(delivery.deliver(ID)).isTrue();

        verify(sms).send(PHONE, "Karvon: заказ KRV-000042 оплачен. Скоро выкупим его.");
        verify(outbox).markSent(ID);
    }

    @Test
    void telegramGoesToTheLinkedChat() {
        claimed(NotificationChannel.TELEGRAM);
        when(users.contact(USER)).thenReturn(Optional.of(new UserContact(USER, PHONE, Language.UZ, 777L)));

        assertThat(delivery.deliver(ID)).isTrue();

        verify(telegram).sendMessage(777L, "Karvon: KRV-000042 buyurtmangiz to'landi. Tez orada sotib olamiz.");
        verifyNoInteractions(sms);
    }

    @Test
    void providerErrorMarksItFailedForTheRetryJob() {
        claimed(NotificationChannel.SMS);
        when(users.contact(USER)).thenReturn(Optional.of(new UserContact(USER, PHONE, Language.UZ, null)));
        doThrow(new SmsSendException(new IllegalStateException("Eskiz is down"))).when(sms).send(anyString(), anyString());

        assertThat(delivery.deliver(ID)).isFalse();

        verify(outbox).markFailed(eq(ID), contains("Eskiz is down"));
        verify(outbox, never()).markSent(ID);
    }

    @Test
    void telegramUnlinkedMeanwhileFails() {
        claimed(NotificationChannel.TELEGRAM);
        when(users.contact(USER)).thenReturn(Optional.of(new UserContact(USER, PHONE, Language.UZ, null)));

        assertThat(delivery.deliver(ID)).isFalse();

        verify(outbox).markFailed(eq(ID), contains("Telegram is not linked"));
        verify(telegram, never()).sendMessage(anyLong(), anyString());
    }

    @Test
    void alreadySentOrTakenByAnotherSenderDoesNothing() {
        when(outbox.claim(ID)).thenReturn(Optional.empty());

        assertThat(delivery.deliver(ID)).isFalse();

        verifyNoInteractions(sms, telegram, users);
    }

    private void claimed(NotificationChannel channel) {
        when(outbox.claim(ID)).thenReturn(Optional.of(
                new NotificationService.Outgoing(ID, USER, channel, "order.paid", List.of("KRV-000042"))));
    }
}
