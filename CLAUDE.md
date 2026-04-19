# Logcat Sidecar API — Claude Code Instructions

## Project purpose

This is a standalone Java HTTP sidecar that enables Maestro UI test flows to make assertions
against Android logcat output. Maestro drives the Conversations XMPP client on an Android
emulator; it calls this API to assert that expected log events have occurred.

The system under test is **Openfire**, an XMPP server. This sidecar asserts only on
**client-side** (Conversations app) logcat output — never on server logs.

---

## Repository layout

```
/
├── CLAUDE.md                  ← this file
├── pom.xml
├── scripts/
│   ├── start.sh               ← build JAR and start the sidecar; poll until ready
│   └── stop.sh                ← stop the sidecar process
├── src/
│   └── main/
│       └── java/
│           └── org/igniterealtime/logcat/
│               ├── Main.java
│               ├── LogcatBuffer.java
│               └── SidecarServer.java
└── flows/
    ├── checkForLogs.js        ← reusable assertion script called via runScript
    └── (Maestro .yaml flow files live here)
```

---

## Language and build

- **Java 17**
- **Maven** (produce a single fat JAR via `maven-shade-plugin`)
- **Javalin 7** for the HTTP server (add to `pom.xml`; no other web framework)
- **No Spring, no Quarkus, no Jakarta EE**
- Group ID: `org.igniterealtime`; Artifact ID: `logcat-sidecar`

---

## Architecture

### LogcatBuffer

Starts `adb logcat` as a subprocess via `ProcessBuilder`. Reads its stdout on a background
thread and appends lines to a fixed-capacity circular buffer (default 10,000 lines).

Key behaviours:

- Filter by tag on startup: `adb logcat -s Conversations:* *:S` to suppress unrelated noise.
  The tag filter must be configurable via an environment variable `LOGCAT_TAGS` (default:
  `Conversations:* *:S`).
- Expose a `findMatchingLines(String regex)` method that scans the buffer at the moment of
  the call and returns all lines matching the regex. Lines are matched in the order they
  were received. Returns an empty list if there are no matches.
- Expose a `clear()` method that discards all buffered lines. Called at the start of each
  test session.
- Thread-safe. Use `ReentrantLock` — not `synchronized` blocks.
- If `adb` is not on PATH, fail fast at startup with a clear error message.

### SidecarServer

A Javalin HTTP server listening on port 17777 (configurable via `PORT` env var).

Implement exactly these endpoints:

#### `POST /session/start`

Clears the logcat buffer and marks the start of a new test session. Returns `200 OK` with:

```json
{ "status": "started", "timestamp": "<ISO-8601>" }
```

#### `GET /assert`

Query parameters:
- `pattern` (required) — Java regex to match against buffered logcat lines

Scans the buffer immediately and synchronously — no blocking, no polling. Returns all lines
that match the pattern.

On match:
```
HTTP 200 OK
{ "lines": ["<first matching line>", "<second matching line>"] }
```

On no match:
```
HTTP 404 Not Found
```
No response body on 404.

#### `GET /health`

Returns `200 OK` immediately:

```json
{ "status": "ok" }
```

Used by `scripts/start.sh` to poll for readiness.

#### Error handling

- Missing `pattern` parameter → `400` with `{ "error": "pattern is required" }`
- Invalid regex → `400` with `{ "error": "invalid regex: <message>" }`
- All unhandled exceptions → `500` with `{ "error": "<message>" }`

---

## Order of operations

Each Maestro flow follows this sequence:

1. `POST /session/start` — clears the buffer; logcat capture begins
2. Maestro performs UI actions (taps, typing) against the Conversations app
3. `GET /assert` — by the time this is called, the relevant log lines will already be
   in the buffer. Android's logging subsystem is synchronous from the app's perspective;
   no delay between UI action completion and log line availability should be expected.

`/assert` is therefore a snapshot query, not a poll. Do not implement any retry or
wait logic inside the sidecar.

---

## Shell scripts

### `scripts/start.sh`

1. Run `mvn -q package -DskipTests` to produce the fat JAR.
2. Start the JAR in the background, redirecting stdout/stderr to `sidecar.log`.
3. Poll `GET /health` every 500 ms for up to 15 seconds. Exit 1 if it never responds.
4. Print `Sidecar ready on port 17777` on success.
5. Write the sidecar PID to `.sidecar.pid`.

