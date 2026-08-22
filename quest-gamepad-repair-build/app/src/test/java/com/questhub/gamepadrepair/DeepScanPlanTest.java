package com.questhub.gamepadrepair;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class DeepScanPlanTest {
    @Test
    public void basePlanCoversRolloutAndInputSurfaces() {
        String joined = String.join("\n", DeepScanPlan.baseCommands()).toLowerCase();
        assertTrue(joined.contains("getprop"));
        assertTrue(joined.contains("settings list global"));
        assertTrue(joined.contains("device_config list"));
        assertTrue(joined.contains("pm list packages -d"));
        assertTrue(joined.contains("service list"));
        assertTrue(joined.contains("cmd overlay list"));
        assertTrue(joined.contains("dumpsys input"));
        assertTrue(joined.contains("dumpsys activity services"));
        assertTrue(joined.contains("dumpsys activity providers"));
        assertTrue(joined.contains("dumpsys jobscheduler"));
        assertTrue(joined.contains("logcat"));
    }

    @Test
    public void basePlanContainsNoMutatingCommands() {
        for (String command : DeepScanPlan.baseCommands()) {
            String c = command.toLowerCase();
            assertFalse(c.contains("setprop "));
            assertFalse(c.contains("settings put"));
            assertFalse(c.contains("settings delete"));
            assertFalse(c.contains("device_config put"));
            assertFalse(c.contains("device_config delete"));
            assertFalse(c.contains("pm disable"));
            assertFalse(c.contains("pm enable"));
            assertFalse(c.contains("pm clear"));
            assertFalse(c.contains("force-stop"));
            assertFalse(c.contains(" reboot"));
            assertFalse(c.startsWith("reboot"));
            assertFalse(c.contains(" rm "));
        }
    }

    @Test
    public void vendorPackagesAndInputPackagesAreDeepScanCandidates() {
        assertTrue(DeepScanPlan.isCandidatePackage("com.oculus.gatekeeperservice"));
        assertTrue(DeepScanPlan.isCandidatePackage("com.oculus.systemux"));
        assertTrue(DeepScanPlan.isCandidatePackage("com.meta.xr.system"));
        assertTrue(DeepScanPlan.isCandidatePackage("com.android.settings"));
        assertTrue(DeepScanPlan.isCandidatePackage("com.android.inputdevices"));
        assertTrue(DeepScanPlan.isCandidatePackage("com.example.xboxcontrollerhelper"));
        assertFalse(DeepScanPlan.isCandidatePackage("com.example.weather"));
    }
}
