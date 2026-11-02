package uz.karvon.common.infrastructure.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.LocaleResolver;
import tools.jackson.databind.json.JsonMapper;
import uz.karvon.common.api.GlobalExceptionHandler;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Writes the same RFC 7807 body as GlobalExceptionHandler from servlet filters, which run before Spring MVC:
 * {"type","title","status","detail","instance","code"} with the detail in the request language (uz or ru).
 */
@Component
@RequiredArgsConstructor
public class ProblemResponses {

    private final MessageSource messages;
    private final LocaleResolver locales;
    private final JsonMapper json;

    public void write(HttpServletRequest request, HttpServletResponse response, HttpStatus status, String code,
                      String messageKey, Object... args) throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "about:blank");
        body.put("title", status.getReasonPhrase());
        body.put("status", status.value());
        body.put("detail", messages.getMessage(messageKey, args, locales.resolveLocale(request)));
        body.put("instance", request.getRequestURI());
        body.put(GlobalExceptionHandler.CODE, code);
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(json.writeValueAsString(body));
    }
}
