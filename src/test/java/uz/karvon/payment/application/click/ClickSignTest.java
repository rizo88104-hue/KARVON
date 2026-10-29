package uz.karvon.payment.application.click;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The expected values were computed outside Java (Node.js crypto) with the formulas of the Click SHOP API.
 */
class ClickSignTest {

    @Test
    void prepareSign() {
        // md5(click_trans_id + service_id + SECRET_KEY + merchant_trans_id + amount + action + sign_time)
        assertThat(ClickShopApi.md5("123" + "36" + "test-click-secret" + "KRV-000001" + "238200" + "0" + "2026-10-29 10:00:00"))
                .isEqualTo("e1f1ef153621c51edc873838478fa3ab");
    }

    @Test
    void completeSign() {
        // md5(click_trans_id + service_id + SECRET_KEY + merchant_trans_id + merchant_prepare_id + amount + action + sign_time)
        assertThat(ClickShopApi.md5("123" + "36" + "test-click-secret" + "KRV-000001" + "1005" + "238200" + "1"
                + "2026-10-29 10:00:05")).isEqualTo("98c4d54496bfa0029166fb92e22fd93b");
    }
}
