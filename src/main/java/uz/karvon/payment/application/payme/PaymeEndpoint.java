package uz.karvon.payment.application.payme;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import uz.karvon.payment.domain.PaymentProvider;
import uz.karvon.payment.infrastructure.PaymentLogWriter;
import uz.karvon.payment.infrastructure.PaymentProperties;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * JSON-RPC 2.0 front of the Merchant API ("Протокол Merchant API", "Формат запроса", "Формат ответа"):
 * {method, params, id} in, {result, id} or {error: {code, message: {ru, uz, en}, data}, id} out, always HTTP 200.
 * Authorization: Basic base64(login:key); a wrong or missing header answers -32504.
 * Every call is logged raw.
 */
@Component
public class PaymeEndpoint {

    private static final Logger log = LoggerFactory.getLogger(PaymeEndpoint.class);
    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {
    };

    private final PaymeMethods methods;
    private final PaymentProperties properties;
    private final PaymentLogWriter logs;
    private final JsonMapper json;

    public PaymeEndpoint(PaymeMethods methods, PaymentProperties properties, PaymentLogWriter logs, JsonMapper json) {
        this.methods = methods;
        this.properties = properties;
        this.logs = logs;
        this.json = json;
    }

    public String handle(String authorization, String body) {
        Object id = null;
        String method = "unknown";
        String answer;
        try {
            JsonNode request = parse(body);
            id = request.has("id") && !request.get("id").isNull() ? json.treeToValue(request.get("id"), Object.class) : null;
            if (!authorized(authorization)) {
                throw new PaymeError(PaymeError.INSUFFICIENT_PRIVILEGES);
            }
            JsonNode methodNode = request.get("method");
            JsonNode params = request.get("params");
            if (methodNode == null || !methodNode.isString() || params == null || !params.isObject()) {
                throw new PaymeError(PaymeError.INVALID_REQUEST);
            }
            method = methodNode.asString();
            answer = result(id, call(method, params));
        } catch (PaymeError error) {
            answer = error(id, error);
        } catch (RuntimeException ex) {
            log.error("Payme {} failed", method, ex);
            answer = error(id, new PaymeError(PaymeError.SYSTEM_ERROR));
        }
        logs.write(PaymentProvider.PAYME, method, body == null ? "" : body, answer, null);
        return answer;
    }

    /** The answer for a request that is not POST. */
    public String notPost() {
        return error(null, new PaymeError(PaymeError.NOT_POST));
    }

    private Map<String, Object> call(String method, JsonNode params) {
        return switch (method) {
            case "CheckPerformTransaction" -> methods.checkPerformTransaction(amount(params), account(params));
            case "CreateTransaction" -> methods.createTransaction(text(params, "id"), number(params, "time"),
                    amount(params), account(params));
            case "PerformTransaction" -> methods.performTransaction(text(params, "id"));
            case "CancelTransaction" -> methods.cancelTransaction(text(params, "id"), (int) number(params, "reason"));
            case "CheckTransaction" -> methods.checkTransaction(text(params, "id"));
            case "GetStatement" -> methods.getStatement(number(params, "from"), number(params, "to"));
            default -> throw new PaymeError(PaymeError.METHOD_NOT_FOUND, method);
        };
    }

    private boolean authorized(String header) {
        String login = properties.payme().login();
        String key = properties.payme().key();
        if (header == null || !header.startsWith("Basic ") || key.isBlank()) {
            return false;
        }
        String decoded;
        try {
            decoded = new String(Base64.getDecoder().decode(header.substring("Basic ".length()).trim()), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            return false;
        }
        byte[] expected = (login + ":" + key).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, decoded.getBytes(StandardCharsets.UTF_8));
    }

    private JsonNode parse(String body) {
        try {
            JsonNode node = json.readTree(body == null ? "" : body);
            if (node == null || !node.isObject()) {
                throw new PaymeError(PaymeError.PARSE_ERROR);
            }
            return node;
        } catch (JacksonException ex) {
            throw new PaymeError(PaymeError.PARSE_ERROR);
        }
    }

    private Map<String, Object> account(JsonNode params) {
        JsonNode account = params.get("account");
        if (account == null || !account.isObject()) {
            throw new PaymeError(PaymeError.INVALID_REQUEST);
        }
        return json.treeToValue(account, MAP);
    }

    /** Amount: a positive whole number of tiyin. */
    private static long amount(JsonNode params) {
        JsonNode amount = params.get("amount");
        if (amount == null || !amount.isNumber() || !amount.canConvertToExactIntegral() || amount.asLong() <= 0) {
            throw new PaymeError(PaymeError.INVALID_AMOUNT);
        }
        return amount.asLong();
    }

    private static long number(JsonNode params, String field) {
        JsonNode value = params.get(field);
        if (value == null || !value.isNumber() || !value.canConvertToExactIntegral()) {
            throw new PaymeError(PaymeError.INVALID_REQUEST);
        }
        return value.asLong();
    }

    private static String text(JsonNode params, String field) {
        JsonNode value = params.get(field);
        if (value == null || !value.isString() || value.asString().isBlank()) {
            throw new PaymeError(PaymeError.INVALID_REQUEST);
        }
        return value.asString();
    }

    private String result(Object id, Map<String, Object> result) {
        Map<String, Object> answer = new LinkedHashMap<>();
        answer.put("result", result);
        answer.put("id", id);
        return json.writeValueAsString(answer);
    }

    private String error(Object id, PaymeError error) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", error.code());
        body.put("message", error.message());
        body.put("data", error.data());
        Map<String, Object> answer = new LinkedHashMap<>();
        answer.put("error", body);
        answer.put("id", id);
        return json.writeValueAsString(answer);
    }
}
