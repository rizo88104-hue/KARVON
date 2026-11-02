package uz.karvon.admin.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReportPeriodTest {

    private static final ZoneId TASHKENT = ZoneId.of("Asia/Tashkent");
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 2);

    @Test
    void daysAreCountedInTashkentAndTheEndIsExclusive() {
        ReportPeriod period = ReportPeriod.of(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), TODAY, TASHKENT);

        // Tashkent is UTC+5.
        assertThat(period.start()).isEqualTo(Instant.parse("2026-09-30T19:00:00Z"));
        assertThat(period.end()).isEqualTo(Instant.parse("2026-10-31T19:00:00Z"));
    }

    @Test
    void withoutDatesItIsTheLast30DaysWithToday() {
        ReportPeriod period = ReportPeriod.of(null, null, TODAY, TASHKENT);

        assertThat(period.to()).isEqualTo(TODAY);
        assertThat(period.from()).isEqualTo(LocalDate.of(2026, 10, 4));
        assertThat(ReportPeriod.of(null, LocalDate.of(2026, 1, 30), TODAY, TASHKENT).from()).isEqualTo(LocalDate.of(2026, 1, 1));
    }

    @Test
    void oneDayAndAtMost366Days() {
        assertThat(ReportPeriod.of(TODAY, TODAY, TODAY, TASHKENT).end())
                .isEqualTo(Instant.parse("2026-11-02T19:00:00Z"));
        assertThat(ReportPeriod.of(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31), TODAY, TASHKENT).from())
                .isEqualTo(LocalDate.of(2024, 1, 1));
        assertThatThrownBy(() -> ReportPeriod.of(LocalDate.of(2024, 1, 1), LocalDate.of(2025, 1, 1), TODAY, TASHKENT))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ReportPeriod.of(TODAY, TODAY.minusDays(1), TODAY, TASHKENT))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aYearIsJanuaryToDecember() {
        ReportPeriod year = ReportPeriod.year(2020, TASHKENT);

        assertThat(year.start()).isEqualTo(Instant.parse("2019-12-31T19:00:00Z"));
        assertThat(year.end()).isEqualTo(Instant.parse("2020-12-31T19:00:00Z"));
    }
}
