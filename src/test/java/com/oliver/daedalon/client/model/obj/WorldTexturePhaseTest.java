package com.oliver.daedalon.client.model.obj;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class WorldTexturePhaseTest {
    private static final float EPSILON = 1.0E-6F;

    @Test
    void detailedSurfaceKeepsOneSixBySixWindowAndSixOrigins() {
        WorldTexturePhase.Layout layout = WorldTexturePhase.detailedSurface();

        assertEquals(6, layout.sourceColumns());
        assertEquals(6, layout.sourceRows());
        assertEquals(6, layout.phaseCount());
        assertEquals(0.5F, layout.uScale(), EPSILON);
        assertEquals(1.0F, layout.vScale(), EPSILON);
        assertEquals(1.0F / 12.0F, layout.uStep(), EPSILON);
        assertEquals(5.0F / 12.0F, layout.maximumUOffset(), EPSILON);
        assertEquals(0.5F, layout.mapU(1.0F, 0), EPSILON);
        assertEquals(11.0F / 12.0F, layout.mapU(1.0F, 5), EPSILON);
        assertEquals(1.0F, layout.mapV(1.0F), EPSILON);
    }

    @Test
    void urnSurfaceRetainsFourByOneTextureDensity() {
        WorldTexturePhase.Layout layout = WorldTexturePhase.urnSurface();

        assertEquals(6, layout.phaseCount());
        assertEquals(1.0F / 3.0F, layout.uScale(), EPSILON);
        assertEquals(1.0F / 6.0F, layout.vScale(), EPSILON);
        assertEquals(0.25F, layout.mapU(0.75F, 0), EPSILON);
        assertEquals(2.0F / 3.0F, layout.mapU(0.75F, 5), EPSILON);
    }

    @Test
    void oneTileSurfaceSelectsOneRepeatTile() {
        WorldTexturePhase.Layout layout = WorldTexturePhase.blockSurface();

        assertEquals(6, layout.phaseCount());
        assertEquals(1.0F / 12.0F, layout.uScale(), EPSILON);
        assertEquals(1.0F / 6.0F, layout.vScale(), EPSILON);
        assertEquals(0.5F, layout.mapU(1.0F, 5), EPSILON);
    }

    @Test
    void phaseSelectionTracksWorldCoordinatesWithSixTilePeriod() {
        WorldTexturePhase.Layout layout = WorldTexturePhase.detailedSurface();

        assertEquals(0, layout.index(0, 0, 0));
        assertEquals(1, layout.index(1, 0, 0));
        assertEquals(5, layout.index(0, 1, 0));
        assertEquals(1, layout.index(0, 0, 1));
        assertEquals(5, layout.index(-1, 0, 0));
        assertEquals(0, layout.index(6, 0, 0));
    }

    @Test
    void phaseCannotEscapeTheExpandedSprite() {
        WorldTexturePhase.Layout layout = WorldTexturePhase.detailedSurface();

        assertThrows(IllegalArgumentException.class, () -> layout.mapU(0.0F, -1));
        assertThrows(IllegalArgumentException.class, () -> layout.mapU(0.0F, 6));
    }
}
