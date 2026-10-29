package uz.karvon.payment.api;

import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uz.karvon.payment.application.payme.PaymeEndpoint;

/**
 * Payme Business calls this (Merchant API). Not for customers; authenticated with Basic auth inside.
 * Answers are always HTTP 200, as Payme requires ("Статусы отличные от 200 воспринимаются как RPC-ошибка: -32400").
 */
@Hidden
@RestController
@RequestMapping("/api/v1/payments/payme")
@RequiredArgsConstructor
public class PaymeController {

    private final PaymeEndpoint payme;

    @PostMapping
    public ResponseEntity<String> rpc(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
                                      @RequestBody(required = false) String body) {
        return json(payme.handle(authorization, body));
    }

    @GetMapping
    public ResponseEntity<String> notPost() {
        return json(payme.notPost());
    }

    private static ResponseEntity<String> json(String body) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(body);
    }
}
