package uz.karvon.settings.api;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import uz.karvon.AbstractIntegrationTest;
import uz.karvon.TestLogins;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static uz.karvon.TestLogins.accessToken;
import static uz.karvon.TestLogins.bearer;
import static uz.karvon.TestLogins.newPhone;
import static uz.karvon.TestLogins.read;

/**
 * Settings changes are shared by all tests in the run, so every test that changes them puts the seed values back.
 */
class PricingSettingsIntegrationTest extends AbstractIntegrationTest {

    private static final String CACHE_KEY = "settings:pricing:current";
    private static final String SEED = settings("1780", "10");

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private JdbcTemplate jdbc;

    private String adminToken;

    @BeforeEach
    void loginAsAdmin() {
        adminToken = TestLogins.adminToken(mvc);
    }

    @AfterEach
    void restoreSeedValues() {
        assertThat(put(SEED)).hasStatusOk();
    }

    @Test
    void publicSettingsShowSeedValuesWithoutOurMarkup() {
        var result = mvc.get().uri("/api/v1/settings/pricing");

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.customerRate").isEqualTo(1780.0);
        assertThat(result).bodyJson().extractingPath("$.feePercent").isEqualTo(10.0);
        assertThat(result).bodyJson().extractingPath("$.minServiceFee").isEqualTo(15000);
        assertThat(result).bodyJson().extractingPath("$.shippingPricePerKg").isEqualTo(80000);
        assertThat(result).bodyJson().extractingPath("$.minWeightKg").isEqualTo(0.5);
        assertThat(result).bodyJson().doesNotHavePath("$.ourMarkupPerKg");
        assertThat(result).bodyJson().doesNotHavePath("$.priceChangeThresholdPercent");
    }

    @Test
    void estimateExampleFromTheTask() {
        MvcTestResult result = estimate("{\"priceCny\":129,\"weightKg\":0.9}");

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.goodsUzs").isEqualTo(229600);
        assertThat(result).bodyJson().extractingPath("$.serviceFeeUzs").isEqualTo(23000);
        assertThat(result).bodyJson().extractingPath("$.shippingUzs").isEqualTo(72000);
        assertThat(result).bodyJson().extractingPath("$.totalUzs").isEqualTo(324600);
        assertThat(result).bodyJson().extractingPath("$.chargeableWeightKg").isEqualTo(0.9);
        assertThat(result).bodyJson().extractingPath("$.settingsId").asString().isNotBlank();
    }

    @Test
    void estimateValidatesInput() {
        MvcTestResult result = estimate("{\"priceCny\":0,\"quantity\":0}");

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.errors.priceCny").isNotNull();
        assertThat(result).bodyJson().extractingPath("$.errors.quantity").isNotNull();
    }

    @Test
    void feeChangeIsVisibleInTheNextEstimateAndClearsTheCache() {
        String before = read(estimate("{\"priceCny\":129,\"weightKg\":0.9}"), "$.settingsId");
        mvc.get().uri("/api/v1/settings/pricing").exchange();
        assertThat(redis.hasKey(CACHE_KEY)).as("filled by the reads").isTrue();

        MvcTestResult changed = put(settings("1780", "20"));

        assertThat(changed).hasStatusOk();
        assertThat(redis.hasKey(CACHE_KEY)).as("cleared by the change").isFalse();
        MvcTestResult after = estimate("{\"priceCny\":129,\"weightKg\":0.9}");
        assertThat(after).bodyJson().extractingPath("$.serviceFeeUzs").isEqualTo(45900);
        assertThat(after).bodyJson().extractingPath("$.totalUzs").isEqualTo(347500);
        assertThat(read(after, "$.settingsId")).isEqualTo(read(changed, "$.id")).isNotEqualTo(before);
    }

    @Test
    void changeAddsAVersionAndKeepsOldRowsUnchanged() {
        String previousId = read(adminGet("/api/v1/admin/settings/pricing"), "$.id");
        Integer rows = jdbc.queryForObject("SELECT count(*) FROM pricing_settings", Integer.class);
        BigDecimal previousRate = jdbc.queryForObject(
                "SELECT customer_rate FROM pricing_settings WHERE id = ?::uuid", BigDecimal.class, previousId);

        MvcTestResult changed = put(settings("1805.50", "10"));

        assertThat(changed).bodyJson().extractingPath("$.customerRate").isEqualTo(1805.5);
        assertThat(changed).bodyJson().extractingPath("$.changedBy").asString().isNotBlank();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pricing_settings", Integer.class)).isEqualTo(rows + 1);
        assertThat(jdbc.queryForObject("SELECT customer_rate FROM pricing_settings WHERE id = ?::uuid",
                BigDecimal.class, previousId)).isEqualByComparingTo(previousRate);

        MvcTestResult history = adminGet("/api/v1/admin/settings/pricing/history?size=2");
        assertThat(history).hasStatusOk();
        assertThat(history).bodyJson().extractingPath("$.content[0].id").isEqualTo(read(changed, "$.id"));
        assertThat(history).bodyJson().extractingPath("$.content[1].id").isEqualTo(previousId);
        assertThat(history).bodyJson().extractingPath("$.totalElements").isEqualTo(rows + 1);
    }

    @Test
    void invalidSettingsAreRejected() {
        MvcTestResult result = put(settings("1780", "150"));

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.errors.feePercent").isNotNull();
    }

    @Test
    void historyPageSizeIsLimited() {
        assertThat(adminGet("/api/v1/admin/settings/pricing/history?size=1000")).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void cbuRateIsShownNextToOurs() {
        MvcTestResult result = adminGet("/api/v1/admin/settings/cbu-rate");

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.currency").isEqualTo("CNY");
        assertThat(result).bodyJson().extractingPath("$.cbuRate").isEqualTo(1750.0);
        assertThat(result).bodyJson().extractingPath("$.customerRate").isEqualTo(1780.0);
        assertThat(result).bodyJson().extractingPath("$.differencePercent").isEqualTo(1.71);
    }

    @Test
    void customerCannotSeeOrChangeAdminSettings() {
        String customer = accessToken(mvc, newPhone());

        assertThat(mvc.get().uri("/api/v1/admin/settings/pricing").header(HttpHeaders.AUTHORIZATION, bearer(customer)))
                .hasStatus(HttpStatus.FORBIDDEN);
        assertThat(mvc.put().uri("/api/v1/admin/settings/pricing").header(HttpHeaders.AUTHORIZATION, bearer(customer))
                .contentType(MediaType.APPLICATION_JSON).content(settings("1", "0")))
                .hasStatus(HttpStatus.FORBIDDEN);
        assertThat(mvc.get().uri("/api/v1/admin/settings/pricing")).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    private MvcTestResult estimate(String body) {
        return mvc.post().uri("/api/v1/pricing/estimate").contentType(MediaType.APPLICATION_JSON).content(body).exchange();
    }

    private MvcTestResult adminGet(String uri) {
        return mvc.get().uri(uri).header(HttpHeaders.AUTHORIZATION, bearer(adminToken)).exchange();
    }

    private MvcTestResult put(String body) {
        return mvc.put().uri("/api/v1/admin/settings/pricing")
                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
    }

    private static String settings(String rate, String feePercent) {
        return """
                {"customerRate":%s,"feePercent":%s,"minServiceFee":15000,"shippingCostPerKg":70000,
                 "ourMarkupPerKg":10000,"minWeightKg":0.5,"defaultWeightKg":0.5,"priceChangeThresholdPercent":3}"""
                .formatted(rate, feePercent);
    }
}
