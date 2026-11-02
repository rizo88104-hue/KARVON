package uz.karvon.pricing.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import uz.karvon.AbstractIntegrationTest;
import uz.karvon.TestLogins;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static uz.karvon.TestLogins.read;
import static uz.karvon.payment.PaymentTestSupport.SKU;
import static uz.karvon.payment.PaymentTestSupport.as;

/**
 * Promo codes at checkout. The test order is one line of SKU 001 (89 CNY, 0.8 kg).
 */
class PromoCodeIntegrationTest extends AbstractIntegrationTest {

    private static final String CODES = "/api/v1/admin/promo-codes";

    @Autowired
    private MockMvcTester mvc;

    private String admin;

    @BeforeEach
    void login() {
        admin = TestLogins.adminToken(mvc);
    }

    @Test
    void fixedDiscountLowersTheServiceFee() {
        String code = newCode("{\"code\":\"%s\",\"type\":\"FIXED_UZS\",\"value\":5000,\"active\":true}");
        String customer = customer();

        MvcTestResult order = order(customer, code.toLowerCase());

        assertThat(order).hasStatus(HttpStatus.CREATED);
        assertThat(order).bodyJson().extractingPath("$.promoCode").isEqualTo(code);
        assertThat(order).bodyJson().extractingPath("$.promoDiscountUzs").isEqualTo(5000);
        BigDecimal goods = number(order, "$.goodsUzs");
        BigDecimal fee = number(order, "$.serviceFeeUzs");
        BigDecimal shipping = number(order, "$.prepaidShippingUzs");
        assertThat(number(order, "$.totalUzs")).isEqualByComparingTo(goods.add(fee).add(shipping));
        assertThat(number(order, "$.amountDueUzs")).isEqualByComparingTo(goods.add(fee).add(shipping));
        // The fee without the code is at least the minimum of 15 000.
        assertThat(fee.add(BigDecimal.valueOf(5000))).isGreaterThanOrEqualTo(BigDecimal.valueOf(15000));
    }

    @Test
    void theServiceFeeNeverGoesBelowZero() {
        String code = newCode("{\"code\":\"%s\",\"type\":\"FIXED_UZS\",\"value\":1000000,\"active\":true}");
        MvcTestResult percent = order(customer(), newCode("{\"code\":\"%s\",\"type\":\"PERCENT_OF_FEE\",\"value\":100,\"active\":true}"));
        MvcTestResult fixed = order(customer(), code);

        for (MvcTestResult order : new MvcTestResult[]{percent, fixed}) {
            assertThat(order).hasStatus(HttpStatus.CREATED);
            assertThat(order).bodyJson().extractingPath("$.serviceFeeUzs").isEqualTo(0);
            assertThat(number(order, "$.promoDiscountUzs")).isGreaterThanOrEqualTo(BigDecimal.valueOf(15000));
            assertThat(number(order, "$.totalUzs"))
                    .isEqualByComparingTo(number(order, "$.goodsUzs").add(number(order, "$.prepaidShippingUzs")));
        }
    }

    @Test
    void perUserAndTotalLimits() {
        String code = newCode("{\"code\":\"%s\",\"type\":\"PERCENT_OF_FEE\",\"value\":50,\"maxUses\":2,\"perUserLimit\":1,"
                + "\"active\":true}");
        String first = customer();
        assertThat(order(first, code)).hasStatus(HttpStatus.CREATED);

        MvcTestResult again = order(first, code);
        assertThat(again).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(again).bodyJson().extractingPath("$.code").isEqualTo("PROMO_CODE_USER_LIMIT_REACHED");
        // Nothing was ordered: the line is still in the cart and can be ordered without the code.
        assertThat(as(mvc, first, mvc.post().uri("/api/v1/orders").contentType(MediaType.APPLICATION_JSON)
                .content("{\"deliveryMethod\":\"CARGO_PICKUP\",\"paymentMethod\":\"PAYME\"}"))).hasStatus(HttpStatus.CREATED);

        assertThat(order(customer(), code)).hasStatus(HttpStatus.CREATED);
        MvcTestResult third = order(customer(), code);
        assertThat(third).bodyJson().extractingPath("$.code").isEqualTo("PROMO_CODE_LIMIT_REACHED");

        MvcTestResult listed = as(mvc, admin, mvc.get().uri(CODES + "?q={q}", code.substring(0, 8).toLowerCase()));
        assertThat(listed).bodyJson().extractingPath("$.content[0].usedCount").isEqualTo(2);
    }

    @Test
    void aCancelledOrderGivesItsUseBack() {
        String code = newCode("{\"code\":\"%s\",\"type\":\"FIXED_UZS\",\"value\":1000,\"perUserLimit\":1,\"active\":true}");
        String customer = customer();
        String number = read(order(customer, code), "$.number");
        assertThat(order(customer, code)).bodyJson().extractingPath("$.code").isEqualTo("PROMO_CODE_USER_LIMIT_REACHED");

        assertThat(as(mvc, customer, mvc.post().uri("/api/v1/orders/{n}/cancel", number))).hasStatusOk();

        MvcTestResult again = as(mvc, customer, mvc.post().uri("/api/v1/orders").contentType(MediaType.APPLICATION_JSON)
                .content("{\"deliveryMethod\":\"CARGO_PICKUP\",\"paymentMethod\":\"PAYME\",\"promoCode\":\"%s\"}".formatted(code)));
        assertThat(again).hasStatus(HttpStatus.CREATED);
        assertThat(again).bodyJson().extractingPath("$.promoDiscountUzs").isEqualTo(1000);
    }

