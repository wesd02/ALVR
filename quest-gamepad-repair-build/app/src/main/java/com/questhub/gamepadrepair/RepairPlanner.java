package com.questhub.gamepadrepair;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public final class RepairPlanner {
    private static final Pattern SAFE_TOKEN = Pattern.compile("[A-Za-z0-9._:-]+");

    private RepairPlanner() {}

    public static final class Candidate {
        public final String source;
        public final String namespace;
        public final String key;
        public final String value;

        public Candidate(String source, String namespace, String key, String value) {
            this.source = source;
            this.namespace = namespace == null ? "" : namespace;
            this.key = key;
            this.value = value;
        }
    }

    public static final class Mutation {
        public final String source;
        public final String namespace;
        public final String key;
        public final String originalValue;
        public final String targetValue;
        public final String applyCommand;
        public final String verifyCommand;
        public final String rollbackCommand;

        public Mutation(String source, String namespace, String key,
                        String originalValue, String targetValue,
                        String applyCommand, String verifyCommand, String rollbackCommand) {
            this.source = source;
            this.namespace = namespace == null ? "" : namespace;
            this.key = key;
            this.originalValue = originalValue;
            this.targetValue = targetValue;
            this.applyCommand = applyCommand;
            this.verifyCommand = verifyCommand;
            this.rollbackCommand = rollbackCommand;
        }
    }

    public static List<Candidate> parseSettings(String source, String raw) {
        if (!("global".equals(source) || "secure".equals(source) || "system".equals(source))) {
            return Collections.emptyList();
        }
        List<Candidate> out = new ArrayList<>();
        if (raw == null) return out;
        for (String line : raw.split("\\r?\\n")) {
            int eq = line.indexOf('=');
            if (eq <= 0) continue;
            String key = line.substring(0, eq).trim();
            String value = line.substring(eq + 1).trim();
            if (!safeToken(key) || value.isEmpty()) continue;
            out.add(new Candidate(source, "", key, value));
        }
        return out;
    }

    public static List<Candidate> parseDeviceConfig(String raw) {
        List<Candidate> out = new ArrayList<>();
        if (raw == null) return out;
        for (String line : raw.split("\\r?\\n")) {
            int eq = line.indexOf('=');
            if (eq <= 0) continue;
            String left = line.substring(0, eq).trim();
            String value = line.substring(eq + 1).trim();
            int slash = left.indexOf('/');
            if (slash <= 0 || slash == left.length() - 1) continue;
            String namespace = left.substring(0, slash).trim();
            String key = left.substring(slash + 1).trim();
            if (!safeToken(namespace) || !safeToken(key) || value.isEmpty()) continue;
            out.add(new Candidate("device_config", namespace, key, value));
        }
        return out;
    }

    public static List<Mutation> plan(List<Candidate> candidates) {
        List<Mutation> out = new ArrayList<>();
        if (candidates == null) return out;
        for (Candidate c : candidates) {
            if (c == null || !isStrongMatch(c.key)) continue;
            String target = enabledEquivalent(c.value);
            if (target == null) continue;
            Mutation m = mutationFor(c, target);
            if (m != null) out.add(m);
        }
        return out;
    }

    public static boolean isStrongMatch(String key) {
        if (!safeToken(key)) return false;
        String k = key.toLowerCase(Locale.ROOT);
        boolean gamepad = k.contains("gamepad") || k.contains("xbox");
        boolean controller = k.contains("controller") || k.contains("touch");
        return gamepad && controller;
    }

    public static String enabledEquivalent(String value) {
        if (value == null) return null;
        switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "0": return "1";
            case "false": return "true";
            case "off": return "on";
            case "disabled": return "enabled";
            default: return null;
        }
    }

    private static Mutation mutationFor(Candidate c, String target) {
        if (!safeToken(c.key) || !safeValue(c.value) || !safeValue(target)) return null;
        if ("device_config".equals(c.source)) {
            if (!safeToken(c.namespace)) return null;
            return new Mutation(
                    c.source, c.namespace, c.key, c.value, target,
                    "device_config put " + c.namespace + " " + c.key + " " + target,
                    "device_config get " + c.namespace + " " + c.key,
                    "device_config put " + c.namespace + " " + c.key + " " + c.value);
        }
        if (!("global".equals(c.source) || "secure".equals(c.source) || "system".equals(c.source))) {
            return null;
        }
        return new Mutation(
                c.source, "", c.key, c.value, target,
                "settings put " + c.source + " " + c.key + " " + target,
                "settings get " + c.source + " " + c.key,
                "settings put " + c.source + " " + c.key + " " + c.value);
    }

    private static boolean safeToken(String value) {
        return value != null && value.length() <= 48 && SAFE_TOKEN.matcher(value).matches();
    }

    private static boolean safeValue(String value) {
        if (value == null) return false;
        String v = value.trim().toLowerCase(Locale.ROOT);
        return "0".equals(v) || "1".equals(v) || "false".equals(v) || "true".equals(v)
                || "off".equals(v) || "on".equals(v) || "disabled".equals(v) || "enabled".equals(v);
    }
}
