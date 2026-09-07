package com.oliver.daedalon.client.fountain;

import com.oliver.daedalon.block.FountainAssemblyLayout;
import com.oliver.daedalon.block.FountainBasinBlock;
import com.oliver.daedalon.block.FountainBowlModel;
import com.oliver.daedalon.block.PlinthBlock;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

final class FountainSprayPlanTest {
    @Test
    void allStylesSizesAndTierCountsLandInsideTheReceivingWaterBeforeExpiry() {
        int combinations = 0;
        int maximumPeak = 0;
        String maximumContext = "";
        for (var style : FountainBasinBlock.Style.values()) {
            for (var size : FountainBasinBlock.Size.values()) {
                for (var plinthSize : PlinthBlock.Size.values()) {
                    for (var tiers : FountainBowlModel.TierCount.values()) {
                        var bowls = bowls(style, size, plinthSize, tiers);
                        var plan = FountainSprayPlan.create(style, size, bowls);
                        String context = style + " " + size + " " + plinthSize + " " + tiers;
                        if (bowls.isEmpty()) {
                            assertTrue(plan.emitters().isEmpty());
                            assertEquals(0, plan.emissionsPerTick());
                            continue;
                        }
                        assertEquals(1 + 4 * bowls.size(), plan.emitters().size(), context);
                        assertEquals(4 + 4 * bowls.size(), plan.emissionsPerTick(), context);
                        int[] emitted = new int[plan.emitters().size()];
                        for (int emission = 0; emission < plan.emissionsPerTick(); emission++) {
                            emitted[plan.emitters().indexOf(plan.emitterForEmission(emission))]++;
                        }
                        assertEquals(4, emitted[0], context);
                        for (int outlet = 1; outlet < emitted.length; outlet++) {
                            assertEquals(1, emitted[outlet], context);
                        }
                        int peakParticles = 0;
                        for (var emitter : plan.emitters()) {
                            assertTrue(emitter.landing().contains(emitter.x(), emitter.z()), context);
                            assertTrue(emitter.y() > emitter.landing().y(), context);
                            int longestFlight = 0;
                            for (double velocityScale : new double[]{0.88, 1.12}) {
                                double y = emitter.y(), v = emitter.velocityY() * velocityScale;
                                int age = 0;
                                do {
                                    v = FountainSprayPlan.velocityAfterTick(v);
                                    y += v;
                                    age++;
                                } while ((v >= 0 || y > emitter.landing().y())
                                        && age < FountainSprayPlan.MAX_AGE);
                                assertEquals(age, FountainSprayPlan.flightTicks(emitter.y() - emitter.landing().y(), emitter.velocityY() * velocityScale), context);
                                assertTrue(age < FountainSprayPlan.MAX_AGE - 4, context);
                                assertTrue(v < 0, context);
                                longestFlight = Math.max(longestFlight, age);
                            }
                            peakParticles += (longestFlight + 5) * (emitter.velocityY() > 0 ? 4 : 1);
                            for (double signX : new double[]{-1, 1}) {
                                for (double signZ : new double[]{-1, 1}) {
                                    boolean jet = emitter.velocityY() > 0;
                                    double dx = signX * (jet ? 0.03 : 0.045);
                                    double dz = signZ * (jet ? 0.03 : 0.045);
                                    if (!jet && emitter.x() != 0) dx = Math.copySign(0.01, emitter.x());
                                    if (!jet && emitter.z() != 0) dz = Math.copySign(0.01, emitter.z());
                                    assertTrue(emitter.landing().contains(emitter.x() + dx, emitter.z() + dz), context);
                                }
                            }
                        }
                        if (peakParticles > maximumPeak) {
                            maximumPeak = peakParticles;
                            maximumContext = context;
                        }
                        combinations++;
                    }
                }
            }
        }
        assertEquals(81, combinations);
        assertTrue(maximumPeak <= FountainParticles.MAX_PER_FOUNTAIN,
                maximumContext + " steady spray needs " + maximumPeak + " particle slots");
    }

    @Test
    void dropletSizesVaryWithinBoundsAndSplashesFollowTheirSize() {
        assertEquals(0.055F, FountainSprayPlan.dropletScale(0), 0.00001F);
        assertEquals(0.11F, FountainSprayPlan.dropletScale(1), 0.00001F);
        float previous = 0;
        for (int sample = 0; sample <= 100; sample++) {
            float size = FountainSprayPlan.dropletScale(sample / 100F);
            assertTrue(size >= previous);
            assertTrue(size >= 0.055F && size <= 0.11001F);
            assertTrue(FountainSprayPlan.splashScale(size, 0) > size);
            assertTrue(FountainSprayPlan.splashScale(size, 3) > FountainSprayPlan.splashScale(size, 0));
            assertTrue(FountainSprayPlan.splashScale(size, 3) < 0.32F);
            previous = size;
        }
    }

    @Test
    void jetRisesThenFallsAndEveryCascadeUsesTheNextLowerPool() {
        var style = FountainBasinBlock.Style.GREEK;
        var size = FountainBasinBlock.Size.MEDIUM;
        var bowls = bowls(style, size, PlinthBlock.Size.LARGE, FountainBowlModel.TierCount.THREE);
        var plan = FountainSprayPlan.create(style, size, bowls);
        var jet = plan.emitters().get(0);
        double y = jet.y(), v = jet.velocityY();
        for (int i = 0; i < 5; i++) {
            v = FountainSprayPlan.velocityAfterTick(v);
            y += v;
        }
        assertTrue(y > jet.y() + 0.5);
        var top = bowls.get(2);
        assertEquals(top.baseY() + top.style().waterSurfaceY(top.size()), jet.landing().y());
        for (int bowl = 0; bowl < 3; bowl++) {
            double targetY = bowl == 0 ? style.waterSurfaceY(size)
                    : bowls.get(bowl - 1).baseY()
                    + bowls.get(bowl - 1).style().waterSurfaceY(bowls.get(bowl - 1).size());
            for (int direction = 0; direction < 4; direction++) {
                assertEquals(targetY, plan.emitters().get(1 + bowl * 4 + direction).landing().y());
            }
        }
        assertThrows(UnsupportedOperationException.class, () -> plan.emitters().clear());
    }

    private static List<FountainAssemblyLayout.PlacedBowl> bowls(FountainBasinBlock.Style style,
                                                                 FountainBasinBlock.Size size,
                                                                 PlinthBlock.Size plinth,
                                                                 FountainBowlModel.TierCount tiers) {
        var result = new ArrayList<FountainAssemblyLayout.PlacedBowl>();
        double base = style.basinFloorY(size) + plinth.height();
        for (var bowlSize : FountainBowlModel.sequence(tiers)) {
            double connection = base + style.bowlStyle().connectionRise(bowlSize);
            result.add(new FountainAssemblyLayout.PlacedBowl(style.bowlStyle(), bowlSize, base, connection));
            base = connection;
        }
        return result;
    }
}
