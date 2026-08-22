package com.questhub.gamepadrepair;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class ShellFramingTest {
    @Test
    public void scriptBuildsMarkersWithoutEmbeddingRawDelimiters() {
        String marker = "QGR_ABC123";
        String script = ShellFraming.script("id", marker);
        assertTrue(script.contains("stty -echo"));
        assertTrue(script.contains("PS1=''"));
        assertTrue(script.contains("PS2=''"));
        assertFalse("raw begin marker must not appear in echoed input", script.contains(ShellFraming.begin(marker)));
        assertFalse("raw end marker must not appear in echoed input", script.contains(ShellFraming.end(marker)));
        assertTrue(script.endsWith("exit\n"));
        assertFalse(script.contains("shell:id"));
    }

    @Test
    public void extractsOnlyCommandOutputBetweenMarkers() {
        String marker = "QGR_ABC123";
        String transcript = "quest:/ $ stty -echo\r\n"
                + ShellFraming.begin(marker) + "\r\n"
                + "uid=2000(shell) gid=2000(shell)\r\n"
                + ShellFraming.end(marker) + "\r\nquest:/ $ ";
        assertEquals("uid=2000(shell) gid=2000(shell)", ShellFraming.extract(transcript, marker));
    }

    @Test
    public void echoedCommandsCannotFakeCompletion() {
        String marker = "QGR_ABC123";
        String begin = ShellFraming.begin(marker);
        String end = ShellFraming.end(marker);
        String script = ShellFraming.script("id", marker);
        String echoedOnly = "quest:/ $ " + script.replace("\n", "\r\nquest:/ $ ");

        assertFalse(script.contains(begin));
        assertFalse(script.contains(end));
        assertFalse("echoed input alone must never contain the completion delimiter",
                ShellFraming.isComplete(echoedOnly, marker));

        String actualTranscript = echoedOnly
                + "\r\nquest:/ $ " + begin + "\r\n"
                + "uid=2000(shell) gid=2000(shell) groups=1003(graphics)\r\n"
                + "quest:/ $ " + end + "\r\n";
        assertTrue(ShellFraming.isComplete(actualTranscript, marker));
        assertEquals("uid=2000(shell) gid=2000(shell) groups=1003(graphics)\nquest:/ $",
                ShellFraming.extract(actualTranscript, marker));
    }

    @Test(expected = IllegalStateException.class)
    public void missingEndMarkerIsRejected() {
        String marker = "QGR_ABC123";
        ShellFraming.extract(ShellFraming.begin(marker) + "\npartial", marker);
    }

    @Test
    public void detectsEndMarkerAcrossAccumulatedTranscript() {
        String marker = "QGR_ABC123";
        assertFalse(ShellFraming.isComplete("noise\n" + ShellFraming.begin(marker) + "\nabc", marker));
        assertTrue(ShellFraming.isComplete("noise\n" + ShellFraming.begin(marker) + "\nabc\n" + ShellFraming.end(marker), marker));
    }
}
