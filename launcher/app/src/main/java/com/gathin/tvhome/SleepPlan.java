package com.gathin.tvhome;

/**
 * sleep_timer: the steps OK walks through on the Sleep pill (off, 15, 30, 60, 90 minutes, off) and what the pill
 * shows. Plain Java, tested on the JVM; SleepTimer does the alarm, the warning and the sleep.
 */
final class SleepPlan {

    static final int[] STEPS = {0, 15, 30, 60, 90};
    /** The warning shows this long before the end; a press on the remote then cancels. */
    static final long WARN_MS = 60_000L;

    private SleepPlan() {
    }

    /** The step after {@code minutes}: 0 (off) goes to 15, 90 back to off; a value not in STEPS starts over. */
    static int next(int minutes) {
        for (int i = 0; i < STEPS.length; i++) {
            if (STEPS[i] == minutes) return STEPS[(i + 1) % STEPS.length];
        }
        return STEPS[1];
    }

    /** Whole minutes left until {@code end}, rounded up; 0 once it has passed or with no timer ({@code end} 0). */
    static long minutesLeft(long now, long end) {
        if (end <= now) return 0;
        return (end - now + 59_999) / 60_000;
    }

    /** Whole seconds left, rounded up, for the warning's countdown; 0 once it has passed. */
    static long secondsLeft(long now, long end) {
        if (end <= now) return 0;
        return (end - now + 999) / 1000;
    }
}
