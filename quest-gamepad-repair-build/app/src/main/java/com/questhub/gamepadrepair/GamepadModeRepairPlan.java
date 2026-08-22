package com.questhub.gamepadrepair;

public final class GamepadModeRepairPlan {
    public static final String NAMESPACE = "hzos_vendor_native";
    public static final String KEY = "oculus_emulated_gamepad";

    private GamepadModeRepairPlan() {}

    public static String readCommand() {
        return "device_config get " + NAMESPACE + " " + KEY;
    }

    public static String enableCommand() {
        return "device_config put " + NAMESPACE + " " + KEY + " true";
    }

    public static String restoreCommand() {
        return "device_config put " + NAMESPACE + " " + KEY + " false";
    }

    public static boolean canEnable(String observed) {
        return observed != null && "false".equalsIgnoreCase(observed.trim());
    }
}
