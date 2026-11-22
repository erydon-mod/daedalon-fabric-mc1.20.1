package com.oliver.daedalon.client.glaux.interaction;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;

/** Camera's positive-Z look basis to the negative-Z projection/view basis. */
final class GlauxHudProjection {
    private GlauxHudProjection() { }
    static void toClip(Vector3f relative, Quaternionf inverseCamera, Matrix4f projection, Vector4f output) {
        relative.rotate(inverseCamera);
        relative.x = -relative.x;
        relative.z = -relative.z;
        output.set(relative.x, relative.y, relative.z, 1).mul(projection);
    }
}
