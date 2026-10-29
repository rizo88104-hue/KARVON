package uz.karvon.catalog.domain;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Understands what customers paste: a product URL, a URL without "https://", or the Taobao app's share text
 * ("【淘宝】... https://m.tb.cn/h.5AbC?tk=... 「title」"). Only Taobao and Tmall hosts are accepted.
 * <ul>
 *   <li>{@code ?id=}, {@code ?itemId=} or {@code ?item_id=}: item.taobao.com, detail.tmall.com, detail.m.tmall.com,
 *       chaoshi.detail.tmall.com, detail.tmall.hk, h5.m.taobao.com, m.intl.taobao.com ...</li>
 *   <li>path ids: world.taobao.com/item/123.htm, a.m.taobao.com/i123.htm</li>
 *   <li>short links m.tb.cn / tb.cn: the id is not in the URL, the provider has to open it</li>
 * </ul>
 */
public final class TaobaoLinks {

    private static final Pattern URL = Pattern.compile("(?i)(?:https?://)?[a-z0-9.-]+\\.(?:com|cn|hk)(?:/[^\\s「」【】\"'<>，。]*)?");
    private static final Pattern QUERY_ID = Pattern.compile("(?:^|&)(?:id|itemId|item_id)=(\\d{5,20})(?:&|$)");
    private static final Pattern PATH_ID = Pattern.compile("^/(?:item/(\\d{5,20})\\.htm|i(\\d{5,20})\\.htm)$");
    private static final Set<String> ITEM_DOMAINS = Set.of("taobao.com", "tmall.com", "tmall.hk");
    private static final Set<String> SHORT_HOSTS = Set.of("m.tb.cn", "tb.cn");

    private TaobaoLinks() {
    }

    /** The item id written in a Taobao/Tmall product link, if the pasted text has one. */
    public static Optional<String> itemId(String pasted) {
        return firstUri(pasted).filter(TaobaoLinks::isItemHost).flatMap(TaobaoLinks::idOf);
    }

    /** A Taobao short link (m.tb.cn) in the pasted text; its id is found by opening it. */
    public static Optional<URI> shortLink(String pasted) {
        return firstUri(pasted).filter(uri -> SHORT_HOSTS.contains(host(uri)) && uri.getPath() != null
                && uri.getPath().length() > 1);
    }

    /** Reads an id from a full Taobao/Tmall URL, e.g. one a short link redirected to. */
    public static Optional<String> idOf(URI uri) {
        if (uri.getRawQuery() != null) {
            Matcher query = QUERY_ID.matcher(uri.getRawQuery());
            if (query.find()) {
                return Optional.of(query.group(1));
            }
        }
        if (uri.getPath() != null) {
            Matcher path = PATH_ID.matcher(uri.getPath());
            if (path.matches()) {
                return Optional.of(path.group(1) != null ? path.group(1) : path.group(2));
            }
        }
        return Optional.empty();
    }

    public static boolean isItemHost(URI uri) {
        String host = host(uri);
        return ITEM_DOMAINS.stream().anyMatch(domain -> host.equals(domain) || host.endsWith("." + domain));
    }

    private static Optional<URI> firstUri(String pasted) {
        if (pasted == null || pasted.isBlank()) {
            return Optional.empty();
        }
        Matcher matcher = URL.matcher(pasted);
        while (matcher.find()) {
            String candidate = matcher.group();
            if (!candidate.regionMatches(true, 0, "http", 0, 4)) {
                candidate = "https://" + candidate;
            }
            try {
                URI uri = new URI(candidate);
                if (uri.getHost() != null) {
                    return Optional.of(uri);
                }
            } catch (URISyntaxException ignored) {
                // try the next URL-like part of the text
            }
        }
        return Optional.empty();
    }

    private static String host(URI uri) {
        return uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
    }
}
