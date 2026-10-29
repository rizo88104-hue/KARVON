package uz.karvon.order.api;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import uz.karvon.AbstractIntegrationTest;
import uz.karvon.TestLogins;
import uz.karvon.catalog.domain.ProductDetail;
import uz.karvon.catalog.domain.Sku;
import uz.karvon.catalog.infrastructure.FakeTaobaoProvider;
import uz.karvon.order.application.OrderRefundRequestedEvent;
import uz.karvon.order.application.OrderStatusChangedEvent;
import uz.karvon.order.application.RefundReason;
import uz.karvon.order.application.UnpaidOrderCancelJob;
import uz.karvon.order.domain.OrderStatus;
import uz.karvon.translation.infrastructure.FakeTranslator;

import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static uz.karvon.TestLogins.accessToken;
import static uz.karvon.TestLogins.bearer;
import static uz.karvon.TestLogins.login;
import static uz.karvon.TestLogins.newPhone;
import static uz.karvon.TestLogins.read;

/**
 * Orders with the fake provider (a spy, as in the cart and catalog tests, so the Spring context is shared).
 * The cart line used: 女士小白鞋 SKU 001, 89 CNY, 0.8 kg, shop 小白鞋工厂店:
 * 158 400 goods + 15 800 fee + 64 000 shipping = 238 200 UZS.
 */
@RecordApplicationEvents
class OrderIntegrationTest extends AbstractIntegrationTest {

    private static final String WHITE_SHOES = "680000002234";
    private static final String SKU = WHITE_SHOES + "001";

    @Autowired
    private MockMvcTester mvc;

    @MockitoSpyBean
    private FakeTaobaoProvider provider;

    @MockitoSpyBean
    private FakeTranslator translator;

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private UnpaidOrderCancelJob cancelJob;

    @Autowired
    private ApplicationEvents events;

    private String customer;
    private String admin;
    private String addressId;

    @BeforeEach
    void setUp() {
        customer = accessToken(mvc, newPhone());
        admin = TestLogins.adminToken(mvc);
        addressId = read(as(customer, mvc.post().uri("/api/v1/me/addresses").contentType(MediaType.APPLICATION_JSON)
                .content("{\"region\":\"Toshkent\",\"city\":\"Toshkent\",\"street\":\"Navoiy 5\",\"house\":\"7\"}")), "$.id");
    }

    @AfterEach
    void forgetChangedProducts() {
        redis.delete("catalog:product:" + WHITE_SHOES);
    }

    @Test
    void orderIsMadeFromSelectedCartLinesOnly() {
        addToCart(SKU, 1);
        String otherLine = itemId(addToCart("680000001117001", 1), "$.shops[*].items[*]", "680000001117001");
        as(customer, mvc.patch().uri("/api/v1/cart/items/{id}", otherLine).contentType(MediaType.APPLICATION_JSON)
                .content("{\"selected\":false}"));

        MvcTestResult order = createOrder("POST", addressId);

        assertThat(order).hasStatus(HttpStatus.CREATED);
        assertThat(order).bodyJson().extractingPath("$.number").asString().matches("KRV-\\d{6}");
        assertThat(order).bodyJson().extractingPath("$.status").isEqualTo("AWAITING_PAYMENT");
        assertThat(order).bodyJson().extractingPath("$.items.length()").isEqualTo(1);
        assertThat(order).bodyJson().extractingPath("$.items[0].skuId").isEqualTo(SKU);
        assertThat(order).bodyJson().extractingPath("$.items[0].totalUzs").isEqualTo(238200);
        assertThat(order).bodyJson().extractingPath("$.goodsUzs").isEqualTo(158400);
        assertThat(order).bodyJson().extractingPath("$.serviceFeeUzs").isEqualTo(15800);
        assertThat(order).bodyJson().extractingPath("$.prepaidShippingUzs").isEqualTo(64000);
        assertThat(order).bodyJson().extractingPath("$.totalUzs").isEqualTo(238200);
        assertThat(order).bodyJson().extractingPath("$.amountDueUzs").isEqualTo(238200);
        assertThat(order).bodyJson().extractingPath("$.address.street").isEqualTo("Navoiy 5");
        assertThat(order).bodyJson().extractingPath("$.paymentDeadline").isNotNull();
        assertThat(order).bodyJson().extractingPath("$.timeline[0].state").isEqualTo("CURRENT");
        assertThat(order).bodyJson().extractingPath("$.timeline[0].at").isNotNull();

        // The ordered line left the cart, the unselected one stayed.
        MvcTestResult cart = as(customer, mvc.get().uri("/api/v1/cart"));
        assertThat(cart).bodyJson().extractingPath("$.itemCount").isEqualTo(1);
        assertThat(cart).bodyJson().extractingPath("$.shops[0].items[0].id").isEqualTo(otherLine);
        assertThat(events.stream(OrderStatusChangedEvent.class)).anyMatch(e -> e.from() == null
                && e.to() == OrderStatus.AWAITING_PAYMENT);
    }

