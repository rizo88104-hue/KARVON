package uz.karvon.order.application;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.karvon.common.api.PageResponse;
import uz.karvon.common.application.BusinessException;
import uz.karvon.common.application.NotFoundException;
import uz.karvon.common.domain.PhoneNumbers;
import uz.karvon.order.api.dto.OrderCommentResponse;
import uz.karvon.order.api.dto.StaffOrderResponse;
import uz.karvon.order.api.dto.StaffOrderSummaryResponse;
import uz.karvon.order.api.dto.StatusChangeRequest;
import uz.karvon.order.api.dto.UpdateOrderItemRequest;
import uz.karvon.order.domain.Actor;
import uz.karvon.order.domain.Order;
import uz.karvon.order.domain.OrderComment;
import uz.karvon.order.domain.OrderItem;
import uz.karvon.order.domain.OrderItemStatus;
import uz.karvon.order.domain.OrderStateMachine;
import uz.karvon.order.domain.OrderStatus;
import uz.karvon.order.infrastructure.OrderCommentRepository;
import uz.karvon.order.infrastructure.OrderRepository;
import uz.karvon.order.infrastructure.OrderStatusHistoryRepository;
import uz.karvon.pricing.application.PricingService;
import uz.karvon.pricing.domain.ShippingAdjustment;
import uz.karvon.user.api.dto.UserResponse;
import uz.karvon.user.application.UserService;
import uz.karvon.user.domain.Role;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Orders for staff (buyer, operator, admin). Status changes are checked by the state machine against the
 * caller roles; this service adds the data each step needs.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StaffOrderService {

    private static final Set<Actor> SYSTEM = Set.of(Actor.SYSTEM);

    private final OrderRepository orders;
    private final OrderStatusHistoryRepository history;
    private final OrderCommentRepository comments;
    private final OrderWorkflow workflow;
    private final OrderViews views;
    private final UserService users;
    private final PricingService pricing;
    private final Clock clock;

    /** All filters are optional; {@code from}/{@code to} limit the creation time; {@code number} matches any part. */
    public PageResponse<StaffOrderSummaryResponse> search(OrderStatus status, Instant from, Instant to, String phone,
                                                          String number, int page, int size) {
        UUID customerId = null;
        if (phone != null && !phone.isBlank()) {
            Optional<UserResponse> customer = PhoneNumbers.normalize(phone).flatMap(users::findByPhone);
            if (customer.isEmpty()) {
                return new PageResponse<>(List.of(), page, size, 0, 0);
            }
            customerId = customer.get().id();
        }
        UUID customer = customerId;
        Specification<Order> filter = (root, query, cb) -> {
            List<Predicate> where = new ArrayList<>();
            if (status != null) {
                where.add(cb.equal(root.get("status"), status));
            }
            if (from != null) {
                where.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            }
            if (to != null) {
                where.add(cb.lessThan(root.get("createdAt"), to));
            }
            if (customer != null) {
                where.add(cb.equal(root.get("customerId"), customer));
            }
            if (number != null && !number.isBlank()) {
                where.add(cb.like(root.get("number"), "%" + number.trim().toUpperCase(Locale.ROOT) + "%"));
            }
            return cb.and(where.toArray(Predicate[]::new));
        };
        var found = orders.findAll(filter, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        Map<UUID, String> phones = new HashMap<>();
        return PageResponse.of(found, order -> new StaffOrderSummaryResponse(order.getId(), order.getNumber(),
                order.getStatus(), order.getCustomerId(),
                phones.computeIfAbsent(order.getCustomerId(), id -> users.findById(id).map(UserResponse::phone).orElse(null)),
                order.getTotalUzs(), order.amountDueUzs(), order.getItems().size(), order.getCreatedAt()));
    }

    public StaffOrderResponse get(UUID orderId) {
        return response(load(orderId));
    }

    /**
     * Moves the order to {@code request.toStatus()} as one of the caller roles. Weighing (AT_WAREHOUSE) computes the
     * final shipping with the settings the order was priced with: more than prepaid moves the order on to
     * AWAITING_SHIPPING_PAYMENT, less is refunded to the wallet.
     */
    @Transactional
    public StaffOrderResponse changeStatus(UUID staffId, Set<Role> roles, UUID orderId, StatusChangeRequest request) {
        Order order = load(orderId);
        Set<Actor> actors = Actor.staffOf(roles);
        OrderStatus to = request.toStatus();
        workflow.check(order, to, actors);
        switch (to) {
            case OUT_OF_STOCK -> markItems(order, request, OrderItemStatus.OUT_OF_STOCK);
            case PRICE_CHANGED -> markItems(order, request, OrderItemStatus.PRICE_CHANGED);
            case AT_WAREHOUSE -> weigh(order, request.realWeightKg());
            case IN_TRANSIT -> {
                if (order.amountDueUzs().signum() > 0) {
                    throw new BusinessException(HttpStatus.CONFLICT, "SHIPPING_DIFFERENCE_UNPAID",
                            "error.order.shippingDifferenceUnpaid", order.amountDueUzs());
                }
                if (request.internationalTrackingNumber() != null && !request.internationalTrackingNumber().isBlank()) {
                    order.setInternationalTrackingNumber(request.internationalTrackingNumber().trim());
                }
            }
            default -> {
            }
        }
        workflow.change(order, to, actors, staffId, request.comment());
        if (to == OrderStatus.AT_WAREHOUSE) {
            BigDecimal difference = order.getShippingDifferenceUzs();
            if (difference.signum() > 0) {
                workflow.change(order, OrderStatus.AWAITING_SHIPPING_PAYMENT, SYSTEM, null, null);
            } else if (difference.signum() < 0) {
                workflow.refund(order, difference.negate(), RefundReason.SHIPPING_OVERPAID);
            }
        }
        return response(order);
    }

    /** Buyer data of an item. */
    @Transactional
    public StaffOrderResponse updateItem(UUID orderId, UUID itemId, UpdateOrderItemRequest request) {
        Order order = load(orderId);
        OrderItem item = item(order, itemId);
        if (request.actualPriceCny() != null) {
            item.setActualPriceCny(request.actualPriceCny());
        }
        if (request.taobaoOrderNumber() != null) {
            item.setTaobaoOrderNumber(blankToNull(request.taobaoOrderNumber()));
        }
        if (request.chinaTrackingNumber() != null) {
            item.setChinaTrackingNumber(blankToNull(request.chinaTrackingNumber()));
        }
        return response(order);
    }

    @Transactional
    public OrderCommentResponse addComment(UUID staffId, UUID orderId, String text) {
        Order order = load(orderId);
        return views.comment(comments.save(new OrderComment(order.getId(), staffId, text.trim(), Instant.now(clock))));
    }

    /**
     * ADMIN: the customer paid the shipping difference outside the app (until payments come). Records the payment
     * and an internal note; the operator can then send the parcel (IN_TRANSIT).
     */
    @Transactional
    public StaffOrderResponse recordShippingPayment(UUID adminId, UUID orderId) {
        Order order = load(orderId);
        if (order.getStatus() != OrderStatus.AWAITING_SHIPPING_PAYMENT) {
            throw new BusinessException("NO_SHIPPING_DIFFERENCE_DUE", "error.order.noShippingDifferenceDue");
        }
        BigDecimal due = order.amountDueUzs();
        order.setPaidUzs(order.getPaidUzs().add(due));
        comments.save(new OrderComment(order.getId(), adminId, "Shipping difference paid manually: " + due + " UZS",
                Instant.now(clock)));
        return response(order);
    }

    private void markItems(Order order, StatusChangeRequest request, OrderItemStatus status) {
        if (request.items() == null || request.items().isEmpty()) {
            throw new BusinessException("ORDER_ITEMS_REQUIRED", "error.order.itemsRequired");
        }
        for (StatusChangeRequest.ItemChange change : request.items()) {
            OrderItem item = item(order, change.itemId());
            if (status == OrderItemStatus.PRICE_CHANGED) {
                if (change.actualPriceCny() == null) {
                    throw new BusinessException("ACTUAL_PRICE_REQUIRED", "error.order.actualPriceRequired");
                }
                item.setActualPriceCny(change.actualPriceCny());
            }
            item.setStatus(status);
        }
    }

    /** Final shipping for the items that are being sent (out-of-stock items were refunded). */
    private void weigh(Order order, BigDecimal realWeightKg) {
        if (realWeightKg == null) {
            throw new BusinessException("WEIGHT_REQUIRED", "error.order.weightRequired");
        }
        List<OrderItem> sent = order.getItems().stream()
                .filter(item -> item.getStatus() != OrderItemStatus.OUT_OF_STOCK).toList();
        int quantity = sent.stream().mapToInt(OrderItem::getQuantity).sum();
        BigDecimal prepaid = sent.stream().map(OrderItem::getShippingUzs).reduce(BigDecimal.ZERO, BigDecimal::add);
        ShippingAdjustment adjustment = pricing.shippingAdjustment(order.getPricingSettingsId(), prepaid, realWeightKg, quantity);
        order.setRealWeightKg(realWeightKg);
        order.setFinalShippingUzs(adjustment.realShippingUzs());
        order.setShippingDifferenceUzs(adjustment.differenceUzs());
    }

    private StaffOrderResponse response(Order order) {
        var entries = history.findByOrderIdOrderByCreatedAtAsc(order.getId());
        return new StaffOrderResponse(views.detail(order, entries, true),
                users.findById(order.getCustomerId()).orElse(null),
                entries.stream().map(views::history).toList(),
                comments.findByOrderIdOrderByCreatedAtAsc(order.getId()).stream().map(views::comment).toList(),
                OrderStateMachine.next(order.getStatus()));
    }

    private static OrderItem item(Order order, UUID itemId) {
        return order.getItems().stream().filter(item -> item.getId().equals(itemId)).findFirst()
                .orElseThrow(() -> new NotFoundException("ORDER_ITEM_NOT_FOUND", "error.order.itemNotFound"));
    }

    private Order load(UUID orderId) {
        return orders.findById(orderId)
                .orElseThrow(() -> new NotFoundException("ORDER_NOT_FOUND", "error.order.notFound"));
    }

    private static String blankToNull(String text) {
        return text.isBlank() ? null : text.trim();
    }
}
