package org.igniterealtime.logcat;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link LogcatBuffer#findMatchingLines(String)}.
 *
 * <p>Uses reflection to inject lines directly, avoiding the need for a live adb process.
 */
class LogcatBufferTest {

    /**
     * Verifies that a line added after {@code clear()} is matched, and that a line
     * present before {@code clear()} is not.
     */
    @Test
    void findMatchingLines_afterClear_onlyPostClearLinesAreVisible() throws Exception {
        LogcatBuffer buf = new LogcatBuffer(100);

        appendLine(buf, "pre-clear: Conversations connected to server");
        buf.clear();
        appendLine(buf, "post-clear: Conversations session started");

        List<String> matches = buf.findMatchingLines("Conversations");

        assertEquals(1, matches.size(), "Only the post-clear line should match");
        assertTrue(matches.get(0).contains("post-clear"), "Matched line should be the post-clear one");
    }

    // ------------------------------------------------------------------

    private static void appendLine(LogcatBuffer buf, String line) throws Exception {
        Method m = LogcatBuffer.class.getDeclaredMethod("appendLine", String.class);
        m.setAccessible(true);
        m.invoke(buf, line);
    }
}
