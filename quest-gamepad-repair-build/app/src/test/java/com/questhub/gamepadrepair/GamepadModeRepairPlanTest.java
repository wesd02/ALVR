package com.questhub.gamepadrepair;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GamepadModeRepairPlanTest {
    @Test public void targetsOnlyPrimaryEmulatedGamepadFlag() {
        assertEquals("hzos_vendor_native", GamepadModeRepairPlan.NAMESPACE);
        assertEquals("oculus_emulated_gamepad", GamepadModeRepairPlan.KEY);
        assertEquals("device_config get hzos_vendor_native oculus_emulated_gamepad", GamepadModeRepairPlan.readCommand());
        assertEquals("device_config override hzos_vendor_native oculus_emulated_gamepad true", GamepadModeRepairPlan.enableCommand());
        assertEquals("device_config clear_override hzos_vendor_native oculus_emulated_gamepad", GamepadModeRepairPlan.restoreCommand());
        assertEquals("device_config list_local_overrides", GamepadModeRepairPlan.listOverridesCommand());
        assertEquals("device_config help", GamepadModeRepairPlan.helpCommand());
        assertFalse(GamepadModeRepairPlan.enableCommand().contains("kill_switch"));
        assertFalse(GamepadModeRepairPlan.restoreCommand().contains("kill_switch"));
        assertFalse(GamepadModeRepairPlan.enableCommand().contains("set_sync_disabled_for_tests"));
    }

    @Test public void recognizesOverrideCapabilityAndExactEntry() {
        String help = "Device Config commands:\n override NAMESPACE KEY VALUE\n clear_override NAMESPACE KEY\n list_local_overrides\n";
        assertTrue(GamepadModeRepairPlan.supportsStickyOverride(help));
        assertFalse(GamepadModeRepairPlan.supportsStickyOverride("get put delete reset"));
        assertTrue(GamepadModeRepairPlan.hasTargetOverride("hzos_vendor_native/oculus_emulated_gamepad=true\n"));
        assertFalse(GamepadModeRepairPlan.hasTargetOverride("hzos_vendor_native/oculus_emulated_gamepad=false\n"));
        assertFalse(GamepadModeRepairPlan.hasTargetOverride("other/oculus_emulated_gamepad=true\n"));
    }

    @Test public void onlyKnownBooleanUnderlyingValuesAreSafe() {
        assertTrue(GamepadModeRepairPlan.isSafeUnderlyingValue("false"));
        assertTrue(GamepadModeRepairPlan.isSafeUnderlyingValue("true"));
        assertTrue(GamepadModeRepairPlan.isSafeUnderlyingValue(" FALSE \n"));
        assertFalse(GamepadModeRepairPlan.isSafeUnderlyingValue("0"));
        assertFalse(GamepadModeRepairPlan.isSafeUnderlyingValue(""));
        assertFalse(GamepadModeRepairPlan.isSafeUnderlyingValue(null));
    }
}
