package uz.karvon.common.application;

import org.springframework.http.HttpStatus;

/**
 * The requested object does not exist. Answered with HTTP 404.
 */
public class NotFoundException extends BusinessException {

    public NotFoundException(String code, String messageKey, Object... args) {
        super(HttpStatus.NOT_FOUND, code, messageKey, args);
    }
}
