#!/usr/bin/env bash
# Puts a TV back to a snapshot from tools/tv-backup.sh: enables every package that is disabled now but was not then,
# reinstalls for the user any package removed since (install-existing), sets Home back, and restores the settings
# tv-setup changes (animation scales, screensaver, location, accessibility helpers). The launcher and ByeByeDPI stay
# installed but no longer own anything. Run it with the snapshot the setup started from:
#
# Usage: TV_HOST=192.168.1.50 tools/tv-restore.sh backup/original-<model> [--dry-run]
set -euo pipefail
SNAP="${1:?usage: tv-restore.sh <backup dir> [--dry-run]}"
DRY="${2:-}"
SNAP="$(cd "$SNAP" && pwd)"
cd "$(dirname "$0")/.."
PY=tools/venv/bin/python
[ -x "$PY" ] || PY=tools/venv/Scripts/python.exe
: "${TV_HOST:?set TV_HOST to the TV IP address, e.g. TV_HOST=192.168.1.50 $0}"
export MSYS_NO_PATHCONV=1
for f in disabled.txt packages-all.txt home.txt settings-global.txt settings-secure.txt; do
  [ -f "$SNAP/$f" ] || { echo "not a tv-backup snapshot: $SNAP/$f is missing" >&2; exit 1; }
done

tv() { "$PY" -I tools/tv.py "$1"; }
# Names from files go into the TV's shell: only bare package names pass.
pkgs() { grep -E '^[A-Za-z_][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+$' || true; }

NOW_DISABLED=$(tv 'pm list packages -d' | tr -d '\r' | sed -n 's/^package://p' | pkgs | sort)
NOW_ALL=$(tv 'pm list packages' | tr -d '\r' | sed -n 's/^package://p' | pkgs | sort)
CMDS=$(mktemp)
trap 'rm -f "$CMDS"' EXIT

# Disabled since the snapshot: enable.
comm -23 <(printf '%s\n' "$NOW_DISABLED") <(pkgs < "$SNAP/disabled.txt" | sort) | while read -r p; do
  [ -n "$p" ] && echo "pm enable --user 0 $p"
done >> "$CMDS"
# Installed for the user then, gone now: bring the system copy back.
comm -23 <(pkgs < "$SNAP/packages-all.txt" | sort) <(printf '%s\n' "$NOW_ALL") | while read -r p; do
  [ -n "$p" ] && echo "cmd package install-existing --user 0 $p"
done >> "$CMDS"

# Settings tv-setup touches, back to their recorded values (deleted when there was none).
restore_key() {   # table key
  local v
  v=$(sed -n "s/^$2=//p" "$SNAP/settings-$1.txt" | head -n 1)
  if grep -q "^$2=" "$SNAP/settings-$1.txt"; then
    printf "settings put %s %s '%s'\n" "$1" "$2" "$(printf '%s' "$v" | sed "s/'/'\\\\''/g")"
  else
    echo "settings delete $1 $2"
  fi
}
for k in window_animation_scale transition_animation_scale animator_duration_scale; do restore_key global $k; done >> "$CMDS"
for k in screensaver_components screensaver_enabled location_mode location_providers_allowed \
         enabled_accessibility_services accessibility_enabled; do restore_key secure $k; done >> "$CMDS"

HOME_COMPONENT=$(tr -d '\r' < "$SNAP/home.txt" | grep -E '^[A-Za-z0-9_.]+/[A-Za-z0-9_.$]+$' || true)
if [ -n "$HOME_COMPONENT" ]; then echo "cmd package set-home-activity --user 0 $HOME_COMPONENT" >> "$CMDS"; fi

echo "Restore plan for $TV_HOST from $SNAP:"
sed 's/^/  /' "$CMDS"
if [ "$DRY" = "--dry-run" ]; then echo "(dry run: nothing sent)"; exit 0; fi
"$PY" -I tools/tv.py --batch < "$CMDS"
echo "Done. Home now: $(tv 'cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME' | tr -d '\r' | tail -n 1)"
