package com.questhub.gamepadrepair;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class RollbackCodecTest {
    @Test public void roundTripPreservesExactMutationValues() {
        List<RollbackCodec.Entry> entries = Arrays.asList(new RollbackCodec.Entry("secure", "", "touch_controller_gamepad", "0", "1", 1234L), new RollbackCodec.Entry("device_config", "oculus", "touch_xbox_gamepad", "false", "true", 5678L), new RollbackCodec.Entry("property", "", "debug.oculus.experimentalEnabled", "__ABSENT__", "1", 9999L));
        assertEquals(entries, RollbackCodec.decode(RollbackCodec.encode(entries)));
    }
    @Test public void malformedLinesAreIgnoredInsteadOfBecomingCommands() { List<RollbackCodec.Entry> d = RollbackCodec.decode("secure||good_key|0|1|123\nsecure||bad;key|0|1|123\nnonsense\n"); assertEquals(1, d.size()); assertEquals("good_key", d.get(0).key); }
    @Test public void rollbackCommandsAreExactAndAbsentPropertyUsesClear() {
        RollbackCodec.Entry s = new RollbackCodec.Entry("secure", "", "touch_controller_gamepad", "false", "true", 1L); RollbackCodec.Entry c = new RollbackCodec.Entry("device_config", "oculus", "touch_xbox_gamepad", "0", "1", 2L); RollbackCodec.Entry p = new RollbackCodec.Entry("property", "", "debug.oculus.experimentalEnabled", "__ABSENT__", "1", 3L);
        assertEquals("settings put secure touch_controller_gamepad false", RollbackCodec.rollbackCommand(s)); assertEquals("device_config put oculus touch_xbox_gamepad 0", RollbackCodec.rollbackCommand(c)); assertEquals("setprop debug.oculus.experimentalEnabled ''", RollbackCodec.rollbackCommand(p)); assertTrue(RollbackCodec.isSafe(s)); assertTrue(RollbackCodec.isSafe(c)); assertTrue(RollbackCodec.isSafe(p));
    }
}
