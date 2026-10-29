package uz.karvon.catalog.infrastructure;

import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import uz.karvon.catalog.domain.TaobaoLinks;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Opens a Taobao short link (m.tb.cn) and finds the item id: either in the redirect Location, or in the page,
 * which carries the real item URL in its script. Redirects are not followed, and only short-link hosts
 * reach this class (see {@link TaobaoLinks#shortLink}), so it cannot be used to call other servers.
 */
public class ShortLinkResolver {

    private static final Pattern ITEM_URL_IN_PAGE = Pattern.compile(
            "(?:item\\.taobao\\.com|detail\\.tmall\\.com|detail\\.m\\.tmall\\.com|h5\\.m\\.taobao\\.com|a\\.m\\.taobao\\.com)"
                    + "[^'\"\\s]*?[?&](?:id|itemId)=(\\d{5,20})");

    private final RestClient http;

    /** {@code builder} must use a request factory that does not follow redirects. */
    public ShortLinkResolver(RestClient.Builder builder) {
        this.http = builder.build();
    }

    public Optional<String> resolve(URI shortLink) {
        try {
            return http.get().uri(shortLink).exchange((request, response) -> {
                String location = response.getHeaders().getFirst(HttpHeaders.LOCATION);
                if (response.getStatusCode().is3xxRedirection() && location != null) {
                    URI target = shortLink.resolve(location);
                    return TaobaoLinks.isItemHost(target) ? TaobaoLinks.idOf(target) : Optional.<String>empty();
                }
                if (!response.getStatusCode().is2xxSuccessful()) {
                    return Optional.<String>empty();
                }
                String page = new String(response.getBody().readNBytes(512 * 1024), StandardCharsets.UTF_8);
                Matcher matcher = ITEM_URL_IN_PAGE.matcher(page);
                return matcher.find() ? Optional.of(matcher.group(1)) : Optional.<String>empty();
            });
        } catch (RestClientException ex) {
            return Optional.empty();
        }
    }
}
