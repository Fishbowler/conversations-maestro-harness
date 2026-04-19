#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PID_FILE="$(cd "$SCRIPT_DIR/.." && pwd)/.sidecar.pid"

if [ ! -f "$PID_FILE" ]; then
    echo "No sidecar PID file found at $PID_FILE; nothing to stop." >&2
    exit 0
fi

PID="$(cat "$PID_FILE")"
kill "$PID" 2>/dev/null && echo "Sidecar (PID $PID) stopped." || echo "Process $PID was not running."
rm -f "$PID_FILE"
