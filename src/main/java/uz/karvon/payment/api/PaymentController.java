package uz.karvon.payment.api;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uz.karvon.common.api.CurrentUser;
import uz.karvon.order.application.PaymentPurpose;
import uz.karvon.payment.api.dto.StartPaymentRequest;
import uz.karvon.payment.api.dto.StartPaymentResponse;
import uz.karvon.payment.application.PaymentService;

@Tag(name = "Orders: payment")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/orders/{number}")
@PreAuthorize("hasRole('CUSTOMER')")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService payments;

    /** Pays an order waiting for payment. PAYME/CLICK answer a checkout link; WALLET pays at once. */
    @PostMapping("/pay")
    public StartPaymentResponse pay(Authentication authentication, @PathVariable String number,
                                    @Valid @RequestBody StartPaymentRequest request) {
        return payments.start(CurrentUser.id(authentication), number, PaymentPurpose.ORDER, request.provider());
    }

    /** Pays the extra shipping of an order in AWAITING_SHIPPING_PAYMENT. */
    @PostMapping("/pay-shipping")
    public StartPaymentResponse payShipping(Authentication authentication, @PathVariable String number,
                                            @Valid @RequestBody StartPaymentRequest request) {
        return payments.start(CurrentUser.id(authentication), number, PaymentPurpose.SHIPPING_DIFFERENCE, request.provider());
    }
}
