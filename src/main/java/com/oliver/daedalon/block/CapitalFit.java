package com.oliver.daedalon.block;

import com.google.gson.JsonParser;
import net.minecraft.util.math.Box;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/** Measured placed fits, shared by all materials; no mesh or support queries at render time. */
public final class CapitalFit {
    public static final float MAX_SCALE = 2.5F;
    private static final Map<String, float[][]> SCALES = load();

    private CapitalFit() { }

    public static float scale(String style, CapitalTransition support, CapitalBlock.Size size) {
        if (support == CapitalTransition.NONE) return 1;
        float[][] scales = SCALES.get(style);
        if (scales == null) throw new IllegalArgumentException("Unknown capital style: " + style);
        return scales[support.ordinal()][size.ordinal()];
    }

    /** The source top stays flush; only rendered geometry extends down into its support. */
    public static Box bounds(double width, double depth, CapitalOrientation orientation,
                             CapitalBlock.Size size, float fit) {
        double multiple = size == CapitalBlock.Size.DOUBLE ? 2 : 1;
        double centre = multiple * .5;
        double halfX = (Math.abs(orientation.cosine) * width + Math.abs(orientation.sine) * depth) * multiple * fit * .5;
        double halfZ = (Math.abs(orientation.sine) * width + Math.abs(orientation.cosine) * depth) * multiple * fit * .5;
        // Authored OBJ coordinates and baked float positions differ by a few millionths.
        return new Box(centre - halfX, multiple * (1 - fit), centre - halfZ,
                centre + halfX, multiple, centre + halfZ).expand(1e-5);
    }

    private static Map<String, float[][]> load() {
        String resource = "/assets/daedalon/geometry/capital_column_fits.json";
        try (var input = CapitalFit.class.getResourceAsStream(resource)) {
            if (input == null) throw new IllegalStateException("Missing measured capital fits");
            var styles = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8))
                    .getAsJsonObject().getAsJsonObject("styles");
            Map<String, float[][]> result = new LinkedHashMap<>();
            for (String style : java.util.List.of("byzantine", "corinthian", "gothic", "greek_ionic", "roman_composite", "tuscan")) {
                var values = styles.getAsJsonObject(style);
                float[][] scales = new float[CapitalTransition.values().length][CapitalBlock.Size.values().length];
                for (CapitalTransition support : CapitalTransition.values()) for (CapitalBlock.Size size : CapitalBlock.Size.values()) {
                    float scale = support == CapitalTransition.NONE ? 1
                            : values.getAsJsonObject(support.asString()).get(size.asString()).getAsFloat();
                    if (!Float.isFinite(scale) || scale < 1 || scale > MAX_SCALE)
                        throw new IllegalStateException("Invalid measured capital fit: " + style + "/" + support + "/" + size);
                    scales[support.ordinal()][size.ordinal()] = scale;
                }
                result.put(style, scales);
            }
            return Map.copyOf(result);
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Cannot read measured capital fits", exception);
        }
    }
}
