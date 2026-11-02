package uz.karvon.order.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import uz.karvon.order.domain.Order;
import uz.karvon.order.domain.OrderItem;
import uz.karvon.pricing.application.PricingService;
import uz.karvon.pricing.domain.PriceBreakdown;

import java.math.BigDecimal;

/**
 * Extra cost of an item bought at a higher price than at order time, with the settings the order was priced with:
 * (goods + service fee at the actual price) - (goods + service fee paid). Shipping does not depend on the price.
 * Never negative: a cheaper purchase is not refunded here.
 */
@Component
@RequiredArgsConstructor
public class PriceDifferences {

    private final PricingService pricing;

    public BigDecimal of(Order order, OrderItem item) {
        if (item.getActualPriceCny() == null) {
            return BigDecimal.ZERO;
        }
        PriceBreakdown actual = pricing.quoter(order.getPricingSettingsId())
                .price(item.getActualPriceCny(), item.getChinaShippingCny(), item.getQuantity(), item.getWeightKg());
        BigDecimal paid = item.getGoodsUzs().add(item.getServiceFeeUzs());
        return actual.goodsUzs().add(actual.serviceFeeUzs()).subtract(paid).max(BigDecimal.ZERO);
    }
}
