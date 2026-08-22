package com.questhub.gamepadrepair;

import android.content.Context;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class GamepadModeRepairService {
    private final Context context;
    private final RollbackJournal journal;

    public static final class Result {
        public final boolean success;
        public final boolean changed;
        public final String observedValue;
        public final List<String> messages;

        Result(boolean success, boolean changed, String observedValue, List<String> messages) {
            this.success = success;
            this.changed = changed;
            this.observedValue = observedValue == null ? "" : observedValue;
            this.messages = Collections.unmodifiableList(new ArrayList<>(messages));
        }
    }

    public GamepadModeRepairService(Context context) {
        this.context = context.getApplicationContext();
        this.journal = new RollbackJournal(this.context);
    }

    public String readCurrentValue() throws Exception {
        requireShellUid();
        return AdbClient.shell(context, GamepadModeRepairPlan.readCommand()).trim();
    }

    public Result enable() {
        List<String> messages = new ArrayList<>();
        try {
            requireShellUid();
            String before = AdbClient.shell(context, GamepadModeRepairPlan.readCommand()).trim();
            messages.add("Observed before: " + printable(before));

            if ("true".equalsIgnoreCase(before)) {
                messages.add("Gamepad Mode flag is already true. No write was performed.");
                return new Result(true, false, before, messages);
            }
            if (!GamepadModeRepairPlan.canEnable(before)) {
                messages.add("Refused: expected the exact current value false, but observed " + printable(before) + ".");
                messages.add("No settings were changed.");
                return new Result(false, false, before, messages);
            }

            boolean wrote = false;
            try {
                AdbClient.shell(context, GamepadModeRepairPlan.enableCommand());
                wrote = true;
                String after = AdbClient.shell(context, GamepadModeRepairPlan.readCommand()).trim();
                if (!"true".equalsIgnoreCase(after)) {
                    messages.add("Verification failed: write did not read back as true. Restoring false.");
                    try { AdbClient.shell(context, GamepadModeRepairPlan.restoreCommand()); } catch (Exception ignored) {}
                    String restored = safeRead();
                    messages.add("Observed after rollback attempt: " + printable(restored));
                    return new Result(false, false, restored, messages);
                }

                journal.append(new RollbackCodec.Entry(
                        "device_config",
                        GamepadModeRepairPlan.NAMESPACE,
                        GamepadModeRepairPlan.KEY,
                        "false",
                        "true",
                        System.currentTimeMillis()));
                messages.add("Verified: hzos_vendor_native/oculus_emulated_gamepad = true");
                messages.add("Kill-switch key was not read or modified by this repair.");
                return new Result(true, true, after, messages);
            } catch (Exception e) {
                if (wrote) {
                    try { AdbClient.shell(context, GamepadModeRepairPlan.restoreCommand()); } catch (Exception ignored) {}
                }
                messages.add("Enable failed: " + shortError(e));
                messages.add("Rollback was attempted if a write may have occurred.");
                return new Result(false, false, safeRead(), messages);
            }
        } catch (Exception e) {
            messages.add("Refused before write: " + shortError(e));
            return new Result(false, false, "", messages);
        }
    }

    public Result restore() {
        List<String> messages = new ArrayList<>();
        try {
            requireShellUid();
            List<RollbackCodec.Entry> entries = journal.load();
            boolean found = false;
            for (RollbackCodec.Entry entry : entries) {
                if (isTargetEntry(entry)) { found = true; break; }
            }
            if (!found) {
                String observed = AdbClient.shell(context, GamepadModeRepairPlan.readCommand()).trim();
                messages.add("Refused: v7 has no recorded Gamepad Mode change to restore.");
                messages.add("Observed current value: " + printable(observed));
                return new Result(false, false, observed, messages);
            }

            AdbClient.shell(context, GamepadModeRepairPlan.restoreCommand());
            String after = AdbClient.shell(context, GamepadModeRepairPlan.readCommand()).trim();
            if (!"false".equalsIgnoreCase(after)) {
                messages.add("Restore verification failed: expected false, observed " + printable(after) + ".");
                return new Result(false, false, after, messages);
            }

            List<RollbackCodec.Entry> keep = new ArrayList<>();
            for (RollbackCodec.Entry entry : entries) if (!isTargetEntry(entry)) keep.add(entry);
            journal.save(keep);
            messages.add("Verified restore: hzos_vendor_native/oculus_emulated_gamepad = false");
            messages.add("No other rollback entries were changed.");
            return new Result(true, true, after, messages);
        } catch (Exception e) {
            messages.add("Restore failed: " + shortError(e));
            return new Result(false, false, safeRead(), messages);
        }
    }

    private void requireShellUid() throws Exception {
        String identity = AdbClient.shell(context, "id");
        if (!identity.contains("uid=2000")) throw new IllegalStateException("Android shell UID 2000 was not verified");
    }

    private boolean isTargetEntry(RollbackCodec.Entry entry) {
        return entry != null
                && "device_config".equals(entry.source)
                && GamepadModeRepairPlan.NAMESPACE.equals(entry.namespace)
                && GamepadModeRepairPlan.KEY.equals(entry.key)
                && "false".equals(entry.originalValue)
                && "true".equals(entry.appliedValue);
    }

    private String safeRead() {
        try { return AdbClient.shell(context, GamepadModeRepairPlan.readCommand()).trim(); }
        catch (Exception ignored) { return ""; }
    }

    private static String printable(String value) {
        return value == null || value.trim().isEmpty() ? "<empty>" : value.trim();
    }

    private static String shortError(Exception e) {
        String message = e.getMessage();
        if (message == null || message.trim().isEmpty()) message = e.getClass().getSimpleName();
        message = message.replace('\n', ' ').replace('\r', ' ').trim();
        return message.length() > 180 ? message.substring(0, 180) : message;
    }
}
