package uz.karvon.notification.infrastructure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import uz.karvon.common.domain.PhoneNumbers;
import uz.karvon.notification.application.SmsSender;

/**
 * The SMS sender while SMS is turned off (karvon.sms.enabled=false, every profile): sends nothing and only logs
 * that a message was skipped. The text is not logged, it may hold a login code.
 */
@Component
@ConditionalOnProperty(name = SmsProperties.ENABLED, havingValue = "false", matchIfMissing = true)
public class NoopSmsSender implements SmsSender {

    private static final Logger log = LoggerFactory.getLogger(NoopSmsSender.class);

    @Override
    public void send(String phone, String text) {
        log.info("SMS is turned off (karvon.sms.enabled=false): nothing sent to {}", PhoneNumbers.mask(phone));
    }
}
