package uz.karvon.admin.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class BannerTest {

    private static final Instant NOW = Instant.parse("2026-11-02T10:00:00Z");

    @Test
    void visibleWhenActiveAndInsideItsPeriod() {
        Banner banner = new Banner();
        banner.setActive(true);
        assertThat(banner.visibleAt(NOW)).isTrue();

        banner.setActiveFrom(NOW);
        banner.setActiveTo(NOW.plusSeconds(3600));
        assertThat(banner.visibleAt(NOW)).isTrue();
        assertThat(banner.visibleAt(NOW.minusSeconds(1))).isFalse();
        assertThat(banner.visibleAt(NOW.plusSeconds(3600))).isFalse();

        banner.setActive(false);
        assertThat(banner.visibleAt(NOW)).isFalse();
    }
}
