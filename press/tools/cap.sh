#!/usr/bin/env bash
# Pulls one screen off the connected phone into press/raw/.
#
#   cap.sh <name>           screenshot what is on screen
#   cap.sh <name> <x> <y>   tap there first, let it settle, then shoot
#
# Coordinates are in the device's own pixels (1080 x 2412 on the handset
# these were taken on). Read them off the previous capture; never guess at
# one, and never tap a row with a switch on it -- in the Apps list a tap on
# the row toggles the app rather than opening it.
set -e
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OUT="$HERE/../raw"
ADB="${ADB:-$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe}"
[ -x "$ADB" ] || ADB="$(command -v adb)"

mkdir -p "$OUT"
if [ -n "$2" ]; then
  "$ADB" shell input tap "$2" "$3"
  "$ADB" shell sleep 1.2
fi
"$ADB" exec-out screencap -p > "$OUT/$1.png"
"$ADB" shell "dumpsys window | grep mCurrentFocus"
ls -la "$OUT/$1.png" | awk '{print $5, $9}'
