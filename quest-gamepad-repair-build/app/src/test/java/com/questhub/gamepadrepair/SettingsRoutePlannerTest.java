package com.questhub.gamepadrepair;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

public final class SettingsRoutePlannerTest {
    @Test
    public void questOnlyRoutesPreferHiddenSettingsRootAndKeepManualFallbacks() {
        List<SettingsRoutePlanner.Route> routes = SettingsRoutePlanner.routes();
        assertTrue(routes.size() >= 5);
        assertEquals(SettingsRoutePlanner.Kind.HIDDEN_SETTINGS_ROOT, routes.get(0).kind);
        assertTrue(routes.stream().anyMatch(r -> r.kind == SettingsRoutePlanner.Kind.ABOUT_HEADSET));
        assertTrue(routes.stream().anyMatch(r -> r.kind == SettingsRoutePlanner.Kind.DEVELOPER_OPTIONS));
        assertTrue(routes.stream().anyMatch(r -> r.kind == SettingsRoutePlanner.Kind.SETTINGS_APP_INFO));
        assertTrue(routes.stream().anyMatch(r -> r.kind == SettingsRoutePlanner.Kind.WIRELESS_DEBUGGING));
    }

    @Test
    public void everyRouteIsBoundToAndroidSettingsPackage() {
        for (SettingsRoutePlanner.Route route : SettingsRoutePlanner.routes()) {
            assertEquals("com.android.settings", route.packageName);
        }
    }

    @Test
    public void directDeveloperAndWirelessRoutesHaveSafeFallbackText() {
        SettingsRoutePlanner.Route dev = SettingsRoutePlanner.first(SettingsRoutePlanner.Kind.DEVELOPER_OPTIONS);
        SettingsRoutePlanner.Route wireless = SettingsRoutePlanner.first(SettingsRoutePlanner.Kind.WIRELESS_DEBUGGING);
        assertTrue(dev.fallbackInstruction.toLowerCase().contains("about headset"));
        assertTrue(wireless.fallbackInstruction.toLowerCase().contains("wireless debugging"));
    }
}
