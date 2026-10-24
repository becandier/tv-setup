package com.gathin.tvhome;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * usage_dock: the dock as the apps watched most in the last {@link #DAYS} days. Plain Java, tested on the JVM; the
 * usage itself comes from UsageStatsManager in MainActivity.
 */
final class UsageDock {

    static final int DAYS = 14;

    private UsageDock() {
    }

    /**
     * The dock's packages, in order. First the tiles' packages with time in the foreground, most first (a tie goes
     * by package name, so the dock does not shuffle between two equal apps), leaving out {@code excluded} (Settings,
     * VPN apps). Then, while the dock has room, the tiles in {@code ordered}, the order Home would use without this
     * switch (saved order, dock_apps, the grid), so the dock never shows a gap.
     *
     * @param ordered the tiles' packages in Home's usual order; only these can be in the dock
     * @param usage   milliseconds in the foreground per package; missing means none
     */
    static List<String> pick(List<String> ordered, Map<String, Long> usage, Collection<String> excluded, int size) {
        Set<String> tiles = new LinkedHashSet<>(ordered);
        List<String> used = new ArrayList<>();
        for (String pkg : tiles) {
            if (!excluded.contains(pkg) && time(usage, pkg) > 0) used.add(pkg);
        }
        Collections.sort(used, (a, b) -> {
            int byTime = Long.compare(time(usage, b), time(usage, a));
            return byTime != 0 ? byTime : a.compareTo(b);
        });
        Set<String> dock = new LinkedHashSet<>(used.subList(0, Math.min(size, used.size())));
        for (String pkg : tiles) {
            if (dock.size() >= size) break;
            dock.add(pkg);
        }
        return new ArrayList<>(dock);
    }

    private static long time(Map<String, Long> usage, String pkg) {
        Long t = usage.get(pkg);
        return t == null ? 0 : t;
    }
}
