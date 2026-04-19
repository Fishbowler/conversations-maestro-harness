package org.igniterealtime.logcat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Captures lines from {@code adb logcat} into a fixed-capacity circular buffer.
 *
 * <p>Thread-safe; all mutable state is guarded by a {@link ReentrantLock}.
 */
public class LogcatBuffer {

    private static final Logger LOG = Logger.getLogger(LogcatBuffer.class.getName());

    private final int capacity;
    private final Deque<String> buffer;
    private final ReentrantLock lock = new ReentrantLock();
    private Thread readerThread;
    private Process adbProcess;

    /**
     * Creates a buffer with the given maximum line capacity.
     *
     * @param capacity maximum number of lines retained; oldest lines are evicted when full
     */
    public LogcatBuffer(int capacity) {
        this.capacity = capacity;
        this.buffer = new ArrayDeque<>(capacity);
    }

    /**
     * Starts the {@code adb logcat} subprocess and begins capturing output.
     *
     * <p>The tag filter is read from the {@code LOGCAT_TAGS} environment variable;
     * it defaults to {@code Conversations:* *:S}.
     *
     * @throws RuntimeException if {@code adb} is not found on PATH
     */
    public void start() {
        verifyAdbOnPath();

        String tags = System.getenv("LOGCAT_TAGS");
        if (tags == null || tags.isBlank()) {
            tags = "conversations:* *:S";
        }

        List<String> cmd = new ArrayList<>();
        cmd.add("adb");
        cmd.add("logcat");
        cmd.addAll(List.of(tags.split("\\s+")));

        try {
            adbProcess = new ProcessBuilder(cmd)
                    .redirectErrorStream(true)
                    .start();
        } catch (IOException e) {
            throw new RuntimeException("Failed to start adb logcat: " + e.getMessage(), e);
        }

        readerThread = new Thread(this::readLoop, "logcat-reader");
        readerThread.setDaemon(true);
        readerThread.start();
        LOG.info("logcat capture started with tags: " + tags);
    }

    /**
     * Stops the logcat subprocess and the reader thread.
     */
    public void stop() {
        if (adbProcess != null) {
            adbProcess.destroy();
        }
        if (readerThread != null) {
            readerThread.interrupt();
        }
    }

    /**
     * Discards all buffered lines.
     */
    public void clear() {
        lock.lock();
        try {
            buffer.clear();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns all buffered lines whose full text matches {@code regex}, in receipt order.
     *
     * @param regex Java regular expression; matched via {@link Pattern#find()} against each line
     * @return matched lines, or an empty list if none match
     * @throws PatternSyntaxException if {@code regex} is not a valid Java regex
     */
    public List<String> findMatchingLines(String regex) {
        Pattern pattern = Pattern.compile(regex);
        List<String> matches = new ArrayList<>();
        lock.lock();
        try {
            for (String line : buffer) {
                if (pattern.matcher(line).find()) {
                    matches.add(line);
                }
            }
        } finally {
            lock.unlock();
        }
        return matches;
    }

    // ------------------------------------------------------------------

    private void readLoop() {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(adbProcess.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                appendLine(line);
            }
        } catch (IOException e) {
            if (!Thread.currentThread().isInterrupted()) {
                LOG.log(Level.WARNING, "logcat reader stopped unexpectedly", e);
            }
        }
    }

    private void appendLine(String line) {
        lock.lock();
        try {
            if (buffer.size() >= capacity) {
                buffer.pollFirst();
            }
            buffer.addLast(line);
        } finally {
            lock.unlock();
        }
    }

    private static void verifyAdbOnPath() {
        try {
            Process p = new ProcessBuilder("adb", "version")
                    .redirectErrorStream(true)
                    .start();
            p.waitFor();
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(
                    "adb not found on PATH. Install Android SDK platform-tools and ensure adb is on PATH.",
                    e);
        }
    }
}
