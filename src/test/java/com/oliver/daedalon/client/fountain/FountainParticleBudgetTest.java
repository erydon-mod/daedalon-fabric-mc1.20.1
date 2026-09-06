package com.oliver.daedalon.client.fountain;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FountainParticleBudgetTest {
    @Test void normalRetainsOriginalLimitsAndReusesItsBudget() {
        var normal = FountainParticleBudget.select(false);
        assertSame(FountainParticleBudget.NORMAL, normal);
        assertEquals(768, normal.maxParticles());
        assertEquals(320, normal.maxPerFountain());
        assertEquals(48, normal.spawnLimit(false));
        assertEquals(24, normal.spawnLimit(true));
    }
    @Test void highQuadruplesEmissionsAndEveryParticleLimitIncludingDecreasedMode() {
        var normal = FountainParticleBudget.NORMAL;
        var high = FountainParticleBudget.select(true);
        assertEquals(normal.maxParticles() * 4, high.maxParticles());
        assertEquals(normal.maxPerFountain() * 4, high.maxPerFountain());
        for (boolean decreased : new boolean[]{false, true}) {
            assertEquals(normal.spawnLimit(decreased) * 4, high.spawnLimit(decreased));
            for (int cadence = 0; cadence <= 48; cadence++) {
                assertEquals(normal.emissions(cadence, decreased) * 4, high.emissions(cadence, decreased));
            }
        }
        assertSame(normal, FountainParticleBudget.select(false));
    }
}