    @Test
    void priceChangeReturns409AndTheSecondTryGoesThrough() {
        addToCart(SKU, 1);
        ProductDetail original = provider.getProduct(WHITE_SHOES);
        doReturn(withPrice(original, SKU, new BigDecimal("99.00"))).when(provider).getProduct(WHITE_SHOES);

        MvcTestResult changed = createOrder("POST", addressId);

        assertThat(changed).hasStatus(HttpStatus.CONFLICT);
        assertThat(changed).bodyJson().extractingPath("$.code").isEqualTo("CART_CHANGED");
        assertThat(changed).bodyJson().extractingPath("$.changes[0].change").isEqualTo("PRICE_CHANGED");
        assertThat(changed).bodyJson().extractingPath("$.changes[0].oldPriceCny").asNumber()
                .satisfies(n -> assertThat(n.doubleValue()).isEqualTo(89.0));
        assertThat(changed).bodyJson().extractingPath("$.changes[0].newPriceCny").asNumber()
                .satisfies(n -> assertThat(n.doubleValue()).isEqualTo(99.0));
        assertThat(as(customer, mvc.get().uri("/api/v1/orders"))).bodyJson().extractingPath("$.totalElements").isEqualTo(0);

        MvcTestResult again = createOrder("POST", addressId);

        assertThat(again).hasStatus(HttpStatus.CREATED);
        // 99 CNY: 176 200 + 17 600 + 64 000
        assertThat(again).bodyJson().extractingPath("$.totalUzs").isEqualTo(257800);
    }

    @Test
    void creationChecks() {
        assertThat(createOrder("POST", addressId)).bodyJson().extractingPath("$.code").isEqualTo("CART_EMPTY");
        addToCart(SKU, 1);
        assertThat(createOrder("COURIER", null)).bodyJson().extractingPath("$.code").isEqualTo("ADDRESS_REQUIRED");
        assertThat(createOrder("POST", "00000000-0000-0000-0000-000000000000"))
                .hasStatus(HttpStatus.NOT_FOUND);

        MvcTestResult pickup = createOrder("CARGO_PICKUP", null);
        assertThat(pickup).hasStatus(HttpStatus.CREATED);
        assertThat(pickup).bodyJson().extractingPath("$.address").isNull();
    }

