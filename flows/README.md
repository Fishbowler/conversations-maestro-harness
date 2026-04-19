# Conversations Maestro Test Harness

Maestro UI test flows for the [Conversations](https://conversations.im) XMPP client,
asserting against Android logcat output via the
[maestro-logcat-sidecar](https://github.com/<GITHUB_ORG>/maestro-logcat-sidecar).

## Prerequisites

- **`adb` on PATH** — the Android SDK platform-tools provide `adb`. Maestro uses its own
  internal `dadb` library and does _not_ put `adb` on PATH for you.

  ```bash
  export PATH="$ANDROID_HOME/platform-tools:$PATH"
  ```

- **Java** on PATH (required to run the sidecar JAR).

## Starting and stopping the sidecar

```bash
# Before your test run
./scripts/start.sh

# After your test run
./scripts/stop.sh
```

`start.sh` downloads the sidecar JAR from GitHub Releases (if not already cached),
starts it in the background, and polls `GET /health` until it responds.
Logs are written to `sidecar.log`.

To pin a specific sidecar version:
```bash
SIDECAR_VERSION=1.2.0 ./scripts/start.sh
```

## API reference

See the [maestro-logcat-sidecar docs](https://github.com/<GITHUB_ORG>/maestro-logcat-sidecar/blob/main/flows/README.md)
for the full API reference.

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/session/start` | Clears the logcat buffer. |
| `GET`  | `/assert?pattern=<regex>` | Snapshot scan. Returns `200` with matched lines, or `404` if none match. |
| `GET`  | `/health` | Liveness probe. Always returns `200 {"status":"ok"}`. |

## Configuration

| Variable | Default | Effect |
|----------|---------|--------|
| `PORT` | `17777` | HTTP port the sidecar listens on |
| `LOGCAT_TAGS` | `Conversations:* *:S` | Tags passed to `adb logcat`. Space-separated. |
| `SIDECAR_VERSION` | `1.0.0` | Which release of maestro-logcat-sidecar to download |

The `LOGCAT_TAGS` default filters to Conversations output only — override to capture additional tags if needed.

## Writing regex patterns

Patterns are Java regexes matched with `Pattern.find()` (substring match, not full-line).
Case-sensitive by default. Examples:

| Pattern | Matches |
|---------|---------|
| `SMACK.*connected` | Any line containing "SMACK" followed by "connected" |
| `(?i)tls handshake` | Case-insensitive TLS handshake messages |
| `SASL.*jane@example\.org` | SASL authentication for a specific JID |

Use `checkForLogs.js` in a flow:

```yaml
- runScript:
    file: scripts/checkForLogs.js
    env:
      PATTERN: 'SMACK.*connected'
```

The matched lines are available in subsequent steps as `${checkForLogs.output.matchedLines}`.
