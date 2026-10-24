package com.gathin.tvhome;

import java.time.LocalDate;
import java.time.chrono.HijrahDate;
import java.time.temporal.ChronoField;

/**
 * hijri_date: the Islamic date under the Gregorian one, "13 Jumada al-Ula 1448". The Umm al-Qura calendar of
 * java.time's HijrahChronology (part of Android since API 26, so no ICU and no dependency); it covers 1300-1600 AH.
 * The civil day: the line changes at midnight with the date beside it, not at maghrib. Plain Java, tested on the JVM.
 */
final class Hijri {

    private Hijri() {
    }

    /** {day, month 1-12, year} of {@code date}, or null outside the calendar's range. */
    static int[] of(LocalDate date) {
        try {
            HijrahDate h = HijrahDate.from(date);
            return new int[]{h.get(ChronoField.DAY_OF_MONTH), h.get(ChronoField.MONTH_OF_YEAR), h.get(ChronoField.YEAR)};
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** "13 Jumada al-Ula 1448" with {@code months} (twelve names, Muharram first), or "" outside the range. */
    static String format(LocalDate date, String[] months) {
        int[] d = of(date);
        if (d == null || months.length != 12) return "";
        return d[0] + " " + months[d[1] - 1] + " " + d[2];
    }
}
