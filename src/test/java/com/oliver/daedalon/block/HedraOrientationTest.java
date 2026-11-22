package com.oliver.daedalon.block;

import com.oliver.daedalon.item.DaedalonDebugProperties;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

final class HedraOrientationTest {
    @Test void oppositeDiagonalIsAppendedWithoutChangingExistingSavedValues() {
        assertEquals(List.of("straight", "diagonal", "straight_90", "diagonal_135"),
                Arrays.stream(HedraOrientation.values()).map(HedraOrientation::asString).toList());
        assertEquals(List.of(0, 1, 2, 3),
                Arrays.stream(HedraOrientation.values()).map(axis -> axis.eighthTurns).toList());
        assertSame(HedraOrientation.DIAGONAL_135, HedraOrientation.fromEighthTurns(3));
        assertSame(HedraOrientation.DIAGONAL_135, HedraOrientation.fromEighthTurns(-1));
        assertSame(HedraOrientation.DIAGONAL, HedraOrientation.fromEighthTurns(5));
    }

    @Test void placementUsesEveryAxisAndKeepsOppositePlayerLooksEquivalent() {
        var axes = HedraOrientation.values();
        for (int turn = -16; turn <= 16; turn++) {
            var expected = axes[Math.floorMod(turn, 4)];
            assertSame(expected, HedraOrientation.fromPlayerYaw(turn * 45F));
            assertSame(expected, HedraOrientation.fromPlayerYaw(turn * 45F + 180));
        }
        assertSame(HedraOrientation.STRAIGHT, HedraOrientation.fromPlayerYaw(22.4F));
        assertSame(HedraOrientation.DIAGONAL, HedraOrientation.fromPlayerYaw(22.6F));
        assertSame(HedraOrientation.STRAIGHT, HedraOrientation.fromPlayerYaw(-22.4F));
        assertSame(HedraOrientation.DIAGONAL_135, HedraOrientation.fromPlayerYaw(-22.6F));
    }

    @Test void debugCyclesThroughBothDiagonalsInEachDirection() {
        var axes = List.of(HedraOrientation.values());
        var current = HedraOrientation.STRAIGHT;
        for (int step = 1; step <= 12; step++) {
            current = DaedalonDebugProperties.cycle(axes, current, false);
            assertSame(axes.get(step % 4), current);
        }
        for (int step = 1; step <= 12; step++) {
            current = DaedalonDebugProperties.cycle(axes, current, true);
            assertSame(axes.get(Math.floorMod(-step, 4)), current);
        }
    }
}
