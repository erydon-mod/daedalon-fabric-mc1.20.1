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

    @Test void bareShaftRingsRetainTheMeasuredFlutesAndLobesAtBothSizes() {
        assertEquals("none", CapitalTransition.values()[0].asString());
        assertEquals("square", CapitalTransition.values()[1].asString());
        assertEquals("gothic", CapitalTransition.values()[2].asString());
        for (var transition : new CapitalTransition[]{CapitalTransition.SQUARE_SHAFT, CapitalTransition.GOTHIC_SHAFT}) {
            var profile = transition.profile();
            assertTrue(profile.size() > 16, "The collar must retain the authored flutes and lobes");
            assertEquals(transition == CapitalTransition.SQUARE_SHAFT ? 2.5 / 16 : 2.0922891001 / 16,
                    profile.minX(), 1e-6);
            assertEquals(transition == CapitalTransition.SQUARE_SHAFT ? 13.5 / 16 : 13.9077108999 / 16,
                    profile.maxX(), 1e-6);
            double outlineArea = 0, trianglesArea = 0;
            for (int i = 0; i < profile.size(); i++) {
                int next = (i + 1) % profile.size();
                outlineArea += profile.x(i) * profile.z(next) - profile.x(next) * profile.z(i);
            }
            for (int i = 0; i < profile.triangleCount(); i++) {
                int a = profile.triangleVertex(i, 0), b = profile.triangleVertex(i, 1), c = profile.triangleVertex(i, 2);
                double area = (profile.x(b) - profile.x(a)) * (profile.z(c) - profile.z(a))
                        - (profile.z(b) - profile.z(a)) * (profile.x(c) - profile.x(a));
                assertTrue(area > 0, "Bottom triangles must point downwards without overlaps or zero-area faces");
                trianglesArea += area;
            }
            assertEquals(outlineArea, trianglesArea, 1e-9, "The triangulated underside must cover the whole shaft outline");
            for (var size : CapitalBlock.Size.values()) {
                double scale = size == CapitalBlock.Size.DOUBLE ? 2 : 1;
                var sized = transition.profile(size);
                var rings = CapitalTransitionMesh.rings(transition, size);
                assertEquals(transition == CapitalTransition.SQUARE_SHAFT ? (size == CapitalBlock.Size.DOUBLE ? 27.0 / 16 : 11.0 / 16)
                                : (size == CapitalBlock.Size.DOUBLE ? 27.8154217998 / 16 : 11.8154217998 / 16),
                        (sized.maxX() - sized.minX()) * scale, 1e-6, "Large bases must span all four real shafts");
                for (int i = 0; i < sized.size(); i++) {
                    assertEquals(sized.x(i) * scale, rings[0][i][0]);
                    assertEquals(sized.z(i) * scale, rings[0][i][2]);
                    assertEquals(0, rings[0][i][1]);
                    assertEquals(CapitalTransition.HEIGHT * scale, rings[2][i][1]);
                    assertEquals(CapitalTransition.JOIN_DIAMETER * scale * 0.5,
                            Math.hypot(rings[2][i][0] - scale * 0.5, rings[2][i][2] - scale * 0.5), 1e-9);
                }
            }
        }
    }

    @Test void doubleCollarSplittingRetainsAreaAndEveryVertexStaysInsideItsTextureCell() {
        var source = java.util.List.of(new double[]{0.2, 0.25, 0.2}, new double[]{1.8, 0.25, 0.2},
                new double[]{1.8, 0.25, 1.8}, new double[]{0.2, 0.25, 1.8});
        double area = 0;
        for (int x = 0; x < 2; x++) for (int z = 0; z < 2; z++) {
            var polygon = CapitalTransitionMesh.clipToCell(source, x, z);
            for (int i = 0; i < polygon.size(); i++) {
                var p = polygon.get(i); var next = polygon.get((i + 1) % polygon.size());
                assertTrue(p[0] >= x && p[0] <= x + 1 && p[2] >= z && p[2] <= z + 1);
                assertEquals(0.25, p[1]);
                area += (p[0] * next[2] - next[0] * p[2]) * 0.5;
            }
        }
        assertEquals(1.6 * 1.6, area, 1e-12);
        assertArrayEquals(new double[]{0.2, 0.25, 0.2}, source.get(0), "Clipping must not move the source geometry");
    }
}
