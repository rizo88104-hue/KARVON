package uz.karvon.wallet;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import uz.karvon.AbstractIntegrationTest;
import uz.karvon.TestLogins;
import uz.karvon.common.application.BusinessException;
import uz.karvon.wallet.application.WalletService;
import uz.karvon.wallet.domain.WalletTransactionType;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static uz.karvon.TestLogins.bearer;
import static uz.karvon.TestLogins.login;
import static uz.karvon.TestLogins.newPhone;
import static uz.karvon.TestLogins.read;

class WalletIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private WalletService wallets;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void concurrentRefundsAndPaymentsKeepTheBalanceEqualToTheLedger() throws Exception {
        UUID userId = UUID.fromString(read(login(mvc, newPhone()), "$.user.id"));
        int threads = 16;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<?>> done = new ArrayList<>();
            // 32 refunds of 1 000 and 16 payments of 500, all at once, the first ones creating the wallet.
            for (int i = 0; i < 32; i++) {
                done.add(pool.submit(() -> wallets.credit(userId, new BigDecimal("1000"), WalletTransactionType.REFUND,
                        null, null, "refund")));
            }
            for (int i = 0; i < 16; i++) {
                done.add(pool.submit(() -> {
                    try {
                        wallets.debit(userId, new BigDecimal("500"), WalletTransactionType.ORDER_PAYMENT, null, null, "pay");
                    } catch (BusinessException insufficient) {
                        // May happen if a payment runs before enough refunds arrived.
                    }
                }));
            }
            for (Future<?> future : done) {
                future.get();
            }
        } finally {
            pool.shutdown();
        }

        BigDecimal ledger = jdbc.queryForObject("SELECT sum(amount_uzs) FROM wallet_transactions WHERE user_id = ?",
                BigDecimal.class, userId);
        Integer refunds = jdbc.queryForObject(
                "SELECT count(*) FROM wallet_transactions WHERE user_id = ? AND type = 'REFUND'", Integer.class, userId);
        Integer negativeAfter = jdbc.queryForObject(
                "SELECT count(*) FROM wallet_transactions WHERE user_id = ? AND balance_after_uzs < 0", Integer.class, userId);
        assertThat(wallets.balance(userId)).isEqualByComparingTo(ledger);
        assertThat(refunds).isEqualTo(32);
        assertThat(negativeAfter).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM wallets WHERE user_id = ?", Integer.class, userId)).isEqualTo(1);
    }

    @Test
    void notMoreThanTheBalanceCanBeTaken() {
        UUID userId = UUID.fromString(read(login(mvc, newPhone()), "$.user.id"));
        wallets.credit(userId, new BigDecimal("1000"), WalletTransactionType.REFUND, null, null, null);

        assertThatThrownBy(() -> wallets.debit(userId, new BigDecimal("1001"), WalletTransactionType.ORDER_PAYMENT,
                null, null, null)).hasFieldOrPropertyWithValue("code", "INSUFFICIENT_FUNDS");
        assertThat(wallets.balance(userId)).isEqualByComparingTo("1000");
    }

    @Test
    void customerSeesTheWalletAndAdminAdjustsIt() {
        MvcTestResult customerLogin = login(mvc, newPhone());
        String customer = read(customerLogin, "$.accessToken");
        String userId = read(customerLogin, "$.user.id");
        String admin = TestLogins.adminToken(mvc);

        assertThat(get(customer, "/api/v1/me/wallet")).bodyJson().extractingPath("$.balanceUzs").isEqualTo(0);

        MvcTestResult added = post(admin, "/api/v1/admin/wallets/{id}/adjust", userId,
                "{\"amount\":50000,\"reason\":\"Kompensatsiya\"}");
        assertThat(added).hasStatusOk();
        assertThat(added).bodyJson().extractingPath("$.type").isEqualTo("ADMIN_ADJUSTMENT");
        assertThat(added).bodyJson().extractingPath("$.createdBy").asString().isNotBlank();

        assertThat(post(admin, "/api/v1/admin/wallets/{id}/adjust", userId, "{\"amount\":-60000,\"reason\":\"Xato\"}"))
                .bodyJson().extractingPath("$.code").isEqualTo("INSUFFICIENT_FUNDS");
        assertThat(post(admin, "/api/v1/admin/wallets/{id}/adjust", userId, "{\"amount\":0,\"reason\":\"Nol\"}"))
                .bodyJson().extractingPath("$.code").isEqualTo("WALLET_AMOUNT_ZERO");
        assertThat(post(admin, "/api/v1/admin/wallets/{id}/adjust", userId, "{\"amount\":100}"))
                .hasStatus(HttpStatus.BAD_REQUEST);

        MvcTestResult toCard = post(admin, "/api/v1/admin/wallets/{id}/card-refund", userId,
                "{\"amount\":20000,\"comment\":\"Payme kabinetidan qaytarildi\"}");
        assertThat(toCard).bodyJson().extractingPath("$.amountUzs").isEqualTo(-20000);
        assertThat(toCard).bodyJson().extractingPath("$.balanceAfterUzs").isEqualTo(30000);

        MvcTestResult wallet = get(customer, "/api/v1/me/wallet");
        assertThat(wallet).bodyJson().extractingPath("$.balanceUzs").isEqualTo(30000);
        assertThat(wallet).bodyJson().extractingPath("$.transactions.content[0].type").isEqualTo("CARD_REFUND");
        assertThat(wallet).bodyJson().extractingPath("$.transactions.content[1].comment").isEqualTo("Kompensatsiya");

        assertThat(post(customer, "/api/v1/admin/wallets/{id}/adjust", userId, "{\"amount\":1,\"reason\":\"x\"}"))
                .hasStatus(HttpStatus.FORBIDDEN);
        assertThat(post(admin, "/api/v1/admin/wallets/{id}/adjust", UUID.randomUUID().toString(),
                "{\"amount\":1,\"reason\":\"x\"}")).hasStatus(HttpStatus.NOT_FOUND);
    }

    private MvcTestResult get(String token, String uri) {
        return mvc.get().uri(uri).header(HttpHeaders.AUTHORIZATION, bearer(token)).exchange();
    }

    private MvcTestResult post(String token, String uri, String userId, String body) {
        return mvc.post().uri(uri, userId).header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(body).exchange();
    }
}
