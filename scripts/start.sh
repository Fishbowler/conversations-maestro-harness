#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
JAR="$ROOT_DIR/target/logcat-sidecar-1.0-SNAPSHOT.jar"
PID_FILE="$ROOT_DIR/.sidecar.pid"
LOG_FILE="$ROOT_DIR/sidecar.log"
PORT="${PORT:-17777}"

cd "$ROOT_DIR"
mvn -q package -DskipTests

java -jar "$JAR" >"$LOG_FILE" 2>&1 &
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
