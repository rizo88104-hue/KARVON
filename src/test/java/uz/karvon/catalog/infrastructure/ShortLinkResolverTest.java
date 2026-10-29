package uz.karvon.catalog.infrastructure;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ShortLinkResolverTest {

    private static final URI LINK = URI.create("https://m.tb.cn/h.5AbCdEf");

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final ShortLinkResolver resolver = new ShortLinkResolver(builder);

    @Test
    void readsIdFromRedirect() {
        server.expect(requestTo(LINK)).andRespond(withStatus(HttpStatus.MOVED_PERMANENTLY)
                .header(HttpHeaders.LOCATION, "https://detail.tmall.com/item.htm?id=681234567890"));

        assertThat(resolver.resolve(LINK)).hasValue("681234567890");
    }

    @Test
    void readsIdFromThePageScript() {
        server.expect(requestTo(LINK)).andRespond(withSuccess("""
                <html><script>var url = 'https://a.m.taobao.com/i681234567890.htm';
                var real = 'https://item.taobao.com/item.htm?ut_sk=1.abc&id=681234567890&sourceType=item';</script></html>""",
                MediaType.TEXT_HTML));

        assertThat(resolver.resolve(LINK)).hasValue("681234567890");
    }

    @Test
    void redirectToAnotherSiteIsIgnored() {
        server.expect(requestTo(LINK)).andRespond(withStatus(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, "https://evil.example/item.htm?id=681234567890"));

        assertThat(resolver.resolve(LINK)).isEmpty();
    }

    @Test
    void errorPageGivesNothing() {
        server.expect(requestTo(LINK)).andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThat(resolver.resolve(LINK)).isEmpty();
    }
}
