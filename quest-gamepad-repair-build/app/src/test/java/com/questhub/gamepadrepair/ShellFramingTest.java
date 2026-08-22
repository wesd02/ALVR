package com.questhub.gamepadrepair;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class ShellFramingTest {
    @Test
    public void scriptUsesInteractiveShellMarkersAndExit() {
        String marker = "QGR_ABC123";
        String script = ShellFraming.script("id", marker);
        assertTrue(script.contains("stty -echo"));
        assertTrue(script.contains(ShellFraming.begin(marker)));
        assertTrue(script.contains(ShellFraming.end(marker)));
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
