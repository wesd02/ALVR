package com.questhub.gamepadrepair;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

public final class DeepScanServiceTest {
    @Test
    public void parsesPackageNamesFromPathUidAndPlainPmFormats() {
        String output = "package:/system_ext/priv-app/SystemUX/SystemUX.apk=com.oculus.systemux uid:1000\n"
                + "package:com.oculus.gatekeeperservice\n"
                + "package:/data/app/abc/base.apk=com.example.weather uid:10123\n";
        List<String> packages = DeepScanService.parsePackageNames(output);
        assertEquals(3, packages.size());
        assertEquals("com.oculus.systemux", packages.get(0));
        assertEquals("com.oculus.gatekeeperservice", packages.get(1));
        assertEquals("com.example.weather", packages.get(2));
    }

    @Test
    public void reportClearlyStatesReadOnlyAndIncludesVerdictAndHits() {
        ForensicAnalyzer.Result analysis = ForensicAnalyzer.analyze(java.util.Map.of(
                "packages", "package:com.oculus.gatekeeperservice\n",
                "disabled_packages", "package:com.oculus.gatekeeperservice\n"));
        String report = DeepScanService.renderReport(analysis, java.util.Map.of(
                "identity", "uid=2000(shell)",
                "disabled_packages", "package:com.oculus.gatekeeperservice"),
                "[com.oculus.gatekeeperservice] versionName=2.7");
        String lower = report.toLowerCase();
        assertTrue(lower.contains("read-only"));
        assertTrue(lower.contains("local_rollout_infra_blocked"));
        assertTrue(lower.contains("gatekeeper"));
        assertTrue(lower.contains("versionname=2.7"));
    }
}
