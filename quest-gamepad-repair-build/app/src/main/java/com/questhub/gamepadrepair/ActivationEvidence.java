package com.questhub.gamepadrepair;

import java.util.Locale;

public final class ActivationEvidence {
    private static final String[] KEEP = {
            "emulated_gamepad", "xboxgamepad", "xbox gamepad", "gamepad mode",
            "keycode_button", "controller", "touch controller", "uhid", "systemshell",
            "systemux", "inputreader", "inputdispatcher", "joystick"
    };

    private static final String[] STRONG = {
            "xboxgamepad", "xbox gamepad", "emulated_gamepad", "gamepad mode", "uhid"
    };

    private ActivationEvidence() {}

    public static String filter(String raw) {
        if (raw == null || raw.isEmpty()) return "";
        StringBuilder out = new StringBuilder();
        for (String line : raw.split("\\r?\\n")) {
            String lower = line.toLowerCase(Locale.ROOT);
            boolean keep = false;
            for (String token : KEEP) {
                if (lower.contains(token)) { keep = true; break; }
            }
            if (keep) out.append(line).append('\n');
        }
        return out.toString().trim();
    }

    public static boolean hasActivationSignal(String raw) {
        if (raw == null || raw.isEmpty()) return false;
        String lower = raw.toLowerCase(Locale.ROOT);
        for (String token : STRONG) {
            if (lower.contains(token)) return true;
        }
        return false;
    }
}
