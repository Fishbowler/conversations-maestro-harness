# conversations-maestro-harness — Claude Code Instructions

## Project purpose

Maestro UI test flows for the [Conversations](https://conversations.im) XMPP client, asserting
against Android logcat output. Maestro drives Conversations on an Android emulator; a sidecar
HTTP server (from [maestro-logcat-sidecar](https://github.com/Fishbowler/maestro-logcat-sidecar))
captures logcat so flow scripts can assert on expected log events.

The system under test is **Openfire**, an XMPP server. Assertions are on **client-side**
(Conversations app) logcat only — never on server logs.

This repo contains no Java source. The sidecar is a pre-built JAR downloaded at runtime.

---

## Repository layout

```
/
├── CLAUDE.md
├── scripts/
│   ├── prepare-emulator.sh    ← local setup: start emulator, install Conversations
│   ├── start-sidecar-api.sh   ← download JAR and start the sidecar; poll until ready
│   └── stop-sidecar-api.sh    ← stop the sidecar process
└── flows/
    ├── launch.yaml            ← Maestro flow: account setup and connection test
    ├── README.md
    └── scripts/
        ├── checkHealth.js     ← verify sidecar is reachable
        ├── startSession.js    ← clear logcat buffer; call before UI actions
        └── checkForLogs.js    ← assert log output; requires PATTERN env var
```

---

## Commands

```bash
# Local setup (once per emulator session)
./scripts/prepare-emulator.sh

# Before each test run
./scripts/start-sidecar-api.sh

# Run all flows
maestro test flows/

# After each test run
./scripts/stop-sidecar-api.sh
```

---

## Local prerequisites

- **`adb`** on PATH — Android SDK platform-tools. Maestro uses its own `dadb` and does not provide `adb`.
  ```bash
  export PATH="$ANDROID_HOME/platform-tools:$PATH"
  ```
- **Java 17+** on PATH — required to run the sidecar JAR.
- **Maestro CLI** — `curl -Ls "https://get.maestro.mobile.dev" | bash`
- **Android emulator** — run `./scripts/prepare-emulator.sh` to start one and install Conversations.

---

## What `prepare-emulator.sh` does

1. Downloads the Conversations APK (cached in repo root as `conversations.apk`).
2. Starts an Android 34 emulator via `maestro start-device --platform=android --os-version=34`.
3. Waits for the device to be ADB-accessible, fully booted (`sys.boot_completed`), and the
   package manager service active — three separate checks, following Maestro's own e2e CI pattern.
4. Installs Conversations (skips if already installed, to support repeated local runs).

This script is for **local use only**. CI uses `reactivecircus/android-emulator-runner` instead.

---

## What `start-sidecar-api.sh` does

1. Downloads the sidecar JAR from GitHub Releases if not already cached (version pinned via
   `SIDECAR_VERSION` env var, default `1.0.0`).
2. Starts the JAR in the background with `LOGCAT_TAGS=conversations:V *:S`, redirecting output
   to `sidecar.log`.
3. Polls `GET /health` every 500 ms for up to 15 seconds; exits 1 if it never responds.

---

## Configuration

| Variable | Default | Effect |
|----------|---------|--------|
| `PORT` | `17777` | HTTP port the sidecar listens on |
| `LOGCAT_TAGS` | `conversations:V *:S` | Tag filter passed to `adb logcat` |
| `SIDECAR_VERSION` | `1.0.0` | Sidecar release to download |

---

## Maestro JS runtime conventions

Applies to all scripts in `flows/scripts/`:

- **Variable injection**: `env` values are injected as globals — use `PATTERN` directly, not `process.env.PATTERN`.
- **Sleep**: no `setTimeout` — use a busy-wait: `const end = Date.now() + ms; while (Date.now() < end) {}`.
- **HTTP**: `http.get(url)` and `http.post(url, body)` are globals; responses have `.ok`, `.status`, `.body`.
- **Output**: write to `output.<key>` to expose values to subsequent flow steps.

## Flow structure

Every flow calls the helper scripts in this order:

1. **`checkHealth.js`** (in `onFlowStart`) — verifies the sidecar is reachable; retries 3× with 500 ms delay.
2. **`startSession.js`** — calls `POST /session/start` to clear the buffer. Sets `output.timestamp`.
3. UI actions (taps, inputs, assertions).
4. **`checkForLogs.js`** — calls `GET /assert?pattern=PATTERN`. Throws if no match. Sets `output.matchedLines`.

```yaml
appId: eu.siacs.conversations
onFlowStart:
    - runScript: scripts/checkHealth.js
---

- runScript: scripts/startSession.js

# … UI actions …

- runScript:
    file: scripts/checkForLogs.js
    env:
      PATTERN: 'SMACK.*connected'
```

## Sidecar API summary

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/session/start` | Clears the logcat buffer |
| `GET` | `/assert?pattern=<regex>` | Returns matched lines (`200`) or `404` |
| `GET` | `/health` | Liveness probe |

See [maestro-logcat-sidecar](https://github.com/Fishbowler/maestro-logcat-sidecar) for full API docs and sidecar architecture.

---

## What NOT to do

- Do not assert on Openfire server logs — client side only.
- Do not implement authentication on the HTTP API.
- Do not support multiple simultaneous ADB devices (one emulator, default device).
- Do not add retry or polling logic inside `/assert` — it is a snapshot query by design.
