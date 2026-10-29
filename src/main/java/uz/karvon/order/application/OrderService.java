package uz.karvon.order.application;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;
import uz.karvon.cart.api.dto.CartItemResponse;
import uz.karvon.cart.api.dto.CartResponse;
import uz.karvon.cart.application.CartService;
import uz.karvon.common.api.PageResponse;
import uz.karvon.common.application.BusinessException;
import uz.karvon.common.application.NotFoundException;
import uz.karvon.order.api.dto.CartChange;
import uz.karvon.order.api.dto.CreateOrderRequest;
import uz.karvon.order.api.dto.DecisionRequest;
import uz.karvon.order.api.dto.OrderDetailResponse;
import uz.karvon.order.api.dto.OrderSummaryResponse;
import uz.karvon.order.domain.Actor;
import uz.karvon.order.domain.Order;
import uz.karvon.order.domain.OrderItem;
import uz.karvon.order.domain.OrderItemStatus;
import uz.karvon.order.domain.OrderStatus;
import uz.karvon.order.domain.OrderTab;
import uz.karvon.order.infrastructure.OrderRepository;
import uz.karvon.order.infrastructure.OrderStatusHistoryRepository;
import uz.karvon.translation.domain.Lang;
import uz.karvon.user.api.dto.AddressResponse;
import uz.karvon.user.application.AddressService;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Orders for customers, and the automatic cancel of unpaid orders.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderService {

    private static final Set<Actor> CUSTOMER = Set.of(Actor.CUSTOMER);
    private static final Set<Actor> SYSTEM = Set.of(Actor.SYSTEM);

    private final OrderRepository orders;
    private final OrderStatusHistoryRepository history;
    private final OrderWorkflow workflow;
    private final OrderViews views;
    private final CartService cart;
    private final AddressService addresses;
    private final JsonMapper json;
    private final Clock clock;

    @Value("${karvon.orders.payment-timeout:30m}")
    private Duration paymentTimeout;

    /**
     * Orders the selected cart lines. Prices are refreshed first; if a selected line changed (price, stock, blocked)
     * nothing is ordered and CART_CHANGED (409) lists the changes. The refreshed cart is kept, so ordering again
     * goes through. Ordered lines leave the cart.
     */
    @Transactional(noRollbackFor = CartChangedException.class)
    public OrderDetailResponse create(UUID customerId, CreateOrderRequest request, Lang lang) {
        AddressResponse address = null;
        if (request.addressId() != null) {
            address = addresses.get(customerId, request.addressId());
        } else if (request.deliveryMethod().needsAddress()) {
            throw new BusinessException("ADDRESS_REQUIRED", "error.order.addressRequired");
        }
        List<CartItemResponse> selectedBefore = lines(cart.get(customerId)).stream()
                .filter(CartItemResponse::selected).toList();
        if (selectedBefore.isEmpty()) {
            throw new BusinessException("CART_EMPTY", "error.order.cartEmpty");
        }
        CartResponse refreshed = cart.refreshPrices(customerId, lang);
        Map<UUID, CartItemResponse> after = lines(refreshed).stream()
                .collect(Collectors.toMap(CartItemResponse::id, Function.identity()));
        List<CartChange> changes = changes(selectedBefore, after);
        if (!changes.isEmpty()) {
            throw new CartChangedException(changes);
        }

        Order order = new Order("KRV-%06d".formatted(orders.nextNumber()), customerId);
        order.setDeliveryMethod(request.deliveryMethod());
        order.setPaymentMethod(request.paymentMethod());
        order.setAddressSnapshot(address == null ? null : json.writeValueAsString(address));
        order.setCustomerComment(request.comment() == null || request.comment().isBlank() ? null : request.comment().trim());
        order.setPricingSettingsId(refreshed.settingsId());
        BigDecimal goods = BigDecimal.ZERO;
        BigDecimal fee = BigDecimal.ZERO;
        BigDecimal shipping = BigDecimal.ZERO;
        for (CartItemResponse line : selectedBefore) {
            CartItemResponse current = after.get(line.id());
            order.addItem(item(current));
            goods = goods.add(current.price().goodsUzs());
            fee = fee.add(current.price().serviceFeeUzs());
            shipping = shipping.add(current.price().shippingUzs());
        }
        order.setGoodsUzs(goods);
        order.setServiceFeeUzs(fee);
        order.setPrepaidShippingUzs(shipping);
        order.setTotalUzs(goods.add(fee).add(shipping));
        orders.save(order);
        workflow.created(order, customerId);
        cart.removeItems(customerId, selectedBefore.stream().map(CartItemResponse::id).toList());
        return views.detail(order, history.findByOrderIdOrderByCreatedAtAsc(order.getId()), false);
    }

    public PageResponse<OrderSummaryResponse> list(UUID customerId, OrderTab tab, int page, int size) {
        return PageResponse.of(orders.findAll(
                (root, query, cb) -> cb.and(cb.equal(root.get("customerId"), customerId), root.get("status").in(tab.statuses())),
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))), views::summary);
    }

    public OrderDetailResponse get(UUID customerId, String number) {
        Order order = load(customerId, number);
        return views.detail(order, history.findByOrderIdOrderByCreatedAtAsc(order.getId()), false);
    }

    /** Only while waiting for payment, or paid but not yet being bought; a paid order is refunded. */
    @Transactional
    public OrderDetailResponse cancel(UUID customerId, String number, String reason) {
        Order order = load(customerId, number);
        if (order.getStatus() != OrderStatus.AWAITING_PAYMENT && order.getStatus() != OrderStatus.PAID) {
            throw new BusinessException("INVALID_STATUS_TRANSITION", "error.order.invalidTransition",
                    order.getStatus(), OrderStatus.CANCELLED);
        }
        workflow.change(order, OrderStatus.CANCELLED, CUSTOMER, customerId, reason);
        return views.detail(order, history.findByOrderIdOrderByCreatedAtAsc(order.getId()), false);
    }

    /**
     * The answer to OUT_OF_STOCK or PRICE_CHANGED. REFUND cancels the order with a full refund. ACCEPT continues
     * purchasing: out-of-stock items are refunded (not possible when every item is out of stock); new prices
     * are taken as they are.
     */
    @Transactional
    public OrderDetailResponse decide(UUID customerId, String number, DecisionRequest.Action action) {
        Order order = load(customerId, number);
        OrderStatus status = order.getStatus();
        if (status != OrderStatus.OUT_OF_STOCK && status != OrderStatus.PRICE_CHANGED) {
            throw new BusinessException("DECISION_NOT_EXPECTED", "error.order.decisionNotExpected");
        }
        if (action == DecisionRequest.Action.REFUND) {
            workflow.change(order, OrderStatus.CANCELLED, CUSTOMER, customerId, null);
        } else if (status == OrderStatus.OUT_OF_STOCK) {
            List<OrderItem> missing = order.getItems().stream()
                    .filter(item -> item.getStatus() == OrderItemStatus.OUT_OF_STOCK).toList();
            if (missing.size() == order.getItems().size()) {
                throw new BusinessException("ALL_ITEMS_OUT_OF_STOCK", "error.order.allItemsOutOfStock");
            }
            workflow.check(order, OrderStatus.PURCHASING, CUSTOMER);
            BigDecimal missingTotal = missing.stream().map(OrderItem::getTotalUzs).reduce(BigDecimal.ZERO, BigDecimal::add);
            workflow.refund(order, missingTotal.min(order.refundableUzs()), RefundReason.ITEMS_OUT_OF_STOCK);
            workflow.change(order, OrderStatus.PURCHASING, CUSTOMER, customerId, null);
        } else {
            order.getItems().stream()
                    .filter(item -> item.getStatus() == OrderItemStatus.PRICE_CHANGED)
                    .forEach(item -> item.setStatus(OrderItemStatus.OK));
            workflow.change(order, OrderStatus.PURCHASING, CUSTOMER, customerId, null);
        }
        return views.detail(order, history.findByOrderIdOrderByCreatedAtAsc(order.getId()), false);
    }

    /** Orders still waiting for payment after the payment timeout (30 minutes). */
    public List<UUID> unpaidExpired() {
        return orders.findIdsByStatusCreatedBefore(OrderStatus.AWAITING_PAYMENT, Instant.now(clock).minus(paymentTimeout));
    }

    /** Cancels the order if it is still unpaid and expired; returns whether it did. */
    @Transactional
    public boolean cancelIfUnpaid(UUID orderId) {
        Order order = orders.findById(orderId).orElse(null);
        if (order == null || order.getStatus() != OrderStatus.AWAITING_PAYMENT
                || !order.getCreatedAt().isBefore(Instant.now(clock).minus(paymentTimeout))) {
            return false;
        }
        workflow.change(order, OrderStatus.CANCELLED, SYSTEM, null, "To'lov muddati tugadi");
        return true;
    }

    private static List<CartChange> changes(List<CartItemResponse> selectedBefore, Map<UUID, CartItemResponse> after) {
        List<CartChange> changes = new ArrayList<>();
        for (CartItemResponse before : selectedBefore) {
            CartItemResponse now = after.get(before.id());
            if (now == null) {
                changes.add(new CartChange(before.id(), before.title(), CartChange.Type.REMOVED, before.priceCny(), null));
            } else if (now.blocked()) {
                changes.add(new CartChange(now.id(), now.title(), CartChange.Type.BLOCKED, now.priceCny(), null));
            } else if (now.outOfStock()) {
                changes.add(new CartChange(now.id(), now.title(), CartChange.Type.OUT_OF_STOCK, now.priceCny(), null));
            } else if (now.priceChanged()) {
                changes.add(new CartChange(now.id(), now.title(), CartChange.Type.PRICE_CHANGED, now.previousPriceCny(),
                        now.priceCny()));
            }
        }
        return changes;
    }

    private static List<CartItemResponse> lines(CartResponse cart) {
        return cart.shops().stream().flatMap(shop -> shop.items().stream()).toList();
    }

    private OrderItem item(CartItemResponse line) {
        OrderItem item = new OrderItem();
        item.setExternalProductId(line.externalProductId());
        item.setSkuId(line.skuId());
        item.setTitle(line.title());
        item.setImageUrl(line.imageUrl());
        item.setSkuProperties(json.writeValueAsString(line.skuProperties()));
        item.setShopName(line.shopName());
        item.setPriceCny(line.priceCny());
        item.setChinaShippingCny(line.chinaShippingCny());
        item.setWeightKg(line.weightKg());
        item.setQuantity(line.quantity());
        item.setComment(line.comment());
        item.setGoodsUzs(line.price().goodsUzs());
        item.setServiceFeeUzs(line.price().serviceFeeUzs());
        item.setShippingUzs(line.price().shippingUzs());
        item.setTotalUzs(line.price().totalUzs());
        item.setChargeableWeightKg(line.price().chargeableWeightKg());
        return item;
    }

    private Order load(UUID customerId, String number) {
        return orders.findByNumberAndCustomerId(number, customerId)
                .orElseThrow(() -> new NotFoundException("ORDER_NOT_FOUND", "error.order.notFound"));
    }
}
