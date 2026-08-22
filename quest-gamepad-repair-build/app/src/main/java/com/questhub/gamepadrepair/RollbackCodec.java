package com.questhub.gamepadrepair;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

public final class RollbackCodec {
    public static final String ABSENT = "__ABSENT__";
    private static final Pattern TOKEN = Pattern.compile("[A-Za-z0-9._:-]{1,48}");

    private RollbackCodec() {}

    public static final class Entry {
        public final String source;
        public final String namespace;
        public final String key;
        public final String originalValue;
        public final String appliedValue;
        public final long timestamp;

        public Entry(String source, String namespace, String key, String originalValue, String appliedValue, long timestamp) {
            this.source = source == null ? "" : source;
            this.namespace = namespace == null ? "" : namespace;
            this.key = key == null ? "" : key;
            this.originalValue = originalValue == null ? "" : originalValue;
            this.appliedValue = appliedValue == null ? "" : appliedValue;
            this.timestamp = timestamp;
        }

        @Override public boolean equals(Object other) {
            if (!(other instanceof Entry)) return false;
            Entry e = (Entry) other;
            return timestamp == e.timestamp && source.equals(e.source) && namespace.equals(e.namespace)
                    && key.equals(e.key) && originalValue.equals(e.originalValue) && appliedValue.equals(e.appliedValue);
        }

        @Override public int hashCode() {
            return Objects.hash(source, namespace, key, originalValue, appliedValue, timestamp);
        }
    }

    public static String encode(List<Entry> entries) {
        if (entries == null || entries.isEmpty()) return "";
        StringBuilder out = new StringBuilder();
        for (Entry entry : entries) {
            if (!isSafe(entry)) continue;
            out.append(entry.source).append('|')
                    .append(entry.namespace).append('|')
                    .append(entry.key).append('|')
                    .append(entry.originalValue).append('|')
                    .append(entry.appliedValue).append('|')
                    .append(entry.timestamp).append('\n');
        }
        return out.toString();
    }

    public static List<Entry> decode(String encoded) {
        if (encoded == null || encoded.trim().isEmpty()) return Collections.emptyList();
        List<Entry> out = new ArrayList<>();
        for (String line : encoded.split("\\r?\\n")) {
            if (line.isEmpty()) continue;
            String[] parts = line.split("\\|", -1);
            if (parts.length != 6) continue;
            try {
                Entry entry = new Entry(parts[0], parts[1], parts[2], parts[3], parts[4], Long.parseLong(parts[5]));
                if (isSafe(entry)) out.add(entry);
            } catch (NumberFormatException ignored) {
            }
        }
        return out;
    }

    public static boolean isSafe(Entry e) {
        if (e == null || !TOKEN.matcher(e.key).matches() || e.timestamp < 0) return false;
        if (!(safeValue(e.originalValue) || ABSENT.equals(e.originalValue)) || !safeValue(e.appliedValue)) return false;
        if ("device_config".equals(e.source)) return TOKEN.matcher(e.namespace).matches();
        if ("property".equals(e.source)) return e.namespace.isEmpty();
        return e.namespace.isEmpty() && ("global".equals(e.source) || "secure".equals(e.source) || "system".equals(e.source));
    }

    public static String rollbackCommand(Entry e) {
        if (!isSafe(e)) throw new IllegalArgumentException("unsafe rollback entry");
        if ("device_config".equals(e.source)) {
            return "device_config put " + e.namespace + " " + e.key + " " + e.originalValue;
        }
        if ("property".equals(e.source)) {
            return "setprop " + e.key + " " + (ABSENT.equals(e.originalValue) ? "''" : e.originalValue);
        }
        return "settings put " + e.source + " " + e.key + " " + e.originalValue;
    }

    public static String verifyCommand(Entry e) {
        if (!isSafe(e)) throw new IllegalArgumentException("unsafe rollback entry");
        if ("device_config".equals(e.source)) return "device_config get " + e.namespace + " " + e.key;
        if ("property".equals(e.source)) return "getprop " + e.key;
        return "settings get " + e.source + " " + e.key;
    }

    private static boolean safeValue(String value) {
        return "0".equals(value) || "1".equals(value) || "false".equals(value) || "true".equals(value)
                || "off".equals(value) || "on".equals(value) || "disabled".equals(value) || "enabled".equals(value);
    }
}
