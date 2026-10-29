package uz.karvon.payment.domain;

import org.junit.jupiter.api.Test;
import uz.karvon.order.application.PaymentPurpose;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentAccountTest {

    @Test
    void orderAndShippingDifferenceAccounts() {
        assertThat(new PaymentAccount("KRV-000001", PaymentPurpose.ORDER).value()).isEqualTo("KRV-000001");
        assertThat(new PaymentAccount("KRV-000001", PaymentPurpose.SHIPPING_DIFFERENCE).value()).isEqualTo("KRV-000001-SHIP");

        assertThat(PaymentAccount.parse(" KRV-000001 ")).hasValue(new PaymentAccount("KRV-000001", PaymentPurpose.ORDER));
        assertThat(PaymentAccount.parse("KRV-000001-SHIP"))
                .hasValue(new PaymentAccount("KRV-000001", PaymentPurpose.SHIPPING_DIFFERENCE));
        assertThat(PaymentAccount.parse("")).isEmpty();
        assertThat(PaymentAccount.parse(null)).isEmpty();
    }
}
