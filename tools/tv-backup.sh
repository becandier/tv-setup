#!/usr/bin/env bash
# Snapshot of the TV before tv-setup changes anything: packages (enabled, disabled, installed for the user),
# getprop, the three settings tables and the Home component, into backup/<dir>. The first snapshot of a TV is also
# kept as backup/original-<model>, which tools/tv-restore.sh puts the TV back to; a later run never overwrites it,
# so running the setup again keeps the way back to the state before the very first change.
#
# Usage: TV_HOST=192.168.1.50 tools/tv-backup.sh
set -euo pipefail
cd "$(dirname "$0")/.."
PY=tools/venv/bin/python
[ -x "$PY" ] || PY=tools/venv/Scripts/python.exe
[ -x "$PY" ] || { echo "run: python3 -m venv tools/venv && tools/venv/bin/pip install -r tools/requirements.txt" >&2; exit 1; }
: "${TV_HOST:?set TV_HOST to the TV IP address, e.g. TV_HOST=192.168.1.50 $0}"
export MSYS_NO_PATHCONV=1

tv() { "$PY" -I tools/tv.py "$1"; }

MODEL=$(tv 'getprop ro.product.model' | tr -d '\r' | tr -c 'A-Za-z0-9._-' '_' | sed 's/_*$//')
[ -n "$MODEL" ] || { echo "the TV did not answer; check TV_HOST and the debugging prompt on the TV" >&2; exit 1; }
STAMP=$(date +%Y%m%d-%H%M%S)
DIR="backup/$MODEL-$STAMP"
mkdir -p "$DIR"

tv 'pm list packages' | tr -d '\r' | sed -n 's/^package://p' | sort > "$DIR/packages.txt"
tv 'pm list packages -d' | tr -d '\r' | sed -n 's/^package://p' | sort > "$DIR/disabled.txt"
# -u also lists packages removed for this user only (pm uninstall --user 0), which install-existing brings back.
tv 'pm list packages -u' | tr -d '\r' | sed -n 's/^package://p' | sort > "$DIR/packages-all.txt"
tv 'getprop' | tr -d '\r' > "$DIR/getprop.txt"
for t in global secure system; do tv "settings list $t" | tr -d '\r' > "$DIR/settings-$t.txt"; done
tv 'cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME' \
  | tr -d '\r' | tail -n 1 > "$DIR/home.txt"

# Nothing in these files leaves this computer: backup/ is in .gitignore (serials, Wi-Fi names and the Android ID
# are in getprop and settings).
ORIG="backup/original-$MODEL"
if [ -d "$ORIG" ]; then
  echo "kept $ORIG (the state before the first setup); this snapshot: $DIR"
else
  cp -R "$DIR" "$ORIG"
  echo "first snapshot of this TV: $ORIG (tools/tv-restore.sh $ORIG puts it back)"
fi
echo "$(wc -l < "$DIR/packages.txt" | tr -d ' ') packages, $(wc -l < "$DIR/disabled.txt" | tr -d ' ') disabled, Home: $(cat "$DIR/home.txt")"
