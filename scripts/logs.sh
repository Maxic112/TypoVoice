#!/usr/bin/env bash
# Tail logcat for the app process only.
set -euo pipefail
source "$(dirname "$0")/env.sh"
pid=$(adb shell pidof ru.maximv.typovoice.debug || true)
if [ -z "$pid" ]; then echo "App is not running"; exit 1; fi
adb logcat --pid="$pid" "$@"
