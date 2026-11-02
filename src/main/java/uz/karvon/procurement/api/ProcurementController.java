package uz.karvon.procurement.api;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uz.karvon.common.api.CurrentUser;
import uz.karvon.common.api.PageResponse;
import uz.karvon.order.api.dto.StaffOrderResponse;
import uz.karvon.order.application.ProcurementQueueOrder;
import uz.karvon.procurement.api.dto.ItemOutOfStockRequest;
import uz.karvon.procurement.api.dto.ItemPurchasedRequest;
import uz.karvon.procurement.application.ProcurementService;
import uz.karvon.user.api.StaffRoles;

import java.util.UUID;

/**
 * The buyer in China: take paid orders from the queue and buy them item by item.
 */
@Tag(name = "Admin: procurement")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/admin/procurement")
@PreAuthorize("hasAnyRole('BUYER', 'ADMIN')")
@RequiredArgsConstructor
public class ProcurementController {

    private final ProcurementService procurement;

    /** PAID orders, the longest waiting first. */
    @GetMapping("/queue")
    public PageResponse<ProcurementQueueOrder> queue(@RequestParam(defaultValue = "0") @Min(0) int page,
                                                     @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return procurement.queue(page, size);
    }

    /** Takes the order: PURCHASING, and you become its buyer. */
    @PostMapping("/orders/{id}/start")
    public StaffOrderResponse start(Authentication authentication, @PathVariable UUID id) {
        return procurement.start(CurrentUser.id(authentication), StaffRoles.of(authentication), id);
    }

    /** The item is bought. When every item is done the order becomes PURCHASED. */
    @PostMapping("/items/{itemId}/purchased")
    public StaffOrderResponse purchased(Authentication authentication, @PathVariable UUID itemId,
                                        @Valid @RequestBody ItemPurchasedRequest request) {
        return procurement.purchased(CurrentUser.id(authentication), StaffRoles.of(authentication), itemId,
                request.actualPriceCny(), request.taobaoOrderNumber(), request.chinaTrackingNumber());
    }

    /** The item cannot be bought; the customer decides (continue without it, or refund). */
    @PostMapping("/items/{itemId}/out-of-stock")
    public StaffOrderResponse outOfStock(Authentication authentication, @PathVariable UUID itemId,
                                         @Valid @RequestBody(required = false) ItemOutOfStockRequest request) {
        return procurement.outOfStock(CurrentUser.id(authentication), StaffRoles.of(authentication), itemId,
                request == null ? null : request.comment());
    }
}
