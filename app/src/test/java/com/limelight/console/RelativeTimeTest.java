package com.limelight.console;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class RelativeTimeTest {
    private static final long DAY = 24L * 60 * 60 * 1000;

    private static void assertSpan(RelativeTime.Unit unit, int count, long milliseconds) {
        RelativeTime.Span span = RelativeTime.span(milliseconds);
        assertEquals(unit, span.unit);
        assertEquals(count, span.count);
    }

    @Test public void countsInDaysUntilAYear() {
        assertSpan(RelativeTime.Unit.NOW, 0, 30_000L);
        assertSpan(RelativeTime.Unit.MINUTES, 5, 5 * 60_000L);
        assertSpan(RelativeTime.Unit.HOURS, 3, 3 * 60 * 60_000L);
        assertSpan(RelativeTime.Unit.YESTERDAY, 1, 30 * 60 * 60_000L);
        assertSpan(RelativeTime.Unit.DAYS, 3, 3 * DAY);
        assertSpan(RelativeTime.Unit.DAYS, 364, 364 * DAY);
    }

    @Test public void countsInYearsFromAYear() {
        assertSpan(RelativeTime.Unit.YEAR, 1, 365 * DAY);
        assertSpan(RelativeTime.Unit.YEAR, 1, 547 * DAY);
        assertSpan(RelativeTime.Unit.YEAR_AND_A_HALF, 1, 548 * DAY);
        assertSpan(RelativeTime.Unit.YEAR_AND_A_HALF, 1, 729 * DAY);
        assertSpan(RelativeTime.Unit.YEARS, 2, 730 * DAY);
        assertSpan(RelativeTime.Unit.YEARS, 5, 5 * 365 * DAY + 100 * DAY);
        assertSpan(RelativeTime.Unit.YEARS, 22, 22 * 365 * DAY);
    }

    @Test public void aFutureTimeReadsAsNow() {
        assertSpan(RelativeTime.Unit.NOW, 0, -5 * DAY);
    }
}
