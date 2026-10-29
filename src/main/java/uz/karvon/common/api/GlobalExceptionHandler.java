package uz.karvon.common.api;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import uz.karvon.common.application.BusinessException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Turns every error into an RFC 7807 ProblemDetail with a stable "code" and a localized "detail".
 */
@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    public static final String CODE = "code";
    public static final String ERRORS = "errors";

    private final MessageSource messages;

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ProblemDetail> handleBusiness(BusinessException ex) {
        ResponseEntity<ProblemDetail> response = problem(ex.getStatus(), ex.getCode(), ex.getMessageKey(), ex.getArgs());
        ex.getProperties().forEach(response.getBody()::setProperty);
        return response;
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException ex) {
        return problem(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "error.http.403");
    }

    /** A valid token of a user who has been blocked. */
    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<ProblemDetail> handleDisabled(DisabledException ex) {
        return problem(HttpStatus.FORBIDDEN, "USER_BLOCKED", "error.user.blocked");
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ProblemDetail> handleAuthentication(AuthenticationException ex) {
        return problem(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "error.http.401");
    }

    /** Unknown field in a "sort" query parameter. */
    @ExceptionHandler(PropertyReferenceException.class)
    public ResponseEntity<ProblemDetail> handleBadSort(PropertyReferenceException ex) {
        return problem(HttpStatus.BAD_REQUEST, "INVALID_SORT", "error.http.400");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception ex) {
        log.error("Unexpected error", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "error.internal");
    }

    @Override
    protected @Nullable ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(status, text("error.validation"));
        body.setProperty(CODE, "VALIDATION_ERROR");
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errors.putIfAbsent(error.getField(), messages.getMessage(error, LocaleContextHolder.getLocale()));
        }
        body.setProperty(ERRORS, errors);
        return handleExceptionInternal(ex, body, headers, status, request);
    }

    /** Adds "code" and a localized "detail" to the ProblemDetail Spring MVC built for its own exceptions. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, @Nullable Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        ProblemDetail problem = body instanceof ProblemDetail pd ? pd : ProblemDetail.forStatus(statusCode);
        if (problem.getProperties() == null || !problem.getProperties().containsKey(CODE)) {
            problem.setProperty(CODE, codeFor(statusCode));
            problem.setDetail(messages.getMessage("error.http." + statusCode.value(), null,
                    problem.getDetail(), LocaleContextHolder.getLocale()));
        }
        return super.handleExceptionInternal(ex, problem, headers, statusCode, request);
    }

    private ResponseEntity<ProblemDetail> problem(HttpStatus status, String code, String messageKey, Object... args) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(status, text(messageKey, args));
        body.setProperty(CODE, code);
        return ResponseEntity.status(status).body(body);
    }

    private String text(String key, Object... args) {
        return messages.getMessage(key, args, key, LocaleContextHolder.getLocale());
    }

    private static String codeFor(HttpStatusCode statusCode) {
        HttpStatus status = HttpStatus.resolve(statusCode.value());
        return status != null ? status.name() : "HTTP_" + statusCode.value();
    }
}
