package com.questhub.gamepadrepair;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.junit.Test;

public final class AdbExecTransportTest {
    @Test
    public void buildsOneShotExecDestination() {
        assertEquals("exec:id", AdbExecTransport.destination("id"));
        assertEquals("exec:settings list global", AdbExecTransport.destination("settings list global"));
    }

    @Test
    public void execDestinationHasNoInteractiveShellFraming() {
        String destination = AdbExecTransport.destination("getprop ro.build.version.release");
        assertFalse(destination.startsWith("shell:"));
        assertFalse(destination.contains("QGR_BEGIN"));
        assertFalse(destination.contains("QGR_END"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNewlines() {
        AdbExecTransport.destination("id\nuname");
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsEmptyCommand() {
        AdbExecTransport.destination("");
    }
}
