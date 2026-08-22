package com.questhub.gamepadrepair;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.Test;

public final class ForensicAnalyzerTest {
    @Test
    public void disabledGatekeeperIsStrongLocalCause() {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("disabled_packages", "package:com.oculus.gatekeeperservice\n");
        sources.put("packages", "package:com.oculus.gatekeeperservice\npackage:com.oculus.systemux\n");
        ForensicAnalyzer.Result result = ForensicAnalyzer.analyze(sources);
        assertEquals(ForensicAnalyzer.Verdict.LOCAL_ROLLOUT_INFRA_BLOCKED, result.verdict);
        assertTrue(result.summary.toLowerCase().contains("gatekeeper"));
    }

    @Test
    public void explicitDisabledGamepadFlagWinsOverRemoteRolloutGuess() {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("settings_global", "touch_controller_gamepad_mode=0\n");
        sources.put("packages", "package:com.oculus.gatekeeperservice\n");
        ForensicAnalyzer.Result result = ForensicAnalyzer.analyze(sources);
        assertEquals(ForensicAnalyzer.Verdict.EXPLICIT_LOCAL_GATE_FOUND, result.verdict);
        assertTrue(result.highValueHits.toString().contains("touch_controller_gamepad_mode=0"));
    }

    @Test
    public void codePresentAndRolloutInfraHealthyMeansRemoteTreatmentLikely() {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("packages", "package:com.oculus.gatekeeperservice\npackage:com.oculus.mobileconfig\n");
        sources.put("disabled_packages", "");
        sources.put("package_dump", "GamepadModeControllerActivity\nTouchControllerXboxEmulationService\nMobileConfigService\n");
        sources.put("services", "MobileConfigService running\nGatekeeperService running\n");
        ForensicAnalyzer.Result result = ForensicAnalyzer.analyze(sources);
        assertEquals(ForensicAnalyzer.Verdict.REMOTE_ROLLOUT_LIKELY, result.verdict);
        assertTrue(result.summary.toLowerCase().contains("remote"));
    }

    @Test
    public void noGamepadEvidenceCannotClaimRemoteRollout() {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("packages", "package:com.oculus.gatekeeperservice\n");
        sources.put("disabled_packages", "");
        sources.put("package_dump", "ordinary controller settings\n");
        ForensicAnalyzer.Result result = ForensicAnalyzer.analyze(sources);
        assertEquals(ForensicAnalyzer.Verdict.CODE_NOT_CONFIRMED, result.verdict);
    }
}
