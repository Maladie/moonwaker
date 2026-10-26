package com.limelight.console;

import android.content.res.Resources;

import com.limelight.R;

/**
 * "3 days ago" for every last-played and last-launched text. From a year on it counts in
 * years: "a year ago", "a year and a half ago", then whole years ("2 years ago").
 */
final class RelativeTime {
    enum Unit { NOW, MINUTES, HOURS, YESTERDAY, DAYS, YEAR, YEAR_AND_A_HALF, YEARS }

    static final class Span {
        final Unit unit;
        final int count;

        Span(Unit unit, int count) {
            this.unit = unit;
            this.count = count;
        }
    }

    private static final long YEAR_DAYS = 365;
    private static final long YEAR_AND_A_HALF_DAYS = 548;

    private RelativeTime() { }

    static Span span(long milliseconds) {
        long minutes = Math.max(0L, milliseconds / 60_000L);
        if (minutes < 1) return new Span(Unit.NOW, 0);
        if (minutes < 60) return new Span(Unit.MINUTES, (int) minutes);
        long hours = minutes / 60;
        if (hours < 24) return new Span(Unit.HOURS, (int) hours);
        if (hours < 48) return new Span(Unit.YESTERDAY, 1);
        long days = hours / 24;
        if (days < YEAR_DAYS) return new Span(Unit.DAYS, (int) days);
        if (days < YEAR_AND_A_HALF_DAYS) return new Span(Unit.YEAR, 1);
        if (days < 2 * YEAR_DAYS) return new Span(Unit.YEAR_AND_A_HALF, 1);
        return new Span(Unit.YEARS, (int) Math.min(Integer.MAX_VALUE, days / YEAR_DAYS));
    }

    static String format(Resources resources, long milliseconds) {
        Span span = span(milliseconds);
        switch (span.unit) {
            case NOW: return resources.getString(R.string.console_relative_now);
            case YESTERDAY: return resources.getString(R.string.console_relative_yesterday);
            case YEAR: return resources.getString(R.string.console_relative_year);
            case YEAR_AND_A_HALF:
                return resources.getString(R.string.console_relative_year_and_a_half);
            case MINUTES:
                return resources.getQuantityString(R.plurals.console_relative_minutes,
                        span.count, span.count);
            case HOURS:
                return resources.getQuantityString(R.plurals.console_relative_hours,
                        span.count, span.count);
            case DAYS:
                return resources.getQuantityString(R.plurals.console_relative_days,
                        span.count, span.count);
            default:
                return resources.getQuantityString(R.plurals.console_relative_years,
                        span.count, span.count);
        }
    }
}
