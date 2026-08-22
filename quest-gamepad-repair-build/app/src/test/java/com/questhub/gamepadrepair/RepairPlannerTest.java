package com.questhub.gamepadrepair;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class RepairPlannerTest {
    @Test public void strongSettingsMatchProducesReversibleMutation() {
        List<RepairPlanner.Candidate> candidates = RepairPlanner.parseSettings("secure", "quest_touch_xbox_gamepad_enabled=0\nhand_controller_enabled=0\n");
        List<RepairPlanner.Mutation> plan = RepairPlanner.plan(candidates);
        assertEquals(1, plan.size()); RepairPlanner.Mutation m = plan.get(0);
        assertEquals("secure", m.source); assertEquals("quest_touch_xbox_gamepad_enabled", m.key); assertEquals("0", m.originalValue); assertEquals("1", m.targetValue);
        assertEquals("settings put secure quest_touch_xbox_gamepad_enabled 1", m.applyCommand); assertEquals("settings get secure quest_touch_xbox_gamepad_enabled", m.verifyCommand); assertEquals("settings put secure quest_touch_xbox_gamepad_enabled 0", m.rollbackCommand);
    }
    @Test public void genericControllerSettingIsNeverAutoRepaired() { assertTrue(RepairPlanner.plan(RepairPlanner.parseSettings("global", "controller_enabled=0\ntouch_controller_tracking=0\n")).isEmpty()); }
    @Test public void gamepadWithoutControllerSignalIsNeverAutoRepaired() { assertTrue(RepairPlanner.plan(RepairPlanner.parseSettings("system", "gamepad_enabled=0\nxbox_enabled=false\n")).isEmpty()); }
    @Test public void alreadyEnabledStrongMatchIsNotChanged() { assertTrue(RepairPlanner.plan(RepairPlanner.parseSettings("secure", "touch_controller_gamepad_enabled=1\n")).isEmpty()); }
    @Test public void falseStyleIsPreservedAsTrue() { RepairPlanner.Mutation m = RepairPlanner.plan(RepairPlanner.parseSettings("secure", "touch_controller_gamepad_enabled=false\n")).get(0); assertEquals("true", m.targetValue); }
    @Test public void deviceConfigNamespaceIsParsedAndRestoredExactly() {
        List<RepairPlanner.Mutation> plan = RepairPlanner.plan(RepairPlanner.parseDeviceConfig("oculus/quest_touch_controller_xbox_gamepad=false\nother/some_controller=false\n"));
        assertEquals(1, plan.size()); RepairPlanner.Mutation m = plan.get(0); assertEquals("oculus", m.namespace); assertEquals("device_config put oculus quest_touch_controller_xbox_gamepad true", m.applyCommand); assertEquals("device_config put oculus quest_touch_controller_xbox_gamepad false", m.rollbackCommand);
    }
    @Test public void malformedOrUnsafeKeysAreIgnored() { List<RepairPlanner.Candidate> c = new ArrayList<>(); c.addAll(RepairPlanner.parseSettings("secure", "touch_controller_gamepad;rm=0\n")); c.addAll(RepairPlanner.parseDeviceConfig("oculus/quest_touch_xbox_gamepad;rm=0\n")); assertTrue(RepairPlanner.plan(c).isEmpty()); }
    @Test public void offAndDisabledAreRecognizedButUnknownValuesAreNot() { List<RepairPlanner.Candidate> c = new ArrayList<>(); c.addAll(RepairPlanner.parseSettings("secure", "touch_controller_gamepad_a=off\n")); c.addAll(RepairPlanner.parseSettings("secure", "touch_controller_gamepad_b=disabled\n")); c.addAll(RepairPlanner.parseSettings("secure", "touch_controller_gamepad_c=maybe\n")); List<RepairPlanner.Mutation> p = RepairPlanner.plan(c); assertEquals(2, p.size()); assertEquals("on", p.get(0).targetValue); assertEquals("enabled", p.get(1).targetValue); }
}
