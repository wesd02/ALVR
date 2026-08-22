package com.questhub.gamepadrepair;

public final class GamepadModeRepairPlan {
    public static final String NAMESPACE = "hzos_vendor_native";
    public static final String KEY = "oculus_emulated_gamepad";
    private static final String TARGET_OVERRIDE = NAMESPACE + "/" + KEY + "=true";

    private GamepadModeRepairPlan() {}

    public static String readCommand() {
        return "device_config get " + NAMESPACE + " " + KEY;
    }

    public static String enableCommand() {
        return "device_config override " + NAMESPACE + " " + KEY + " true";
    }

    public static String restoreCommand() {
        return "device_config clear_override " + NAMESPACE + " " + KEY;
    }

    public static String listOverridesCommand() {
        return "device_config list_local_overrides";
    }

    public static String capabilityProbeCommand() {
        return listOverridesCommand();
    }

    public static boolean hasTargetOverride(String output) {
        if (output == null) return false;
        for (String line : output.split("\\r?\\n")) {
            if (TARGET_OVERRIDE.equalsIgnoreCase(line.trim())) return true;
        }
        return false;
    }

    public static boolean isSafeUnderlyingValue(String observed) {
        if (observed == null) return false;
        String value = observed.trim();
        return "false".equalsIgnoreCase(value) || "true".equalsIgnoreCase(value);
    }
}