    @Test
    void adminMovesTheOrderThroughEveryStatus() {
        String number = orderOneItem();
        String id = orderId(number);

        assertThat(status(admin, id, "{\"toStatus\":\"PAID\"}")).bodyJson().extractingPath("$.order.paidUzs").isEqualTo(238200);
        status(admin, id, "{\"toStatus\":\"PURCHASING\"}");
        status(admin, id, "{\"toStatus\":\"PURCHASED\"}");

        MvcTestResult noWeight = status(admin, id, "{\"toStatus\":\"AT_WAREHOUSE\"}");
        assertThat(noWeight).bodyJson().extractingPath("$.code").isEqualTo("WEIGHT_REQUIRED");

        // 1.2 kg instead of 0.8 kg: 96 000 instead of 64 000, the customer pays 32 000 more.
        MvcTestResult weighed = status(admin, id, "{\"toStatus\":\"AT_WAREHOUSE\",\"realWeightKg\":1.2}");
        assertThat(weighed).bodyJson().extractingPath("$.order.status").isEqualTo("AWAITING_SHIPPING_PAYMENT");
        assertThat(weighed).bodyJson().extractingPath("$.order.finalShippingUzs").isEqualTo(96000);
        assertThat(weighed).bodyJson().extractingPath("$.order.shippingDifferenceUzs").isEqualTo(32000);
        assertThat(weighed).bodyJson().extractingPath("$.order.amountDueUzs").isEqualTo(32000);

        MvcTestResult unpaid = status(admin, id, "{\"toStatus\":\"IN_TRANSIT\"}");
        assertThat(unpaid).hasStatus(HttpStatus.CONFLICT);
        assertThat(unpaid).bodyJson().extractingPath("$.code").isEqualTo("SHIPPING_DIFFERENCE_UNPAID");

        assertThat(as(admin, mvc.post().uri("/api/v1/admin/orders/{id}/shipping-payment", id)))
                .bodyJson().extractingPath("$.order.amountDueUzs").isEqualTo(0);
        assertThat(status(admin, id, "{\"toStatus\":\"IN_TRANSIT\",\"internationalTrackingNumber\":\"CN123456789UZ\"}"))
                .bodyJson().extractingPath("$.order.internationalTrackingNumber").isEqualTo("CN123456789UZ");
        status(admin, id, "{\"toStatus\":\"ARRIVED\"}");
        MvcTestResult delivered = status(admin, id, "{\"toStatus\":\"DELIVERED\",\"comment\":\"Qo'liga berildi\"}");

        assertThat(delivered).hasStatusOk();
        assertThat(delivered).bodyJson().extractingPath("$.order.status").isEqualTo("DELIVERED");
        assertThat(delivered).bodyJson().extractingPath("$.order.deliveredAt").isNotNull();
        assertThat(delivered).bodyJson().extractingPath("$.history.length()").isEqualTo(9);
        assertThat(delivered).bodyJson().extractingPath("$.history[5].changedByRole").isEqualTo("SYSTEM");
        assertThat(delivered).bodyJson().extractingPath("$.nextStatuses").asArray().isEmpty();

        MvcTestResult seenByCustomer = as(customer, mvc.get().uri("/api/v1/orders/{number}", number));
        assertThat(seenByCustomer).bodyJson().extractingPath("$.timeline.length()").isEqualTo(9);
        assertThat(seenByCustomer).bodyJson().extractingPath("$.timeline[*].state").asArray().containsOnly("DONE");
        assertThat(seenByCustomer).bodyJson().extractingPath("$.timeline[8].comment").isEqualTo("Qo'liga berildi");
        assertThat(events.stream(OrderStatusChangedEvent.class).filter(e -> e.number().equals(number))).hasSize(9);
    }

    @Test
    void lighterParcelRefundsTheDifference() {
        String id = paidAndPurchased();

        // 0.6 kg instead of 0.8 kg: 48 000 instead of 64 000.
        MvcTestResult weighed = status(admin, id, "{\"toStatus\":\"AT_WAREHOUSE\",\"realWeightKg\":0.6}");

        assertThat(weighed).bodyJson().extractingPath("$.order.status").isEqualTo("AT_WAREHOUSE");
        assertThat(weighed).bodyJson().extractingPath("$.order.shippingDifferenceUzs").isEqualTo(-16000);
        assertThat(weighed).bodyJson().extractingPath("$.order.refundedUzs").isEqualTo(16000);
        assertThat(refunds(id)).singleElement()
                .satisfies(e -> assertThat(e.amountUzs()).isEqualByComparingTo("16000"))
                .satisfies(e -> assertThat(e.reason()).isEqualTo(RefundReason.SHIPPING_OVERPAID));
        assertThat(status(admin, id, "{\"toStatus\":\"IN_TRANSIT\"}")).bodyJson()
                .extractingPath("$.order.status").isEqualTo("IN_TRANSIT");
    }

    @Test
    void customerCancels() {
        String unpaid = orderOneItem();
        assertThat(as(customer, mvc.post().uri("/api/v1/orders/{n}/cancel", unpaid)))
                .bodyJson().extractingPath("$.status").isEqualTo("CANCELLED");
        assertThat(refunds(orderId(unpaid))).isEmpty();

        String paid = orderOneItem();
        String paidId = orderId(paid);
        status(admin, paidId, "{\"toStatus\":\"PAID\"}");
        assertThat(as(customer, mvc.post().uri("/api/v1/orders/{n}/cancel", paid)))
                .bodyJson().extractingPath("$.refundedUzs").isEqualTo(238200);
        assertThat(refunds(paidId)).singleElement()
                .satisfies(e -> assertThat(e.reason()).isEqualTo(RefundReason.ORDER_CANCELLED));

        String purchasing = orderOneItem();
        String purchasingId = orderId(purchasing);
        status(admin, purchasingId, "{\"toStatus\":\"PAID\"}");
        status(admin, purchasingId, "{\"toStatus\":\"PURCHASING\"}");
        MvcTestResult tooLate = as(customer, mvc.post().uri("/api/v1/orders/{n}/cancel", purchasing));
        assertThat(tooLate).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(tooLate).bodyJson().extractingPath("$.code").isEqualTo("INVALID_STATUS_TRANSITION");
    }

