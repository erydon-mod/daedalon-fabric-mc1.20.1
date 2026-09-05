package com.oliver.daedalon.item;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class DaedalonDebugPropertiesTest {
    @Test
    void cyclesRememberedTextByValueRatherThanObjectIdentity() {
        List<String> controls = List.of("basin_size", "plinth_size", "tiers");
        String rememberedFromNbt = new String("plinth_size");

        assertEquals(
                "tiers",
                DaedalonDebugProperties.cycle(controls, rememberedFromNbt, false)
        );
        assertEquals(
                "basin_size",
                DaedalonDebugProperties.cycle(controls, rememberedFromNbt, true)
        );
    }

    @Test
    void cyclesAtBothEndsAndRecoversFromAnUnknownValue() {
        List<String> controls = List.of("basin_size", "waterlogged");

        assertEquals(
                "basin_size",
                DaedalonDebugProperties.cycle(controls, "waterlogged", false)
        );
        assertEquals(
                "waterlogged",
                DaedalonDebugProperties.cycle(controls, "basin_size", true)
        );
        assertEquals(
                "basin_size",
                DaedalonDebugProperties.cycle(controls, "old_control", false)
        );
        assertEquals(
                "waterlogged",
                DaedalonDebugProperties.cycle(controls, "old_control", true)
        );
    }

    @Test
    void rejectsAnEmptyCycle() {
        assertThrows(
                IllegalArgumentException.class,
                () -> DaedalonDebugProperties.cycle(List.of(), "anything", false)
        );
    }
}
