#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

# TODO: research F-Droid APIs to resolve the latest Conversations APK dynamically. v2.19.15 is 4217303 (x86_64) and 4217304 (arm64-v8a).
HOST_ARCH="$(uname -m)"
case "$HOST_ARCH" in
    x86_64)        FDROID_BUILD_ID="4217303" ;;
    aarch64|arm64) FDROID_BUILD_ID="4217304" ;;
    *) echo "Unsupported architecture: $HOST_ARCH" >&2; exit 1 ;;
esac
CONVERSATIONS_APK_URL="https://f-droid.org/repo/eu.siacs.conversations_${FDROID_BUILD_ID}.apk"
CONVERSATIONS_APK="$ROOT_DIR/conversations.apk"

if [ ! -f "$CONVERSATIONS_APK" ]; then
    echo "Downloading Conversations APK..."
    curl -fsSL -o "$CONVERSATIONS_APK" "$CONVERSATIONS_APK_URL"
fi

maestro start-device --platform=android --os-version=34

adb wait-for-device

echo "Waiting for emulator to finish booting..."
adb wait-for-device shell 'while [[ -z $(getprop sys.boot_completed) ]]; do sleep 1; done'

echo "Waiting for package manager service..."
until adb shell service list | grep -q 'package'; do
    sleep 1
done

if adb shell pm list packages | grep -q "eu.siacs.conversations"; then
    echo "Conversations already installed, skipping."
else
    echo "Installing Conversations..."
    adb install "$CONVERSATIONS_APK"
fi