    @Test
    void unknownInactiveAndExpiredCodes() {
        String customer = customer();
        assertThat(order(customer, "NO-SUCH-CODE")).bodyJson().extractingPath("$.code").isEqualTo("PROMO_CODE_NOT_FOUND");

        String inactive = newCode("{\"code\":\"%s\",\"type\":\"FIXED_UZS\",\"value\":1000,\"active\":false}");
        assertThat(order(customer, inactive)).bodyJson().extractingPath("$.code").isEqualTo("PROMO_CODE_NOT_ACTIVE");

        Instant now = Instant.now();
        String expired = newCode(("{\"code\":\"%s\",\"type\":\"FIXED_UZS\",\"value\":1000,\"active\":true,"
                + "\"validFrom\":\"" + now.minusSeconds(7200) + "\",\"validTo\":\"" + now.minusSeconds(3600) + "\"}"));
        assertThat(order(customer, expired)).bodyJson().extractingPath("$.code").isEqualTo("PROMO_CODE_NOT_ACTIVE");
    }

    @Test
    void adminRules() {
        String code = newCode("{\"code\":\"%s\",\"type\":\"FIXED_UZS\",\"value\":1000,\"active\":true}");
        MvcTestResult taken = as(mvc, admin, mvc.post().uri(CODES).contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"%s\",\"type\":\"FIXED_UZS\",\"value\":1000,\"active\":true}".formatted(code.toLowerCase())));
        assertThat(taken).hasStatus(HttpStatus.CONFLICT);
        assertThat(taken).bodyJson().extractingPath("$.code").isEqualTo("PROMO_CODE_TAKEN");

        MvcTestResult tooMuch = as(mvc, admin, mvc.post().uri(CODES).contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"P%d\",\"type\":\"PERCENT_OF_FEE\",\"value\":150,\"active\":true}".formatted(random())));
        assertThat(tooMuch).bodyJson().extractingPath("$.code").isEqualTo("PROMO_CODE_VALUE_INVALID");
        assertThat(as(mvc, admin, mvc.post().uri(CODES).contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"bad code\",\"type\":\"FIXED_UZS\",\"value\":1000}"))).hasStatus(HttpStatus.BAD_REQUEST);

        String id = read(as(mvc, admin, mvc.get().uri(CODES + "?q={q}", code)), "$.content[0].id");
        MvcTestResult updated = as(mvc, admin, mvc.put().uri(CODES + "/{id}", id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"%s\",\"type\":\"PERCENT_OF_FEE\",\"value\":20,\"maxUses\":5,\"active\":false}".formatted(code)));
        assertThat(updated).bodyJson().extractingPath("$.type").isEqualTo("PERCENT_OF_FEE");
        assertThat(updated).bodyJson().extractingPath("$.maxUses").isEqualTo(5);
        assertThat(as(mvc, admin, mvc.delete().uri(CODES + "/{id}", id))).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(as(mvc, admin, mvc.get().uri(CODES + "/{id}", id))).hasStatus(HttpStatus.NOT_FOUND);
    }

    /** Creates a code from a JSON template with %s for a new random code; returns the code. */
    private String newCode(String template) {
        String code = "PROMO" + random();
        MvcTestResult created = as(mvc, admin, mvc.post().uri(CODES).contentType(MediaType.APPLICATION_JSON)
                .content(template.formatted(code)));
        assertThat(created).hasStatus(HttpStatus.CREATED);
        assertThat(created).bodyJson().extractingPath("$.code").isEqualTo(code);
        return code;
    }

    private String customer() {
        return TestLogins.accessToken(mvc, TestLogins.newPhone());
    }

    /** Puts the test line in the cart and orders it with {@code code}. */
    private MvcTestResult order(String customer, String code) {
        assertThat(as(mvc, customer, mvc.post().uri("/api/v1/cart/items").contentType(MediaType.APPLICATION_JSON)
                .content("{\"externalProductId\":\"%s\",\"skuId\":\"%s\"}".formatted(SKU.substring(0, 12), SKU))))
                .hasStatus(HttpStatus.CREATED);
        return as(mvc, customer, mvc.post().uri("/api/v1/orders").contentType(MediaType.APPLICATION_JSON)
                .content("{\"deliveryMethod\":\"CARGO_PICKUP\",\"paymentMethod\":\"PAYME\",\"promoCode\":\"%s\"}".formatted(code)));
    }

    private static BigDecimal number(MvcTestResult result, String path) {
        return new BigDecimal(read(result, path));
    }

    private static int random() {
        return ThreadLocalRandom.current().nextInt(1_000_000, 10_000_000);
    }
}
