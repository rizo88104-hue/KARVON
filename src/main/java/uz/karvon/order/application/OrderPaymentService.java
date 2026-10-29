package uz.karvon.order.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.karvon.common.application.BusinessException;
import uz.karvon.common.application.NotFoundException;
import uz.karvon.order.domain.Actor;
import uz.karvon.order.domain.Order;
import uz.karvon.order.domain.OrderStatus;
import uz.karvon.order.infrastructure.OrderRepository;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The order side of payments, used by the payment module (SYSTEM actor). Runs inside the caller's transaction,
 * so the payment record and the order change are saved together.
 * <ul>
 *   <li>ORDER can be paid while AWAITING_PAYMENT; the amount is the order total.</li>
 *   <li>SHIPPING_DIFFERENCE can be paid while AWAITING_SHIPPING_PAYMENT; the amount is what is still due.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderPaymentService {

    private static final Set<Actor> SYSTEM = Set.of(Actor.SYSTEM);

    private final OrderRepository orders;
    private final OrderWorkflow workflow;

    /** By order number, for provider callbacks (no customer check). Empty if there is no such order. */
    public Optional<PayableOrder> find(String number, PaymentPurpose purpose) {
        return orders.findByNumber(number).map(order -> payable(order, purpose));
    }

    /** For the customer starting a payment: the order must be theirs. */
    public PayableOrder forCustomer(UUID customerId, String number, PaymentPurpose purpose) {
        return orders.findByNumberAndCustomerId(number, customerId).map(order -> payable(order, purpose))
                .orElseThrow(() -> new NotFoundException("ORDER_NOT_FOUND", "error.order.notFound"));
    }

    public PayableOrder get(UUID orderId, PaymentPurpose purpose) {
        return payable(load(orderId), purpose);
    }

    /** The money arrived. ORDER moves the order to PAID; SHIPPING_DIFFERENCE records the paid difference. */
    @Transactional
    public void paymentReceived(UUID orderId, PaymentPurpose purpose, BigDecimal amountUzs) {
        Order order = load(orderId);
        PayableOrder payable = payable(order, purpose);
        if (!payable.payable() || payable.amountDueUzs().compareTo(amountUzs) != 0) {
            throw new BusinessException("ORDER_NOT_PAYABLE", "error.payment.orderNotPayable");
        }
        if (purpose == PaymentPurpose.ORDER) {
            workflow.change(order, OrderStatus.PAID, SYSTEM, null, null);
        } else {
            order.setPaidUzs(order.getPaidUzs().add(amountUzs));
        }
    }

    /** Whether the provider may still take this payment back (the goods were not bought / the parcel not sent). */
    public boolean canReverse(UUID orderId, PaymentPurpose purpose) {
        OrderStatus status = load(orderId).getStatus();
        return purpose == PaymentPurpose.ORDER ? status == OrderStatus.PAID : status == OrderStatus.AWAITING_SHIPPING_PAYMENT;
    }

    /**
     * The provider returned the money to the card. An ORDER payment cancels the order (nothing goes to the wallet,
     * the card already got it back); a shipping payment makes the difference due again.
     */
    @Transactional
    public void paymentReversed(UUID orderId, PaymentPurpose purpose, BigDecimal amountUzs, String comment) {
        if (!canReverse(orderId, purpose)) {
            throw new BusinessException("PAYMENT_NOT_REVERSIBLE", "error.payment.notReversible");
        }
        Order order = load(orderId);
        order.setPaidUzs(order.getPaidUzs().subtract(amountUzs).max(BigDecimal.ZERO));
        if (purpose == PaymentPurpose.ORDER) {
            workflow.change(order, OrderStatus.CANCELLED, SYSTEM, null, comment);
        }
    }

    private static PayableOrder payable(Order order, PaymentPurpose purpose) {
        PayableOrder.State state = state(order, purpose);
        return new PayableOrder(order.getId(), order.getNumber(), order.getCustomerId(), order.getStatus(), purpose,
                state, state == PayableOrder.State.PAYABLE ? order.amountDueUzs() : BigDecimal.ZERO);
    }

    private static PayableOrder.State state(Order order, PaymentPurpose purpose) {
        OrderStatus status = order.getStatus();
        if (status == OrderStatus.CANCELLED) {
            return PayableOrder.State.CANCELLED;
        }
        if (purpose == PaymentPurpose.ORDER) {
            return status == OrderStatus.AWAITING_PAYMENT ? PayableOrder.State.PAYABLE : PayableOrder.State.ALREADY_PAID;
        }
        if (status == OrderStatus.AWAITING_SHIPPING_PAYMENT) {
            return order.amountDueUzs().signum() > 0 ? PayableOrder.State.PAYABLE : PayableOrder.State.ALREADY_PAID;
        }
        // After the shipping step a positive difference was paid; before weighing nothing is due yet.
        boolean afterShipping = status == OrderStatus.IN_TRANSIT || status == OrderStatus.ARRIVED
                || status == OrderStatus.DELIVERED;
        return afterShipping && order.getShippingDifferenceUzs().signum() > 0
                ? PayableOrder.State.ALREADY_PAID : PayableOrder.State.NOT_DUE;
    }

    private Order load(UUID orderId) {
        return orders.findById(orderId)
                .orElseThrow(() -> new NotFoundException("ORDER_NOT_FOUND", "error.order.notFound"));
    }
}
