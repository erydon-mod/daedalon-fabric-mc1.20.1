package com.oliver.daedalon.client.compat;

import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class AxiomLanternSupportsTest {
    @Test void regionSupportsWorkBeforeOrAfterTheLanternAndCanBeResizedOrRemoved() {
        var supports = new AxiomLanternSupports();
        assertEquals(Vec3d.ZERO, supports.offset(23, 17, -40, Direction.SOUTH));
        assertTrue(supports.set(23, 17, -41, new Box(0, 0, 0, 1, 1, .125)));
        assertEquals(new Vec3d(0, 0, -.875), supports.offset(23, 17, -40, Direction.SOUTH));
        assertFalse(supports.set(23, 17, -41, new Box(0, 0, 0, 1, 1, .125)));
        assertTrue(supports.set(23, 17, -41, new Box(0, 0, 0, 1, 1, .625)));
        assertEquals(new Vec3d(0, 0, -.375), supports.offset(23, 17, -40, Direction.SOUTH));
        assertTrue(supports.set(23, 17, -41, null));
        assertEquals(Vec3d.ZERO, supports.offset(23, 17, -40, Direction.SOUTH));
        assertFalse(supports.set(23, 17, -41, null));
    }

    @Test void allEighthDepthsAndFacingsUseOnlyTheAdjacentSelectedSupport() {
        var supports = new AxiomLanternSupports();
        int x = -123, y = 29, z = 216;
        for (Direction face : Direction.Type.HORIZONTAL) for (int layers = 1; layers <= 8; layers++) {
            supports.clear();
            double t = layers / 8.;
            Box box = switch (face) {
                case NORTH -> new Box(0, 0, 1 - t, 1, 1, 1);
                case SOUTH -> new Box(0, 0, 0, 1, 1, t);
                case EAST -> new Box(0, 0, 0, t, 1, 1);
                default -> new Box(1 - t, 0, 0, 1, 1, 1);
            };
            int supportX = x - face.getOffsetX(), supportZ = z - face.getOffsetZ();
            supports.set(supportX, y, supportZ, box);
            Vec3d offset = supports.offset(x, y, z, face);
            Vec3d expected = Vec3d.of(face.getVector()).multiply(t - 1);
            assertEquals(expected.x, offset.x, 1e-9);
            assertEquals(expected.y, offset.y, 1e-9);
            assertEquals(expected.z, offset.z, 1e-9);
            assertEquals(Vec3d.ZERO, supports.offset(x, y + 1, z, face));
            assertEquals(Vec3d.ZERO, supports.offset(x + 7, y, z + 7, face));
            supports.clear();
            assertEquals(Vec3d.ZERO, supports.offset(x, y, z, face));
        }
    }

    @Test void exposedEdgesKeepTheSelectedThinFacesTransverseCentre() {
        var supports = new AxiomLanternSupports();
        supports.set(0, 0, 0, new Box(0, 0, 0, 1, 1, .125));
        assertEquals(new Vec3d(0, 0, -.4375), supports.offset(1, 0, 0, Direction.EAST));
        assertEquals(new Vec3d(0, 0, -.4375), supports.offset(-1, 0, 0, Direction.WEST));
    }
}
