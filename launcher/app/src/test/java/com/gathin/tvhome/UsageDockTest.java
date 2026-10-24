package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Test;

public class UsageDockTest {

    // Home's usual order without usage_dock: dock_apps first, then the grid.
    private static final List<String> ORDERED = Arrays.asList(
            "com.netflix.ninja", "com.google.android.youtube.tv", "ru.kinopoisk", "com.android.tv.settings",
            "com.happproxy", "org.videolan.vlc", "tv.twitch", "com.spotify.tv");
    private static final Set<String> EXCLUDED = new java.util.HashSet<>(Arrays.asList(
            "com.android.tv.settings", "com.happproxy"));

    private static Map<String, Long> usage(Object... pairs) {
        Map<String, Long> m = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) m.put((String) pairs[i], ((Number) pairs[i + 1]).longValue());
        return m;
    }

    @Test
    public void mostWatchedFirst() {
        assertEquals(Arrays.asList("tv.twitch", "com.google.android.youtube.tv", "org.videolan.vlc", "ru.kinopoisk"),
                UsageDock.pick(ORDERED, usage("com.google.android.youtube.tv", 5_000, "tv.twitch", 9_000,
                        "org.videolan.vlc", 3_000, "ru.kinopoisk", 1_000, "com.spotify.tv", 500), EXCLUDED, 4));
    }

    @Test
    public void settingsVpnAndUnusedAppsAreLeftOut() {
        assertEquals(Arrays.asList("tv.twitch", "com.spotify.tv", "com.netflix.ninja", "com.google.android.youtube.tv"),
                UsageDock.pick(ORDERED, usage("com.android.tv.settings", 99_000, "com.happproxy", 88_000,
                        "tv.twitch", 7, "com.spotify.tv", 3, "org.videolan.vlc", 0), EXCLUDED, 4));
    }

    @Test
    public void appsWithoutATileAreLeftOut() {
        assertEquals(Arrays.asList("ru.kinopoisk", "com.netflix.ninja"),
                UsageDock.pick(ORDERED, usage("com.android.vending", 50_000, "ru.kinopoisk", 10), EXCLUDED, 2));
    }

    @Test
    public void aShortListFillsFromTheUsualOrder() {
        // Only one app used: the rest of the dock comes from Home's usual order, without repeating it.
        assertEquals(Arrays.asList("org.videolan.vlc", "com.netflix.ninja", "com.google.android.youtube.tv",
                "ru.kinopoisk"), UsageDock.pick(ORDERED, usage("org.videolan.vlc", 100), EXCLUDED, 4));
    }

    @Test
    public void noUsageIsTheUsualDock() {
        assertEquals(ORDERED.subList(0, 4), UsageDock.pick(ORDERED, Collections.emptyMap(), EXCLUDED, 4));
    }

    @Test
    public void equalTimesGoByPackageName() {
        assertEquals(Arrays.asList("com.spotify.tv", "org.videolan.vlc", "tv.twitch"),
                UsageDock.pick(ORDERED, usage("tv.twitch", 60, "org.videolan.vlc", 60, "com.spotify.tv", 60),
                        EXCLUDED, 3));
    }

    @Test
    public void fewerTilesThanSlots() {
        assertEquals(Arrays.asList("b", "a"), UsageDock.pick(Arrays.asList("a", "b", "a"), usage("b", 1),
                Collections.emptySet(), 4));
    }
}
