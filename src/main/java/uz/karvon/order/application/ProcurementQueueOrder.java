package uz.karvon.order.application;

import uz.karvon.catalog.domain.TranslatedPair;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A paid order waiting for a buyer, with what is needed to buy it on Taobao.
 *
 * @param paidMinutesAgo how long ago the customer paid
 */
public record ProcurementQueueOrder(UUID orderId, String number, Instant paidAt, long paidMinutesAgo,
                                    String customerComment, List<Item> items) {

    /** {@code skuProperties} has the Chinese names and values to pick on Taobao. */
    public record Item(UUID itemId, String externalProductId, String taobaoUrl, String skuId,
                       List<TranslatedPair> skuProperties, int quantity, BigDecimal priceCny, String title,
                       String imageUrl, String shopName, String comment) {
    }
}
