package uz.karvon.translation.application;

import org.springframework.http.HttpStatus;
import uz.karvon.common.application.BusinessException;

/**
 * The translation provider is down or refused the request. Answered with HTTP 503.
 */
public class TranslationUnavailableException extends BusinessException {

    public TranslationUnavailableException(Throwable cause) {
        super(HttpStatus.SERVICE_UNAVAILABLE, "TRANSLATION_UNAVAILABLE", "error.translation.unavailable");
        initCause(cause);
    }
}
