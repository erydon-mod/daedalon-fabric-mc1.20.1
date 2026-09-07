package com.oliver.daedalon.client.fountain;

import com.oliver.daedalon.block.FountainAssemblyLayout;
import com.oliver.daedalon.block.FountainBasinBlock;
import com.oliver.daedalon.block.FountainWaterShape;

import java.util.ArrayList;
import java.util.List;

/** Small immutable emitter list, rebuilt only when the fountain is edited. */
public record FountainSprayPlan(List<Emitter> emitters) {
    public static final double GRAVITY = 0.028; // Brisk jets and falling streams, without longer lifetimes.
    public static final int MAX_AGE = 60;

    public FountainSprayPlan {
        emitters = List.copyOf(emitters);
    }

    public static FountainSprayPlan create(FountainBasinBlock.Style style,
                                          FountainBasinBlock.Size size,
                                          List<FountainAssemblyLayout.PlacedBowl> bowls) {
        List<Emitter> emitters = new ArrayList<>(13);
        if (bowls.isEmpty()) return new FountainSprayPlan(emitters);

        var top = bowls.get(bowls.size() - 1);
        Pool topPool = pool(top);
        emitters.add(new Emitter(0, topPool.y + 0.025, 0, 0.28, topPool));

        Pool below = new Pool(style.waterSurfaceY(size), size.modelScale(), style.waterOutline(),
                style.waterHalfWidth(size), style.waterBevel(size));
        for (var bowl : bowls) {
            // Four pilot outlets just outside the model's maximum X/Z rim.
            // This is a droplet test, not yet a continuous over-the-lip sheet.
            double radius = bowl.size().diameter() * 0.5 + 0.015;
            double y = bowl.baseY() + bowl.style().modelHeight(bowl.size()) + 0.015;
            for (int direction = 0; direction < 4; direction++) {
                double x = direction == 0 ? radius : direction == 2 ? -radius : 0;
                double z = direction == 1 ? radius : direction == 3 ? -radius : 0;
                if (below.y < y && below.contains(x, z)) {
                    emitters.add(new Emitter(x, y, z, -0.015, below));
                }
            }
            below = pool(bowl);
        }
        return new FountainSprayPlan(emitters);
    }

    private static Pool pool(FountainAssemblyLayout.PlacedBowl bowl) {
        return new Pool(bowl.baseY() + bowl.style().waterSurfaceY(bowl.size()),
                bowl.size().diameter(), bowl.style().waterOutline(), 0, 0);
    }

    /** Same discrete gravity step used by the actual particle. No drag or world scans. */
    public static double velocityAfterTick(double velocityY) {
        return velocityY - GRAVITY;
    }

    /** Exact integer landing tick for our discrete gravity integration. */
    public static int flightTicks(double height, double velocityY) {
        double b = velocityY - GRAVITY * 0.5;
        return Math.max(1, (int)Math.ceil((b + Math.sqrt(b * b + 2 * GRAVITY * height)) / GRAVITY));
    }

    /** Four jet drops plus one per rim outlet: 8/12/16 drops per full-rate tick. */
    public int emissionsPerTick() {
        return emitters.isEmpty() ? 0 : emitters.size() + 3;
    }

    public Emitter emitterForEmission(int emission) {
        // Retain a cycling index when a global/per-fountain budget interrupts this sequence.
        return emitters.get(emission < 4 ? 0 : emission - 3);
    }

    /** Mostly medium drops with occasional larger ones, rather than uniform oversized beads. */
    public static float dropletScale(float sample) {
        return 0.055F + 0.055F * sample * sample;
    }

    public static float splashScale(float dropletScale, int age) {
        return dropletScale * 2.1F + age * 0.025F;
    }

    public record Emitter(double x, double y, double z, double velocityY, Pool landing) { }

    public record Pool(double y, double scale, FountainWaterShape outline,
                       double halfWidth, double bevel) {
        public boolean contains(double x, double z) {
            if (outline != null) return outline.contains(x / scale, z / scale);
            return Math.abs(x) <= halfWidth && Math.abs(z) <= halfWidth
                    && Math.abs(x) + Math.abs(z) <= halfWidth + bevel;
        }
    }
}
