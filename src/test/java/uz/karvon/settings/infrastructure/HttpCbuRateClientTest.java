package uz.karvon.settings.infrastructure;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;
import uz.karvon.settings.application.CbuRateClient.CbuRate;
import uz.karvon.settings.application.CbuUnavailableException;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class HttpCbuRateClientTest {

    private static final String BASE = "https://cbu.test";

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final HttpCbuRateClient client = new HttpCbuRateClient(builder, BASE, JsonMapper.builder().build());

    @Test
    void readsRateAndDate() {
        respond("""
                [{"id":1,"Code":"156","Ccy":"CNY","CcyNm_UZ":"Xitoy yuani","Nominal":"1","Rate":"1800.68",
                  "Diff":"13.08","Date":"29.10.2026"}]""", MediaType.APPLICATION_JSON);

        CbuRate rate = client.cnyRate();

        assertThat(rate.rate()).isEqualByComparingTo("1800.68");
        assertThat(rate.date()).isEqualTo(LocalDate.of(2026, 10, 29));
    }

    @Test
    void dividesByNominalAndIgnoresContentType() {
        respond("[{\"Ccy\":\"CNY\",\"Nominal\":\"10\",\"Rate\":\"18006.80\",\"Date\":\"01.11.2026\"}]",
                MediaType.TEXT_HTML);

        assertThat(client.cnyRate().rate()).isEqualByComparingTo("1800.68");
    }

    @Test
    void emptyAnswerIsUnavailable() {
        respond("[]", MediaType.APPLICATION_JSON);

        assertThatThrownBy(client::cnyRate).isInstanceOf(CbuUnavailableException.class);
    }

    @Test
    void serverErrorIsUnavailable() {
        server.expect(requestTo(BASE + HttpCbuRateClient.PATH)).andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        assertThatThrownBy(client::cnyRate)
                .isInstanceOf(CbuUnavailableException.class)
                .hasFieldOrPropertyWithValue("code", "CBU_UNAVAILABLE");
    }

    private void respond(String body, MediaType type) {
        server.expect(requestTo(BASE + HttpCbuRateClient.PATH)).andRespond(withSuccess(body, type));
    }
}
