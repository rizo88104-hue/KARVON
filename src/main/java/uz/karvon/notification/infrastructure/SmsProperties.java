package uz.karvon.notification.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * SMS on or off ({@code karvon.sms.enabled}, env SMS_ENABLED). Off (the default): no SMS is sent at all, the
 * {@link NoopSmsSender} only logs, login codes and notifications go to Telegram, otherwise in-app.
 * On: {@link EskizSmsSender} in prod, {@link FakeSmsSender} in dev and test.
 */
@ConfigurationProperties("karvon.sms")
public record SmsProperties(@DefaultValue("false") boolean enabled) {

    public static final String ENABLED = "karvon.sms.enabled";
}
