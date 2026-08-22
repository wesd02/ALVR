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
    private TextView connectionStatus, flagStatus, log;
    private EditText pairingPort, pairingCode, connectionPort;
    private GamepadModeRepairService gamepadRepair;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        gamepadRepair = new GamepadModeRepairService(this);
        setContentView(buildUi());
        appendLog("Quest Gamepad Mode Fix v7 ready. This build targets one DeviceConfig key only.");
        runTask("Checking saved local ADB pairing…", () -> {
            boolean connected = AdbClient.autoConnect(this);
            setConnectionStatus(connected ? "Connected to local ADB shell" : "Not connected — pair Wireless Debugging below");
            if (connected || AdbClient.isConnected(this)) refreshFlag();
        });
    }

    private View buildUi() {
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(28), dp(24), dp(28), dp(36)); root.setBackgroundColor(0xFF101416);
        scroll.addView(root, new ScrollView.LayoutParams(-1, -1));

        root.addView(text("Quest Gamepad Mode Fix v7", 30, true));
        TextView subtitle = text("Surgical Quest-only fix • one DeviceConfig flag • verified write • dedicated rollback", 16, false);
        subtitle.setTextColor(0xFFB8C6CA); root.addView(subtitle, margins(0, 4, 0, 18));

        TextView target = text(
                "Target: hzos_vendor_native/oculus_emulated_gamepad\n" +
                "Expected before: false   →   Requested: true\n" +
                "oculus_emulated_gamepad_kill_switch is NEVER modified by v7.",
                16, true);
        target.setTextColor(0xFF80CBC4); target.setTextIsSelectable(true); root.addView(target, margins(0, 0, 0, 22));

        root.addView(sectionTitle("1 · Wireless Debugging bootstrap"));
        TextView help = text(
                "If v7 is not paired yet: open Android Settings App Info → Open → System → Developer Options → Wireless Debugging → Pair device with pairing code. Keep the pairing panel open while scanning the pairing port.",
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

        root.addView(sectionTitle("2 · Verify the exact flag"));
        flagStatus = text("Current flag: not read yet", 18, true); flagStatus.setTextColor(0xFFF4F7F8); flagStatus.setTextIsSelectable(true);
        root.addView(flagStatus, margins(0, 6, 0, 8));
        root.addView(button("Check Gamepad Mode Flag", v -> checkFlag()), margins(0, 0, 0, 22));

        root.addView(sectionTitle("3 · Enable Gamepad Mode"));
        TextView safety = text(
                "v7 reads the flag immediately before writing. It only proceeds if the value is exactly false. After writing true, it reads the value back. If verification fails, v7 attempts to restore false automatically.",
                15, false);
        safety.setTextColor(0xFFD6E2E5); root.addView(safety, margins(0, 4, 0, 10));
        Button enable = button("ENABLE GAMEPAD MODE — false → true", v -> enableGamepadMode());
        enable.setTextSize(18); enable.setMinHeight(dp(68)); root.addView(enable, margins(0, 0, 0, 8));
        root.addView(button("RESTORE v7 CHANGE — true → false", v -> restoreGamepadMode()), margins(0, 0, 0, 16));

        TextView after = text(
                "After v7 reports VERIFIED TRUE: check Settings → Devices → Controllers and try Meta + Menu. If the option is still absent, restart the headset once and check again.",
                15, true);
        after.setTextColor(0xFFFFCC80); root.addView(after, margins(0, 0, 0, 22));

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
            if (connected || AdbClient.isConnected(this)) refreshFlag();
        });
    }

    private void autoConnect() {
        runTask("Searching for paired local ADB…", () -> {
            boolean connected = AdbClient.autoConnect(this);
            setConnectionStatus(connected || AdbClient.isConnected(this) ? "Connected to local ADB shell" : "No paired ADB service found");
            if (connected || AdbClient.isConnected(this)) refreshFlag();
        });
    }

    private void manualConnect() {
        final int port;
        try { port = parsePort(connectionPort.getText().toString()); }
        catch (Exception e) { appendLog("Manual connection port is invalid."); return; }
        runTask("Connecting to ADB port " + port + "…", () -> {
            boolean connected = AdbClient.connect(this, port);
            setConnectionStatus(connected || AdbClient.isConnected(this) ? "Connected to local ADB shell" : "Connection failed");
            if (connected || AdbClient.isConnected(this)) refreshFlag();
        });
    }

    private void checkFlag() {
        if (!AdbClient.isConnected(this)) { appendLog("Connect local ADB first."); return; }
        runTask("Reading exact Gamepad Mode flag…", this::refreshFlag);
    }

    private void refreshFlag() throws Exception {
        String value = gamepadRepair.readCurrentValue();
        runOnUiThread(() -> {
            flagStatus.setText("Current flag: hzos_vendor_native/oculus_emulated_gamepad = " + printable(value));
            flagStatus.setTextColor("true".equalsIgnoreCase(value) ? 0xFF81C784 : ("false".equalsIgnoreCase(value) ? 0xFFFFCC80 : 0xFFEF9A9A));
        });
        appendLog("Observed Gamepad Mode flag = " + printable(value));
    }

    private void enableGamepadMode() {
        if (!AdbClient.isConnected(this)) { appendLog("Enable refused: connect local ADB first."); return; }
        runTask("Running surgical Gamepad Mode enable…", () -> {
            GamepadModeRepairService.Result result = gamepadRepair.enable();
            appendResult("Enable", result);
            String value = result.observedValue;
            runOnUiThread(() -> {
                flagStatus.setText("Current flag: hzos_vendor_native/oculus_emulated_gamepad = " + printable(value));
                flagStatus.setTextColor("true".equalsIgnoreCase(value) ? 0xFF81C784 : 0xFFEF9A9A);
            });
            if (result.success && "true".equalsIgnoreCase(value)) {
                appendLog("VERIFIED TRUE. Check Controllers settings and Meta + Menu now. Restart once only if the UI has not refreshed.");
            }
        });
    }

    private void restoreGamepadMode() {
        if (!AdbClient.isConnected(this)) { appendLog("Restore refused: connect local ADB first."); return; }
        runTask("Restoring only the Gamepad Mode flag recorded by v7…", () -> {
            GamepadModeRepairService.Result result = gamepadRepair.restore();
            appendResult("Restore", result);
            String value = result.observedValue;
            runOnUiThread(() -> {
                flagStatus.setText("Current flag: hzos_vendor_native/oculus_emulated_gamepad = " + printable(value));
                flagStatus.setTextColor("false".equalsIgnoreCase(value) ? 0xFFFFCC80 : 0xFFEF9A9A);
            });
        });
    }

    private void appendResult(String label, GamepadModeRepairService.Result result) {
        appendLog(label + ": " + (result.success ? "VERIFIED" : "NOT VERIFIED") + "; changed=" + result.changed);
        for (String message : result.messages) appendLog("  " + message);
    }

    private void runTask(String label, ThrowingTask task) {
        appendLog(label);
        worker.execute(() -> {
            try { task.run(); }
            catch (Throwable t) { appendLog("Error: " + sanitizeError(t)); }
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
            if (next.length() > 18_000) next = next.substring(next.length() - 18_000);
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