### `scripts/stop.sh`

1. Read `.sidecar.pid`.
2. Kill the process.
3. Remove `.sidecar.pid`.

Both scripts must work on Linux. They will be called from GitHub Actions (ubuntu-latest
cloud runner) and from a developer's local Linux machine. No macOS or Windows compatibility
is required.

---

## Maestro integration

Maestro flow files live in `flows/`. Maestro connects to the Android emulator via its
bundled `dadb` library (a pure-JVM ADB client). The sidecar connects via the `adb` binary.
Both communicate through the same ADB server on the host and do not interfere with each
other.

### Maestro JS runtime conventions

Maestro executes `runScript` files using GraalVM's JavaScript engine with Java interop
available. Key rules that apply to **all** scripts in `flows/`:

- **Variable injection**: `env` values from the `runScript` block are injected as global
  variables by name — access them directly (e.g., `PATTERN`), not via `process.env`.
- **Sleep**: there is no `setTimeout` or Java interop — use a busy-wait: `const end = Date.now() + ms; while (Date.now() < end) {}`.
- **HTTP**: `http.get(url)` and `http.post(url, body)` are globals; responses have `.ok`,
  `.status`, and `.body` (string).
- **Output**: write to `output.<key>` to expose values to subsequent flow steps.

### Flow scripts

Three reusable scripts live in `flows/`. Call them early in every flow in this order:

1. **`checkHealth.js`** — verifies the sidecar is reachable; retries 3 × with 500 ms delay.
   Call this first so a misconfigured environment fails fast with a clear error.
2. **`startSession.js`** — calls `POST /session/start` to clear the logcat buffer.
   Sets `output.timestamp`. Call this immediately before the UI actions under test.
3. **`checkForLogs.js`** — queries `GET /assert` with a regex. Requires `PATTERN` env var.
   Sets `output.matchedLines`. Call this after UI actions to assert expected log output.

Example flow skeleton:

```yaml
- runScript:
    file: checkHealth.js

- runScript:
    file: startSession.js

# … UI actions …

- runScript:
    file: checkForLogs.js
    env:
      PATTERN: 'SMACK.*connected'
```

The matched lines are exposed as `output.matchedLines` and are available in subsequent
flow steps as `${checkForLogs.output.matchedLines}` for further assertion if needed.

The `flows/README.md` must explain:

- Local setup prerequisite: the `adb` binary must be on PATH (the Android SDK platform-tools
  provide this; Maestro does not, as it uses its own `dadb` implementation internally)
- How to start and stop the sidecar before/after a test run
- The meaning of each API endpoint and its response codes
- How to tune `LOGCAT_TAGS` and `PORT`
- How to write regex patterns against Conversations log output

---

## GitHub Actions

Create `.github/workflows/ci.yml`. It must:

1. Check out the repository.
2. Set up Java 17 (`actions/setup-java` with `temurin` distribution).
3. Install Maestro CLI.
4. Start an Android emulator (use `reactivecircus/android-emulator-runner`).
5. Run `scripts/start.sh`.
6. Run all Maestro flows in `flows/` via `maestro test flows/`.
7. Run `scripts/stop.sh`.
8. Upload `sidecar.log` as an artifact on failure.

The workflow must work on `ubuntu-latest` (GitHub-hosted cloud runner). No self-hosted
runner assumptions.

---

## Coding standards

- All public classes and methods must have Javadoc.
- Use `java.util.logging` — no SLF4J, no Log4j.
- No checked exceptions in public API surfaces; wrap in `RuntimeException` where needed.
- The main class is `org.igniterealtime.logcat.Main`. It reads env vars and wires up
  `LogcatBuffer` and `SidecarServer`.
- Single-session only — no concurrency between simultaneous test runs is required.
- Write one JUnit 5 unit test for `LogcatBuffer.findMatchingLines`: verify that a line
  added after `clear()` is matched, and that a line present before `clear()` is not.

---

## What NOT to build

- Do not assert on Openfire server logs — client side only.
- Do not implement authentication on the HTTP API.
- Do not build a UI or dashboard.
- Do not support multiple simultaneous ADB devices (assume one emulator, default device).
- Do not add any persistence layer.
- Do not implement any blocking, polling, or retry logic in `/assert`.