    @Test
    void outOfStockAndPriceChangedDecisions() {
        // Two lines; the buyer finds one out of stock, the customer accepts: that line is refunded.
        addToCart(SKU, 1);
        addToCart(WHITE_SHOES + "002", 1);
        String number = read(createOrder("POST", addressId), "$.number");
        String id = orderId(number);
        status(admin, id, "{\"toStatus\":\"PAID\"}");
        status(admin, id, "{\"toStatus\":\"PURCHASING\"}");
        String missingItem = itemId(as(admin, mvc.get().uri("/api/v1/admin/orders/{id}", id)), "$.order.items", WHITE_SHOES + "002");

        MvcTestResult noItems = status(admin, id, "{\"toStatus\":\"OUT_OF_STOCK\"}");
        assertThat(noItems).bodyJson().extractingPath("$.code").isEqualTo("ORDER_ITEMS_REQUIRED");
        status(admin, id, "{\"toStatus\":\"OUT_OF_STOCK\",\"items\":[{\"itemId\":\"%s\"}]}".formatted(missingItem));

        MvcTestResult adminDecides = status(admin, id, "{\"toStatus\":\"PURCHASING\"}");
        assertThat(adminDecides).hasStatus(HttpStatus.FORBIDDEN);
        MvcTestResult accepted = decide(number, "ACCEPT");
        assertThat(accepted).bodyJson().extractingPath("$.status").isEqualTo("PURCHASING");
        assertThat(accepted).bodyJson().extractingPath("$.items[?(@.skuId == '" + WHITE_SHOES + "002')].status")
                .asArray().containsExactly("OUT_OF_STOCK");
        assertThat(refunds(id)).singleElement()
                .satisfies(e -> assertThat(e.amountUzs()).isEqualByComparingTo("238200"))
                .satisfies(e -> assertThat(e.reason()).isEqualTo(RefundReason.ITEMS_OUT_OF_STOCK));

        // Now a new price; the customer refuses and gets the rest back.
        String item = itemId(accepted, "$.items", SKU);
        status(admin, id, "{\"toStatus\":\"PRICE_CHANGED\",\"items\":[{\"itemId\":\"%s\",\"actualPriceCny\":120}]}"
                .formatted(item));
        MvcTestResult refused = decide(number, "REFUND");

        assertThat(refused).bodyJson().extractingPath("$.status").isEqualTo("CANCELLED");
        assertThat(refused).bodyJson().extractingPath("$.refundedUzs").isEqualTo(476400);
        assertThat(decide(number, "ACCEPT")).bodyJson().extractingPath("$.code").isEqualTo("DECISION_NOT_EXPECTED");
    }

