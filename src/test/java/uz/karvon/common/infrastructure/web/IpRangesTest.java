package uz.karvon.common.infrastructure.web;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IpRangesTest {

    @Test
    void singleAddressesAndCidrRanges() {
        IpRanges ranges = IpRanges.parse(List.of("185.234.113.15", " 213.230.65.0/26 ", "2001:db8::/32"));

        assertThat(ranges.contains("185.234.113.15")).isTrue();
        assertThat(ranges.contains("185.234.113.16")).isFalse();
        assertThat(ranges.contains("213.230.65.0")).isTrue();
        assertThat(ranges.contains("213.230.65.63")).isTrue();
        assertThat(ranges.contains("213.230.65.64")).as("outside /26").isFalse();
        assertThat(ranges.contains("2001:db8:1::5")).isTrue();
        assertThat(ranges.contains("2001:db9::1")).isFalse();
    }

    @Test
    void emptyListAndBlankEntries() {
        assertThat(IpRanges.parse(List.of()).isEmpty()).isTrue();
        assertThat(IpRanges.parse(List.of(" ", "")).isEmpty()).isTrue();
    }

    @Test
    void hostNamesAndGarbageNeverMatchAndAreRefusedInTheList() {
        IpRanges ranges = IpRanges.parse(List.of("10.0.0.0/8"));
        assertThat(ranges.contains("localhost")).isFalse();
        assertThat(ranges.contains(null)).isFalse();
        assertThat(ranges.contains("10.0.0.1; DROP")).isFalse();

        assertThatThrownBy(() -> IpRanges.parse(List.of("payme.uz"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> IpRanges.parse(List.of("10.0.0.0/33"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> IpRanges.parse(List.of("10.0.0.0/x"))).isInstanceOf(IllegalArgumentException.class);
    }
}
