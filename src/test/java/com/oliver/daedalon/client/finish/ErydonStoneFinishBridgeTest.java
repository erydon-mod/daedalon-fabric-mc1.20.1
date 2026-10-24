package com.oliver.daedalon.client.finish;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ErydonStoneFinishBridgeTest {
    @Test
    void optionalBindingUsesTheCapturedRendererSnapshotAndItsActualClassifier() throws Exception {
        Preferences.active = new Settings(Map.of("glacium_hedra", Level.HONED,
                "latmion_zeus_statue", Level.POLISHED, "statue_spartan_promachos_glacium", Level.MIRROR));
        var result = ErydonStoneFinishBridge.read(Preferences.class, Materials.class, List.of(
                "glacium_hedra", "latmion_zeus_statue", "statue_spartan_promachos_glacium",
                "glacium_aged_hedra", "white_gothic_wall_panel", "silver_gothic_frieze"));
        assertEquals(Map.of("glacium_hedra", "honed", "latmion_zeus_statue", "polished",
                "statue_spartan_promachos_glacium", "mirror"), result);
        Preferences.active = new Settings(Map.of("glacium_hedra", Level.MIRROR));
        assertEquals("honed", result.get("glacium_hedra"), "A saved preference is inactive until restart, like rendering");
        assertThrows(UnsupportedOperationException.class, () -> result.put("glacium_hedra", "mirror"));
    }

    @Test
    void absentOrOlderOptionalApiCanBeRejectedWithoutLinkingErydonTypes() {
        ClassLoader absent = new ClassLoader(null) { };
        assertThrows(ClassNotFoundException.class, () -> ErydonStoneFinishBridge.read(absent, List.of("glacium_hedra")));
        assertThrows(NoSuchMethodException.class, () -> ErydonStoneFinishBridge.read(Object.class, Materials.class, List.of()));
    }

    public enum Level { HONED, POLISHED, MIRROR }
    public record Settings(Map<String, Level> values) { }
    public static final class Preferences {
        private static Settings active;
        public static Settings activeSettings() { return active; }
    }
    public static final class Materials {
        public static boolean includes(String namespace, String path) {
            return namespace.equals("daedalon") && Preferences.active.values.containsKey(path);
        }
        public static Level level(Settings settings, String path) { return settings.values.get(path); }
    }
}
