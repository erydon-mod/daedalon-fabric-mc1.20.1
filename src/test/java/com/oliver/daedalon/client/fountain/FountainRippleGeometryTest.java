package com.oliver.daedalon.client.fountain;

import net.minecraft.client.render.VertexConsumer;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

final class FountainRippleGeometryTest {
    @Test
    void movingDropsStretchWithSpeedWithoutAddingQuadsOrChangingCamera() {
        for (float pitch : new float[]{0, .7F, 1.57F}) {
            var rotation = new org.joml.Quaternionf().rotationXYZ(pitch, .4F, 0);
            var original = new org.joml.Quaternionf(rotation);
            for (float speed : new float[]{0, .1F, .8F}) {
                Capture capture = new Capture();
                FountainStreakGeometry.emit(capture, rotation, 1, 2, 3, .04F, .01F, speed, .02F,
                        0, 1, 0, 1, 1, 1, 1, .9F, 0);
                assertEquals(4, capture.vertices.size());
                assertEquals(original, rotation, "Rendering must not mutate the camera");
                for (Vertex vertex : capture.vertices) {
                    assertTrue(Double.isFinite(vertex.x) && Double.isFinite(vertex.y) && Double.isFinite(vertex.z));
                    assertTrue(Math.abs(vertex.x-1)<.35 && Math.abs(vertex.y-2)<.35 && Math.abs(vertex.z-3)<.35);
                }
                var a = capture.vertices.get(0);
                var b = capture.vertices.get(1);
                var c = capture.vertices.get(2);
                double length = Math.sqrt(Math.pow(b.x-a.x,2)+Math.pow(b.y-a.y,2)+Math.pow(b.z-a.z,2));
                double width = Math.sqrt(Math.pow(c.x-b.x,2)+Math.pow(c.y-b.y,2)+Math.pow(c.z-b.z,2));
                assertEquals(.08, width, .00001);
                assertTrue(length >= width - .00001);
            }
        }
    }
    @Test
    void rippleIsSquareFlatUpwardFacingAndKeepsTintAndLighting() {
        for (float radius : new float[]{0.05F, 0.1F, 0.2F}) {
            Capture capture = new Capture();
            FountainRippleGeometry.emit(capture, 2, 3, 4, radius, 0.2F, 0.4F, 0.6F, 0.8F,
                    0.25F, 0.5F, 0.75F, 0.8F, 0x00F000A0);
            assertEquals(4, capture.vertices.size());
            for (Vertex vertex : capture.vertices) {
                assertEquals(3, vertex.y);
                assertEquals(radius, Math.abs(vertex.x - 2), 0.00001);
                assertEquals(radius, Math.abs(vertex.z - 4), 0.00001);
                assertEquals(List.of(63, 127, 191, 204), vertex.color);
                assertEquals(0x00F000A0, vertex.light);
            }
            Vertex a = capture.vertices.get(0), b = capture.vertices.get(1), c = capture.vertices.get(2);
            double normalY = (b.z - a.z) * (c.x - a.x) - (b.x - a.x) * (c.z - a.z);
            assertTrue(normalY > 0, "Water ripple must face up");
            assertEquals(0.2F, a.u);
            assertEquals(0.6F, a.v);
            assertEquals(0.4F, c.u);
            assertEquals(0.8F, c.v);
        }
    }

    @Test
    void changingViewPositionOnlyTranslatesTheWaterPlane() {
        Capture first = new Capture(), second = new Capture();
        FountainRippleGeometry.emit(first, 1, 2, 3, 0.125F, 0, 1, 0, 1, 1, 1, 1, 1, 0);
        FountainRippleGeometry.emit(second, -5, -6, -7, 0.125F, 0, 1, 0, 1, 1, 1, 1, 1, 0);
        for (int i = 0; i < 4; i++) {
            Vertex a = first.vertices.get(i), b = second.vertices.get(i);
            assertEquals(-6, b.x - a.x);
            assertEquals(-8, b.y - a.y);
            assertEquals(-10, b.z - a.z);
            assertEquals(a.u, b.u);
            assertEquals(a.v, b.v);
        }
    }

    private record Vertex(double x, double y, double z, float u, float v, List<Integer> color, int light) { }

    private static final class Capture implements VertexConsumer {
        final List<Vertex> vertices = new ArrayList<>();
        double x, y, z;
        float u, v;
        List<Integer> color;
        int light;

        @Override public VertexConsumer vertex(double x, double y, double z) {
            this.x = x; this.y = y; this.z = z; return this;
        }
        @Override public VertexConsumer color(int r, int g, int b, int a) {
            color = List.of(r, g, b, a); return this;
        }
        @Override public VertexConsumer texture(float u, float v) { this.u = u; this.v = v; return this; }
        @Override public VertexConsumer light(int u, int v) { light = u | (v << 16); return this; }
        @Override public VertexConsumer overlay(int u, int v) { return this; }
        @Override public VertexConsumer normal(float x, float y, float z) { return this; }
        @Override public void next() { vertices.add(new Vertex(x, y, z, u, v, color, light)); }
        @Override public void fixedColor(int r, int g, int b, int a) { fail("No global color state changes"); }
        @Override public void unfixColor() { fail("No global color state changes"); }
    }
}
