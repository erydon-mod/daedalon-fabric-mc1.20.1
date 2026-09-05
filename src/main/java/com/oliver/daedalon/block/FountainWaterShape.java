package com.oliver.daedalon.block;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import net.minecraft.util.function.BooleanBiFunction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** Immutable, offline-measured, star-shaped and X-monotone fountain waterlines. */
public final class FountainWaterShape {
    public static final FountainWaterShape GEORGIAN = load("georgian");
    public static final FountainWaterShape GREEK = load("greek");
    public static final FountainWaterShape GOTHIC_BOWL = load("gothic_bowl");
    public static final FountainWaterShape GEORGIAN_BOWL = load("georgian_bowl");
    public static final FountainWaterShape GREEK_BOWL = load("greek_bowl");
    private static final int PRISM_STRIPS = 48;
    private final float[] x;
    private final float[] z;
    private final float halfWidth;

    private FountainWaterShape(float[] x, float[] z) {
        this.x = x;
        this.z = z;
        float width = 0.0F;
        for (int i = 0; i < x.length; i++) {
            width = Math.max(width, Math.max(Math.abs(x[i]), Math.abs(z[i])));
        }
        halfWidth = width;
    }

    private static FountainWaterShape load(String style) {
        String path = "/data/daedalon/fountain_water/" + style + ".json";
        try (InputStream stream = FountainWaterShape.class.getResourceAsStream(path)) {
            if (stream == null) {
                throw new IllegalStateException("Missing fountain waterline: " + path);
            }
            JsonArray points = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                    .getAsJsonObject().getAsJsonArray("points");
            if (points.size() < 3 || points.size() > 256) {
                throw new IllegalStateException("Invalid fountain waterline: " + path);
            }
            float[] x = new float[points.size()];
            float[] z = new float[points.size()];
            for (int i = 0; i < points.size(); i++) {
                x[i] = points.get(i).getAsJsonArray().get(0).getAsFloat();
                z[i] = points.get(i).getAsJsonArray().get(1).getAsFloat();
            }
            return new FountainWaterShape(x, z);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read fountain waterline: " + path, exception);
        }
    }

    public int vertexCount() { return x.length; }
    public float x(int index) { return x[index]; }
    public float z(int index) { return z[index]; }
    public float halfWidth() { return halfWidth; }

    public boolean contains(double px, double pz) {
        boolean inside = false;
        for (int i = 0, j = x.length - 1; i < x.length; j = i++) {
            if ((z[i] > pz) != (z[j] > pz)
                    && px < (x[j] - x[i]) * (pz - z[i]) / (z[j] - z[i]) + x[i]) {
                inside = !inside;
            }
        }
        return inside;
    }

    /** Exact polygon/rectangle overlap, with no allocations in the entity-water path. */
    public boolean intersects(double minX, double minZ, double maxX, double maxZ) {
        if (minX > halfWidth || minZ > halfWidth || maxX < -halfWidth || maxZ < -halfWidth) {
            return false;
        }
        if (contains(minX, minZ) || contains(minX, maxZ)
                || contains(maxX, minZ) || contains(maxX, maxZ)) {
            return true;
        }
        for (int i = 0, j = x.length - 1; i < x.length; j = i++) {
            double dx = x[j] - x[i];
            double dz = z[j] - z[i];
            double lo = 0.0D;
            double hi = 1.0D;
            if (Math.abs(dx) < 1.0E-12D) {
                if (x[i] < minX || x[i] > maxX) continue;
            } else {
                double a = (minX - x[i]) / dx;
                double b = (maxX - x[i]) / dx;
                lo = Math.max(lo, Math.min(a, b));
                hi = Math.min(hi, Math.max(a, b));
            }
            if (Math.abs(dz) < 1.0E-12D) {
                if (z[i] < minZ || z[i] > maxZ) continue;
            } else {
                double a = (minZ - z[i]) / dz;
                double b = (maxZ - z[i]) / dz;
                lo = Math.max(lo, Math.min(a, b));
                hi = Math.min(hi, Math.max(a, b));
            }
            if (lo <= hi) return true;
        }
        return false;
    }

    /** One-time bounded voxel approximation for collision/selection, never rendering. */
    VoxelShape prism(double scale, double bottom, double top, boolean expand) {
        float minX = x[0], maxX = x[0];
        for (float value : x) {
            minX = Math.min(minX, value);
            maxX = Math.max(maxX, value);
        }
        VoxelShape result = VoxelShapes.empty();
        double width = (maxX - minX) / PRISM_STRIPS;
        for (int strip = 0; strip < PRISM_STRIPS; strip++) {
            double left = minX + strip * width;
            double right = left + width;
            double lower = expand ? Double.POSITIVE_INFINITY : Double.NEGATIVE_INFINITY;
            double upper = -lower;
            for (double sample : new double[]{left + 1.0E-8D, (left + right) * 0.5D, right - 1.0E-8D}) {
                double low = Double.POSITIVE_INFINITY, high = Double.NEGATIVE_INFINITY;
                for (int i = 0, j = x.length - 1; i < x.length; j = i++) {
                    if ((x[i] <= sample && sample < x[j]) || (x[j] <= sample && sample < x[i])) {
                        double value = z[i] + (sample - x[i]) * (z[j] - z[i]) / (x[j] - x[i]);
                        low = Math.min(low, value);
                        high = Math.max(high, value);
                    }
                }
                lower = expand ? Math.min(lower, low) : Math.max(lower, low);
                upper = expand ? Math.max(upper, high) : Math.min(upper, high);
            }
            if (lower < upper) {
                result = VoxelShapes.combine(result, VoxelShapes.cuboid(
                        0.5D + left * scale, bottom, 0.5D + lower * scale,
                        0.5D + right * scale, top, 0.5D + upper * scale), BooleanBiFunction.OR);
            }
        }
        return result;
    }
}
