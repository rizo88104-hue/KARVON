package uz.karvon.settings.application;

import org.springframework.http.HttpStatus;
import uz.karvon.common.application.BusinessException;

/**
 * The Central Bank API is down or answered something unexpected. Answered with HTTP 503.
 */
public class CbuUnavailableException extends BusinessException {

    public CbuUnavailableException(Throwable cause) {
        super(HttpStatus.SERVICE_UNAVAILABLE, "CBU_UNAVAILABLE", "error.cbu.unavailable");
        initCause(cause);
    }
}
