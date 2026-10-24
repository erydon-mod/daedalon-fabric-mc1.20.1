package com.oliver.daedalon.block;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/** Bundled, immutable shaft outlines; loaded once, with no runtime ERYDON dependency. */
public final class CapitalTransitionProfile {
    private static final CapitalTransitionProfile SQUARE_HEAD = square(1.0);
    private static final CapitalTransitionProfile GOTHIC_HEAD = square(14.0 / 16.0);
    private final double[][] points;
    private final double[] angles;
    private final int[][] triangles;
    private final double minX, minZ, maxX, maxZ;

    private CapitalTransitionProfile(double[][] points, double[] angles, int[][] triangles) {
        this.points = points;
        this.angles = angles;
        this.triangles = triangles;
        minX = java.util.Arrays.stream(points).mapToDouble(p -> p[0]).min().orElseThrow();
        minZ = java.util.Arrays.stream(points).mapToDouble(p -> p[1]).min().orElseThrow();
        maxX = java.util.Arrays.stream(points).mapToDouble(p -> p[0]).max().orElseThrow();
        maxZ = java.util.Arrays.stream(points).mapToDouble(p -> p[1]).max().orElseThrow();
    }

    public static CapitalTransitionProfile forTransition(CapitalTransition transition) {
        return forTransition(transition, CapitalBlock.Size.STANDARD);
    }

    public static CapitalTransitionProfile forTransition(CapitalTransition transition, CapitalBlock.Size size) {
        return switch (transition) {
            case SQUARE -> SQUARE_HEAD;
            case GOTHIC -> GOTHIC_HEAD;
            case SQUARE_SHAFT, GOTHIC_SHAFT -> Shafts.PROFILES.get(transition.asString()
                    + (size == CapitalBlock.Size.DOUBLE ? "_double" : ""));
            case NONE -> throw new IllegalArgumentException("An unfitted capital has no collar profile");
        };
    }

    public int size() { return points.length; }
    public double x(int vertex) { return points[vertex][0]; }
    public double z(int vertex) { return points[vertex][1]; }
    public double angle(int vertex) { return angles[vertex]; }
    public int triangleCount() { return triangles.length; }
    public int triangleVertex(int triangle, int vertex) { return triangles[triangle][vertex]; }
    public double minX() { return minX; }
    public double minZ() { return minZ; }
    public double maxX() { return maxX; }
    public double maxZ() { return maxZ; }

    private static CapitalTransitionProfile square(double width) {
        double[][] points = new double[16][2];
        double[] angles = new double[16];
        for (int i = 0; i < 16; i++) {
            double angle = i * Math.PI / 8, x = Math.cos(angle), z = Math.sin(angle);
            double radius = width * 0.5 / Math.max(Math.abs(x), Math.abs(z));
            points[i] = new double[]{0.5 + x * radius, 0.5 + z * radius};
            angles[i] = angle;
        }
        int[][] triangles = new int[14][3];
        for (int i = 0; i < 14; i++) triangles[i] = new int[]{0, i + 1, i + 2};
        return new CapitalTransitionProfile(points, angles, triangles);
    }

    private static final class Shafts {
        private static final Map<String, CapitalTransitionProfile> PROFILES = load();

        private static Map<String, CapitalTransitionProfile> load() {
            String resource = "/assets/daedalon/geometry/capital_shaft_profiles.json";
            try (var input = CapitalTransitionProfile.class.getResourceAsStream(resource)) {
                if (input == null) throw new IllegalStateException("Missing capital shaft profiles");
                JsonObject profiles = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8))
                        .getAsJsonObject().getAsJsonObject("profiles");
                Map<String, CapitalTransitionProfile> result = new LinkedHashMap<>();
                for (String name : java.util.List.of("square_shaft", "gothic_shaft", "square_shaft_double", "gothic_shaft_double")) {
                    JsonObject data = profiles.getAsJsonObject(name);
                    var outline = data.getAsJsonArray("outline");
                    double[][] points = new double[outline.size()][2];
                    double[] angles = new double[outline.size()];
                    for (int i = 0; i < points.length; i++) {
                        points[i][0] = outline.get(i).getAsJsonArray().get(0).getAsDouble();
                        points[i][1] = outline.get(i).getAsJsonArray().get(1).getAsDouble();
                        angles[i] = data.getAsJsonArray("circle_angles").get(i).getAsDouble();
                    }
                    var faces = data.getAsJsonArray("bottom_triangles");
                    int[][] triangles = new int[faces.size()][3];
                    for (int i = 0; i < faces.size(); i++) for (int v = 0; v < 3; v++)
                        triangles[i][v] = faces.get(i).getAsJsonArray().get(v).getAsInt();
                    result.put(name, new CapitalTransitionProfile(points, angles, triangles));
                }
                return Map.copyOf(result);
            } catch (java.io.IOException exception) {
                throw new IllegalStateException("Cannot read capital shaft profiles", exception);
            }
        }
    }
}
