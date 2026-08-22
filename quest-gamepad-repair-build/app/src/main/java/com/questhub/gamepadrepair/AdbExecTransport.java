package com.questhub.gamepadrepair;

final class AdbExecTransport {
    private static final int MAX_COMMAND_CHARS = 96;

    private AdbExecTransport() {}

    static String destination(String command) {
        if (command == null || command.isEmpty()) throw new IllegalArgumentException("empty command");
        if (command.length() > MAX_COMMAND_CHARS) throw new IllegalArgumentException("command too long for safe local ADB transport");
        if (command.indexOf('\n') >= 0 || command.indexOf('\r') >= 0 || command.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("invalid command characters");
        }
        return "exec:" + command;
    }
}
