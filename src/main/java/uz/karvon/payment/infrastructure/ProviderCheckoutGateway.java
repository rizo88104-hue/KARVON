package uz.karvon.payment.infrastructure;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;
import uz.karvon.payment.application.PaymentGateway;
import uz.karvon.payment.domain.PaymentAccount;
import uz.karvon.payment.domain.PaymentProvider;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

/**
 * Real checkout links.
 * <ul>
 *   <li>Payme, "Отправка чека по методу GET" (developer.help.paycom.uz/initsializatsiya-platezhey/otpravka-cheka-po-metodu-get):
 *       {@code <checkout_url>/base64(m=<merchant id>;ac.order=<account>;a=<amount in tiyin>;l=uz[;c=<return url>])}.</li>
 *   <li>Click, "Payment Button — With Redirection" (docs.click.uz/click-button):
 *       {@code https://my.click.uz/services/pay?service_id=..&merchant_id=..&amount=N.NN&transaction_param=<account>[&return_url=..]}.</li>
 * </ul>
 * TODO: confirm with Payme that the cash desk account field is named "order" (it is set in the Payme cabinet).
 */
@Component
@Profile("!dev & !test")
public class ProviderCheckoutGateway implements PaymentGateway {

    private final PaymentProperties properties;

    public ProviderCheckoutGateway(PaymentProperties properties) {
        this.properties = properties;
    }

    @Override
    public boolean simulated() {
        return false;
    }

    @Override
    public String checkoutUrl(PaymentProvider provider, PaymentAccount account, BigDecimal amountUzs, UUID pendingTransactionId) {
        return switch (provider) {
            case PAYME -> paymeUrl(properties.payme(), properties.returnUrl(), account, amountUzs);
            case CLICK -> clickUrl(properties.click(), properties.returnUrl(), account, amountUzs);
            case WALLET -> throw new IllegalArgumentException("The wallet has no checkout page");
        };
    }

    static String paymeUrl(PaymentProperties.Payme payme, String returnUrl, PaymentAccount account, BigDecimal amountUzs) {
        StringBuilder params = new StringBuilder()
                .append("m=").append(payme.merchantId())
                .append(";ac.order=").append(account.value())
                .append(";a=").append(PaymeAmounts.toTiyin(amountUzs))
                .append(";l=uz");
        if (returnUrl != null && !returnUrl.isBlank()) {
            params.append(";c=").append(returnUrl);
        }
        String encoded = Base64.getEncoder().encodeToString(params.toString().getBytes(StandardCharsets.UTF_8));
        return trimSlash(payme.checkoutUrl()) + "/" + encoded;
    }

    static String clickUrl(PaymentProperties.Click click, String returnUrl, PaymentAccount account, BigDecimal amountUzs) {
        UriComponentsBuilder url = UriComponentsBuilder.fromUriString(click.checkoutUrl())
                .queryParam("service_id", click.serviceId())
                .queryParam("merchant_id", click.merchantId())
                .queryParam("amount", amountUzs.setScale(2, RoundingMode.UNNECESSARY).toPlainString())
                .queryParam("transaction_param", account.value());
        if (returnUrl != null && !returnUrl.isBlank()) {
            url.queryParam("return_url", returnUrl);
        }
        return url.encode().build().toUriString();
    }

    private static String trimSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
