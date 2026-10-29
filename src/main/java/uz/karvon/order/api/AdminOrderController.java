package uz.karvon.order.api;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import uz.karvon.common.api.CurrentUser;
import uz.karvon.common.api.PageResponse;
import uz.karvon.order.api.dto.OrderCommentRequest;
import uz.karvon.order.api.dto.OrderCommentResponse;
import uz.karvon.order.api.dto.StaffOrderResponse;
import uz.karvon.order.api.dto.StaffOrderSummaryResponse;
import uz.karvon.order.api.dto.StatusChangeRequest;
import uz.karvon.order.api.dto.UpdateOrderItemRequest;
import uz.karvon.order.application.StaffOrderService;
import uz.karvon.order.domain.OrderStatus;
import uz.karvon.user.domain.Role;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

@Tag(name = "Admin: orders")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/admin/orders")
@PreAuthorize("hasAnyRole('OPERATOR', 'BUYER', 'ADMIN')")
@RequiredArgsConstructor
public class AdminOrderController {

    private final StaffOrderService orders;

    /** {@code from}/{@code to}: creation time (ISO-8601, e.g. 2026-10-01T00:00:00Z); {@code number} matches any part. */
    @GetMapping
    public PageResponse<StaffOrderSummaryResponse> search(
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) String number,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return orders.search(status, from, to, phone, number, page, size);
    }

    @GetMapping("/{id}")
    public StaffOrderResponse get(@PathVariable UUID id) {
        return orders.get(id);
    }

    /** Checked by the order state machine against your roles; see StatusChangeRequest for the data each step needs. */
    @PostMapping("/{id}/status")
    public StaffOrderResponse changeStatus(Authentication authentication, @PathVariable UUID id,
                                           @Valid @RequestBody StatusChangeRequest request) {
        return orders.changeStatus(CurrentUser.id(authentication), roles(authentication), id, request);
    }

    /** Buyer data: actual price, Taobao order number, China tracking number. */
    @PatchMapping("/{id}/items/{itemId}")
    @PreAuthorize("hasAnyRole('BUYER', 'ADMIN')")
    public StaffOrderResponse updateItem(@PathVariable UUID id, @PathVariable UUID itemId,
                                         @Valid @RequestBody UpdateOrderItemRequest request) {
        return orders.updateItem(id, itemId, request);
    }

    /** Internal note, never shown to the customer. */
    @PostMapping("/{id}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public OrderCommentResponse addComment(Authentication authentication, @PathVariable UUID id,
                                           @Valid @RequestBody OrderCommentRequest request) {
        return orders.addComment(CurrentUser.id(authentication), id, request.text());
    }

    /** ADMIN only, until online payments exist: the customer paid the shipping difference another way. */
    @PostMapping("/{id}/shipping-payment")
    @PreAuthorize("hasRole('ADMIN')")
    public StaffOrderResponse recordShippingPayment(Authentication authentication, @PathVariable UUID id) {
        return orders.recordShippingPayment(CurrentUser.id(authentication), id);
    }

    private static Set<Role> roles(Authentication authentication) {
        Set<Role> roles = EnumSet.noneOf(Role.class);
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            String name = authority.getAuthority();
            if (name != null && name.startsWith("ROLE_")) {
                try {
                    roles.add(Role.valueOf(name.substring("ROLE_".length())));
                } catch (IllegalArgumentException ignored) {
                    // not one of our roles
                }
            }
        }
        return roles;
    }
}
