package com.questhub.gamepadrepair;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Pure-data launch plan so Quest-only settings routes can be unit tested without Android. */
public final class SettingsRoutePlanner {
    public enum Kind {
        HIDDEN_SETTINGS_ROOT,
        ABOUT_HEADSET,
        DEVELOPER_OPTIONS,
        SETTINGS_APP_INFO,
        WIRELESS_DEBUGGING
    }

    public static final class Route {
        public final Kind kind;
        public final String label;
        public final String packageName;
        public final String action;
        public final String componentClass;
        public final String dataUri;
        public final String fallbackInstruction;

        Route(Kind kind, String label, String action, String componentClass, String dataUri,
              String fallbackInstruction) {
            this.kind = kind;
            this.label = label;
            this.packageName = "com.android.settings";
            this.action = action == null ? "" : action;
            this.componentClass = componentClass == null ? "" : componentClass;
            this.dataUri = dataUri == null ? "" : dataUri;
            this.fallbackInstruction = fallbackInstruction == null ? "" : fallbackInstruction;
        }
    }

    private static final List<Route> ROUTES;
    static {
        List<Route> routes = new ArrayList<>();
        routes.add(new Route(
                Kind.HIDDEN_SETTINGS_ROOT,
                "Hidden Android Settings (explicit root)",
                "android.settings.SETTINGS",
                "com.android.settings.Settings",
                "",
                "If this is blocked or redirected, try Android Settings App Info and tap Open."));
        routes.add(new Route(
                Kind.HIDDEN_SETTINGS_ROOT,
                "Hidden Android Settings (package action)",
                "android.settings.SETTINGS",
                "",
                "",
                "If Android Settings opens, scroll to About Headset and tap Build Number seven times."));
        routes.add(new Route(
                Kind.ABOUT_HEADSET,
                "About Headset",
                "android.settings.DEVICE_INFO_SETTINGS",
                "",
                "",
                "If this route is blocked, open Hidden Android Settings, then scroll to About Headset."));
        routes.add(new Route(
                Kind.DEVELOPER_OPTIONS,
                "Developer Options",
                "android.settings.APPLICATION_DEVELOPMENT_SETTINGS",
                "",
                "",
                "If Developer Options is hidden, open About Headset and tap Build Number seven times first."));
        routes.add(new Route(
                Kind.DEVELOPER_OPTIONS,
                "Developer Options (explicit activity)",
                "android.intent.action.MAIN",
                "com.android.settings.Settings$DevelopmentSettingsDashboardActivity",
                "",
                "If Meta blocks this activity, open About Headset, enable developer options, then navigate there manually."));
        routes.add(new Route(
                Kind.SETTINGS_APP_INFO,
                "Android Settings App Info",
                "android.settings.APPLICATION_DETAILS_SETTINGS",
                "",
                "package:com.android.settings",
                "If App Info appears, press Open to enter the hidden Android Settings app."));
        routes.add(new Route(
                Kind.WIRELESS_DEBUGGING,
                "Wireless Debugging",
                "android.settings.WIRELESS_DEBUGGING_SETTINGS",
                "",
                "",
                "Meta may block this direct route. If so, use Hidden Android Settings → System → Developer Options → Wireless Debugging."));
        routes.add(new Route(
                Kind.WIRELESS_DEBUGGING,
                "Wireless Debugging (explicit activity)",
                "android.intent.action.MAIN",
                "com.android.settings.Settings$WirelessDebuggingActivity",
                "",
                "Meta may block this activity. Use Hidden Android Settings → System → Developer Options → Wireless Debugging instead."));
        ROUTES = Collections.unmodifiableList(routes);
    }

    private SettingsRoutePlanner() {}

    public static List<Route> routes() {
        return ROUTES;
    }

    public static Route first(Kind kind) {
        for (Route route : ROUTES) if (route.kind == kind) return route;
        throw new IllegalArgumentException("unknown route kind: " + kind);
    }

    public static List<Route> forKind(Kind kind) {
        List<Route> out = new ArrayList<>();
        for (Route route : ROUTES) if (route.kind == kind) out.add(route);
        return Collections.unmodifiableList(out);
    }
}
