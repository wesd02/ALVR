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
        assertEquals(GamepadModeRepairPlan.listOverridesCommand(), GamepadModeRepairPlan.capabilityProbeCommand());
        assertFalse(GamepadModeRepairPlan.enableCommand().contains("kill_switch"));
        assertFalse(GamepadModeRepairPlan.restoreCommand().contains("kill_switch"));
        assertFalse(GamepadModeRepairPlan.enableCommand().contains("set_sync_disabled_for_tests"));
    }

    @Test public void directProbeAcceptsEmptySuccessButRejectsCommandErrors() {
        assertTrue(GamepadModeRepairPlan.isCapabilityProbeSuccessful(""));
        assertTrue(GamepadModeRepairPlan.isCapabilityProbeSuccessful("hzos_vendor_native/other_flag=true\n"));
        assertFalse(GamepadModeRepairPlan.isCapabilityProbeSuccessful("Invalid command: list_local_overrides"));
        assertFalse(GamepadModeRepairPlan.isCapabilityProbeSuccessful("Unknown command list_local_overrides"));
        assertFalse(GamepadModeRepairPlan.isCapabilityProbeSuccessful("Error: unsupported command"));
        assertFalse(GamepadModeRepairPlan.isCapabilityProbeSuccessful(null));
    }

    @Test public void exactTargetOverrideIsRecognized() {
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
