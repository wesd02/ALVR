package com.questhub.gamepadrepair;

import android.content.Context;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class DeepScanService {
    private static final int MAX_DEEP_PACKAGES = 220;
    private final Context context;

    public interface Progress {
        void onProgress(String message);
    }

    public static final class ScanReport {
        public final ForensicAnalyzer.Result analysis;
        public final String reportText;
        public final int scannedSources;
        public final int scannedPackages;

        ScanReport(ForensicAnalyzer.Result analysis, String reportText, int scannedSources, int scannedPackages) {
            this.analysis = analysis;
            this.reportText = reportText;
            this.scannedSources = scannedSources;
            this.scannedPackages = scannedPackages;
        }
    }

    public DeepScanService(Context context) {
        this.context = context.getApplicationContext();
    }

    public ScanReport scanEverything(Progress progress) throws Exception {
        LinkedHashMap<String, String> sources = new LinkedHashMap<>();
        List<String> commands = DeepScanPlan.baseCommands();
        for (int i = 0; i < commands.size(); i++) {
            String command = commands.get(i);
            String source = DeepScanPlan.sourceName(command);
            emit(progress, "Read-only probe " + (i + 1) + "/" + commands.size() + ": " + source);
            String output = probe(command);
            sources.put(source, output);
            if ("identity".equals(source) && !output.contains("uid=2000")) {
                throw new IllegalStateException("Deep scan requires verified Android shell UID 2000; observed: " + oneLine(output));
            }
        }

        List<String> packageNames = parsePackageNames(sources.get("packages"));
        List<String> candidates = new ArrayList<>();
        for (String packageName : packageNames) if (DeepScanPlan.isCandidatePackage(packageName)) candidates.add(packageName);
        candidates.sort(Comparator.comparingInt(DeepScanService::packagePriority).thenComparing(String::compareTo));

        StringBuilder packageEvidence = new StringBuilder();
        int scannedPackages = 0;
        for (String packageName : candidates) {
            if (scannedPackages >= MAX_DEEP_PACKAGES) break;
            if (!packageName.matches("[A-Za-z0-9._]+")) continue;
            if (scannedPackages == 0 || scannedPackages % 10 == 0) {
                emit(progress, "Inspecting Meta/system package metadata " + (scannedPackages + 1) + "/" + Math.min(candidates.size(), MAX_DEEP_PACKAGES) + "…");
            }
            String dump = probe("dumpsys package " + packageName);
            if (!dump.startsWith("<ERROR:")) packageEvidence.append(ForensicAnalyzer.relevantPackageLines(packageName, dump));
            scannedPackages++;
        }
        if (candidates.size() > MAX_DEEP_PACKAGES) {
            packageEvidence.append("\n[scanner] Candidate package cap reached: scanned ").append(MAX_DEEP_PACKAGES)
                    .append(" of ").append(candidates.size()).append(" candidates.\n");
        }
        sources.put("package_dump", packageEvidence.toString());

        String binderEvidence = scanInterestingBinderServices(sources.get("service_list"), progress);
        if (!binderEvidence.isEmpty()) sources.put("rollout_service_dump", binderEvidence);

        ForensicAnalyzer.Result analysis = ForensicAnalyzer.analyze(sources);
        String reportText = renderReport(analysis, sources, packageEvidence.toString());
        emit(progress, "Deep forensic scan complete: " + analysis.verdict.name());
        return new ScanReport(analysis, reportText, sources.size(), scannedPackages);
    }

    private String scanInterestingBinderServices(String serviceList, Progress progress) {
        if (serviceList == null || serviceList.startsWith("<ERROR:")) return "";
        Set<String> serviceNames = new LinkedHashSet<>();
        for (String line : serviceList.split("\\r?\\n")) {
            String lower = line.toLowerCase(Locale.US);
            if (!(lower.contains("mobileconfig") || lower.contains("gatekeeper") || lower.contains("oculus") && lower.contains("config"))) continue;
            int firstSpace = line.indexOf(' ');
            int colon = line.indexOf(':', Math.max(0, firstSpace + 1));
            if (firstSpace >= 0 && colon > firstSpace) {
                String name = line.substring(firstSpace + 1, colon).trim();
                if (name.matches("[A-Za-z0-9._:-]{1,80}")) serviceNames.add(name);
            }
        }
        StringBuilder out = new StringBuilder();
        for (String service : serviceNames) {
            emit(progress, "Inspecting rollout Binder service: " + service);
            String dump = probe("dumpsys " + service);
            if (!dump.startsWith("<ERROR:")) out.append(ForensicAnalyzer.relevantPackageLines("binder:" + service, dump));
        }
        return out.toString();
    }

    private String probe(String command) {
        try {
            return AdbClient.shell(context, command);
        } catch (Exception e) {
            return "<ERROR: " + shortError(e) + ">";
        }
    }

    public static List<String> parsePackageNames(String output) {
        if (output == null || output.trim().isEmpty()) return Collections.emptyList();
        List<String> out = new ArrayList<>();
        for (String raw : output.split("\\r?\\n")) {
            String line = raw.trim();
            if (!line.startsWith("package:")) continue;
            String value = line.substring("package:".length()).trim();
            int space = value.indexOf(' ');
            if (space >= 0) value = value.substring(0, space);
            int equals = value.lastIndexOf('=');
            if (equals >= 0 && equals + 1 < value.length()) value = value.substring(equals + 1);
            if (!value.isEmpty()) out.add(value);
        }
        return out;
    }

    public static String renderReport(ForensicAnalyzer.Result analysis, Map<String, String> sources, String packageEvidence) {
        StringBuilder out = new StringBuilder();
        out.append("QUEST GAMEPAD FORENSIC SCAN v6\n")
                .append("Mode: READ-ONLY. No settings, properties, packages, services, or rollout values were changed.\n\n")
                .append("VERDICT: ").append(analysis.verdict.name()).append('\n')
                .append(analysis.summary).append("\n\n");

        out.append("DEVICE / ADB FACTS\n");
        appendIfPresent(out, "identity", sources.get("identity"));
        appendSelectedProperties(out, sources.get("properties"));
        out.append('\n');

        out.append("ROLLOUT INFRASTRUCTURE\n");
        String disabled = sources.get("disabled_packages");
        if (disabled == null || disabled.trim().isEmpty()) out.append("Disabled packages: none reported by pm.\n");
        else out.append("Disabled packages:\n").append(disabled.trim()).append('\n');
        out.append('\n');

        out.append("HIGH-VALUE FEATURE / ROLLOUT EVIDENCE\n");
        if (analysis.highValueHits.isEmpty()) out.append("No keyword-correlated evidence found.\n");
        else for (String hit : analysis.highValueHits) out.append(hit).append('\n');
        out.append('\n');

        out.append("META / SYSTEM PACKAGE METADATA EVIDENCE\n");
        if (packageEvidence == null || packageEvidence.trim().isEmpty()) out.append("No relevant package metadata lines found.\n");
        else out.append(packageEvidence.trim()).append('\n');
        out.append('\n');

        out.append("PROBE COVERAGE\n");
        for (Map.Entry<String, String> entry : sources.entrySet()) {
            String value = entry.getValue() == null ? "" : entry.getValue();
            out.append("- ").append(entry.getKey()).append(": ");
            if (value.startsWith("<ERROR:")) out.append(value);
            else out.append(value.length()).append(" chars inspected");
            out.append('\n');
        }
        out.append("\nPrivacy: broad sources were inspected locally, but unrelated raw settings/logcat text is intentionally omitted from this report.\n");
        return out.toString();
    }

    private static void appendSelectedProperties(StringBuilder out, String properties) {
        if (properties == null) return;
        String[] needles = {
                "[ro.product.model]", "[ro.product.device]", "[ro.product.name]", "[ro.product.board]",
                "[ro.boot.hardware.sku]", "[ro.build.version.incremental]", "[ro.build.version.release]",
                "[ro.build.fingerprint]", "[ro.build.version.security_patch]", "[ro.build.type]"
        };
        for (String line : properties.split("\\r?\\n")) {
            String lower = line.toLowerCase(Locale.US);
            for (String needle : needles) {
                if (lower.contains(needle)) {
                    out.append(line.trim()).append('\n');
                    break;
                }
            }
        }
    }

    private static void appendIfPresent(StringBuilder out, String label, String value) {
        if (value != null && !value.trim().isEmpty()) out.append(label).append(": ").append(oneLine(value)).append('\n');
    }

    private static int packagePriority(String packageName) {
        String p = packageName.toLowerCase(Locale.US);
        if (p.equals("com.oculus.gatekeeperservice") || p.equals("com.oculus.systemux") || p.equals("com.oculus.vrshell")
                || p.equals("com.oculus.systemactivities") || p.equals("com.oculus.ocms") || p.equals("com.oculus.panelapp.settings")
                || p.equals("com.android.settings") || p.equals("com.android.systemui")) return 0;
        if (p.startsWith("com.oculus.") || p.startsWith("com.meta.") || p.startsWith("com.facebook.")) return 1;
        return 2;
    }

    private static void emit(Progress progress, String message) {
        if (progress != null) progress.onProgress(message);
    }

    private static String shortError(Exception e) {
        String message = e.getMessage();
        if (message == null || message.trim().isEmpty()) message = e.getClass().getSimpleName();
        message = oneLine(message);
        return message.length() > 180 ? message.substring(0, 180) : message;
    }

    private static String oneLine(String value) {
        return value == null ? "" : value.replace('\n', ' ').replace('\r', ' ').trim();
    }
}
