#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
# Pin to a released version of maestro-logcat-sidecar
SIDECAR_VERSION="${SIDECAR_VERSION:-1.0.0}"
SIDECAR_JAR="$ROOT_DIR/maestro-logcat-sidecar-${SIDECAR_VERSION}.jar"
SIDECAR_URL="https://github.com/Fishbowler/maestro-logcat-sidecar/releases/download/v${SIDECAR_VERSION}/maestro-logcat-sidecar-${SIDECAR_VERSION}.jar"
PID_FILE="$ROOT_DIR/.sidecar.pid"
LOG_FILE="$ROOT_DIR/sidecar.log"
PORT="${PORT:-17777}"

if [ ! -f "$SIDECAR_JAR" ]; then
    echo "Downloading maestro-logcat-sidecar v${SIDECAR_VERSION}..."
    curl -fsSL -o "$SIDECAR_JAR" "$SIDECAR_URL"
fi

LOGCAT_TAGS="${LOGCAT_TAGS:-Conversations:* *:S}" java -jar "$SIDECAR_JAR" >"$LOG_FILE" 2>&1 &
SIDECAR_PID=$!
echo "$SIDECAR_PID" >"$PID_FILE"

for i in $(seq 1 30); do
    if curl -sf "http://localhost:${PORT}/health" >/dev/null 2>&1; then
        echo "Sidecar ready on port ${PORT}"
        exit 0
    fi
    sleep 0.5
done

echo "ERROR: sidecar did not become ready within 15 seconds. Check $LOG_FILE" >&2
exit 1
