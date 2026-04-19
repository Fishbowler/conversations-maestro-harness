package org.igniterealtime.logcat;

import io.javalin.Javalin;
import io.javalin.http.Context;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.regex.PatternSyntaxException;

/**
 * HTTP sidecar server exposing logcat assertion endpoints.
 *
 * <p>Listens on {@code PORT} (default {@code 17777}).
 */
public class SidecarServer {

    private static final Logger LOG = Logger.getLogger(SidecarServer.class.getName());

    private final LogcatBuffer buffer;
    private final int port;
    private Javalin app;

    /**
     * @param buffer the logcat buffer to query and control
     * @param port   TCP port to listen on
     */
    public SidecarServer(LogcatBuffer buffer, int port) {
        this.buffer = buffer;
        this.port = port;
    }

    /** Starts the HTTP server. */
    public void start() {
        app = Javalin.create(config -> {
            config.routes.post("/session/start", this::handleSessionStart);
            config.routes.get("/assert", this::handleAssert);
            config.routes.get("/health", this::handleHealth);
            config.routes.exception(Exception.class, (e, ctx) -> {
                LOG.warning("Unhandled exception: " + e.getMessage());
                ctx.status(500).json(Map.of("error", e.getMessage() != null ? e.getMessage() : "internal error"));
            });
        });
        app.start(port);
        LOG.info("Sidecar listening on port " + port);
    }

    /** Stops the HTTP server. */
    public void stop() {
        if (app != null) {
            app.stop();
        }
    }

    // ------------------------------------------------------------------

    /**
     * {@code POST /session/start} — clears the buffer and marks a new test session.
     */
    private void handleSessionStart(Context ctx) {
        buffer.clear();
        ctx.status(200).json(Map.of(
                "status", "started",
                "timestamp", Instant.now().toString()
        ));
    }

    /**
     * {@code GET /assert?pattern=...} — snapshot scan of the buffer against a Java regex.
     */
    private void handleAssert(Context ctx) {
        String pattern = ctx.queryParam("pattern");
        if (pattern == null || pattern.isBlank()) {
            ctx.status(400).json(Map.of("error", "pattern is required"));
            return;
        }

        List<String> matches;
        try {
            matches = buffer.findMatchingLines(pattern);
        } catch (PatternSyntaxException e) {
            ctx.status(400).json(Map.of("error", "invalid regex: " + e.getMessage()));
            return;
        }

        if (matches.isEmpty()) {
            ctx.status(404);
            return;
        }

        ctx.status(200).json(Map.of("lines", matches));
    }

    /**
     * {@code GET /health} — liveness probe used by {@code scripts/start.sh}.
     */
    private void handleHealth(Context ctx) {
        ctx.status(200).json(Map.of("status", "ok"));
    }
}
