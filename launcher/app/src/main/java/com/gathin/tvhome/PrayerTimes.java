package com.gathin.tvhome;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Month;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Prayer times for one city (prayer_times), read from its table: 365 rows of {fajr, sunrise, dhuhr, asr, maghrib,
 * isha} as "HH:MM" on the city's wall clock. The build puts the table chosen with PRAYER_CITY into the assets
 * (prayer-tables/ at the repo root: the Dagestan muftiate's 27 tables, or one from tools/prayer_table.py).
 * The app takes the times as written and never computes them. Plain Java, so the schedule is tested on the JVM.
 */
final class PrayerTimes {

    /** The table's wall clock, the city's zone; the TV's own zone may be anything. */
    final ZoneId zone;
    static final int DAYS = 365;

    /** The five daily prayers in table order. Sunrise is in the table but is not a prayer, so it is never "next". */
    enum Prayer { FAJR, DHUHR, ASR, MAGHRIB, ISHA }

    private static final String[] KEYS = {"fajr", "dhuhr", "asr", "maghrib", "isha"};

    /** One prayer on one date. */
    static final class Time {
        final Prayer prayer;
        final ZonedDateTime at;

        Time(Prayer prayer, ZonedDateTime at) {
            this.prayer = prayer;
            this.at = at;
        }

        /** "14:29" on the table's clock. */
        String clock() {
            return String.format(java.util.Locale.ROOT, "%02d:%02d", at.getHour(), at.getMinute());
        }
    }

    /** Minutes after midnight, [row][prayer]. */
    private final int[][] minutes;

    private PrayerTimes(int[][] minutes, ZoneId zone) {
        this.minutes = minutes;
        this.zone = zone;
    }

    /**
     * Reads the table. A small scanner rather than org.json, which is only a stub on the JVM; the file is ours,
     * so anything unexpected (a row count other than 365, a missing field, a time out of order) is an error.
     */
    static PrayerTimes parse(String json, ZoneId zone) {
        List<int[]> rows = new ArrayList<>();
        int[] row = null;
        String key = null;
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '{') {
                row = new int[]{-1, -1, -1, -1, -1};
            } else if (c == '}') {
                if (row == null) throw new IllegalArgumentException("unbalanced } at " + i);
                for (int k = 0; k < row.length; k++) {
                    if (row[k] < 0) throw new IllegalArgumentException("row " + rows.size() + ": no " + KEYS[k]);
                    if (k > 0 && row[k] <= row[k - 1]) {
                        throw new IllegalArgumentException("row " + rows.size() + ": " + KEYS[k] + " out of order");
                    }
                }
                rows.add(row);
                row = null;
            } else if (c == '"') {
                int end = json.indexOf('"', i + 1);
                if (end < 0) throw new IllegalArgumentException("unterminated string at " + i);
                String s = json.substring(i + 1, end);
                i = end;
                if (key == null) {
                    key = s;
                    continue;
                }
                if (row != null) {
                    for (int k = 0; k < KEYS.length; k++) {
                        if (KEYS[k].equals(key)) row[k] = clockMinutes(s);
                    }
                }
                key = null;
            }
        }
        if (rows.size() != DAYS) throw new IllegalArgumentException(rows.size() + " rows, expected " + DAYS);
        return new PrayerTimes(rows.toArray(new int[0][]), zone);
    }

    private static int clockMinutes(String hhmm) {
        if (hhmm.length() != 5 || hhmm.charAt(2) != ':') throw new IllegalArgumentException("not HH:MM: " + hhmm);
        int h, m;
        try {
            h = Integer.parseInt(hhmm.substring(0, 2));
            m = Integer.parseInt(hhmm.substring(3));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("not HH:MM: " + hhmm, e);
        }
        if (h > 23 || m > 59) throw new IllegalArgumentException("no such time: " + hhmm);
        return h * 60 + m;
    }

    /**
     * The table row for a date: its day of a non-leap year, from 0 (1 January) to 364 (31 December). The table has
     * no 29 February; that day takes the 28th's row, which differs from the next day's by under a minute.
     */
    static int rowIndex(LocalDate date) {
        int day = date.getMonth() == Month.FEBRUARY && date.getDayOfMonth() == 29 ? 28 : date.getDayOfMonth();
        return LocalDate.of(2025, date.getMonth(), day).getDayOfYear() - 1;
    }

    /** The five prayers of one date, in order. */
    List<Time> day(LocalDate date) {
        int[] row = minutes[rowIndex(date)];
        List<Time> out = new ArrayList<>(row.length);
        for (Prayer p : Prayer.values()) {
            int m = row[p.ordinal()];
            out.add(new Time(p, ZonedDateTime.of(date, LocalTime.of(m / 60, m % 60), zone)));
        }
        return out;
    }

    /**
     * The first prayer strictly after {@code now}: at the very minute a prayer begins, the next one is already
     * the one after it. After isha it is tomorrow's fajr.
     */
    Time next(Instant now) {
        LocalDate today = now.atZone(zone).toLocalDate();
        for (Time t : day(today)) {
            if (t.at.toInstant().isAfter(now)) return t;
        }
        return day(today.plusDays(1)).get(0);
    }

    /**
     * The prayer that began at most {@code window} before {@code now} (its own minute included), or null. Lets an
     * alarm that fires a little late still show its prayer, and one delivered long after (the TV was asleep) show
     * nothing.
     */
    Time justStarted(Instant now, Duration window) {
        LocalDate today = now.atZone(zone).toLocalDate();
        Time last = null;
        for (Time t : day(today)) {
            if (!t.at.toInstant().isAfter(now)) last = t;
        }
        if (last == null) return null;
        return Duration.between(last.at.toInstant(), now).compareTo(window) < 0 ? last : null;
    }

    /**
     * Whole minutes until {@code at}, rounded up, so a prayer 20 seconds away still reads "1 min" and the line
     * never says 0 before the prayer has begun.
     */
    static long minutesUntil(Instant now, Instant at) {
        long seconds = Duration.between(now, at).getSeconds();
        return Math.max(1, (seconds + 59) / 60);
    }
}