    @Test
    void staffRolesAreChecked() {
        String buyer = staff("BUYER");
        String operator = staff("OPERATOR");
        String id = orderId(orderOneItem());

        MvcTestResult buyerPays = status(buyer, id, "{\"toStatus\":\"PAID\"}");
        assertThat(buyerPays).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(buyerPays).bodyJson().extractingPath("$.code").isEqualTo("STATUS_CHANGE_FORBIDDEN");
        status(admin, id, "{\"toStatus\":\"PAID\"}");

        assertThat(status(operator, id, "{\"toStatus\":\"PURCHASING\"}")).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(status(buyer, id, "{\"toStatus\":\"PURCHASING\"}")).hasStatusOk();
        assertThat(status(buyer, id, "{\"toStatus\":\"PURCHASED\"}")).hasStatusOk();
        assertThat(status(operator, id, "{\"toStatus\":\"AT_WAREHOUSE\",\"realWeightKg\":0.8}")).hasStatusOk();
        assertThat(status(buyer, id, "{\"toStatus\":\"IN_TRANSIT\"}")).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(status(operator, id, "{\"toStatus\":\"IN_TRANSIT\"}")).hasStatusOk();

        MvcTestResult skipping = status(operator, id, "{\"toStatus\":\"DELIVERED\"}");
        assertThat(skipping).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(skipping).bodyJson().extractingPath("$.code").isEqualTo("INVALID_STATUS_TRANSITION");

        assertThat(as(buyer, mvc.post().uri("/api/v1/admin/orders/{id}/shipping-payment", id)))
                .hasStatus(HttpStatus.FORBIDDEN);
        assertThat(as(operator, mvc.patch().uri("/api/v1/admin/orders/{id}/items/{item}", id,
                "00000000-0000-0000-0000-000000000000").contentType(MediaType.APPLICATION_JSON).content("{}")))
                .hasStatus(HttpStatus.FORBIDDEN);
        assertThat(as(customer, mvc.get().uri("/api/v1/admin/orders"))).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(as(admin, mvc.get().uri("/api/v1/orders"))).hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void unpaidOrdersAreCancelledAfterThirtyMinutes() {
        String old = orderOneItem();
        String fresh = orderOneItem();
        jdbc.update("UPDATE orders SET created_at = now() - interval '31 minutes' WHERE number = ?", old);

        assertThat(cancelJob.cancelUnpaid()).isGreaterThanOrEqualTo(1);

        assertThat(as(customer, mvc.get().uri("/api/v1/orders/{n}", old))).bodyJson()
                .extractingPath("$.status").isEqualTo("CANCELLED");
        assertThat(as(customer, mvc.get().uri("/api/v1/orders/{n}", fresh))).bodyJson()
                .extractingPath("$.status").isEqualTo("AWAITING_PAYMENT");
        assertThat(as(admin, mvc.get().uri("/api/v1/admin/orders/{id}", orderId(old))))
                .bodyJson().extractingPath("$.history[1].changedByRole").isEqualTo("SYSTEM");
    }

    @Test
    void listsDetailsAndInternalNotes() {
        String first = orderOneItem();
        String second = orderOneItem();
        String secondId = orderId(second);
        status(admin, secondId, "{\"toStatus\":\"PAID\"}");
        status(admin, secondId, "{\"toStatus\":\"PURCHASING\"}");
        String item = read(as(admin, mvc.get().uri("/api/v1/admin/orders/{id}", secondId)), "$.order.items[0].id");
        as(admin, mvc.patch().uri("/api/v1/admin/orders/{id}/items/{item}", secondId, item)
                .contentType(MediaType.APPLICATION_JSON).content("{\"taobaoOrderNumber\":\"TB-777\",\"chinaTrackingNumber\":\"SF100\"}"));
        assertThat(as(admin, mvc.post().uri("/api/v1/admin/orders/{id}/comments", secondId)
                .contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"Sotuvchi bilan gaplashildi\"}")))
                .hasStatus(HttpStatus.CREATED);

        assertThat(as(customer, mvc.get().uri("/api/v1/orders?tab=awaiting_payment"))).bodyJson()
                .extractingPath("$.content[*].number").asArray().containsExactly(first);
        assertThat(as(customer, mvc.get().uri("/api/v1/orders?tab=in_progress"))).bodyJson()
                .extractingPath("$.content[*].number").asArray().containsExactly(second);
        assertThat(as(customer, mvc.get().uri("/api/v1/orders?tab=all"))).bodyJson()
                .extractingPath("$.totalElements").isEqualTo(2);
        assertThat(as(customer, mvc.get().uri("/api/v1/orders?tab=nope"))).hasStatus(HttpStatus.BAD_REQUEST);

        MvcTestResult customerView = as(customer, mvc.get().uri("/api/v1/orders/{n}", second));
        assertThat(customerView).bodyJson().extractingPath("$.items[0].chinaTrackingNumber").isEqualTo("SF100");
        assertThat(customerView).bodyJson().extractingPath("$.items[0].taobaoOrderNumber").isNull();
        assertThat(customerView).bodyJson().doesNotHavePath("$.comments");

        MvcTestResult staffView = as(admin, mvc.get().uri("/api/v1/admin/orders/{id}", secondId));
        assertThat(staffView).bodyJson().extractingPath("$.order.items[0].taobaoOrderNumber").isEqualTo("TB-777");
        assertThat(staffView).bodyJson().extractingPath("$.comments[0].text").isEqualTo("Sotuvchi bilan gaplashildi");
        assertThat(staffView).bodyJson().extractingPath("$.nextStatuses").asArray()
                .containsExactlyInAnyOrder("PURCHASED", "OUT_OF_STOCK", "PRICE_CHANGED");

        MvcTestResult search = as(admin, mvc.get().uri("/api/v1/admin/orders?status=PURCHASING&number={n}", second.substring(4)));
        assertThat(search).bodyJson().extractingPath("$.content[0].number").isEqualTo(second);
        assertThat(search).bodyJson().extractingPath("$.content[0].customerPhone").asString().startsWith("+998");

        String stranger = accessToken(mvc, newPhone());
        assertThat(as(stranger, mvc.get().uri("/api/v1/orders/{n}", second))).hasStatus(HttpStatus.NOT_FOUND);
    }

