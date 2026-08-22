package com.questhub.gamepadrepair;

import android.app.Activity;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private TextView connectionStatus, diagnosis, log;
    private EditText pairingPort, pairingCode, connectionPort;
    private RepairService repairService;
    private volatile RepairService.DiagnosticReport lastReport;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        repairService = new RepairService(this);
        setContentView(buildUi());
        appendLog("Quest-only v5 ready. Nothing is changed until you explicitly press a repair button.");
        runTask("Checking saved local ADB pairing…", () -> {
            boolean connected = AdbClient.autoConnect(this);
            setConnectionStatus(connected ? "Connected to local ADB shell" : "Not connected — use the hidden-settings bootstrap above");
        });
    }

    private View buildUi() {
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(28), dp(24), dp(28), dp(36)); root.setBackgroundColor(0xFF101416);
        scroll.addView(root, new ScrollView.LayoutParams(-1, -1));
        root.addView(text("Quest Gamepad Repair v5", 30, true));
        TextView subtitle = text("Quest-only bootstrap • hidden Android Settings • local ADB • guarded repair • rollback", 16, false); subtitle.setTextColor(0xFFB8C6CA); root.addView(subtitle, margins(0, 4, 0, 22));

        root.addView(sectionTitle("0 · Unlock hidden Android Settings — no PC/phone/Pi"));
        TextView hiddenHelp = text(
                "Start with Hidden Android Settings. If it opens: About Headset → tap Build Number 7 times → back → System → Developer Options → Wireless Debugging. Meta may block the direct Developer/Wireless buttons, so App Info is included as another escape hatch: if Android Settings App Info opens, press Open.",
                16, false);
        hiddenHelp.setTextColor(0xFFD6E2E5); root.addView(hiddenHelp, margins(0, 4, 0, 12));
        LinearLayout hiddenRow1 = row();
        hiddenRow1.addView(button("Open Hidden Android Settings", v -> launchSettings(SettingsRoutePlanner.Kind.HIDDEN_SETTINGS_ROOT)), weight(1));
        hiddenRow1.addView(button("Open About Headset", v -> launchSettings(SettingsRoutePlanner.Kind.ABOUT_HEADSET)), weight(1));
        root.addView(hiddenRow1);
        LinearLayout hiddenRow2 = row();
        hiddenRow2.addView(button("Open Developer Options", v -> launchSettings(SettingsRoutePlanner.Kind.DEVELOPER_OPTIONS)), weight(1));
        hiddenRow2.addView(button("Open Wireless Debugging", v -> launchSettings(SettingsRoutePlanner.Kind.WIRELESS_DEBUGGING)), weight(1));
        hiddenRow2.addView(button("Android Settings App Info", v -> launchSettings(SettingsRoutePlanner.Kind.SETTINGS_APP_INFO)), weight(1));
        root.addView(hiddenRow2, margins(0, 6, 0, 20));

        root.addView(sectionTitle("1 · Pair local Wireless Debugging"));
        TextView pairingHelp = text("After you reach hidden Android Developer Options, open Wireless Debugging → Pair device with pairing code. Keep that pairing-code panel open, return here, then press Scan Pairing Port.", 16, false); pairingHelp.setTextColor(0xFFD6E2E5); root.addView(pairingHelp, margins(0, 4, 0, 12));
        LinearLayout pairRow = row(); pairingPort = numberField("Pairing port", 5); pairingCode = numberField("6-digit code", 6); pairRow.addView(pairingPort, weight(1)); pairRow.addView(pairingCode, weight(1)); root.addView(pairRow);
        LinearLayout pairButtons = row(); pairButtons.addView(button("Scan Pairing Port", v -> scanPairingPort()), weight(1)); pairButtons.addView(button("Pair & Connect", v -> pairAndConnect()), weight(1)); root.addView(pairButtons, margins(0, 8, 0, 8));
        LinearLayout connectRow = row(); connectionPort = numberField("Manual ADB port (optional)", 5); connectRow.addView(connectionPort, weight(1)); connectRow.addView(button("Auto Connect", v -> autoConnect()), weight(1)); connectRow.addView(button("Connect Port", v -> manualConnect()), weight(1)); root.addView(connectRow);
        connectionStatus = text("Connection: checking…", 17, true); connectionStatus.setTextColor(0xFF80CBC4); root.addView(connectionStatus, margins(0, 10, 0, 22));

        root.addView(sectionTitle("2 · Diagnose before changing anything")); root.addView(button("Diagnose Horizon Controller Settings", v -> diagnose()));
        diagnosis = text("No diagnosis yet.", 15, false); diagnosis.setTextColor(0xFFD6E2E5); diagnosis.setTextIsSelectable(true); root.addView(diagnosis, margins(0, 10, 0, 22));

        root.addView(sectionTitle("3 · Repair & verify"));
        TextView repairHelp = text("Safe Repair only touches disabled keys that contain BOTH a gamepad signal (gamepad/xbox) and a controller signal (controller/touch). Every write is read back and journaled for Restore.", 15, false); repairHelp.setTextColor(0xFFB8C6CA); root.addView(repairHelp, margins(0, 4, 0, 10));
        LinearLayout repairButtons = row(); repairButtons.addView(button("Apply Safe Repair", v -> applySafeRepair()), weight(1)); repairButtons.addView(button("Experimental Compatibility Repair", v -> applyExperimental()), weight(1)); repairButtons.addView(button("Restore My Changes", v -> restore()), weight(1)); root.addView(repairButtons);

        root.addView(sectionTitle("Activity log"), margins(0, 24, 0, 6)); log = text("", 13, false); log.setTextColor(0xFFB8C6CA); log.setTypeface(Typeface.MONOSPACE); log.setTextIsSelectable(true); root.addView(log);
        return scroll;
    }

    private void launchSettings(SettingsRoutePlanner.Kind kind) {
        HiddenSettingsLauncher.LaunchResult result = HiddenSettingsLauncher.launch(this, kind);
        for (String attempt : result.attempts) appendLog("Settings route: " + attempt);
        if (result.launched) appendLog("Opened route: " + result.routeLabel + ". " + result.message);
        else appendLog("All direct routes were blocked. " + result.message);
    }

    private void scanPairingPort() { runTask("Scanning local pairing service…", () -> { AdbClient.DiscoveredPort found = AdbClient.discoverPairingPort(this, 20_000L); if (found.port <= 0) throw new IllegalStateException("No pairing port found. Keep the hidden Android pairing-code panel open and try again."); runOnUiThread(() -> pairingPort.setText(String.valueOf(found.port))); appendLog("Pairing service found on port " + found.port + "."); }); }
    private void pairAndConnect() {
        final int port; try { port = parsePort(pairingPort.getText().toString()); } catch (Exception e) { appendLog("Pairing port is invalid."); return; }
        final String code = pairingCode.getText().toString().trim(); pairingCode.setText("");
        if (!code.matches("[0-9]{6}")) { appendLog("Pairing code must be six digits."); return; }
        runTask("Pairing with local adbd…", () -> { boolean paired = AdbClient.pair(this, port, code); if (!paired) throw new IllegalStateException("Pairing was rejected."); appendLog("Pairing accepted. Pairing code was discarded from the UI."); boolean connected = AdbClient.autoConnect(this); if (!connected) { AdbClient.DiscoveredPort found = AdbClient.discoverConnectionPort(this, 10_000L); if (found.port > 0) connected = AdbClient.connect(this, found.port); } setConnectionStatus(connected || AdbClient.isConnected(this) ? "Connected to local ADB shell" : "Paired, but connection port was not found yet"); });
    }
    private void autoConnect() { runTask("Searching for paired local ADB…", () -> { boolean connected = AdbClient.autoConnect(this); setConnectionStatus(connected || AdbClient.isConnected(this) ? "Connected to local ADB shell" : "No paired ADB service found"); }); }
    private void manualConnect() { final int port; try { port = parsePort(connectionPort.getText().toString()); } catch (Exception e) { appendLog("Manual connection port is invalid."); return; } runTask("Connecting to local ADB port " + port + "…", () -> { boolean connected = AdbClient.connect(this, port); setConnectionStatus(connected || AdbClient.isConnected(this) ? "Connected to local ADB shell" : "Connection failed"); }); }
    private void diagnose() { runTask("Reading Horizon OS settings — no changes…", () -> { RepairService.DiagnosticReport report = repairService.diagnose(); lastReport = report; runOnUiThread(() -> diagnosis.setText(renderDiagnosis(report))); String identity = report.identity == null ? "<unknown>" : report.identity.replace('\n', ' ').replace('\r', ' '); appendLog("ADB identity: " + identity); appendLog("Diagnosis complete: shell UID " + (report.shellUid ? "VERIFIED" : "NOT VERIFIED") + "; " + report.plan.size() + " safe disabled candidate(s)."); }); }
    private void applySafeRepair() { RepairService.DiagnosticReport report = lastReport; if (report == null) { appendLog("Run Diagnose first so the exact proposed changes are visible before repair."); return; } runTask("Applying only the displayed safe repair plan…", () -> { appendResult("Safe repair", repairService.applySafeRepair(report)); RepairService.DiagnosticReport verified = repairService.diagnose(); lastReport = verified; runOnUiThread(() -> diagnosis.setText(renderDiagnosis(verified))); }); }
    private void applyExperimental() { runTask("Applying reversible experimental compatibility property…", () -> { appendResult("Experimental repair", repairService.applyExperimentalCompatibilityRepair()); if (AdbClient.isConnected(this)) { RepairService.DiagnosticReport verified = repairService.diagnose(); lastReport = verified; runOnUiThread(() -> diagnosis.setText(renderDiagnosis(verified))); } }); }
    private void restore() { runTask("Restoring values recorded by this app…", () -> { appendResult("Restore", repairService.restore()); if (AdbClient.isConnected(this)) { RepairService.DiagnosticReport verified = repairService.diagnose(); lastReport = verified; runOnUiThread(() -> diagnosis.setText(renderDiagnosis(verified))); } }); }

    private String renderDiagnosis(RepairService.DiagnosticReport r) {
        StringBuilder out = new StringBuilder(); out.append("Build: ").append(empty(r.build)).append('\n').append("Android: ").append(empty(r.androidRelease)).append('\n').append("ADB identity: ").append(empty(r.identity)).append('\n').append("Shell UID verified: ").append(r.shellUid ? "YES" : "NO").append('\n').append("debug.oculus.experimentalEnabled: ").append(r.experimentalValue.isEmpty() ? "<unset>" : r.experimentalValue).append("\n\nSafe proposed changes:\n");
        if (r.plan.isEmpty()) out.append("  None. The APK will not invent a Meta flag.\n");
        else for (RepairPlanner.Mutation m : r.plan) { out.append("  • ").append(m.source); if (!m.namespace.isEmpty()) out.append('/').append(m.namespace); out.append('/').append(m.key).append(": ").append(m.originalValue).append(" → ").append(m.targetValue).append('\n'); }
        if (!r.warnings.isEmpty()) { out.append("\nNotes:\n"); for (String warning : r.warnings) out.append("  • ").append(warning).append('\n'); }
        out.append("\nRollback entries recorded: ").append(repairService.pendingRollback().size()); return out.toString();
    }
    private void appendResult(String label, RepairService.RepairResult result) { appendLog(label + ": " + (result.success ? "verified" : "not fully verified") + ", changed/restored=" + result.changed); for (String message : result.messages) appendLog("  " + message); }
    private void runTask(String label, ThrowingTask task) { appendLog(label); worker.execute(() -> { try { task.run(); } catch (Throwable t) { appendLog("Error: " + sanitizeError(t)); } }); }
    private void setConnectionStatus(String value) { runOnUiThread(() -> connectionStatus.setText("Connection: " + value)); appendLog("Connection: " + value); }
    private void appendLog(String message) { String safe = message == null ? "" : message.replaceAll("(?<![0-9])[0-9]{6}(?![0-9])", "******"); runOnUiThread(() -> { if (log == null) return; String current = log.getText().toString(); String next = current.isEmpty() ? safe : current + "\n" + safe; if (next.length() > 18_000) next = next.substring(next.length() - 18_000); log.setText(next); }); }
    private static String sanitizeError(Throwable t) { String message = t.getMessage(); if (message == null || message.trim().isEmpty()) message = t.getClass().getSimpleName(); message = message.replace('\n', ' ').replace('\r', ' ').replaceAll("(?<![0-9])[0-9]{6}(?![0-9])", "******").trim(); return message.length() > 240 ? message.substring(0, 240) : message; }
    private int parsePort(String raw) { int value = Integer.parseInt(raw.trim()); if (value <= 0 || value > 65535) throw new IllegalArgumentException("port"); return value; }
    private TextView sectionTitle(String value) { TextView view = text(value, 20, true); view.setTextColor(0xFF80CBC4); return view; }
    private TextView text(String value, int sp, boolean bold) { TextView view = new TextView(this); view.setText(value); view.setTextSize(sp); view.setTextColor(0xFFF4F7F8); if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD); view.setLineSpacing(0f, 1.12f); return view; }
    private Button button(String value, View.OnClickListener listener) { Button button = new Button(this); button.setText(value); button.setTextSize(15); button.setAllCaps(false); button.setMinHeight(dp(54)); button.setOnClickListener(listener); return button; }
    private EditText numberField(String hint, int maxDigits) { EditText edit = new EditText(this); edit.setHint(hint); edit.setHintTextColor(0xFF78909C); edit.setTextColor(0xFFF4F7F8); edit.setTextSize(16); edit.setSingleLine(true); edit.setInputType(InputType.TYPE_CLASS_NUMBER); edit.setMaxEms(maxDigits + 3); return edit; }
    private LinearLayout row() { LinearLayout layout = new LinearLayout(this); layout.setOrientation(LinearLayout.HORIZONTAL); layout.setGravity(Gravity.CENTER_VERTICAL); return layout; }
    private LinearLayout.LayoutParams weight(float value) { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, value); p.setMargins(dp(4), dp(3), dp(4), dp(3)); return p; }
    private LinearLayout.LayoutParams margins(int left, int top, int right, int bottom) { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.setMargins(dp(left), dp(top), dp(right), dp(bottom)); return p; }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private static String empty(String value) { return value == null || value.isEmpty() ? "<unknown>" : value; }
    @Override protected void onDestroy() { super.onDestroy(); worker.shutdownNow(); AdbClient.disconnect(this); }
    private interface ThrowingTask { void run() throws Exception; }
}
