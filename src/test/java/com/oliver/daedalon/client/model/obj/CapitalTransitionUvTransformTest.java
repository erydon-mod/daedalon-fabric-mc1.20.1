package com.oliver.daedalon.client.model.obj;

import net.fabricmc.fabric.api.renderer.v1.mesh.MutableQuadView;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.*;

final class CapitalTransitionUvTransformTest {
    @Test void everyFaceAndDoubleCellUsesTheColumnsHandedWorldPhaseAtNegativeCoordinates() {
        for (Direction face : Direction.values()) for (int n = -13; n <= 13; n++)
            for (int x = 0; x < 2; x++) for (int z = 0; z < 2; z++) {
                BlockPos anchor = new BlockPos(n, n + 1, -n);
                float px = x + 0.17F, py = 0.11F, pz = z + 0.23F;
                float[] uv = new float[2];
                var transform = CapitalTransitionUvTransform.forCell(anchor, x, z);
                assertSame(transform, CapitalTransitionUvTransform.forCell(anchor.add(6, 6, 6), x, z));
                transform.transform(quad(px, py, pz, face, uv));
                int wx = anchor.getX() + x, wz = anchor.getZ() + z;
                int tileU = switch (face) { case NORTH -> -wx - 1; case EAST -> -wz - 1;
                    case WEST -> wz; default -> wx; };
                int tileV = switch (face) { case UP -> wz; case DOWN -> -wz - 1; default -> -anchor.getY(); };
                float localU = switch (face) { case NORTH -> 1 - (px - x); case EAST -> 1 - (pz - z);
                    case WEST -> pz - z; default -> px - x; };
                float localV = switch (face) { case UP -> pz - z; case DOWN -> 1 - (pz - z); default -> 1 - py; };
                assertEquals((Math.floorMod(tileU, 6) + localU) / 6, uv[0], 1e-6);
                assertEquals((Math.floorMod(tileV, 6) + localV) / 6, uv[1], 1e-6);
                assertTrue(uv[0] >= 0 && uv[0] <= 1 && uv[1] >= 0 && uv[1] <= 1);
            }
    }

    @Test void materialBakingKeepsOneTilePerBlockAndVerticalUvsPointUpCorrectly() {
        for (int cell = 0; cell < 2; cell++) {
            float[] a = new float[2], b = new float[2];
            var transform = CapitalTransitionUvTransform.forCell(BlockPos.ORIGIN, cell, 0);
            transform.transform(quad(cell + 0.2F, 0.05F, 0.2F, Direction.SOUTH, a));
            transform.transform(quad(cell + 0.45F, 0.15F, 0.2F, Direction.SOUTH, b));
            var layout = WorldTexturePhase.detailedSurface();
            assertEquals(0.25, (layout.mapU(b[0], 0) - layout.mapU(a[0], 0)) * 12, 1e-6);
            assertEquals(-0.1, (layout.mapV(b[1]) - layout.mapV(a[1])) * 6, 1e-6);
        }
    }

    @Test void adjacentDoubleCellsAndTheColumnBelowShareTexturePhaseAtPeriodEdges() {
        for (Direction face : Direction.values()) for (int n = -7; n <= 7; n++) {
            BlockPos anchor = new BlockPos(n, n, n);
            float[] a = new float[2], b = new float[2];
            CapitalTransitionUvTransform.forCell(anchor, 0, 0).transform(quad(1, 0.1F, 0.3F, face, a));
            CapitalTransitionUvTransform.forCell(anchor, 1, 0).transform(quad(1, 0.1F, 0.3F, face, b));
            assertSamePhase(a, b);
            if (face.getAxis().isHorizontal()) {
                CapitalTransitionUvTransform.forCell(anchor, 0, 0).transform(quad(0.3F, 0, 0.3F, face, a));
                CapitalTransitionUvTransform.forCell(anchor.down(), 0, 0).transform(quad(0.3F, 1, 0.3F, face, b));
                assertSamePhase(a, b);
            }
        }
    }

    private static void assertSamePhase(float[] a, float[] b) {
        for (int i = 0; i < 2; i++) assertEquals(0, Math.IEEEremainder(a[i] - b[i], 1), 1e-6);
    }

    private static MutableQuadView quad(float x, float y, float z, Direction face, float[] uv) {
        return (MutableQuadView) Proxy.newProxyInstance(MutableQuadView.class.getClassLoader(),
                new Class<?>[]{MutableQuadView.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "x" -> x; case "y" -> y; case "z" -> z; case "nominalFace" -> face;
                    case "uv" -> { uv[0] = (float) args[1]; uv[1] = (float) args[2]; yield proxy; }
                    default -> throw new AssertionError("Collar UV projection must not edit geometry, normals, faces or tags: " + method.getName());
                });
    }
}
