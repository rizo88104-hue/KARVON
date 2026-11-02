package uz.karvon.common.infrastructure.web;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

/**
 * A list of IPv4/IPv6 addresses and CIDR ranges ("185.234.113.1", "185.234.113.0/24", "2001:db8::/32").
 * Only literal addresses are accepted, never host names (no DNS look-ups).
 */
public final class IpRanges {

    private record Range(byte[] network, int prefix) {
        boolean contains(byte[] address) {
            if (address.length != network.length) {
                return false;
            }
            int fullBytes = prefix / 8;
            for (int i = 0; i < fullBytes; i++) {
                if (address[i] != network[i]) {
                    return false;
                }
            }
            int rest = prefix % 8;
            if (rest == 0) {
                return true;
            }
            int mask = (0xFF << (8 - rest)) & 0xFF;
            return (address[fullBytes] & mask) == (network[fullBytes] & mask);
        }
    }

    private final List<Range> ranges;

    private IpRanges(List<Range> ranges) {
        this.ranges = ranges;
    }

    /** Throws IllegalArgumentException for an entry that is not an IP literal or CIDR range (fails startup). */
    public static IpRanges parse(List<String> entries) {
        return new IpRanges(entries.stream().map(String::trim).filter(e -> !e.isEmpty()).map(IpRanges::range).toList());
    }

    public boolean isEmpty() {
        return ranges.isEmpty();
    }

    public boolean contains(String ip) {
        byte[] address = literal(ip);
        return address != null && ranges.stream().anyMatch(r -> r.contains(address));
    }

    private static Range range(String entry) {
        int slash = entry.indexOf('/');
        byte[] network = literal(slash < 0 ? entry : entry.substring(0, slash));
        if (network == null) {
            throw new IllegalArgumentException("Not an IP address or CIDR range: " + entry);
        }
        int max = network.length * 8;
        int prefix;
        try {
            prefix = slash < 0 ? max : Integer.parseInt(entry.substring(slash + 1));
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Bad CIDR prefix: " + entry, ex);
        }
        if (prefix < 0 || prefix > max) {
            throw new IllegalArgumentException("Bad CIDR prefix: " + entry);
        }
        return new Range(network, prefix);
    }

    /** Bytes of an IP literal, or null. Host names are refused before InetAddress could resolve them. */
    private static byte[] literal(String value) {
        if (value == null || value.isBlank() || !value.matches("[0-9A-Fa-f:.]+")) {
            return null;
        }
        try {
            return InetAddress.getByName(value).getAddress();
        } catch (UnknownHostException ex) {
            return null;
        }
    }
}
