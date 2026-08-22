package com.questhub.gamepadrepair;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ActivationEvidenceTest {
    @Test public void keepsGamepadAndControllerActivationLines() {
        String raw = "noise line\n"
                + "I SystemShell: emulated_gamepad entered gamepad mode\n"
                + "I Input: KEYCODE_BUTTON_MODE controller event\n"
                + "I Bluetooth: unrelated\n"
                + "D Oculus: XboxGamepad device created\n";
        String filtered = ActivationEvidence.filter(raw);
        assertTrue(filtered.contains("emulated_gamepad"));
        assertTrue(filtered.contains("KEYCODE_BUTTON_MODE"));
        assertTrue(filtered.contains("XboxGamepad"));
        assertFalse(filtered.contains("noise line"));
        assertFalse(filtered.contains("Bluetooth: unrelated"));
    }

    @Test public void recognizesActivationSignals() {
        assertTrue(ActivationEvidence.hasActivationSignal("created XboxGamepad through uhid"));
        assertTrue(ActivationEvidence.hasActivationSignal("emulated_gamepad gamepad mode enabled"));
        assertFalse(ActivationEvidence.hasActivationSignal("controller battery changed"));
        assertFalse(ActivationEvidence.hasActivationSignal(""));
    }
}
