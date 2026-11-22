package com.oliver.daedalon.client.glaux.interaction;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class GlauxHudProjectionTest {
    private final Matrix4f projection = new Matrix4f().perspective((float)Math.toRadians(70), 16f / 9f, .05f, 100f);

    @Test void owlInMinecraftForwardDirectionProjectsInFront() {
        Vector4f clip = new Vector4f();
        GlauxHudProjection.toClip(new Vector3f(0, 0, 4), new Quaternionf(), projection, clip);
        assertTrue(clip.w > 0);
        assertEquals(0, clip.x / clip.w, 1e-6);
        assertEquals(0, clip.y / clip.w, 1e-6);
    }

    @Test void owlBehindCameraIsNotPresentedAsFrontAnchor() {
        Vector4f clip = new Vector4f();
        GlauxHudProjection.toClip(new Vector3f(0, 0, -4), new Quaternionf(), projection, clip);
        assertTrue(clip.w < 0);
    }

    @Test void actualMinecraftCameraAnglesPutIndependentWorldLookVectorAtCentre() {
        for (float yawDegrees : new float[]{0, 90, 180, -90, 37}) {
            for (float pitchDegrees : new float[]{-60, -20, 0, 30, 70}) {
                float yaw = (float)Math.toRadians(yawDegrees), pitch = (float)Math.toRadians(pitchDegrees);
                // Named 1.20.1 Camera.setRotation bytecode uses (-yaw, +pitch, 0), forward +Z.
                Quaternionf camera = new Quaternionf().rotationYXZ(-yaw, pitch, 0);
                // Minecraft look direction, calculated independently of that quaternion.
                Vector3f direction = new Vector3f(-(float)(Math.sin(yaw) * Math.cos(pitch)),
                        -(float)Math.sin(pitch), (float)(Math.cos(yaw) * Math.cos(pitch))).mul(4);
                Vector4f clip = new Vector4f();
                GlauxHudProjection.toClip(direction, camera.conjugate(new Quaternionf()), projection, clip);
                assertTrue(clip.w > 0);
                assertEquals(0, clip.x / clip.w, 1e-5);
                assertEquals(0, clip.y / clip.w, 1e-5);
            }
        }
    }

    @Test void worldRightAndUpKeepTheExpectedScreenDirections() {
        Vector4f right = new Vector4f(), up = new Vector4f();
        GlauxHudProjection.toClip(new Vector3f(-1, 0, 4), new Quaternionf(), projection, right);
        GlauxHudProjection.toClip(new Vector3f(0, 1, 4), new Quaternionf(), projection, up);
        assertTrue(right.x / right.w > 0);
        assertTrue(up.y / up.w > 0); // HUD converts positive NDC Y to a smaller screen Y.
    }
}
