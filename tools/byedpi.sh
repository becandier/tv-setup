#!/usr/bin/env bash
# ByeByeDPI (github.com/romanvht/ByeByeDPI) for YouTube on the TV: downloads the release APK for the TV's CPU,
# checks its SHA-256 against the release and its signing certificate against the author's, installs it (skipped when
# that version is already there), sets the DPI strategy and leaves a settings file for the one import that needs the
# remote: only YouTube for TV through the VPN, the system DNS, start at boot.
#
# The strategy below worked for one provider in Dagestan in October 2026. DPI differs between providers and
# changes over time: if YouTube still does not play, pick another one in ByeByeDPI (its proxy test finds working
# ones) or pass BYEDPI_STRATEGY.
#
# Usage: TV_HOST=192.168.1.50 tools/byedpi.sh [--settings-only]
#   BYEDPI_VERSION=v.1.8.2   a release tag instead of the latest
#   BYEDPI_STRATEGY='...'    command-line arguments for byedpi instead of the default below
set -euo pipefail
cd "$(dirname "$0")/.."
PY=tools/venv/bin/python
[ -x "$PY" ] || PY=tools/venv/Scripts/python.exe
: "${TV_HOST:?set TV_HOST to the TV IP address, e.g. TV_HOST=192.168.1.50 $0}"
export MSYS_NO_PATHCONV=1

PKG=io.github.romanvht.byedpi
REPO=romanvht/ByeByeDPI
# SHA-256 of the certificate the author signs releases with (CN=Roman Kostin, O=romanvht), read from v.1.8.2.
CERT_SHA256=77451075acea406406475d74d459883a49a64051faf32e42f718f3f9777a8dfb
STRATEGY="${BYEDPI_STRATEGY:--d1 -s1+s -s3+s -s6+s -s9+s -s12+s -s15+s -s20+s -s30+s -a1}"
YOUTUBE=com.google.android.youtube.tv
SETTINGS_ON_TV=/sdcard/Download/tv-setup-byedpi.json

tv() { "$PY" -I tools/tv.py "$1"; }

# The strategy goes into the TV's shell inside single quotes: refuse anything that could close them.
case "$STRATEGY" in *"'"*|*'\'*) echo "BYEDPI_STRATEGY must not contain quotes or backslashes" >&2; exit 1 ;; esac

if [ "${1:-}" != "--settings-only" ]; then
  ABI=$(tv 'getprop ro.product.cpu.abi' | tr -d '\r[:space:]')
  case "$ABI" in
    arm64-v8a|armeabi-v7a|x86|x86_64) ;;
    *) echo "CPU ABI '$ABI': using the universal APK"; ABI=universal ;;
  esac
  API=https://api.github.com/repos/$REPO/releases/${BYEDPI_VERSION:+tags/}${BYEDPI_VERSION:-latest}
  RELEASE=$(curl -fsSL "$API")
  read -r TAG URL DIGEST < <(printf '%s' "$RELEASE" | "$PY" -I -c '
import json, sys
abi = sys.argv[1]
r = json.load(sys.stdin)
for a in r["assets"]:
    if a["name"].endswith("-%s-release.apk" % abi):
        print(r["tag_name"], a["browser_download_url"], (a.get("digest") or "").replace("sha256:", "") or "-")
        break
else:
    sys.exit("no APK for %s in %s" % (abi, r["tag_name"]))
' "$ABI")
  VERSION=${TAG#v.}
  VERSION=${VERSION#v}
  INSTALLED=$(tv "dumpsys package $PKG | grep -m1 versionName=" | tr -d '\r' | sed 's/.*versionName=//')
  if [ "$INSTALLED" = "$VERSION" ]; then
    echo "ByeByeDPI $VERSION is already installed"
  else
    WORK=$(mktemp -d)
    trap 'rm -rf "$WORK"' EXIT
    curl -fsSL -o "$WORK/byedpi.apk" "$URL"
    GOT=$( (shasum -a 256 "$WORK/byedpi.apk" 2>/dev/null || sha256sum "$WORK/byedpi.apk") | cut -d' ' -f1)
    if [ "$DIGEST" = "-" ]; then
      echo "the release lists no SHA-256 for this file; relying on the signature check alone"
    elif [ "$GOT" != "$DIGEST" ]; then
      echo "SHA-256 mismatch: got $GOT, the release says $DIGEST. Not installed." >&2; exit 1
    fi
    SDK_DIR="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
    APKSIGNER=$(ls -d "$SDK_DIR"/build-tools/*/apksigner 2>/dev/null | tail -n 1 || true)
    [ -n "$APKSIGNER" ] || { echo "apksigner not found (Android SDK build-tools); cannot check the signature. Not installed." >&2; exit 1; }
    SIGNER=$("$APKSIGNER" verify --print-certs "$WORK/byedpi.apk" | sed -n 's/^Signer #1 certificate SHA-256 digest: //p')
    if [ "$SIGNER" != "$CERT_SHA256" ]; then
      echo "signing certificate $SIGNER is not the author's ($CERT_SHA256). Not installed." >&2; exit 1
    fi
    echo "ByeByeDPI $VERSION ($ABI): SHA-256 and signature check out"
    "$PY" -I tools/b64push.py "$WORK/byedpi.apk" /data/local/tmp/byedpi.apk
    OUT=$(tv 'pm install -r /data/local/tmp/byedpi.apk; rm -f /data/local/tmp/byedpi.apk')
    echo "$OUT"
    case "$OUT" in *Success*) ;; *) echo "pm install failed" >&2; exit 1 ;; esac
  fi
fi

# The strategy: ByeByeDPI's own ToggleActivity takes it as an extra and only saves it (only_update).
tv "am start -n $PKG/.activities.ToggleActivity --es strategy '$STRATEGY' --ez only_update true" >/dev/null
echo "strategy set: $STRATEGY"

# Everything else lives in its preferences, which only its Settings → Import can write. The file is ready on the TV.
JSON=$(mktemp)
"$PY" -I - "$STRATEGY" "$YOUTUBE" > "$JSON" <<'EOF'
import json, sys
strategy, youtube = sys.argv[1], sys.argv[2]
print(json.dumps({
    "app": "io.github.romanvht.byedpi",
    "version": "tv-setup",
    "apps": [youtube],
    "settings": {
        "byedpi_mode": "vpn",
        "dns_ip": "",                       # blank: the system DNS
        "applist_type": "whitelist",        # only the apps above go through the VPN
        "autostart": True,                  # start at boot
        "byedpi_enable_cmd_settings": True,
        "byedpi_cmd_args": strategy,
    },
}, indent=2))
EOF
"$PY" -I tools/tv.py --push "$JSON" "$SETTINGS_ON_TV"
rm -f "$JSON"

# VPN consent without the system dialog: the same app-op the dialog's OK sets, granted over adb.
tv "appops set $PKG ACTIVATE_VPN allow" >/dev/null
CONSENT=$(tv "appops get $PKG ACTIVATE_VPN" | tr -d '\r')
cat <<MSG
Settings file on the TV: $SETTINGS_ON_TV
VPN consent: ${CONSENT:-unknown}
Left for the agent (adb input + screenshots, skills/tv-setup/references/byedpi.md):
  1. ByeByeDPI → Settings → menu → Import settings → $SETTINGS_ON_TV → tick all → OK.
  2. Main screen → Connect.
MSG
