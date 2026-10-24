package com.oliver.daedalon.block;

import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/** Cached collision/selection for the one-block continuation beneath a raised basin. */
public final class FountainRaisedBaseGeometry {
    private static final int STRIPS = 128;

    private FountainRaisedBaseGeometry() { }

    private static final class Shapes {
        private static final VoxelShape[][] VALUES = create();
    }

    public static VoxelShape collision(FountainBasinBlock.Style style, FountainBasinBlock.Size size) {
        return Shapes.VALUES[style.ordinal()][size.ordinal()];
    }

    public static VoxelShape outline(FountainBasinBlock.Style style, FountainBasinBlock.Size size) {
        return collision(style, size);
    }

    private static VoxelShape[][] create() {
        var styles = FountainBasinBlock.Style.values(); var sizes = FountainBasinBlock.Size.values();
        VoxelShape[][] result = new VoxelShape[styles.length][sizes.length];
        for (var style : styles) for (var size : sizes) result[style.ordinal()][size.ordinal()] = prism(
                FountainBaseProfile.forStyle(style), size.modelScale());
        return result;
    }

    private static VoxelShape prism(FountainBaseProfile profile, double scale) {
        double min = Double.POSITIVE_INFINITY, max = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < profile.vertexCount(); i++) { min = Math.min(min, profile.x(i)); max = Math.max(max, profile.x(i)); }
        double step = (max - min) / STRIPS;
        List<VoxelShape> boxes = new ArrayList<>();
        for (int strip = 0; strip < STRIPS; strip++) {
            double left = min + strip * step, right = strip == STRIPS - 1 ? max : left + step;
            List<double[]> intervals = new ArrayList<>();
            for (double sample : new double[]{Math.nextUp(left), (left + right) * .5, Math.nextDown(right)}) {
                double[] crossings = new double[profile.vertexCount()]; int count = 0;
                for (int i = 0, j = profile.vertexCount() - 1; i < profile.vertexCount(); j = i++) {
                    double a = profile.x(i), b = profile.x(j);
                    if ((a <= sample && sample < b) || (b <= sample && sample < a)) {
                        crossings[count++] = profile.z(i) + (sample - a) * (profile.z(j) - profile.z(i)) / (b - a);
                    }
                }
                Arrays.sort(crossings, 0, count);
                for (int i = 0; i + 1 < count; i += 2) if (crossings[i + 1] > crossings[i]) {
                    intervals.add(new double[]{crossings[i], crossings[i + 1]});
                }
            }
            intervals.sort(Comparator.comparingDouble(interval -> interval[0]));
            double lower = 0, upper = 0; boolean open = false;
            for (double[] interval : intervals) {
                if (!open) { lower = interval[0]; upper = interval[1]; open = true; }
                else if (interval[0] <= upper) upper = Math.max(upper, interval[1]);
                else {
                    boxes.add(box(left, right, lower, upper, scale)); lower = interval[0]; upper = interval[1];
                }
            }
            if (open) boxes.add(box(left, right, lower, upper, scale));
        }
        return VoxelShapes.union(VoxelShapes.empty(), boxes.toArray(VoxelShape[]::new)).simplify();
    }

    private static VoxelShape box(double left, double right, double lower, double upper, double scale) {
        return VoxelShapes.cuboid(.5 + left * scale, 0, .5 + lower * scale, .5 + right * scale, 1, .5 + upper * scale);
    }
}
