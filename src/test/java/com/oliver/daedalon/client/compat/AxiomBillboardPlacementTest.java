package com.oliver.daedalon.client.compat;

import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class AxiomBillboardPlacementTest {
    @Test void imageFacesCameraAtEveryViewAndSelectionAngle() {
        for (int yaw = 0; yaw < 360; yaw += 30) for (int pitch : new int[]{-80, 0, 80}) {
            var camera = new Quaternionf().rotationYXZ((float)Math.toRadians(yaw), (float)Math.toRadians(pitch), 0);
            var view = new Matrix4f().rotate(new Quaternionf(camera).conjugate());
            for (int turn = 0; turn < 4; turn++) {
                var region = new Quaternionf().rotationY(turn * (float)Math.PI / 2);
                var matrix = AxiomBillboardPlacement.matrix(view, Vec3d.ZERO, Vec3d.ZERO,
                        region, camera, 3, 2, -5, 4);
                var normal = matrix.transformDirection(new Vector3f(0,0,1)).normalize();
                assertEquals(0, normal.x, 1e-5);
                assertEquals(0, normal.y, 1e-5);
                assertEquals(1, normal.z, 1e-5, "Image must not turn edge-on when the selection rotates");
                assertEquals(4, matrix.transformDirection(new Vector3f(0,1,0)).length(), 1e-5);
            }
        }
    }

    @Test void pasteTranslationRetainsPrecisionNearWorldBorder() {
        var matrix = AxiomBillboardPlacement.matrix(new Matrix4f(),
                new Vec3d(29_999_999.75, 64.25, -29_999_999.75),
                new Vec3d(29_999_999.875, 64.5, -29_999_999.875),
                null, new Quaternionf(), 0, 1.5, 0, 3);
        var center = matrix.transformPosition(new Vector3f());
        assertEquals(.125, center.x, 1e-6);
        assertEquals(1.75, center.y, 1e-6);
        assertEquals(-.125, center.z, 1e-6);
    }

    @Test void selectionRotationMovesCenterWithoutRotatingImage() {
        var matrix = AxiomBillboardPlacement.matrix(new Matrix4f(), Vec3d.ZERO,
                new Vec3d(10, 20, 30), new Quaternionf().rotationY((float)Math.PI / 2),
                new Quaternionf(), 2, 1.5, 0, 3);
        var center = matrix.transformPosition(new Vector3f());
        assertEquals(10, center.x, 1e-5);
        assertEquals(21.5, center.y, 1e-5);
        assertEquals(28, center.z, 1e-5);
        assertEquals(3, matrix.transformDirection(new Vector3f(1,0,0)).x, 1e-5);
    }
}
