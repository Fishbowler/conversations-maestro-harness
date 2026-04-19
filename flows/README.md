# Maestro Flows — Logcat Sidecar

## Prerequisites

- **`adb` on PATH** — the Android SDK platform-tools provide `adb`. Maestro uses its own
  internal `dadb` library and does _not_ put `adb` on PATH for you. Install platform-tools
  and add the directory to PATH before starting the sidecar.

  ```bash
  export PATH="$ANDROID_HOME/platform-tools:$PATH"
  ```

## Starting and stopping the sidecar

```bash
# Before your test run
./scripts/start.sh

# After your test run
./scripts/stop.sh
```

`start.sh` builds the fat JAR, starts the server in the background, and polls
`GET /health` until it responds. Logs are written to `sidecar.log`.

## API reference

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/session/start` | Clears the logcat buffer. Call at the beginning of each flow. |
| `GET`  | `/assert?pattern=<regex>` | Snapshot scan. Returns `200` with matched lines, or `404` if none match. |
| `GET`  | `/health` | Liveness probe. Always returns `200 {"status":"ok"}`. |

### `POST /session/start`

```json
{ "status": "started", "timestamp": "2024-01-01T00:00:00Z" }
```

### `GET /assert`

**200 OK** — one or more lines matched:
```json
{ "lines": ["I/Conversations: connected to openfire.example.com"] }
```

**400 Bad Request** — missing or invalid pattern:
```json
{ "error": "pattern is required" }
{ "error": "invalid regex: ..." }
```

**404 Not Found** — no lines matched (no body).

## Configuration

| Variable | Default | Effect |
|----------|---------|--------|
| `PORT` | `17777` | HTTP port the sidecar listens on |
| `LOGCAT_TAGS` | `Conversations:* *:S` | Tags passed to `adb logcat`. Space-separated. |

Example — capture all tags at debug level:
```bash
export LOGCAT_TAGS="*:D"
./scripts/start.sh
```

## Writing regex patterns

Patterns are Java regexes matched with `Pattern.find()` (substring match, not full-line).
Case-sensitive by default. Examples:

| Pattern | Matches |
|---------|---------|
| `SMACK.*connected` | Any line containing "SMACK" followed by "connected" |
| `(?i)tls handshake` | Case-insensitive TLS handshake messages |
| `MessagePacket.*to=.*@example\.com` | XMPP messages sent to a specific domain |

Use `checkForLogs.js` in a flow:

```yaml
- runScript:
    file: checkForLogs.js
    env:
      PATTERN: 'SMACK.*connected'
```

The matched lines are available in subsequent steps as `${checkForLogs.output.matchedLines}`.
