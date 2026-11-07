#!/usr/bin/env bash
# Build the debug APK and install it on the connected device (USB or Wi-Fi adb).
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/env.sh
./gradlew :app:assembleDebug -q
apk=app/build/outputs/apk/debug/app-debug.apk
# Guard against stale builds: the APK must be newer than every source file.
if [ -n "$(find app/src -newer "$apk" -type f | head -1)" ]; then
    echo "APK is older than sources — stale build, aborting" >&2
    exit 1
fi
adb install -r "$apk"
adb shell am start -n ru.maximv.typovoice.debug/ru.maximv.typovoice.MainActivity
