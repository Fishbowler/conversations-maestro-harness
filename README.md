# conversations-maestro-harness

Maestro UI test harness for the [Conversations](https://conversations.im) XMPP client, asserting
against Android logcat output. Tests run against an [Openfire](https://www.igniterealtime.org/projects/openfire/)
XMPP server; a [sidecar HTTP server](https://github.com/Fishbowler/maestro-logcat-sidecar) captures
logcat so flow scripts can assert on expected log events.

## Prerequisites

- **`adb` on PATH** — Android SDK platform-tools. Maestro uses its own `dadb` and does not put `adb` on PATH.
  ```bash
  export PATH="$ANDROID_HOME/platform-tools:$PATH"
  ```
- **Java 17+** on PATH — required to run the sidecar JAR.
- **Maestro CLI** — `curl -Ls "https://get.maestro.mobile.dev" | bash`
- **Android emulator** — run `./scripts/prepare-emulator.sh` once per session to start one and install Conversations.

## Quick start

```bash
# Once per emulator session
./scripts/prepare-emulator.sh

# Before each test run
./scripts/start-sidecar-api.sh

# Run all flows
maestro test flows/

# After each test run
./scripts/stop-sidecar-api.sh
```

## Configuration

| Variable | Default | Effect |
|----------|---------|--------|
| `PORT` | `17777` | HTTP port the sidecar listens on |
| `LOGCAT_TAGS` | `Conversations:* *:S` | Tag filter passed to `adb logcat` |
| `SIDECAR_VERSION` | `1.0.0` | Sidecar release to download |

## Further reading

See [flows/README.md](flows/README.md) for writing regex patterns, flow structure, and the sidecar API reference.

## License

Apache 2.0 — see [LICENSE](LICENSE).
