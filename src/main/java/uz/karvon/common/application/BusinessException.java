package uz.karvon.common.application;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * A business rule was broken. {@code code} is the stable error code for clients,
 * {@code messageKey} points to the text in messages_uz/messages_ru.
 */
@Getter
public class BusinessException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final String messageKey;
    private final transient Object[] args;

    public BusinessException(String code, String messageKey, Object... args) {
        this(HttpStatus.BAD_REQUEST, code, messageKey, args);
    }

    public BusinessException(HttpStatus status, String code, String messageKey, Object... args) {
        super(code);
        this.status = status;
        this.code = code;
        this.messageKey = messageKey;
        this.args = args;
    }
}
