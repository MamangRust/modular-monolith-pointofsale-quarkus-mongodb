package com.sanedge.transaction.repository;

import java.util.Arrays;
import java.util.List;

/**
 * Shared helpers for the transaction statistics aggregations. Keeps the month
 * abbreviations aligned with PostgreSQL {@code TO_CHAR(..., 'Mon')} / {@code %b}
 * and provides a stable month ordering for the Java-side result sorting.
 */
public final class StatsSupport {

    public static final String[] MONTH_ABBRS = { "Jan", "Feb", "Mar", "Apr", "May", "Jun",
            "Jul", "Aug", "Sep", "Oct", "Nov", "Dec" };

    private StatsSupport() {
    }

    public static String monthAbbr(int month) {
        return MONTH_ABBRS[month - 1];
    }

    public static int monthIndex(String abbr) {
        return Arrays.asList(MONTH_ABBRS).indexOf(abbr) + 1;
    }

    public static List<String> monthAbbrList() {
        return Arrays.asList(MONTH_ABBRS);
    }
}
