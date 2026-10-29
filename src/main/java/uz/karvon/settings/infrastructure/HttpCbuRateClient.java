package uz.karvon.settings.infrastructure;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;
import uz.karvon.settings.application.CbuRateClient;
import uz.karvon.settings.application.CbuUnavailableException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Reads https://cbu.uz/uz/arkhiv-kursov-valyut/json/CNY/, a public API without a key. It answers a one-element
 * array like {@code [{"Ccy":"CNY","Nominal":"1","Rate":"1800.68","Date":"29.10.2026"}]}.
 */
public class HttpCbuRateClient implements CbuRateClient {

    static final String PATH = "/uz/arkhiv-kursov-valyut/json/CNY/";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final RestClient http;
    private final JsonMapper json;

    public HttpCbuRateClient(RestClient.Builder builder, String baseUrl, JsonMapper json) {
        this.http = builder.baseUrl(baseUrl).build();
        this.json = json;
    }

    @Override
    public CbuRate cnyRate() {
        try {
            // Read as text: the content type of this API is not reliable.
            String body = http.get().uri(PATH).retrieve().body(String.class);
            CbuEntry[] entries = json.readValue(body, CbuEntry[].class);
            if (entries.length == 0 || !"CNY".equals(entries[0].currency())) {
                throw new IllegalStateException("CBU answered without a CNY rate");
            }
            CbuEntry cny = entries[0];
            BigDecimal nominal = new BigDecimal(cny.nominal());
            BigDecimal rate = new BigDecimal(cny.rate()).divide(nominal, 2, RoundingMode.HALF_UP);
            return new CbuRate(rate, LocalDate.parse(cny.date(), DATE));
        } catch (RuntimeException ex) {
            throw new CbuUnavailableException(ex);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record CbuEntry(
            @JsonProperty("Ccy") String currency,
            @JsonProperty("Nominal") String nominal,
            @JsonProperty("Rate") String rate,
            @JsonProperty("Date") String date) {
    }
}
