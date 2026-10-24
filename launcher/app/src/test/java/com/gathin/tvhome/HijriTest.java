package com.gathin.tvhome;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.time.LocalDate;

import org.junit.Test;

public class HijriTest {

    private static final String[] RU = {"мухаррам", "сафар", "раби аль-авваль", "раби ас-сани", "джумада аль-уля",
            "джумада ас-сания", "раджаб", "шаабан", "рамадан", "шавваль", "зуль-када", "зуль-хиджа"};

    // Umm al-Qura dates announced in Saudi Arabia.
    @Test
    public void knownUmmAlQuraDates() {
        assertArrayEquals(new int[]{1, 1, 1447}, Hijri.of(LocalDate.of(2025, 6, 26)));   // 1 Muharram 1447
        assertArrayEquals(new int[]{1, 9, 1446}, Hijri.of(LocalDate.of(2025, 3, 1)));    // 1 Ramadan 1446
        assertArrayEquals(new int[]{1, 9, 1447}, Hijri.of(LocalDate.of(2026, 2, 18)));   // 1 Ramadan 1447
        assertArrayEquals(new int[]{1, 10, 1447}, Hijri.of(LocalDate.of(2026, 3, 20)));  // Eid al-Fitr 1447
    }

    @Test
    public void formatsInRussian() {
        assertEquals("13 джумада аль-уля 1448", Hijri.format(LocalDate.of(2026, 10, 24), RU));
        assertEquals("1 рамадан 1447", Hijri.format(LocalDate.of(2026, 2, 18), RU));
    }

    @Test
    public void outsideTheCalendarIsEmpty() {
        assertNull(Hijri.of(LocalDate.of(1800, 1, 1)));
        assertEquals("", Hijri.format(LocalDate.of(1800, 1, 1), RU));
        assertEquals("", Hijri.format(LocalDate.of(2026, 10, 24), new String[]{"x"}));
    }
}
