package com.questhub.gamepadrepair;

import android.app.Activity;
import android.content.ContentValues;
import android.graphics.Typeface;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class MainActivity extends Activity {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final AtomicBoolean busy = new AtomicBoolean(false);
    private TextView connectionStatus, flagStatus, overrideStatus, log;
    private EditText pairingPort, pairingCode, connectionPort;
    private GamepadModeRepairService gamepadRepair;
    private ActivationTraceService activationTrace;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        gamepadRepair = new GamepadModeRepairService(this);
        activationTrace = new ActivationTraceService(this);
        setContentView(buildUi());
        appendLog("Quest Gamepad Sticky Fix v9 ready.");
        appendLog("v8 falsely treated missing help text as proof that sticky overrides were unavailable.");
        appendLog("v9 directly executes the read-only list_local_overrides probe. Only a real successful probe can unlock the one-flag write.");
        runTask("Checking saved local ADB pairing…", () -> {
            boolean connected = AdbClient.autoConnect(this);
            setConnectionStatus(connected ? "Connected to local ADB shell" : "Not connected — pair Wireless Debugging below");
            if (connected || AdbClient.isConnected(this)) refreshState();
        });
    }

    private View buildUi() {
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(28), dp(24), dp(28), dp(36)); root.setBackgroundColor(0xFF101416);
        scroll.addView(root, new ScrollView.LayoutParams(-1, -1));

        root.addView(text("Quest Gamepad Sticky Fix v9", 30, true));
        TextView subtitle = text("Quest-only • direct capability probe • one sticky override • verified rollback • activation trace", 16, false);
        subtitle.setTextColor(0xFFB8C6CA); root.addView(subtitle, margins(0, 4, 0, 18));

        TextView target = text(
                "Target only: hzos_vendor_native/oculus_emulated_gamepad = true\n" +
                "Probe: device_config list_local_overrides (READ ONLY)\n" +
                "Write only if probe really succeeds: device_config override\n" +
                "Rollback: clear_override for this one key\n" +
                "NEVER changes the kill-switch and NEVER disables DeviceConfig sync globally.",
                16, true);
        target.setTextColor(0xFF80CBC4); target.setTextIsSelectable(true); root.addView(target, margins(0, 0, 0, 22));

        root.addView(sectionTitle("1 · Wireless Debugging bootstrap"));
        TextView help = text(
                "v9 installs as an update over v8, so your existing pairing may remain usable. If it does not connect: open Android Settings App Info → Open → System → Developer Options → Wireless Debugging → Pair device with pairing code.",
                15, false);
        help.setTextColor(0xFFD6E2E5); root.addView(help, margins(0, 4, 0, 10));

        LinearLayout settingsRow = row();
        settingsRow.addView(button("Android Settings App Info", v -> launchSettings(SettingsRoutePlanner.Kind.SETTINGS_APP_INFO)), weight(1));
        settingsRow.addView(button("Developer Options", v -> launchSettings(SettingsRoutePlanner.Kind.DEVELOPER_OPTIONS)), weight(1));
        settingsRow.addView(button("Wireless Debugging", v -> launchSettings(SettingsRoutePlanner.Kind.WIRELESS_DEBUGGING)), weight(1));
        root.addView(settingsRow, margins(0, 0, 0, 10));

        LinearLayout pairRow = row();
        pairingPort = numberField("Pairing port", 5); pairingCode = numberField("6-digit code", 6);
        pairRow.addView(pairingPort, weight(1)); pairRow.addView(pairingCode, weight(1)); root.addView(pairRow);
        LinearLayout pairButtons = row();
        pairButtons.addView(button("Scan Pairing Port", v -> scanPairingPort()), weight(1));
        pairButtons.addView(button("Pair & Connect", v -> pairAndConnect()), weight(1));
        root.addView(pairButtons, margins(0, 6, 0, 6));

        LinearLayout connectRow = row();
        connectionPort = numberField("Manual ADB port", 5); connectRow.addView(connectionPort, weight(1));
        connectRow.addView(button("Auto Connect", v -> autoConnect()), weight(1));
        connectRow.addView(button("Connect Port", v -> manualConnect()), weight(1)); root.addView(connectRow);
        connectionStatus = text("Connection: checking…", 17, true); connectionStatus.setTextColor(0xFF80CBC4);
        root.addView(connectionStatus, margins(0, 10, 0, 22));

        root.addView(sectionTitle("2 · Direct override probe + current state"));
        flagStatus = text("Effective flag: not read yet", 18, true); flagStatus.setTextColor(0xFFF4F7F8); flagStatus.setTextIsSelectable(true);
        overrideStatus = text("Sticky override: not checked yet", 16, true); overrideStatus.setTextColor(0xFFB8C6CA); overrideStatus.setTextIsSelectable(true);
        root.addView(flagStatus, margins(0, 6, 0, 6));
        root.addView(overrideStatus, margins(0, 0, 0, 8));
        root.addView(button("Check Flag + DIRECT Override Probe", v -> checkState()), margins(0, 0, 0, 22));

        root.addView(sectionTitle("3 · Install reboot-persistent override"));
        TextView safety = text(
                "v9 does NOT trust device_config help. It executes list_local_overrides directly. Empty output is a valid successful result meaning no overrides are listed. A timeout, invalid/unknown/unsupported-command response, or other command failure blocks the write. Only after that probe succeeds will v9 attempt the single Gamepad override and verify both the override list and effective value.",
                15, false);
        safety.setTextColor(0xFFD6E2E5); root.addView(safety, margins(0, 4, 0, 10));
        Button enable = button("DIRECT PROBE + INSTALL ONE-FLAG OVERRIDE", v -> enableGamepadMode());
        enable.setTextSize(18); enable.setMinHeight(dp(68)); root.addView(enable, margins(0, 0, 0, 8));
        root.addView(button("CLEAR v9 STICKY OVERRIDE", v -> restoreGamepadMode()), margins(0, 0, 0, 18));

        root.addView(sectionTitle("4 · Test the Meta + Menu activation path"));
        TextView activationHelp = text(
                "If v9 actually verifies a sticky override and effective=true, restart once. After boot, re-check the direct probe and flag. Then check Settings → Devices → Controllers and press Meta + Menu once. If nothing changes, capture activation evidence immediately.",
                15, false);
        activationHelp.setTextColor(0xFFFFCC80); root.addView(activationHelp, margins(0, 4, 0, 10));
        root.addView(button("CAPTURE ACTIVATION EVIDENCE — READ ONLY", v -> captureActivationEvidence()), margins(0, 0, 0, 22));

        root.addView(sectionTitle("Activity log"));
        log = text("", 13, false); log.setTextColor(0xFFB8C6CA); log.setTypeface(Typeface.MONOSPACE); log.setTextIsSelectable(true);
        root.addView(log, margins(0, 6, 0, 0));
        return scroll;
    }

    private void launchSettings(SettingsRoutePlanner.Kind kind) {
        HiddenSettingsLauncher.LaunchResult result = HiddenSettingsLauncher.launch(this, kind);
        for (String attempt : result.attempts) appendLog("Settings route: " + attempt);
        if (result.launched) appendLog("Opened: " + result.routeLabel);
        else appendLog("Settings route blocked: " + result.message);
    }

    private void scanPairingPort() {
        runTask("Scanning local pairing service…", () -> {
            AdbClient.DiscoveredPort found = AdbClient.discoverPairingPort(this, 20_000L);
            if (found.port <= 0) throw new IllegalStateException("No pairing port found. Keep Pair device with pairing code open and try again.");
            runOnUiThread(() -> pairingPort.setText(String.valueOf(found.port)));
            appendLog("Pairing service found on port " + found.port + ".");
        });
    }

    private void pairAndConnect() {
        final int port;
        try { port = parsePort(pairingPort.getText().toString()); }
        catch (Exception e) { appendLog("Pairing port is invalid."); return; }
        final String code = pairingCode.getText().toString().trim(); pairingCode.setText("");
        if (!code.matches("[0-9]{6}")) { appendLog("Pairing code must be six digits."); return; }
        runTask("Pairing with local adbd…", () -> {
            if (!AdbClient.pair(this, port, code)) throw new IllegalStateException("Pairing was rejected.");
            appendLog("Pairing accepted. Pairing code discarded from UI.");
            boolean connected = AdbClient.autoConnect(this);
            if (!connected) {
                AdbClient.DiscoveredPort found = AdbClient.discoverConnectionPort(this, 10_000L);
                if (found.port > 0) connected = AdbClient.connect(this, found.port);
            }
            setConnectionStatus(connected || AdbClient.isConnected(this) ? "Connected to local ADB shell" : "Paired, but connection service not found yet");
            if (connected || AdbClient.isConnected(this)) refreshState();
        });
    }

    private void autoConnect() {
        runTask("Searching for paired local ADB…", () -> {
            boolean connected = AdbClient.autoConnect(this);
            setConnectionStatus(connected || AdbClient.isConnected(this) ? "Connected to local ADB shell" : "No paired ADB service found");
            if (connected || AdbClient.isConnected(this)) refreshState();
        });
    }

    private void manualConnect() {
        final int port;
        try { port = parsePort(connectionPort.getText().toString()); }
        catch (Exception e) { appendLog("Manual connection port is invalid."); return; }
        runTask("Connecting to ADB port " + port + "…", () -> {
            boolean connected = AdbClient.connect(this, port);
            setConnectionStatus(connected || AdbClient.isConnected(this) ? "Connected to local ADB shell" : "Connection failed");
            if (connected || AdbClient.isConnected(this)) refreshState();
        });
    }

    private void checkState() {
        if (!AdbClient.isConnected(this)) { appendLog("Connect local ADB first."); return; }
        runTask("Running direct list_local_overrides probe and reading Gamepad flag…", this::refreshState);
    }

    private void refreshState() throws Exception {
        String value = gamepadRepair.readCurrentValue();
        boolean override = gamepadRepair.isTargetOverridePresent();
        updateStateUi(value, override);
        appendLog("Direct list_local_overrides probe: VERIFIED");
        appendLog("Effective Gamepad flag = " + printable(value));
        appendLog("Target sticky override present = " + override);
    }

    private void updateStateUi(String value, boolean override) {
        runOnUiThread(() -> {
            flagStatus.setText("Effective flag: hzos_vendor_native/oculus_emulated_gamepad = " + printable(value));
            flagStatus.setTextColor("true".equalsIgnoreCase(value) ? 0xFF81C784 : ("false".equalsIgnoreCase(value) ? 0xFFFFCC80 : 0xFFEF9A9A));
            overrideStatus.setText("Sticky override: " + (override ? "PRESENT → true" : "ABSENT"));
            overrideStatus.setTextColor(override ? 0xFF81C784 : 0xFFB8C6CA);
        });
    }

    private void enableGamepadMode() {
        if (!AdbClient.isConnected(this)) { appendLog("Enable refused: connect local ADB first."); return; }
        runTask("Direct-probing and attempting one-flag sticky Gamepad override…", () -> {
            GamepadModeRepairService.Result result = gamepadRepair.enable();
            appendResult("Sticky enable", result);
            updateStateUi(result.observedValue, result.overridePresent);
            if (result.success && result.overridePresent && "true".equalsIgnoreCase(result.observedValue)) {
                appendLog("STICKY OVERRIDE VERIFIED TRUE. Restart once, re-check v9, then test Settings and Meta + Menu.");
            }
        });
    }

    private void restoreGamepadMode() {
        if (!AdbClient.isConnected(this)) { appendLog("Clear refused: connect local ADB first."); return; }
        runTask("Clearing only the v9 Gamepad sticky override…", () -> {
            GamepadModeRepairService.Result result = gamepadRepair.restore();
            appendResult("Clear override", result);
            updateStateUi(result.observedValue, result.overridePresent);
        });
    }

    private void captureActivationEvidence() {
        if (!AdbClient.isConnected(this)) { appendLog("Capture refused: connect local ADB first."); return; }
        runTask("Capturing recent Gamepad activation evidence — read only…", () -> {
            String report = activationTrace.capture();
            String name = saveReport(report);
            boolean signal = report.contains("gamepad_activation_signal_seen=true");
            appendLog("Activation evidence captured. Strong activation signal seen = " + signal);
            appendLog("Saved to Downloads/" + name);
            appendLog("Upload that report here if Meta + Menu still does nothing.");
        });
    }

    private String saveReport(String report) throws Exception {
        String name = "QuestGamepadActivation-v9-" + System.currentTimeMillis() + ".txt";
        ContentValues values = new ContentValues();
        values.put(MediaStore.Downloads.DISPLAY_NAME, name);
        values.put(MediaStore.Downloads.MIME_TYPE, "text/plain");
        values.put(MediaStore.Downloads.RELATIVE_PATH, "Download");
        android.net.Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
        if (uri == null) throw new IllegalStateException("Could not create Downloads report");
        try (OutputStream out = getContentResolver().openOutputStream(uri)) {
            if (out == null) throw new IllegalStateException("Could not open Downloads report");
            out.write(report.getBytes(StandardCharsets.UTF_8));
        }
        return name;
    }

    private void appendResult(String label, GamepadModeRepairService.Result result) {
        appendLog(label + ": " + (result.success ? "VERIFIED" : "NOT VERIFIED") + "; changed=" + result.changed);
        for (String message : result.messages) appendLog("  " + message);
    }

    private void runTask(String label, ThrowingTask task) {
        if (!busy.compareAndSet(false, true)) {
            appendLog("Busy — previous operation is still running. Extra tap ignored.");
            return;
        }
        appendLog(label);
        worker.execute(() -> {
            try { task.run(); }
            catch (Throwable t) { appendLog("Error: " + sanitizeError(t)); }
            finally { busy.set(false); }
        });
    }

    private void setConnectionStatus(String value) {
        runOnUiThread(() -> connectionStatus.setText("Connection: " + value));
        appendLog("Connection: " + value);
    }

    private void appendLog(String message) {
        String safe = message == null ? "" : message.replaceAll("(?<![0-9])[0-9]{6}(?![0-9])", "******");
        runOnUiThread(() -> {
            if (log == null) return;
            String current = log.getText().toString();
            String next = current.isEmpty() ? safe : current + "\n" + safe;
            if (next.length() > 24_000) next = next.substring(next.length() - 24_000);
            log.setText(next);
        });
    }

    private static String sanitizeError(Throwable t) {
        String message = t.getMessage();
        if (message == null || message.trim().isEmpty()) message = t.getClass().getSimpleName();
        message = message.replace('\n', ' ').replace('\r', ' ').replaceAll("(?<![0-9])[0-9]{6}(?![0-9])", "******").trim();
        return message.length() > 240 ? message.substring(0, 240) : message;
    }

    private int parsePort(String raw) {
        int value = Integer.parseInt(raw.trim());
        if (value <= 0 || value > 65535) throw new IllegalArgumentException("port");
        return value;
    }

    private TextView sectionTitle(String value) { TextView view = text(value, 20, true); view.setTextColor(0xFF80CBC4); return view; }
    private TextView text(String value, int sp, boolean bold) { TextView view = new TextView(this); view.setText(value); view.setTextSize(sp); view.setTextColor(0xFFF4F7F8); if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD); view.setLineSpacing(0f, 1.12f); return view; }
    private Button button(String value, View.OnClickListener listener) { Button button = new Button(this); button.setText(value); button.setTextSize(15); button.setAllCaps(false); button.setMinHeight(dp(54)); button.setOnClickListener(listener); return button; }
    private EditText numberField(String hint, int maxDigits) { EditText edit = new EditText(this); edit.setHint(hint); edit.setHintTextColor(0xFF78909C); edit.setTextColor(0xFFF4F7F8); edit.setTextSize(16); edit.setSingleLine(true); edit.setInputType(InputType.TYPE_CLASS_NUMBER); edit.setMaxEms(maxDigits + 3); return edit; }
    private LinearLayout row() { LinearLayout layout = new LinearLayout(this); layout.setOrientation(LinearLayout.HORIZONTAL); layout.setGravity(Gravity.CENTER_VERTICAL); return layout; }
    private LinearLayout.LayoutParams weight(float value) { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, value); p.setMargins(dp(4), dp(3), dp(4), dp(3)); return p; }
    private LinearLayout.LayoutParams margins(int left, int top, int right, int bottom) { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.setMargins(dp(left), dp(top), dp(right), dp(bottom)); return p; }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private static String printable(String value) { return value == null || value.trim().isEmpty() ? "<empty>" : value.trim(); }

    @Override protected void onDestroy() {
        super.onDestroy();
        worker.shutdownNow();
        AdbClient.disconnect(this);
    }

    private interface ThrowingTask { void run() throws Exception; }
}
