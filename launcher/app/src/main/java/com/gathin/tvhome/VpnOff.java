package com.gathin.tvhome;

import android.net.VpnService;

/**
 * Never started and never opens a tunnel. It exists so the launcher counts as a VPN app: VpnService.prepare from an
 * app the user (or build-and-install, VPN_PILL=1) has allowed to run a VPN makes that app the VPN owner, and Android
 * then disconnects whichever app's VPN was up. That is how the VPN pill switches off any VPN app; the other app can
 * connect again as usual, taking the slot back.
 */
public class VpnOff extends VpnService {
}
