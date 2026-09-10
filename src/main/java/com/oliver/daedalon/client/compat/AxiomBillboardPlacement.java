package com.oliver.daedalon.client.compat;

import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Quaternionfc;
import org.joml.Vector3d;

/** Region rotation moves the anchor; the image itself always uses camera rotation. */
public final class AxiomBillboardPlacement {
    private AxiomBillboardPlacement() {}

    public static Matrix4f matrix(Matrix4fc view, Vec3d camera, Vec3d translation,
                                   Quaternionfc regionRotation, Quaternionfc cameraRotation,
                                   double x, double y, double z, float height) {
        Vector3d center = new Vector3d(x, y, z);
        if (regionRotation != null) regionRotation.transform(center);
        // Subtract in double precision before converting to the render matrix.
        center.add(translation.x - camera.x, translation.y - camera.y, translation.z - camera.z);
        return new Matrix4f(view).translate((float) center.x, (float) center.y, (float) center.z)
                .rotate(cameraRotation).scale(height);
    }
}
