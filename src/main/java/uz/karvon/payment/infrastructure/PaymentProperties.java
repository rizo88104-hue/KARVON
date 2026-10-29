package uz.karvon.payment.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Provider credentials ({@code karvon.payment.*}), all from environment variables.
 *
 * @param returnUrl     where the customer comes back after paying (optional)
 * @param publicBaseUrl base URL of this API, for the dev fake checkout link
 */
@ConfigurationProperties("karvon.payment")
public record PaymentProperties(Payme payme, Click click, @DefaultValue("") String returnUrl,
                                @DefaultValue("http://localhost:8080") String publicBaseUrl) {

    /**
     * Payme Business sends "Authorization: Basic base64(login:key)". The key is given when the web cash desk is added;
     * the login is given by a Payme Business specialist (developer.help.paycom.uz, "Схема взаимодействия").
     */
    public record Payme(@DefaultValue("") String login, @DefaultValue("") String key,
                        @DefaultValue("") String merchantId,
                        @DefaultValue("https://checkout.paycom.uz") String checkoutUrl) {
    }

    public record Click(@DefaultValue("") String secretKey, @DefaultValue("") String serviceId,
                        @DefaultValue("") String merchantId,
                        @DefaultValue("https://my.click.uz/services/pay") String checkoutUrl) {
    }
}
