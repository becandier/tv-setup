package com.gathin.tvhome;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The VPN pill's choice of app and how to switch one on. Android lets no app start another app's VpnService, so
 * switching on goes through something the VPN app itself offers: for the apps listed here, the broadcast its home
 * screen widget sends; any other app is opened and connected there. Switching off works for every app (see
 * MainActivity.toggleVpn). Plain Java, tested on the JVM.
 */
final class VpnApps {

    /** An explicit broadcast that toggles the app's VPN. */
    static final class Adapter {
        /** "package/.Class", as ComponentName.unflattenFromString takes it. */
        final String component;
        final String action;

        Adapter(String component, String action) {
            this.component = component;
            this.action = action;
        }
    }

    /** One row per app; add a VPN app here once its widget broadcast is tried on a TV. */
    private static final Map<String, Adapter> ADAPTERS = new HashMap<>();

    static {
        // Happ 4.3.0: the widget's own click toggles the VPN (receiver exported for the widget host).
        ADAPTERS.put("com.happproxy", new Adapter("com.happproxy/.receiver.WidgetProvider",
                "com.happproxy.action.widget.click"));
    }

    private VpnApps() {
    }

    /** The adapter that switches {@code pkg}'s VPN, or null: then the pill opens the app. */
    static Adapter adapter(String pkg) {
        return pkg == null ? null : ADAPTERS.get(pkg);
    }

    /**
     * The app the pill acts on: the one last used from the pill, else the configured one, else the first by name;
     * each only while installed. Null when no VPN app is installed.
     */
    static String pick(Collection<String> installed, String last, String configured) {
        if (installed.isEmpty()) return null;
        if (last != null && installed.contains(last)) return last;
        if (configured != null && installed.contains(configured)) return configured;
        List<String> sorted = new ArrayList<>(installed);
        Collections.sort(sorted);
        return sorted.get(0);
    }
}
