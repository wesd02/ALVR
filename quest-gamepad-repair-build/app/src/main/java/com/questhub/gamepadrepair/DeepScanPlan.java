package com.questhub.gamepadrepair;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class DeepScanPlan {
    private static final List<String> BASE_COMMANDS = Collections.unmodifiableList(Arrays.asList(
            "id",
            "getprop",
            "settings list global",
            "settings list secure",
            "settings list system",
            "device_config list",
            "pm list packages -f -U",
            "pm list packages --show-versioncode",
            "pm list packages -d",
            "pm list features",
            "cmd overlay list",
            "service list",
            "dumpsys input",
            "dumpsys activity services",
            "dumpsys activity providers",
            "dumpsys jobscheduler",
            "dumpsys bluetooth_manager",
            "logcat -d -t 2000 -v threadtime"
    ));

    private DeepScanPlan() {}

    public static List<String> baseCommands() {
        return BASE_COMMANDS;
    }

    public static boolean isCandidatePackage(String packageName) {
        if (packageName == null) return false;
        String p = packageName.trim().toLowerCase(Locale.US);
        if (p.startsWith("com.oculus.") || p.startsWith("com.meta.") || p.startsWith("com.facebook.")) return true;
        if (p.equals("oculus.platform") || p.equals("com.android.settings") || p.equals("com.android.systemui") || p.equals("com.android.inputdevices")) return true;
        return p.contains("gamepad") || p.contains("controller") || p.contains("xbox") || p.contains("steam") || p.contains("quest") || p.contains("inputdevice");
    }

    public static String sourceName(String command) {
        if ("id".equals(command)) return "identity";
        if ("getprop".equals(command)) return "properties";
        if ("settings list global".equals(command)) return "settings_global";
        if ("settings list secure".equals(command)) return "settings_secure";
        if ("settings list system".equals(command)) return "settings_system";
        if ("device_config list".equals(command)) return "device_config";
        if ("pm list packages -f -U".equals(command)) return "packages";
        if ("pm list packages --show-versioncode".equals(command)) return "package_versions";
        if ("pm list packages -d".equals(command)) return "disabled_packages";
        if ("pm list features".equals(command)) return "features";
        if ("cmd overlay list".equals(command)) return "overlays";
        if ("service list".equals(command)) return "service_list";
        if ("dumpsys input".equals(command)) return "input";
        if ("dumpsys activity services".equals(command)) return "services";
        if ("dumpsys activity providers".equals(command)) return "providers";
        if ("dumpsys jobscheduler".equals(command)) return "jobscheduler";
        if ("dumpsys bluetooth_manager".equals(command)) return "bluetooth";
        if (command.startsWith("logcat ")) return "logcat";
        return command.replace(' ', '_');
    }
}
