package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.junit.Test;

public class VpnAppsTest {

    private static final Set<String> TWO = new HashSet<>(Arrays.asList("org.zz.vpn", "com.happproxy"));

    @Test
    public void noVpnAppNoPick() {
        assertNull(VpnApps.pick(Collections.emptySet(), "com.happproxy", "com.happproxy"));
    }

    @Test
    public void theLastUsedAppWins() {
        assertEquals("org.zz.vpn", VpnApps.pick(TWO, "org.zz.vpn", "com.happproxy"));
    }

    @Test
    public void thenTheConfiguredOne() {
        assertEquals("com.happproxy", VpnApps.pick(TWO, null, "com.happproxy"));
        // A last-used app that has since been removed no longer counts.
        assertEquals("com.happproxy", VpnApps.pick(TWO, "gone.vpn", "com.happproxy"));
    }

    @Test
    public void thenTheFirstByName() {
        assertEquals("com.happproxy", VpnApps.pick(TWO, null, ""));
        assertEquals("org.zz.vpn", VpnApps.pick(Collections.singleton("org.zz.vpn"), null, "com.happproxy"));
    }

    @Test
    public void happHasAnAdapterOthersAreOpened() {
        VpnApps.Adapter happ = VpnApps.adapter("com.happproxy");
        assertEquals("com.happproxy/.receiver.WidgetProvider", happ.component);
        assertEquals("com.happproxy.action.widget.click", happ.action);
        assertNull(VpnApps.adapter("org.zz.vpn"));
        assertNull(VpnApps.adapter(null));
    }
}
