package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class SleepPlanTest {

    @Test
    public void okCyclesOffThenUpThenOff() {
        assertEquals(15, SleepPlan.next(0));
        assertEquals(30, SleepPlan.next(15));
        assertEquals(60, SleepPlan.next(30));
        assertEquals(90, SleepPlan.next(60));
        assertEquals(0, SleepPlan.next(90));
        assertEquals(15, SleepPlan.next(1));   // the adb shortcut's odd value starts over
    }

    @Test
    public void minutesLeftRoundUp() {
        assertEquals(30, SleepPlan.minutesLeft(0, 30 * 60_000L));
        assertEquals(28, SleepPlan.minutesLeft(0, 27 * 60_000L + 1));
        assertEquals(1, SleepPlan.minutesLeft(0, 1));
        assertEquals(0, SleepPlan.minutesLeft(5, 5));
        assertEquals(0, SleepPlan.minutesLeft(10, 0));
    }

    @Test
    public void secondsLeftRoundUp() {
        assertEquals(60, SleepPlan.secondsLeft(0, 60_000));
        assertEquals(1, SleepPlan.secondsLeft(0, 1));
        assertEquals(0, SleepPlan.secondsLeft(60_000, 60_000));
    }
}
