package uz.karvon.payment.infrastructure;

import org.junit.jupiter.api.Test;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;
import uz.karvon.order.application.PaymentPurpose;
import uz.karvon.payment.domain.PaymentAccount;
import uz.karvon.payment.domain.PaymentProvider;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class ProviderCheckoutGatewayTest {

    private static final PaymentProperties PROPERTIES = new PaymentProperties(
            new PaymentProperties.Payme("Paycom", "key", "587f72c72cac0d162c722ae2", "https://checkout.paycom.uz"),
            new PaymentProperties.Click("secret", "36", "46", "https://my.click.uz/services/pay"),
            "https://karvon.uz/orders", "https://api.karvon.uz");

    private final ProviderCheckoutGateway gateway = new ProviderCheckoutGateway(PROPERTIES);

    @Test
    void paymeLinkIsBase64OfTheParametersInTiyin() {
        String url = gateway.checkoutUrl(PaymentProvider.PAYME, new PaymentAccount("KRV-000197", PaymentPurpose.ORDER),
                new BigDecimal("238200"), null);

        assertThat(url).startsWith("https://checkout.paycom.uz/");
        String params = new String(Base64.getDecoder().decode(url.substring("https://checkout.paycom.uz/".length())),
                StandardCharsets.UTF_8);
        assertThat(params).isEqualTo("m=587f72c72cac0d162c722ae2;ac.order=KRV-000197;a=23820000;l=uz;c=https://karvon.uz/orders");
    }

    @Test
    void paymeDocumentationExampleDecodesTheSameWay() {
        // developer.help.paycom.uz: base64(m=587f72c72cac0d162c722ae2;ac.order_id=197;a=500)
        assertThat(new String(Base64.getDecoder().decode("bT01ODdmNzJjNzJjYWMwZDE2MmM3MjJhZTI7YWMub3JkZXJfaWQ9MTk3O2E9NTAw"),
                StandardCharsets.UTF_8)).isEqualTo("m=587f72c72cac0d162c722ae2;ac.order_id=197;a=500");
    }

    @Test
    void clickLinkHasServiceMerchantAmountAndAccount() {
        String url = gateway.checkoutUrl(PaymentProvider.CLICK,
                new PaymentAccount("KRV-000197", PaymentPurpose.SHIPPING_DIFFERENCE), new BigDecimal("32000"), null);

        UriComponents parsed = UriComponentsBuilder.fromUriString(url).build();
        assertThat(parsed.getHost()).isEqualTo("my.click.uz");
        assertThat(parsed.getPath()).isEqualTo("/services/pay");
        assertThat(parsed.getQueryParams().toSingleValueMap())
                .containsEntry("service_id", "36")
                .containsEntry("merchant_id", "46")
                .containsEntry("amount", "32000.00")
                .containsEntry("transaction_param", "KRV-000197-SHIP")
                .containsKey("return_url");
    }

    @Test
    void walletHasNoCheckoutPage() {
        assertThatIllegalArgumentException().isThrownBy(() -> gateway.checkoutUrl(PaymentProvider.WALLET,
                new PaymentAccount("KRV-1", PaymentPurpose.ORDER), BigDecimal.TEN, null));
    }

    @Test
    void tiyinConversion() {
        assertThat(PaymeAmounts.toTiyin(new BigDecimal("238200"))).isEqualTo(23_820_000L);
        assertThat(PaymeAmounts.toUzs(23_820_000L)).isEqualByComparingTo("238200");
    }
}
