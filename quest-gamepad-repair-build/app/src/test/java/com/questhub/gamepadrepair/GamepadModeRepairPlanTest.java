package com.questhub.gamepadrepair;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class GamepadModeRepairPlanTest {
    @Test public void targetsOnlyPrimaryEmulatedGamepadFlag() {
        assertEquals("hzos_vendor_native", GamepadModeRepairPlan.NAMESPACE);
        assertEquals("oculus_emulated_gamepad", GamepadModeRepairPlan.KEY);
        assertEquals("device_config get hzos_vendor_native oculus_emulated_gamepad", GamepadModeRepairPlan.readCommand());
        assertEquals("device_config put hzos_vendor_native oculus_emulated_gamepad true", GamepadModeRepairPlan.enableCommand());
        assertEquals("device_config put hzos_vendor_native oculus_emulated_gamepad false", GamepadModeRepairPlan.restoreCommand());
        assertFalse(GamepadModeRepairPlan.enableCommand().contains("kill_switch"));
        assertFalse(GamepadModeRepairPlan.restoreCommand().contains("kill_switch"));
    }

    @Test public void onlyExactFalseMayBeEnabled() {
        assertEquals(true, GamepadModeRepairPlan.canEnable("false"));
        assertEquals(true, GamepadModeRepairPlan.canEnable(" FALSE \n"));
        assertEquals(false, GamepadModeRepairPlan.canEnable("true"));
        assertEquals(false, GamepadModeRepairPlan.canEnable("0"));
        assertEquals(false, GamepadModeRepairPlan.canEnable(""));
        assertEquals(false, GamepadModeRepairPlan.canEnable(null));
    }
}
