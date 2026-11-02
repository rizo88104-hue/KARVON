package uz.karvon.order.application;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;
import uz.karvon.catalog.domain.TranslatedPair;
import uz.karvon.common.api.PageResponse;
import uz.karvon.common.application.BusinessException;
import uz.karvon.common.application.NotFoundException;
import uz.karvon.order.api.dto.StaffOrderResponse;
import uz.karvon.order.domain.Actor;
import uz.karvon.order.domain.Order;
import uz.karvon.order.domain.OrderItem;
import uz.karvon.order.domain.OrderItemStatus;
import uz.karvon.order.domain.OrderStatus;
import uz.karvon.order.infrastructure.OrderItemRepository;
import uz.karvon.order.infrastructure.OrderRepository;
import uz.karvon.user.domain.Role;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The order side of purchasing on Taobao, used by the procurement module. A buyer takes a PAID order (it becomes
 * PURCHASING and theirs), then marks each item purchased or out of stock. Only that buyer or an ADMIN may work on it.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderProcurementService {

    private static final TypeReference<List<TranslatedPair>> PROPERTIES = new TypeReference<>() {
    };

    private final OrderRepository orders;
    private final OrderItemRepository items;
    private final OrderWorkflow workflow;
    private final StaffOrderService staffOrders;
    private final JsonMapper json;
    private final Clock clock;

    /** What the price rule needs to know about an item. */
    public record ItemPriceContext(UUID itemId, UUID orderId, BigDecimal priceCny, UUID pricingSettingsId) {
    }

    /** PAID orders, the longest waiting first. */
    public PageResponse<ProcurementQueueOrder> queue(int page, int size) {
        Instant now = Instant.now(clock);
        return PageResponse.of(orders.findAll(
                (root, query, cb) -> cb.equal(root.get("status"), OrderStatus.PAID),
                PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "paidAt"))), order -> queueOrder(order, now));
    }

    /** PAID -> PURCHASING; the caller becomes the order's buyer. */
    @Transactional
    public StaffOrderResponse start(UUID staffId, Set<Role> roles, UUID orderId) {
        Order order = load(orderId);
        workflow.change(order, OrderStatus.PURCHASING, Actor.staffOf(roles), staffId, null);
        order.setBuyerId(staffId);
        return staffOrders.get(orderId);
    }

    public ItemPriceContext priceContext(UUID itemId) {
        OrderItem item = loadItem(itemId);
        return new ItemPriceContext(item.getId(), item.getOrder().getId(), item.getPriceCny(),
                item.getOrder().getPricingSettingsId());
    }

    /**
     * The buyer bought the item. {@code priceChanged} (decided by the procurement price rule): the item and the order
     * go to PRICE_CHANGED and the customer decides. Otherwise the item is PURCHASED, and the order becomes PURCHASED
     * once every item is purchased or out of stock.
     */
    @Transactional
    public StaffOrderResponse markPurchased(UUID staffId, Set<Role> roles, UUID itemId, BigDecimal actualPriceCny,
                                            String taobaoOrderNumber, String chinaTrackingNumber, boolean priceChanged) {
        OrderItem item = loadItem(itemId);
        Order order = item.getOrder();
        Set<Actor> actors = requireBuyerOf(order, staffId, roles);
        requirePending(item);
        if (priceChanged) {
            workflow.check(order, OrderStatus.PRICE_CHANGED, actors);
        }
        item.setActualPriceCny(actualPriceCny);
        item.setTaobaoOrderNumber(blankToNull(taobaoOrderNumber));
        item.setChinaTrackingNumber(blankToNull(chinaTrackingNumber));
        if (priceChanged) {
            item.setStatus(OrderItemStatus.PRICE_CHANGED);
            workflow.change(order, OrderStatus.PRICE_CHANGED, actors, staffId,
                    "Narx oshdi: " + item.getPriceCny() + " -> " + actualPriceCny + " CNY");
        } else {
            item.setStatus(OrderItemStatus.PURCHASED);
            item.setPurchasedAt(Instant.now(clock));
            workflow.finishPurchasingIfDone(order);
        }
        return staffOrders.get(order.getId());
    }

    /** The item cannot be bought: the item and the order go to OUT_OF_STOCK and the customer decides. */
    @Transactional
    public StaffOrderResponse markOutOfStock(UUID staffId, Set<Role> roles, UUID itemId, String comment) {
        OrderItem item = loadItem(itemId);
        Order order = item.getOrder();
        Set<Actor> actors = requireBuyerOf(order, staffId, roles);
        requirePending(item);
        workflow.check(order, OrderStatus.OUT_OF_STOCK, actors);
        item.setStatus(OrderItemStatus.OUT_OF_STOCK);
        item.setStaffComment(blankToNull(comment));
        workflow.change(order, OrderStatus.OUT_OF_STOCK, actors, staffId, comment);
        return staffOrders.get(order.getId());
    }

    /** While PURCHASING, by the order's buyer (or an ADMIN). Returns the caller's staff actors. */
    private static Set<Actor> requireBuyerOf(Order order, UUID staffId, Set<Role> roles) {
        if (order.getStatus() != OrderStatus.PURCHASING) {
            throw new BusinessException("ORDER_NOT_PURCHASING", "error.procurement.notPurchasing");
        }
        if (!roles.contains(Role.ADMIN) && !staffId.equals(order.getBuyerId())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "ORDER_TAKEN_BY_OTHER_BUYER", "error.procurement.otherBuyer");
        }
        return Actor.staffOf(roles);
    }

    private static void requirePending(OrderItem item) {
        if (item.getStatus() != OrderItemStatus.PENDING) {
            throw new BusinessException("ITEM_ALREADY_HANDLED", "error.procurement.itemHandled", item.getStatus());
        }
    }

    private ProcurementQueueOrder queueOrder(Order order, Instant now) {
        List<ProcurementQueueOrder.Item> lines = order.getItems().stream()
                .map(item -> new ProcurementQueueOrder.Item(item.getId(), item.getExternalProductId(),
                        "https://item.taobao.com/item.htm?id=" + item.getExternalProductId(), item.getSkuId(),
                        json.readValue(item.getSkuProperties(), PROPERTIES), item.getQuantity(), item.getPriceCny(),
                        item.getTitle(), item.getImageUrl(), item.getShopName(), item.getComment()))
                .toList();
        long minutes = order.getPaidAt() == null ? 0 : Duration.between(order.getPaidAt(), now).toMinutes();
        return new ProcurementQueueOrder(order.getId(), order.getNumber(), order.getPaidAt(), minutes,
                order.getCustomerComment(), lines);
    }

    private OrderItem loadItem(UUID itemId) {
        return items.findById(itemId)
                .orElseThrow(() -> new NotFoundException("ORDER_ITEM_NOT_FOUND", "error.order.itemNotFound"));
    }

    private Order load(UUID orderId) {
        return orders.findById(orderId)
                .orElseThrow(() -> new NotFoundException("ORDER_NOT_FOUND", "error.order.notFound"));
    }

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text.trim();
    }
}
