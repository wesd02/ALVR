package com.questhub.gamepadrepair;

import android.content.Context;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class RepairService {
    private static final String EXPERIMENTAL_PROPERTY = "debug.oculus.experimentalEnabled";
    private static final int MAX_AUTOMATIC_MUTATIONS = 6;
    private final Context context;
    private final RollbackJournal journal;

    public RepairService(Context context) {
        this.context = context.getApplicationContext();
        this.journal = new RollbackJournal(this.context);
    }

    public static final class DiagnosticReport {
        public final String build, androidRelease, identity, experimentalValue;
        public final boolean shellUid;
        public final List<RepairPlanner.Candidate> candidates;
        public final List<RepairPlanner.Mutation> plan;
        public final List<String> warnings;
        DiagnosticReport(String build, String androidRelease, String identity, String experimentalValue, boolean shellUid,
                         List<RepairPlanner.Candidate> candidates, List<RepairPlanner.Mutation> plan, List<String> warnings) {
            this.build = build; this.androidRelease = androidRelease; this.identity = identity; this.experimentalValue = experimentalValue; this.shellUid = shellUid;
            this.candidates = Collections.unmodifiableList(new ArrayList<>(candidates));
            this.plan = Collections.unmodifiableList(new ArrayList<>(plan));
            this.warnings = Collections.unmodifiableList(new ArrayList<>(warnings));
        }
    }

    public static final class RepairResult {
        public final boolean success;
        public final int changed;
        public final List<String> messages;
        RepairResult(boolean success, int changed, List<String> messages) { this.success = success; this.changed = changed; this.messages = Collections.unmodifiableList(new ArrayList<>(messages)); }
    }

    public DiagnosticReport diagnose() throws Exception {
        String identity = AdbClient.shell(context, "id");
        boolean shellUid = identity.contains("uid=2000");
        String build = safeShell("getprop ro.build.version.incremental");
        String release = safeShell("getprop ro.build.version.release");
        String experimental = safeShell("getprop " + EXPERIMENTAL_PROPERTY);
        List<RepairPlanner.Candidate> candidates = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        collectSettings("global", candidates, warnings);
        collectSettings("secure", candidates, warnings);
        collectSettings("system", candidates, warnings);
        try { candidates.addAll(RepairPlanner.parseDeviceConfig(AdbClient.shell(context, "device_config list"))); }
        catch (Exception e) { warnings.add("device_config unavailable: " + shortError(e)); }
        List<RepairPlanner.Mutation> plan = RepairPlanner.plan(candidates);
        if (plan.size() > MAX_AUTOMATIC_MUTATIONS) {
            warnings.add("Too many strong matches (" + plan.size() + "); automatic repair is disabled as a safety precaution.");
            plan = Collections.emptyList();
        }
        if (!shellUid) warnings.add("ADB connected without shell UID; repair is disabled.");
        if (plan.isEmpty()) warnings.add("No disabled high-confidence controller/gamepad flag was found. Meta's 2.7 feature may still be rollout-gated.");
        return new DiagnosticReport(build, release, identity, experimental, shellUid, candidates, plan, warnings);
    }

    private void collectSettings(String source, List<RepairPlanner.Candidate> candidates, List<String> warnings) {
        try { candidates.addAll(RepairPlanner.parseSettings(source, AdbClient.shell(context, "settings list " + source))); }
        catch (Exception e) { warnings.add("settings " + source + " unavailable: " + shortError(e)); }
    }

    public RepairResult applySafeRepair(DiagnosticReport report) {
        List<String> messages = new ArrayList<>();
        if (report == null || !report.shellUid) { messages.add("Refused: Android shell UID was not verified."); return new RepairResult(false, 0, messages); }
        if (report.plan.isEmpty()) { messages.add("No high-confidence disabled controller/gamepad setting is safe to change."); return new RepairResult(false, 0, messages); }
        int changed = 0; boolean allGood = true;
        for (RepairPlanner.Mutation mutation : report.plan) {
            boolean applied = false;
            try {
                AdbClient.shell(context, mutation.applyCommand); applied = true;
                String observed = AdbClient.shell(context, mutation.verifyCommand).trim();
                if (!mutation.targetValue.equalsIgnoreCase(observed)) {
                    allGood = false; messages.add("Verification failed for " + label(mutation) + "; restoring its previous value.");
                    try { AdbClient.shell(context, mutation.rollbackCommand); } catch (Exception ignored) {}
                    continue;
                }
                journal.append(new RollbackCodec.Entry(mutation.source, mutation.namespace, mutation.key,
                        mutation.originalValue.toLowerCase(), mutation.targetValue.toLowerCase(), System.currentTimeMillis()));
                changed++; messages.add("Verified: " + label(mutation) + " = " + mutation.targetValue);
            } catch (Exception e) {
                allGood = false;
                if (applied) { try { AdbClient.shell(context, mutation.rollbackCommand); } catch (Exception ignored) {} }
                messages.add("Failed: " + label(mutation) + " — " + shortError(e));
            }
        }
        return new RepairResult(allGood && changed > 0, changed, messages);
    }

    public RepairResult applyExperimentalCompatibilityRepair() {
        List<String> messages = new ArrayList<>();
        try {
            String identity = AdbClient.shell(context, "id");
            if (!identity.contains("uid=2000")) { messages.add("Refused: Android shell UID was not verified."); return new RepairResult(false, 0, messages); }
            String old = AdbClient.shell(context, "getprop " + EXPERIMENTAL_PROPERTY).trim();
            String journalOld;
            if (old.isEmpty()) journalOld = RollbackCodec.ABSENT;
            else if (isBooleanValue(old)) journalOld = old.toLowerCase();
            else { messages.add("Refused: existing experimental property value is unfamiliar, so it cannot be rolled back safely."); return new RepairResult(false, 0, messages); }
            if ("1".equals(old)) { messages.add("Experimental compatibility property is already enabled."); return new RepairResult(true, 0, messages); }
            AdbClient.shell(context, "setprop " + EXPERIMENTAL_PROPERTY + " 1");
            String observed = AdbClient.shell(context, "getprop " + EXPERIMENTAL_PROPERTY).trim();
            if (!"1".equals(observed)) { messages.add("Property write was not verified; no success is being claimed."); return new RepairResult(false, 0, messages); }
            journal.append(new RollbackCodec.Entry("property", "", EXPERIMENTAL_PROPERTY, journalOld, "1", System.currentTimeMillis()));
            messages.add("Verified experimental compatibility property = 1.");
            messages.add("This does not override a Meta account-side staged rollout if Gamepad Mode is not provisioned.");
            return new RepairResult(true, 1, messages);
        } catch (Exception e) { messages.add("Experimental compatibility repair failed: " + shortError(e)); return new RepairResult(false, 0, messages); }
    }

    public RepairResult restore() {
        List<RollbackCodec.Entry> entries = journal.load();
        List<String> messages = new ArrayList<>();
        if (entries.isEmpty()) { messages.add("Nothing recorded by this app to restore."); return new RepairResult(true, 0, messages); }
        List<RollbackCodec.Entry> failures = new ArrayList<>(); int restored = 0;
        for (int i = entries.size() - 1; i >= 0; i--) {
            RollbackCodec.Entry entry = entries.get(i);
            try {
                AdbClient.shell(context, RollbackCodec.rollbackCommand(entry));
                String observed = AdbClient.shell(context, RollbackCodec.verifyCommand(entry)).trim();
                boolean verified = RollbackCodec.ABSENT.equals(entry.originalValue) ? observed.isEmpty() : entry.originalValue.equalsIgnoreCase(observed);
                if (!verified) { failures.add(entry); messages.add("Restore verification failed: " + entry.key); }
                else { restored++; messages.add("Restored: " + entry.key); }
            } catch (Exception e) { failures.add(entry); messages.add("Restore failed: " + entry.key + " — " + shortError(e)); }
        }
        Collections.reverse(failures); journal.save(failures);
        return new RepairResult(failures.isEmpty(), restored, messages);
    }

    public List<RollbackCodec.Entry> pendingRollback() { return journal.load(); }
    private String safeShell(String command) { try { return AdbClient.shell(context, command); } catch (Exception e) { return ""; } }
    private static boolean isBooleanValue(String value) {
        String v = value == null ? "" : value.trim().toLowerCase();
        return "0".equals(v) || "1".equals(v) || "false".equals(v) || "true".equals(v) || "off".equals(v) || "on".equals(v) || "disabled".equals(v) || "enabled".equals(v);
    }
    private static String label(RepairPlanner.Mutation m) { return "device_config".equals(m.source) ? m.source + "/" + m.namespace + "/" + m.key : m.source + "/" + m.key; }
    private static String shortError(Exception e) {
        String message = e.getMessage(); if (message == null || message.trim().isEmpty()) message = e.getClass().getSimpleName();
        message = message.replace('\n', ' ').replace('\r', ' ').trim(); return message.length() > 180 ? message.substring(0, 180) : message;
    }
}
