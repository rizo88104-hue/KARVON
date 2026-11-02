package uz.karvon.procurement.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.karvon.common.api.PageResponse;
import uz.karvon.order.api.dto.StaffOrderResponse;
import uz.karvon.order.application.OrderProcurementService;
import uz.karvon.order.application.ProcurementQueueOrder;
import uz.karvon.procurement.domain.PriceChangeRule;
import uz.karvon.settings.application.PricingSettingsService;
import uz.karvon.user.domain.Role;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

/**
 * The buyer queue in China. The price rule uses the threshold of the settings the order was priced with.
 */
@Service
@RequiredArgsConstructor
public class ProcurementService {

    private final OrderProcurementService orders;
    private final PricingSettingsService settings;

    public PageResponse<ProcurementQueueOrder> queue(int page, int size) {
        return orders.queue(page, size);
    }

    public StaffOrderResponse start(UUID buyerId, Set<Role> roles, UUID orderId) {
        return orders.start(buyerId, roles, orderId);
    }

    /** One transaction: the price check and the item change see the same data. */
    @Transactional
    public StaffOrderResponse purchased(UUID buyerId, Set<Role> roles, UUID itemId, BigDecimal actualPriceCny,
                                        String taobaoOrderNumber, String chinaTrackingNumber) {
        OrderProcurementService.ItemPriceContext item = orders.priceContext(itemId);
        BigDecimal threshold = settings.getById(item.pricingSettingsId()).priceChangeThresholdPercent();
        boolean ask = PriceChangeRule.needsCustomerDecision(item.priceCny(), actualPriceCny, threshold);
        return orders.markPurchased(buyerId, roles, itemId, actualPriceCny, taobaoOrderNumber, chinaTrackingNumber, ask);
    }

    public StaffOrderResponse outOfStock(UUID buyerId, Set<Role> roles, UUID itemId, String comment) {
        return orders.markOutOfStock(buyerId, roles, itemId, comment);
    }
}
