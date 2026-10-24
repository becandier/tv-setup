package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;

import org.junit.BeforeClass;
import org.junit.Test;

public class PrayerTimesTest {

    private static final Duration WINDOW = Duration.ofMinutes(5);
    private static PrayerTimes table;

    // The real asset, so a broken copy of the muftiate's table fails here rather than on the TV.
    @BeforeClass
    public static void load() throws IOException {
        table = PrayerTimes.parse(new String(Files.readAllBytes(
                Paths.get("src/main/assets/prayer_makhachkala.json")), StandardCharsets.UTF_8));
    }

    private static Instant msk(String local) {
        return ZonedDateTime.of(java.time.LocalDateTime.parse(local), PrayerTimes.ZONE).toInstant();
    }

    @Test
    public void rowIndexIsTheDayOfANonLeapYear() {
        assertEquals(0, PrayerTimes.rowIndex(LocalDate.of(2026, 1, 1)));
        assertEquals(58, PrayerTimes.rowIndex(LocalDate.of(2026, 2, 28)));
        assertEquals(59, PrayerTimes.rowIndex(LocalDate.of(2026, 3, 1)));
        assertEquals(296, PrayerTimes.rowIndex(LocalDate.of(2026, 10, 24)));
        assertEquals(364, PrayerTimes.rowIndex(LocalDate.of(2026, 12, 31)));
    }

    @Test
    public void leapYearsKeepTheSameRowsAndFebruary29TakesThe28th() {
        assertEquals(58, PrayerTimes.rowIndex(LocalDate.of(2028, 2, 28)));
        assertEquals(58, PrayerTimes.rowIndex(LocalDate.of(2028, 2, 29)));
        assertEquals(59, PrayerTimes.rowIndex(LocalDate.of(2028, 3, 1)));
        assertEquals(364, PrayerTimes.rowIndex(LocalDate.of(2028, 12, 31)));
        assertEquals("15:07", table.day(LocalDate.of(2028, 2, 29)).get(2).clock());
    }

    @Test
    public void readsTheTableAsWrittenInMoscowTime() {
        // Row 296 (24 October): fajr 04:42, dhuhr 11:39, asr 14:29, maghrib 16:58, isha 18:12.
        java.util.List<PrayerTimes.Time> day = table.day(LocalDate.of(2026, 10, 24));
        assertEquals(5, day.size());
        assertEquals("04:42", day.get(0).clock());
        assertEquals("11:39", day.get(1).clock());
        assertEquals("14:29", day.get(2).clock());
        assertEquals("16:58", day.get(3).clock());
        assertEquals("18:12", day.get(4).clock());
        assertEquals(Instant.parse("2026-10-24T11:29:00Z"), day.get(2).at.toInstant());
        // The corrected typo of 1 April (row 90) came along with the copy.
        assertEquals("03:57", table.day(LocalDate.of(2026, 4, 1)).get(0).clock());
    }

    @Test
    public void sunriseIsNeverNext() {
        // 24 October: fajr 04:42, sunrise 06:11, dhuhr 11:39.
        PrayerTimes.Time next = table.next(msk("2026-10-24T05:00"));
        assertSame(PrayerTimes.Prayer.DHUHR, next.prayer);
        assertEquals("11:39", next.clock());
    }

    @Test
    public void nextIsTheNearestPrayerOfTheDay() {
        PrayerTimes.Time next = table.next(msk("2026-10-24T13:17"));
        assertSame(PrayerTimes.Prayer.ASR, next.prayer);
        assertEquals(72, PrayerTimes.minutesUntil(msk("2026-10-24T13:17"), next.at.toInstant()));
    }

    @Test
    public void atThePrayersOwnMinuteTheNextOneIsAlreadyNext() {
        Instant asr = msk("2026-10-24T14:29");
        assertSame(PrayerTimes.Prayer.MAGHRIB, table.next(asr).prayer);
        assertSame(PrayerTimes.Prayer.ASR, table.next(asr.minusSeconds(1)).prayer);
        assertSame(PrayerTimes.Prayer.ASR, table.justStarted(asr, WINDOW).prayer);
    }

    @Test
    public void afterIshaComesTomorrowsFajr() {
        PrayerTimes.Time next = table.next(msk("2026-10-24T18:12"));
        assertSame(PrayerTimes.Prayer.FAJR, next.prayer);
        assertEquals(LocalDate.of(2026, 10, 25), next.at.toLocalDate());
        assertEquals("04:43", next.clock());
        // Past midnight it is the same fajr, now today's.
        assertEquals(next.at, table.next(msk("2026-10-25T00:30")).at);
    }

    @Test
    public void afterIshaOnDecember31ComesFajrOfJanuary1() {
        PrayerTimes.Time next = table.next(msk("2026-12-31T20:00"));
        assertSame(PrayerTimes.Prayer.FAJR, next.prayer);
        assertEquals(LocalDate.of(2027, 1, 1), next.at.toLocalDate());
        assertEquals("05:42", next.clock());
    }

    @Test
    public void theTvsOwnZoneDoesNotMatter() {
        // 11:00 UTC is 14:00 in Makhachkala whatever zone the TV is set to.
        assertSame(PrayerTimes.Prayer.ASR, table.next(Instant.parse("2026-10-24T11:00:00Z")).prayer);
    }

    @Test
    public void justStartedOnlyWithinTheWindow() {
        assertSame(PrayerTimes.Prayer.ASR, table.justStarted(msk("2026-10-24T14:33"), WINDOW).prayer);
        assertNull(table.justStarted(msk("2026-10-24T14:34"), WINDOW));
        assertNull(table.justStarted(msk("2026-10-24T14:28"), WINDOW));
        assertNull(table.justStarted(msk("2026-10-24T03:00"), WINDOW));
    }

    @Test
    public void countdownRoundsUpToTheMinute() {
        Instant at = msk("2026-10-24T14:29");
        assertEquals(1, PrayerTimes.minutesUntil(at.minusSeconds(20), at));
        assertEquals(1, PrayerTimes.minutesUntil(at.minusSeconds(60), at));
        assertEquals(2, PrayerTimes.minutesUntil(at.minusSeconds(61), at));
    }

    @Test(expected = IllegalArgumentException.class)
    public void aShortTableIsRefused() {
        PrayerTimes.parse("[{\"fajr\": \"05:42\", \"sunrise\": \"07:19\", \"dhuhr\": \"11:58\", \"asr\": \"14:09\","
                + " \"maghrib\": \"16:31\", \"isha\": \"17:51\"}]");
    }

    @Test(expected = IllegalArgumentException.class)
    public void aTimeOutOfOrderIsRefused() {
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < PrayerTimes.DAYS; i++) {
            if (i > 0) json.append(',');
            String asr = i == 10 ? "11:00" : "14:09";
            json.append("{\"fajr\": \"05:42\", \"dhuhr\": \"11:58\", \"asr\": \"").append(asr)
                    .append("\", \"maghrib\": \"16:31\", \"isha\": \"17:51\"}");
        }
        PrayerTimes.parse(json.append(']').toString());
    }
}
