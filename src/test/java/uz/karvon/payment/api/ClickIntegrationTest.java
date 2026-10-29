package uz.karvon.payment.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import uz.karvon.AbstractIntegrationTest;
import uz.karvon.payment.application.click.ClickTestSigns;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static uz.karvon.TestLogins.accessToken;
import static uz.karvon.TestLogins.newPhone;
import static uz.karvon.TestLogins.read;
import static uz.karvon.payment.PaymentTestSupport.newOrder;
import static uz.karvon.payment.PaymentTestSupport.orderStatus;

/**
 * Click SHOP API as Click calls it (form-urlencoded, md5 sign with secret "test-click-secret", service 36).
 */
class ClickIntegrationTest extends AbstractIntegrationTest {

    private static final String SECRET = "test-click-secret";
    private static final String SERVICE = "36";
    private static final String SIGN_TIME = "2026-10-29 10:00:00";

    @Autowired
    private MockMvcTester mvc;

    private String customer;
    private String number;
    private String clickTransId;

    @BeforeEach
    void order() {
        customer = accessToken(mvc, newPhone());
        number = newOrder(mvc, customer);
        clickTransId = String.valueOf(ThreadLocalRandom.current().nextLong(1_000_000, 9_000_000_000L));
    }

    @Test
    void prepareAndCompletePayTheOrder() {
        MvcTestResult prepared = prepare(number, "238200");
        assertThat(prepared).hasStatusOk();
        assertThat(prepared).bodyJson().extractingPath("$.error").isEqualTo(0);
        assertThat(prepared).bodyJson().extractingPath("$.error_note").isEqualTo("Success");
        assertThat(prepared).bodyJson().extractingPath("$.merchant_trans_id").isEqualTo(number);
        String prepareId = read(prepared, "$.merchant_prepare_id");

        // Repeated Prepare with the same click_trans_id: the same prepare id.
        assertThat(read(prepare(number, "238200"), "$.merchant_prepare_id")).isEqualTo(prepareId);

        MvcTestResult completed = complete(number, prepareId, "238200", "0");
        assertThat(completed).bodyJson().extractingPath("$.error").isEqualTo(0);
        assertThat(read(completed, "$.merchant_confirm_id")).isEqualTo(prepareId);
        assertThat(orderStatus(mvc, customer, number)).isEqualTo("PAID");

        // Repeated Complete: -4 "Already paid", as the Shop API allows.
        assertThat(complete(number, prepareId, "238200", "0")).bodyJson().extractingPath("$.error").isEqualTo(-4);
        assertThat(prepare(number, "238200")).bodyJson().extractingPath("$.error").isEqualTo(-4);
    }

    @Test
    void wrongSignIsRejected() {
        Map<String, String> form = prepareForm(number, "238200");
        form.put("sign_string", "0123456789abcdef0123456789abcdef");

        MvcTestResult result = post("/api/v1/payments/click/prepare", form);

        assertThat(result).bodyJson().extractingPath("$.error").isEqualTo(-1);
        assertThat(result).bodyJson().extractingPath("$.error_note").isEqualTo("SIGN CHECK FAILED!");
        // A changed amount breaks the sign too.
        Map<String, String> tampered = prepareForm(number, "238200");
        tampered.put("amount", "1000");
        assertThat(post("/api/v1/payments/click/prepare", tampered)).bodyJson().extractingPath("$.error").isEqualTo(-1);
    }

    @Test
    void wrongAmountIsRejected() {
        assertThat(prepare(number, "238100")).bodyJson().extractingPath("$.error").isEqualTo(-2);
        assertThat(prepare(number, "238200.00")).bodyJson().extractingPath("$.error").isEqualTo(0);

        String prepareId = read(prepare(number, "238200.00"), "$.merchant_prepare_id");
        assertThat(complete(number, prepareId, "238100", "0")).bodyJson().extractingPath("$.error").isEqualTo(-2);
        assertThat(orderStatus(mvc, customer, number)).isEqualTo("AWAITING_PAYMENT");
    }

    @Test
    void otherRequestErrors() {
        assertThat(prepare("KRV-999999", "238200")).bodyJson().extractingPath("$.error").isEqualTo(-5);

        Map<String, String> missing = prepareForm(number, "238200");
        missing.remove("sign_time");
        assertThat(post("/api/v1/payments/click/prepare", missing)).bodyJson().extractingPath("$.error").isEqualTo(-8);

        Map<String, String> wrongAction = prepareForm(number, "238200");
        wrongAction.put("action", "1");
        wrongAction.put("sign_string", ClickTestSigns.prepare(clickTransId, SERVICE, SECRET, number, "238200", "1", SIGN_TIME));
        assertThat(post("/api/v1/payments/click/prepare", wrongAction)).bodyJson().extractingPath("$.error").isEqualTo(-3);

        assertThat(complete(number, "424242", "238200", "0")).bodyJson().extractingPath("$.error").isEqualTo(-6);
    }

    @Test
    void failedPaymentIsCancelled() {
        String prepareId = read(prepare(number, "238200"), "$.merchant_prepare_id");

        MvcTestResult failed = complete(number, prepareId, "238200", "-5017");

        assertThat(failed).bodyJson().extractingPath("$.error").isEqualTo(-9);
        assertThat(orderStatus(mvc, customer, number)).isEqualTo("AWAITING_PAYMENT");
        assertThat(complete(number, prepareId, "238200", "0")).bodyJson().extractingPath("$.error").isEqualTo(-9);
    }

    private MvcTestResult prepare(String account, String amount) {
        return post("/api/v1/payments/click/prepare", prepareForm(account, amount));
    }

    private Map<String, String> prepareForm(String account, String amount) {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("click_trans_id", clickTransId);
        form.put("service_id", SERVICE);
        form.put("click_paydoc_id", "777");
        form.put("merchant_trans_id", account);
        form.put("amount", amount);
        form.put("action", "0");
        form.put("error", "0");
        form.put("error_note", "Success");
        form.put("sign_time", SIGN_TIME);
        form.put("sign_string", ClickTestSigns.prepare(clickTransId, SERVICE, SECRET, account, amount, "0", SIGN_TIME));
        return form;
    }

    private MvcTestResult complete(String account, String prepareId, String amount, String error) {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("click_trans_id", clickTransId);
        form.put("service_id", SERVICE);
        form.put("click_paydoc_id", "777");
        form.put("merchant_trans_id", account);
        form.put("merchant_prepare_id", prepareId);
        form.put("amount", amount);
        form.put("action", "1");
        form.put("error", error);
        form.put("error_note", error.equals("0") ? "Success" : "Card declined");
        form.put("sign_time", SIGN_TIME);
        form.put("sign_string", ClickTestSigns.complete(clickTransId, SERVICE, SECRET, account, prepareId, amount, "1",
                SIGN_TIME));
        return post("/api/v1/payments/click/complete", form);
    }

    private MvcTestResult post(String uri, Map<String, String> form) {
        var request = mvc.post().uri(uri).contentType(MediaType.APPLICATION_FORM_URLENCODED);
        form.forEach(request::param);
        return request.exchange();
    }
}
