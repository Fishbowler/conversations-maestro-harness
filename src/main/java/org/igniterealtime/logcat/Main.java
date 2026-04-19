package org.igniterealtime.logcat;

import java.util.logging.Logger;

/**
 * Entry point for the logcat sidecar.
 *
 * <p>Reads configuration from environment variables, wires up {@link LogcatBuffer}
 * and {@link SidecarServer}, and registers a shutdown hook for graceful stop.
 */
public class Main {

    private static final Logger LOG = Logger.getLogger(Main.class.getName());

    /** Default buffer capacity in lines. */
    private static final int DEFAULT_BUFFER_CAPACITY = 10_000;

    /** Default HTTP port. */
    private static final int DEFAULT_PORT = 17777;

    /**
     * Application entry point.
     *
     * @param args unused
     */
    public static void main(String[] args) {
        int port = DEFAULT_PORT;
        String portEnv = System.getenv("PORT");
        if (portEnv != null && !portEnv.isBlank()) {
            try {
                port = Integer.parseInt(portEnv.trim());
            } catch (NumberFormatException e) {
                LOG.warning("Invalid PORT value '" + portEnv + "', using default " + DEFAULT_PORT);
            }
        }

        LogcatBuffer buffer = new LogcatBuffer(DEFAULT_BUFFER_CAPACITY);
        SidecarServer server = new SidecarServer(buffer, port);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LOG.info("Shutting down sidecar...");
            server.stop();
            buffer.stop();
        }, "shutdown-hook"));

        buffer.start();
        server.start();
    }
}