    private String orderOneItem() {
        addToCart(SKU, 1);
        MvcTestResult order = createOrder("POST", addressId);
        assertThat(order).hasStatus(HttpStatus.CREATED);
        return read(order, "$.number");
    }

    private String paidAndPurchased() {
        String id = orderId(orderOneItem());
        status(admin, id, "{\"toStatus\":\"PAID\"}");
        status(admin, id, "{\"toStatus\":\"PURCHASING\"}");
        status(admin, id, "{\"toStatus\":\"PURCHASED\"}");
        return id;
    }

    private String orderId(String number) {
        return read(as(admin, mvc.get().uri("/api/v1/admin/orders?number={n}", number)), "$.content[0].id");
    }

    /** A new user with this staff role (given by the admin). */
    private String staff(String role) {
        MvcTestResult login = login(mvc, newPhone());
        as(admin, mvc.patch().uri("/api/v1/admin/users/{id}/roles", read(login, "$.user.id"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"roles\":[\"%s\"]}".formatted(role)));
        return read(login, "$.accessToken");
    }

    /** Id of the order item with this SKU in the list at {@code itemsPath}. */
    private static String itemId(MvcTestResult result, String itemsPath, String skuId) {
        try {
            List<String> ids = JsonPath.read(result.getResponse().getContentAsString(),
                    itemsPath + "[?(@.skuId == '" + skuId + "')].id");
            return ids.getFirst();
        } catch (UnsupportedEncodingException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private List<OrderRefundRequestedEvent> refunds(String orderId) {
        return events.stream(OrderRefundRequestedEvent.class)
                .filter(event -> event.orderId().toString().equals(orderId)).toList();
    }

    private MvcTestResult addToCart(String skuId, int quantity) {
        MvcTestResult result = as(customer, mvc.post().uri("/api/v1/cart/items").contentType(MediaType.APPLICATION_JSON)
                .content("{\"externalProductId\":\"%s\",\"skuId\":\"%s\",\"quantity\":%d}"
                        .formatted(skuId.substring(0, 12), skuId, quantity)));
        assertThat(result).hasStatus(HttpStatus.CREATED);
        return result;
    }

    private MvcTestResult createOrder(String deliveryMethod, String address) {
        String addressField = address == null ? "" : ",\"addressId\":\"%s\"".formatted(address);
        return as(customer, mvc.post().uri("/api/v1/orders").contentType(MediaType.APPLICATION_JSON)
                .content("{\"deliveryMethod\":\"%s\",\"paymentMethod\":\"PAYME\"%s}".formatted(deliveryMethod, addressField)));
    }

    private MvcTestResult status(String token, String orderId, String body) {
        return as(token, mvc.post().uri("/api/v1/admin/orders/{id}/status", orderId)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private MvcTestResult decide(String number, String action) {
        return as(customer, mvc.post().uri("/api/v1/orders/{n}/decision", number)
                .contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"%s\"}".formatted(action)));
    }

    private static MvcTestResult as(String token, MockMvcTester.MockMvcRequestBuilder request) {
        return request.header(HttpHeaders.AUTHORIZATION, bearer(token)).exchange();
    }

    private static ProductDetail withPrice(ProductDetail product, String skuId, BigDecimal price) {
        List<Sku> skus = product.skus().stream()
                .map(sku -> sku.skuId().equals(skuId)
                        ? new Sku(sku.skuId(), sku.properties(), price, sku.stock(), sku.imageUrl()) : sku)
                .toList();
        return new ProductDetail(product.externalId(), product.titleOriginal(), null, product.mainImageUrl(),
                product.images(), product.priceCny(), product.salesCount(), product.shop(), product.attributes(), skus,
                product.chinaShippingCny(), product.weightKg());
    }
}
