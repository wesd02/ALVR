package com.questhub.gamepadrepair;

import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Launches only known Android Settings surfaces. It never executes shell commands. */
public final class HiddenSettingsLauncher {
    public static final class LaunchResult {
        public final boolean launched;
        public final String routeLabel;
        public final String message;
        public final List<String> attempts;

        LaunchResult(boolean launched, String routeLabel, String message, List<String> attempts) {
            this.launched = launched;
            this.routeLabel = routeLabel;
            this.message = message;
            this.attempts = Collections.unmodifiableList(new ArrayList<>(attempts));
        }
    }

    private HiddenSettingsLauncher() {}

    public static LaunchResult launch(Context context, SettingsRoutePlanner.Kind kind) {
        List<String> attempts = new ArrayList<>();
        for (SettingsRoutePlanner.Route route : SettingsRoutePlanner.forKind(kind)) {
            try {
                Intent intent = buildIntent(route);
                context.startActivity(intent);
                attempts.add(route.label + ": startActivity accepted");
                return new LaunchResult(true, route.label, route.fallbackInstruction, attempts);
            } catch (ActivityNotFoundException | SecurityException e) {
                attempts.add(route.label + ": blocked — " + shortError(e));
            } catch (RuntimeException e) {
                attempts.add(route.label + ": failed — " + shortError(e));
            }
        }
        SettingsRoutePlanner.Route route = SettingsRoutePlanner.first(kind);
        return new LaunchResult(false, "", route.fallbackInstruction, attempts);
    }

    private static Intent buildIntent(SettingsRoutePlanner.Route route) {
        Intent intent = route.action.isEmpty() ? new Intent() : new Intent(route.action);
        if (!route.dataUri.isEmpty()) intent.setData(Uri.parse(route.dataUri));
        if (!route.componentClass.isEmpty()) {
            intent.setComponent(new ComponentName(route.packageName, route.componentClass));
        } else {
            // Force AOSP Android Settings instead of letting Horizon choose another resolver.
            intent.setPackage(route.packageName);
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return intent;
    }

    private static String shortError(Throwable error) {
        String message = error.getMessage();
        if (message == null || message.trim().isEmpty()) message = error.getClass().getSimpleName();
        message = message.replace('\n', ' ').replace('\r', ' ').trim();
        return message.length() > 160 ? message.substring(0, 160) : message;
    }
}
