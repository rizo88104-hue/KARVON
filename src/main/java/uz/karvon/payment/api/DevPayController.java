package uz.karvon.payment.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uz.karvon.order.application.PaymentPurpose;
import uz.karvon.payment.application.PaymentService;
import uz.karvon.payment.domain.PaymentState;
import uz.karvon.payment.domain.PaymentTransaction;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Dev and test only: the fake checkout page. Opening it pays the order. The bean does not exist in prod.
 */
@Tag(name = "Dev")
@Profile({"dev", "test"})
@RestController
@RequestMapping("/api/v1/dev/pay")
@RequiredArgsConstructor
public class DevPayController {

    private final PaymentService payments;

    @GetMapping("/{transactionId}")
    public DevPayResponse pay(@PathVariable UUID transactionId) {
        PaymentTransaction tx = payments.performSimulated(transactionId);
        return new DevPayResponse(tx.getId(), tx.getOrderNumber(), tx.getPurpose(), tx.getAmountUzs(), tx.getState());
    }

    public record DevPayResponse(UUID transactionId, String orderNumber, PaymentPurpose purpose, BigDecimal amountUzs,
                                 PaymentState state) {
    }
}
