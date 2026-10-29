package uz.karvon.notification.application;

import org.springframework.http.HttpStatus;
import uz.karvon.common.application.BusinessException;

/**
 * The SMS provider is down or refused the message. Answered with HTTP 503.
 */
public class SmsSendException extends BusinessException {

    public SmsSendException(Throwable cause) {
        super(HttpStatus.SERVICE_UNAVAILABLE, "SMS_SEND_FAILED", "error.sms.sendFailed");
        initCause(cause);
    }
}
