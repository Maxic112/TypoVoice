# Source this file: `source scripts/env.sh`
# JDK 21 and the Android SDK; existing JAVA_HOME / ANDROID_HOME win, Homebrew paths are the fallback.
if [ -z "${JAVA_HOME:-}" ] && [ -d /opt/homebrew/opt/openjdk@21 ]; then
    export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
fi
if [ -z "${ANDROID_HOME:-}" ] && [ -d /opt/homebrew/share/android-commandlinetools ]; then
    export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools
fi
[ -n "${JAVA_HOME:-}" ] && export PATH="$JAVA_HOME/bin:$PATH"
[ -n "${ANDROID_HOME:-}" ] && export PATH="$ANDROID_HOME/platform-tools:$PATH"
# The same device may be attached via USB and Wi-Fi at once; pick one connection (USB is listed first).
if [ -z "${ANDROID_SERIAL:-}" ] && command -v adb >/dev/null; then
    ANDROID_SERIAL=$(adb devices | awk 'NR > 1 && $2 == "device" { print $1; exit }')
    export ANDROID_SERIAL
fi
