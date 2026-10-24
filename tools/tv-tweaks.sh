#!/usr/bin/env bash
# Small settings tv-setup applies after the launcher: animations at 0.5x (snappier on a slow TV, still smooth),
# app caches trimmed once, and with LOCATION_OFF=1 location switched off. Safe to run again: each step only sets a
# value. tools/tv-restore.sh puts the animation scales and location back.
#
# Usage: TV_HOST=192.168.1.50 [LOCATION_OFF=1] tools/tv-tweaks.sh
set -euo pipefail
cd "$(dirname "$0")/.."
PY=tools/venv/bin/python
[ -x "$PY" ] || PY=tools/venv/Scripts/python.exe
: "${TV_HOST:?set TV_HOST to the TV IP address, e.g. TV_HOST=192.168.1.50 $0}"
export MSYS_NO_PATHCONV=1
{
  for k in window_animation_scale transition_animation_scale animator_duration_scale; do
    echo "settings put global $k 0.5"
  done
  # Asks the system to free cache until this much is free: with a figure larger than the disk it clears every
  # app's cache once. Apps rebuild what they need; nothing the user saved is touched.
  echo "pm trim-caches 999G"
  if [ "${LOCATION_OFF:-0}" = 1 ]; then
    # Android 9 reads the providers list, newer versions location_mode; set both.
    echo "settings put secure location_mode 0"
    echo "settings put secure location_providers_allowed ''"
  fi
  echo "echo tweaks-done"
} | "$PY" -I tools/tv.py --batch
