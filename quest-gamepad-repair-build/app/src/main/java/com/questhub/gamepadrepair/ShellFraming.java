package com.questhub.gamepadrepair;

final class ShellFraming {
    private ShellFraming() {}

    static String begin(String marker) {
        return "__QGR_BEGIN_" + marker + "__";
    }

    static String end(String marker) {
        return "__QGR_END_" + marker + "__";
    }

    static String script(String command, String marker) {
        if (command == null || command.isEmpty()) throw new IllegalArgumentException("empty command");
        if (marker == null || !marker.matches("[A-Za-z0-9_]+")) throw new IllegalArgumentException("invalid marker");

        // Keep the complete raw delimiters out of the transmitted input. Horizon can
        // echo commands before `stty -echo` takes effect; if the raw marker appeared
        // in an echoed printf command, the reader could mistake that echo for actual
        // command completion. The shell assembles each delimiter from two literals.
        String suffix = marker + "__";
        return "stty -echo 2>/dev/null\n"
                + "PS1=''; PS2=''\n"
                + "printf '%s%s\\n' '__QGR_BEGIN_' '" + suffix + "'\n"
                + command + "\n"
                + "printf '\\n%s%s\\n' '__QGR_END_' '" + suffix + "'\n"
                + "exit\n";
    }

    static boolean isComplete(String transcript, String marker) {
        if (transcript == null) return false;
        String normalized = transcript.replace("\r", "");
        return normalized.contains(end(marker));
    }

    static String extract(String transcript, String marker) {
        String normalized = transcript == null ? "" : transcript.replace("\r", "");
        String begin = begin(marker);
        String end = end(marker);
        int start = normalized.indexOf(begin);
        if (start < 0) throw new IllegalStateException("shell begin marker missing");
        start += begin.length();
        if (start < normalized.length() && normalized.charAt(start) == '\n') start++;
        int finish = normalized.indexOf(end, start);
        if (finish < 0) throw new IllegalStateException("shell end marker missing");
        String output = normalized.substring(start, finish);
        while (output.startsWith("\n")) output = output.substring(1);
        while (output.endsWith("\n")) output = output.substring(0, output.length() - 1);
        return output.trim();
    }
}
