package com.questhub.gamepadrepair;

import android.content.Context;

public final class ActivationTraceService {
    private final Context context;

    public ActivationTraceService(Context context) {
        this.context = context.getApplicationContext();
    }

    public String capture() throws Exception {
        String identity = AdbClient.shell(context, "id");
        if (!identity.contains("uid=2000")) throw new IllegalStateException("Android shell UID 2000 was not verified");

        String vendor = safe("device_config get hzos_vendor_native oculus_emulated_gamepad");
        String shellFlag = safe("device_config get oculus_systemshell emulated_gamepad_shell_enabled");
        String overrides = safe("device_config list_local_overrides");
        String features = ActivationEvidence.filter(safe("pm list features"));
        String input = ActivationEvidence.filter(safe("dumpsys input"));
        String systemux = ActivationEvidence.filter(safe("dumpsys package com.oculus.systemux"));
        String logcat = ActivationEvidence.filter(safe("logcat -d -t 2500 -v threadtime"));

        boolean targetOverride = GamepadModeRepairPlan.hasTargetOverride(overrides);
        boolean activationSignal = ActivationEvidence.hasActivationSignal(logcat)
                || ActivationEvidence.hasActivationSignal(input)
                || ActivationEvidence.hasActivationSignal(systemux);

        StringBuilder out = new StringBuilder();
        out.append("QUEST GAMEPAD ACTIVATION TRACE v8\n");
        out.append("Mode: READ-ONLY diagnostics.\n\n");
        out.append("ADB: ").append(identity).append('\n');
        out.append("hzos_vendor_native/oculus_emulated_gamepad=").append(vendor).append('\n');
        out.append("oculus_systemshell/emulated_gamepad_shell_enabled=").append(shellFlag).append('\n');
        out.append("target_sticky_override_present=").append(targetOverride).append('\n');
        out.append("gamepad_activation_signal_seen=").append(activationSignal).append('\n');
        out.append("\nLOCAL OVERRIDES\n").append(overrides.isEmpty() ? "<none>" : overrides).append('\n');
        out.append("\nFEATURE EVIDENCE\n").append(features.isEmpty() ? "<none>" : features).append('\n');
        out.append("\nSYSTEMUX PACKAGE EVIDENCE\n").append(systemux.isEmpty() ? "<none>" : systemux).append('\n');
        out.append("\nINPUT EVIDENCE\n").append(input.isEmpty() ? "<none>" : input).append('\n');
        out.append("\nRECENT LOGCAT EVIDENCE\n").append(logcat.isEmpty() ? "<none>" : logcat).append('\n');
        return out.toString();
    }

    private String safe(String command) {
        try { return AdbClient.shell(context, command).trim(); }
        catch (Exception e) { return "<ERROR: " + shortError(e) + ">"; }
    }

    private static String shortError(Exception e) {
        String message = e.getMessage();
        if (message == null || message.trim().isEmpty()) message = e.getClass().getSimpleName();
        message = message.replace('\n', ' ').replace('\r', ' ').trim();
        return message.length() > 180 ? message.substring(0, 180) : message;
    }
}
