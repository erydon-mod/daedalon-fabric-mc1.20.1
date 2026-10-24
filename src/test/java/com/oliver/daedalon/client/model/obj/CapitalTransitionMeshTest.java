package com.oliver.daedalon.client.model.obj;

import com.oliver.daedalon.block.CapitalBlock;
import com.oliver.daedalon.block.CapitalTransition;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class CapitalTransitionMeshTest {
    @Test void collarKeepsTheColumnFootprintAndOriginalCapitalHeightAtBothSizes() {
        for (var transition : new CapitalTransition[]{CapitalTransition.SQUARE, CapitalTransition.GOTHIC}) {
            for (var size : CapitalBlock.Size.values()) {
                double scale = size == CapitalBlock.Size.DOUBLE ? 2 : 1;
                double centre = scale * 0.5;
                var rings = CapitalTransitionMesh.rings(transition, size);
                assertEquals(3, rings.length);
                for (int i = 0; i < CapitalTransitionMesh.SEGMENTS; i++) {
                    assertEquals(transition.baseWidth() * scale * 0.5,
                            Math.max(Math.abs(rings[0][i][0] - centre), Math.abs(rings[0][i][2] - centre)), 1e-9);
                    assertEquals(0, rings[0][i][1]);
                    assertEquals(scale / 32, rings[1][i][1]);
                    assertEquals(CapitalTransition.HEIGHT * scale, rings[2][i][1]);
                    assertEquals(CapitalTransition.JOIN_DIAMETER * scale * 0.5,
                            Math.hypot(rings[2][i][0] - centre, rings[2][i][2] - centre), 1e-9);
                    assertTrue(rings[2][i][1] < scale, "The collar must remain within the existing capital height");
                }
                assertEquals(0.5 - transition.baseWidth() * 0.5, rings[0][8][0] / scale, 1e-9);
                assertEquals(0.5 + transition.baseWidth() * 0.5, rings[0][0][0] / scale, 1e-9);
            }
        }
    }
}
