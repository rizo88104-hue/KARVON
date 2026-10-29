package uz.karvon.order.api;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import uz.karvon.common.api.CurrentUser;
import uz.karvon.common.api.PageResponse;
import uz.karvon.order.api.dto.CancelOrderRequest;
import uz.karvon.order.api.dto.CreateOrderRequest;
import uz.karvon.order.api.dto.DecisionRequest;
import uz.karvon.order.api.dto.OrderDetailResponse;
import uz.karvon.order.api.dto.OrderSummaryResponse;
import uz.karvon.order.application.OrderService;
import uz.karvon.order.domain.OrderTab;
import uz.karvon.translation.domain.Lang;

@Tag(name = "Orders")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/orders")
@PreAuthorize("hasRole('CUSTOMER')")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orders;

    /**
     * Orders the selected cart lines. 409 CART_CHANGED (with "changes") when prices or stock changed:
     * the cart is already updated, check it and send the same request again.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderDetailResponse create(Authentication authentication, @Valid @RequestBody CreateOrderRequest request) {
        return orders.create(CurrentUser.id(authentication), request, Lang.ofCustomer(LocaleContextHolder.getLocale()));
    }

    @GetMapping
    public PageResponse<OrderSummaryResponse> list(
            Authentication authentication,
            @Parameter(description = "all | awaiting_payment | in_progress | in_transit | delivered")
            @RequestParam(defaultValue = "all") String tab,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return orders.list(CurrentUser.id(authentication), OrderTab.fromParam(tab), page, size);
    }

    @GetMapping("/{number}")
    public OrderDetailResponse get(Authentication authentication, @PathVariable String number) {
        return orders.get(CurrentUser.id(authentication), number);
    }

    /** Only while waiting for payment, or paid before purchasing started (then the money is refunded). */
    @PostMapping("/{number}/cancel")
    public OrderDetailResponse cancel(Authentication authentication, @PathVariable String number,
                                      @Valid @RequestBody(required = false) CancelOrderRequest request) {
        return orders.cancel(CurrentUser.id(authentication), number, request == null ? null : request.reason());
    }

    /** For OUT_OF_STOCK or PRICE_CHANGED: ACCEPT to continue, REFUND to cancel with a refund. */
    @PostMapping("/{number}/decision")
    public OrderDetailResponse decide(Authentication authentication, @PathVariable String number,
                                      @Valid @RequestBody DecisionRequest request) {
        return orders.decide(CurrentUser.id(authentication), number, request.action());
    }
}
