package uz.karvon.common.application;

import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.util.LinkedHashMap;
import java.util.Map;

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
    /** Extra ProblemDetail fields, e.g. a list of what changed. */
    private final transient Map<String, Object> properties = new LinkedHashMap<>();

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

    /** Adds a field to the error answer. */
    public BusinessException withProperty(String name, Object value) {
        properties.put(name, value);
        return this;
    }
}
