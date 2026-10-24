package com.oliver.daedalon.block;

import com.google.gson.JsonParser;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** Immutable final-baseface outlines measured from the three authored basin meshes. */
public final class FountainBaseProfile {
    private final double[] x, z, topOffset;
    private final int[][] triangles;
    private final double sourceUvMinX, sourceUvMinZ, sourceUvSpan;

    private FountainBaseProfile(String style) {
        String path = "/data/daedalon/fountain_base/" + style + ".json";
        try (var input = FountainBaseProfile.class.getResourceAsStream(path)) {
            if (input == null) throw new IllegalStateException("Missing fountain base profile " + path);
            var data = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
            var points = data.getAsJsonArray("points");
            if (points.size() < 3 || points.size() > 512) throw new IllegalStateException("Invalid fountain base outline " + path);
            x = new double[points.size()]; z = new double[points.size()]; topOffset = new double[points.size()];
            var heights = data.getAsJsonArray("top_y");
            if (heights.size() != points.size()) throw new IllegalStateException("Missing fountain underside heights " + path);
            for (int i = 0; i < points.size(); i++) {
                var point = points.get(i).getAsJsonArray();
                x[i] = point.get(0).getAsDouble(); z[i] = point.get(1).getAsDouble();
                topOffset[i] = heights.get(i).getAsDouble();
            }
            var caps = data.getAsJsonArray("triangles");
            triangles = new int[caps.size()][3];
            for (int i = 0; i < caps.size(); i++) for (int corner = 0; corner < 3; corner++) {
                int index = caps.get(i).getAsJsonArray().get(corner).getAsInt();
                if (index < 0 || index >= x.length) throw new IllegalStateException("Invalid fountain cap index " + path);
                triangles[i][corner] = index;
            }
            sourceUvMinX = data.get("source_uv_min_x").getAsDouble();
            sourceUvMinZ = data.get("source_uv_min_z").getAsDouble();
            sourceUvSpan = data.get("source_uv_span").getAsDouble();
        } catch (java.io.IOException failure) {
            throw new IllegalStateException("Cannot read fountain base profile " + path, failure);
        }
    }

    private static final class Profiles {
        private static final FountainBaseProfile[] VALUES = {
                new FountainBaseProfile("gothic"), new FountainBaseProfile("georgian"), new FountainBaseProfile("greek")};
    }

    public static FountainBaseProfile forStyle(FountainBasinBlock.Style style) { return Profiles.VALUES[style.ordinal()]; }
    public int vertexCount() { return x.length; }
    public double x(int index) { return x[index]; }
    public double z(int index) { return z[index]; }
    public double topOffset(int index) { return topOffset[index]; }
    public int triangleCount() { return triangles.length; }
    public int triangleVertex(int triangle, int corner) { return triangles[triangle][corner]; }
    public double sourceUvMinX() { return sourceUvMinX; }
    public double sourceUvMinZ() { return sourceUvMinZ; }
    public double sourceUvSpan() { return sourceUvSpan; }
}
