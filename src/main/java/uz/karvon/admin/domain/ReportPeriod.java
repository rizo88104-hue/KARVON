package uz.karvon.admin.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

/**
 * Whole days {@code from}..{@code to} (both included) in the business time zone (Asia/Tashkent).
 * {@link #start()} is inclusive, {@link #end()} exclusive.
 */
public record ReportPeriod(LocalDate from, LocalDate to, ZoneId zone) {

    /** A report covers at most this many days. */
    public static final int MAX_DAYS = 366;
    /** Without dates: the last 30 days, today included. */
    public static final int DEFAULT_DAYS = 30;

    /**
     * Missing {@code to} is today; missing {@code from} is {@code to} - 29 days.
     *
     * @throws IllegalArgumentException when {@code from} is after {@code to} or the period is longer than {@link #MAX_DAYS}
     */
    public static ReportPeriod of(LocalDate from, LocalDate to, LocalDate today, ZoneId zone) {
        LocalDate end = to == null ? today : to;
        LocalDate start = from == null ? end.minusDays(DEFAULT_DAYS - 1L) : from;
        if (start.isAfter(end) || ChronoUnit.DAYS.between(start, end) + 1 > MAX_DAYS) {
            throw new IllegalArgumentException("Invalid report period " + start + ".." + end);
        }
        return new ReportPeriod(start, end, zone);
    }

    public static ReportPeriod year(int year, ZoneId zone) {
        return new ReportPeriod(LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31), zone);
    }

    public Instant start() {
        return from.atStartOfDay(zone).toInstant();
    }

    public Instant end() {
        return to.plusDays(1).atStartOfDay(zone).toInstant();
    }
}
