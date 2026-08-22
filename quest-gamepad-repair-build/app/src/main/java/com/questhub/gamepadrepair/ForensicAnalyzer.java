package com.questhub.gamepadrepair;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ForensicAnalyzer {
    public enum Verdict {
        LOCAL_ROLLOUT_INFRA_BLOCKED,
        EXPLICIT_LOCAL_GATE_FOUND,
        REMOTE_ROLLOUT_LIKELY,
        CODE_NOT_CONFIRMED
    }

    public static final class Result {
        public final Verdict verdict;
        public final String summary;
        public final List<String> highValueHits;
        public final boolean gamepadCodeEvidence;
        public final boolean rolloutInfrastructureEvidence;

        Result(Verdict verdict, String summary, List<String> highValueHits,
               boolean gamepadCodeEvidence, boolean rolloutInfrastructureEvidence) {
            this.verdict = verdict;
            this.summary = summary;
            this.highValueHits = Collections.unmodifiableList(new ArrayList<>(highValueHits));
            this.gamepadCodeEvidence = gamepadCodeEvidence;
            this.rolloutInfrastructureEvidence = rolloutInfrastructureEvidence;
        }
    }

    private static final int MAX_HITS = 240;

    private ForensicAnalyzer() {}

    public static Result analyze(Map<String, String> sources) {
        String packages = lower(sources.get("packages"));
        String disabled = lower(sources.get("disabled_packages"));
        boolean gatekeeperInstalled = packages.contains("com.oculus.gatekeeperservice");
        boolean gatekeeperDisabled = disabled.contains("com.oculus.gatekeeperservice");

        List<String> hits = collectHighValueHits(sources);
        boolean explicitLocalGate = false;
        boolean gamepadEvidence = false;
        boolean rolloutEvidence = gatekeeperInstalled;

        for (Map.Entry<String, String> entry : sources.entrySet()) {
            String source = entry.getKey() == null ? "unknown" : entry.getKey();
            String text = entry.getValue() == null ? "" : entry.getValue();
            for (String line : text.split("\\r?\\n")) {
                String l = line.toLowerCase(Locale.US);
                if (isDirectGamepadEvidence(l)) gamepadEvidence = true;
                if (isRolloutEvidence(l)) rolloutEvidence = true;
                if (isLocalConfigSource(source) && isDirectGamepadEvidence(l) && isExplicitlyDisabled(l)) {
                    explicitLocalGate = true;
                }
            }
        }

        if (gatekeeperDisabled) {
            return new Result(
                    Verdict.LOCAL_ROLLOUT_INFRA_BLOCKED,
                    "Meta GatekeeperService is installed but disabled. That is a strong local reason staged features may never be provisioned to this headset.",
                    hits, gamepadEvidence, rolloutEvidence);
        }
        if (explicitLocalGate) {
            return new Result(
                    Verdict.EXPLICIT_LOCAL_GATE_FOUND,
                    "A shell-visible local Gamepad/Touch-controller configuration is explicitly disabled. This is stronger evidence than a generic staged-rollout explanation.",
                    hits, gamepadEvidence, rolloutEvidence);
        }
        if (gamepadEvidence && gatekeeperInstalled && !gatekeeperDisabled) {
            return new Result(
                    Verdict.REMOTE_ROLLOUT_LIKELY,
                    "Gamepad Mode-related code/metadata is present while Meta's Gatekeeper rollout package is installed and enabled, but no explicit local disabled gate was found. A remote MobileConfig/Gatekeeper treatment is the leading explanation.",
                    hits, true, rolloutEvidence);
        }
        return new Result(
                Verdict.CODE_NOT_CONFIRMED,
                "No explicit local disabled gate was found, but shell-visible metadata did not confirm the Gamepad Mode implementation strongly enough to distinguish a binary/package rollout from a remote treatment.",
                hits, gamepadEvidence, rolloutEvidence);
    }

    public static List<String> collectHighValueHits(Map<String, String> sources) {
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, String> entry : sources.entrySet()) {
            String source = entry.getKey() == null ? "unknown" : entry.getKey();
            String text = entry.getValue() == null ? "" : entry.getValue();
            for (String line : text.split("\\r?\\n")) {
                String l = line.toLowerCase(Locale.US);
                if (isDirectGamepadEvidence(l) || isRolloutEvidence(l) || isPackageVersionLine(l)) {
                    String trimmed = line.trim();
                    if (!trimmed.isEmpty()) out.add("[" + source + "] " + trimmed);
                    if (out.size() >= MAX_HITS) return out;
                }
            }
        }
        return out;
    }

    public static String relevantPackageLines(String packageName, String dump) {
        StringBuilder out = new StringBuilder();
        boolean wroteHeader = false;
        String[] lines = dump == null ? new String[0] : dump.split("\\r?\\n");
        for (String line : lines) {
            String l = line.toLowerCase(Locale.US);
            if (isDirectGamepadEvidence(l) || isRolloutEvidence(l) || isPackageVersionLine(l) || l.contains("enabled=")) {
                if (!wroteHeader) {
                    out.append("\n===== ").append(packageName).append(" =====\n");
                    wroteHeader = true;
                }
                out.append(line.trim()).append('\n');
            }
        }
        return out.toString();
    }

    private static boolean isLocalConfigSource(String source) {
        return source.startsWith("settings_") || "device_config".equals(source) || "properties".equals(source);
    }

    private static boolean isExplicitlyDisabled(String l) {
        String compact = l.replace(" ", "");
        return compact.endsWith("=0") || compact.endsWith("=false") || compact.endsWith("=off") || compact.endsWith("=disabled")
                || compact.contains(":0") || compact.contains(":false") || compact.contains(":off") || compact.contains(":disabled");
    }

    private static boolean isDirectGamepadEvidence(String l) {
        String compact = l.replace("_", "").replace("-", "").replace(" ", "");
        if (compact.contains("touchcontrollergamepad") || compact.contains("touchcontrollersgamepad")) return true;
        if (compact.contains("xboxgamepad") || compact.contains("gamepademulation") || compact.contains("gamepademulator")) return true;
        if (compact.contains("gamepadmode") && !compact.contains("gamepadgazemode")) return true;
        return l.contains("gamepad") && (l.contains("touch") || l.contains("controller") || l.contains("xbox") || l.contains("emulat"));
    }

    private static boolean isRolloutEvidence(String l) {
        return l.contains("mobileconfig") || l.contains("mobile_config") || l.contains("gatekeeper") || l.contains("remoteconfig")
                || l.contains("remote_config") || l.contains("featureflag") || l.contains("feature_flag") || l.contains("rollout")
                || l.contains("treatment") || l.contains("killswitch") || l.contains("kill_switch") || l.contains("experiment");
    }

    private static boolean isPackageVersionLine(String l) {
        return l.contains("versioncode=") || l.contains("versionname=") || l.contains("lastupdatetime=");
    }

    private static String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.US);
    }
}
