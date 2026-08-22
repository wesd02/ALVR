package com.questhub.gamepadrepair;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class GamepadModeRepairService {
    private static final String PREFS = "gamepad_mode_v8";
    private static final String KEY_CREATED_OVERRIDE = "created_target_override";

    private final Context context;
    private final SharedPreferences prefs;

    public static final class Result {
        public final boolean success;
        public final boolean changed;
        public final String observedValue;
        public final boolean overridePresent;
        public final List<String> messages;

        Result(boolean success, boolean changed, String observedValue, boolean overridePresent, List<String> messages) {
            this.success = success;
            this.changed = changed;
            this.observedValue = observedValue == null ? "" : observedValue;
            this.overridePresent = overridePresent;
            this.messages = Collections.unmodifiableList(new ArrayList<>(messages));
        }
    }

    public GamepadModeRepairService(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = this.context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public String readCurrentValue() throws Exception {
        requireShellUid();
        return AdbClient.shell(context, GamepadModeRepairPlan.readCommand()).trim();
    }

    public boolean isTargetOverridePresent() throws Exception {
        requireShellUid();
        return GamepadModeRepairPlan.hasTargetOverride(
                AdbClient.shell(context, GamepadModeRepairPlan.listOverridesCommand()));
    }

    public Result enable() {
        List<String> messages = new ArrayList<>();
        try {
            requireShellUid();

            String help = AdbClient.shell(context, GamepadModeRepairPlan.helpCommand());
            if (!GamepadModeRepairPlan.supportsStickyOverride(help)) {
                messages.add("Refused: this Horizon build did not advertise DeviceConfig sticky override commands.");
                messages.add("No configuration was changed.");
                return new Result(false, false, safeRead(), safeOverridePresent(), messages);
            }
            messages.add("Sticky override capability: VERIFIED");

            String before = AdbClient.shell(context, GamepadModeRepairPlan.readCommand()).trim();
            messages.add("Observed effective value before: " + printable(before));
            if (!GamepadModeRepairPlan.isSafeUnderlyingValue(before)) {
                messages.add("Refused: expected a boolean Gamepad value but observed " + printable(before) + ".");
                return new Result(false, false, before, safeOverridePresent(), messages);
            }

            String overridesBefore = AdbClient.shell(context, GamepadModeRepairPlan.listOverridesCommand());
            if (GamepadModeRepairPlan.hasTargetOverride(overridesBefore)) {
                String effective = AdbClient.shell(context, GamepadModeRepairPlan.readCommand()).trim();
                if ("true".equalsIgnoreCase(effective)) {
                    prefs.edit().putBoolean(KEY_CREATED_OVERRIDE, true).apply();
                    messages.add("Target sticky override already exists and reads true. No write was performed.");
                    return new Result(true, false, effective, true, messages);
                }
                messages.add("Refused: a target override exists but the effective value is not true.");
                messages.add("No write was performed.");
                return new Result(false, false, effective, true, messages);
            }

            boolean wrote = false;
            try {
                AdbClient.shell(context, GamepadModeRepairPlan.enableCommand());
                wrote = true;

                String overridesAfter = AdbClient.shell(context, GamepadModeRepairPlan.listOverridesCommand());
                boolean overridePresent = GamepadModeRepairPlan.hasTargetOverride(overridesAfter);
                String after = AdbClient.shell(context, GamepadModeRepairPlan.readCommand()).trim();
                if (!overridePresent || !"true".equalsIgnoreCase(after)) {
                    messages.add("Verification failed: sticky override was not present as true. Clearing only the target override.");
                    try { AdbClient.shell(context, GamepadModeRepairPlan.restoreCommand()); } catch (Exception ignored) {}
                    return new Result(false, false, safeRead(), safeOverridePresent(), messages);
                }

                prefs.edit().putBoolean(KEY_CREATED_OVERRIDE, true).apply();
                messages.add("Verified local sticky override: hzos_vendor_native/oculus_emulated_gamepad = true");
                messages.add("This per-flag override ignores server updates for this flag.");
                messages.add("Global DeviceConfig synchronization was NOT disabled.");
                messages.add("The Gamepad kill-switch key was NOT modified.");
                return new Result(true, true, after, true, messages);
            } catch (Exception e) {
                if (wrote) {
                    try { AdbClient.shell(context, GamepadModeRepairPlan.restoreCommand()); } catch (Exception ignored) {}
                }
                messages.add("Sticky enable failed: " + shortError(e));
                messages.add("The target override was cleared if a write may have occurred.");
                return new Result(false, false, safeRead(), safeOverridePresent(), messages);
            }
        } catch (Exception e) {
            messages.add("Refused before write: " + shortError(e));
            return new Result(false, false, safeRead(), safeOverridePresent(), messages);
        }
    }

    public Result restore() {
        List<String> messages = new ArrayList<>();
        try {
            requireShellUid();
            boolean created = prefs.getBoolean(KEY_CREATED_OVERRIDE, false);
            boolean present = GamepadModeRepairPlan.hasTargetOverride(
                    AdbClient.shell(context, GamepadModeRepairPlan.listOverridesCommand()));

            if (!created) {
                messages.add("Refused: v8 has no record that it created the target override.");
                messages.add("No configuration was changed.");
                return new Result(false, false, safeRead(), present, messages);
            }
            if (!present) {
                prefs.edit().remove(KEY_CREATED_OVERRIDE).apply();
                messages.add("The v8 target override is already absent. Nothing to clear.");
                return new Result(true, false, safeRead(), false, messages);
            }

            AdbClient.shell(context, GamepadModeRepairPlan.restoreCommand());
            boolean afterPresent = GamepadModeRepairPlan.hasTargetOverride(
                    AdbClient.shell(context, GamepadModeRepairPlan.listOverridesCommand()));
            if (afterPresent) {
                messages.add("Clear verification failed: the target sticky override is still present.");
                return new Result(false, false, safeRead(), true, messages);
            }

            prefs.edit().remove(KEY_CREATED_OVERRIDE).apply();
            String underlying = AdbClient.shell(context, GamepadModeRepairPlan.readCommand()).trim();
            messages.add("Verified: v8 target sticky override cleared.");
            messages.add("Effective value returned to the current underlying rollout value: " + printable(underlying));
            messages.add("No other DeviceConfig override was changed.");
            return new Result(true, true, underlying, false, messages);
        } catch (Exception e) {
            messages.add("Restore failed: " + shortError(e));
            return new Result(false, false, safeRead(), safeOverridePresent(), messages);
        }
    }

    private void requireShellUid() throws Exception {
        String identity = AdbClient.shell(context, "id");
        if (!identity.contains("uid=2000")) throw new IllegalStateException("Android shell UID 2000 was not verified");
    }

    private String safeRead() {
        try { return AdbClient.shell(context, GamepadModeRepairPlan.readCommand()).trim(); }
        catch (Exception ignored) { return ""; }
    }

    private boolean safeOverridePresent() {
        try {
            return GamepadModeRepairPlan.hasTargetOverride(
                    AdbClient.shell(context, GamepadModeRepairPlan.listOverridesCommand()));
        } catch (Exception ignored) {
            return false;
        }
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
