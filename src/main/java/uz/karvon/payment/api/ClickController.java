package uz.karvon.payment.api;

import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.json.JsonMapper;
import uz.karvon.payment.application.click.ClickShopApi;
import uz.karvon.payment.domain.PaymentProvider;
import uz.karvon.payment.infrastructure.PaymentLogWriter;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Click calls these (SHOP API): form-urlencoded in, JSON out. Not for customers; checked by the sign inside.
 * Every call is logged raw.
 */
@Hidden
@RestController
@RequestMapping(value = "/api/v1/payments/click", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class ClickController {

    private static final Logger log = LoggerFactory.getLogger(ClickController.class);

    private final ClickShopApi click;
    private final PaymentLogWriter logs;
    private final JsonMapper json;

    @PostMapping("/prepare")
    public Map<String, Object> prepare(@RequestParam MultiValueMap<String, String> form) {
        return logged("prepare", form, click::prepare);
    }

    @PostMapping("/complete")
    public Map<String, Object> complete(@RequestParam MultiValueMap<String, String> form) {
        return logged("complete", form, click::complete);
    }

    private Map<String, Object> logged(String operation, MultiValueMap<String, String> form,
                                       Function<Map<String, String>, Map<String, Object>> handler) {
        Map<String, String> params = form.toSingleValueMap();
        Map<String, Object> answer;
        try {
            answer = handler.apply(params);
        } catch (RuntimeException ex) {
            // Rolled back; Click retries or sends it to manual review.
            log.error("Click {} failed", operation, ex);
            answer = new LinkedHashMap<>();
            answer.put("error", ClickShopApi.FAILED_TO_UPDATE_USER);
            answer.put("error_note", "Failed to update user");
        }
        logs.write(PaymentProvider.CLICK, operation, json.writeValueAsString(params), json.writeValueAsString(answer), null);
        return answer;
    }
}
