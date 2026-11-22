package com.oliver.daedalon.block;

import net.minecraft.util.function.BooleanBiFunction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.ArrayVoxelShape;
import net.minecraft.util.shape.BitSetVoxelSet;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;

import java.util.ArrayList;
import java.util.List;

/** Radial stone bands measured from the fitted Louterion OBJ, with an open bowl above its floor. */
final class LouterionBasinGeometry {
    static final double SMALL_HEIGHT = 0.958789790D;
    private static final double HALF_DEPTH = 0.499602787D;
    private static final int HORIZONTAL_STEPS = 48;
    private static final int PART_COUNT = 18;
    // Upper Y, conservative exterior radius, and clear interior radius in Small block units.
    private static final double[][] BANDS = {
            {.058, .256, 0}, {.083, .251, 0}, {.124, .205, 0}, {.149, .189, 0},
            {.170, .147, 0}, {.255, .116, 0}, {.310, .138, 0}, {.450, .151, 0},
            {.510, .135, 0}, {.635, .102, 0}, {.662, .095, 0}, {.715, .119, 0},
            {.744, .123, 0}, {.780, .148, 0}, {.795, .226, 0}, {.810, .305, 0},
            {.827, .374, 0}, {.846, .420, 0}, {.871, .453, 0}, {.896, .466, 0},
            {.900, .468, .265}, {.910, .471, .333}, {.925, .497, .376},
            {.951, .501, .402}, {SMALL_HEIGHT, .496, .423},
    };

    private static final Profile SMALL = createProfile(1.0D);
    private static final Profile LARGE = createProfile(2.0D);
    private static final VoxelShape[] LARGE_COLLISION_PARTS = split(LARGE.stone());
    private static final VoxelShape[] LARGE_WET_OUTLINE_PARTS = split(LARGE.wetOutline());
    static final List<BlockPos> LARGE_OFFSETS = occupiedOffsets();

    private LouterionBasinGeometry() { }

    static VoxelShape collision(boolean large) {
        return (large ? LARGE : SMALL).stone();
    }

    static VoxelShape outline(boolean large, boolean wet) {
        Profile profile = large ? LARGE : SMALL;
        return wet ? profile.wetOutline() : profile.stone();
    }

    static VoxelShape part(boolean wetOutline, int x, int y, int z) {
        if (x < -1 || x > 1 || y < 0 || y > 1 || z < -1 || z > 1) return VoxelShapes.empty();
        return (wetOutline ? LARGE_WET_OUTLINE_PARTS : LARGE_COLLISION_PARTS)[partIndex(x, y, z)];
    }

    private static Profile createProfile(double scale) {
        double[] xGrid = horizontalGrid(0.5D, scale);
        double[] zGrid = horizontalGrid(HALF_DEPTH, scale);
        double[] yGrid = new double[BANDS.length + 1];
        BitSetVoxelSet stone = new BitSetVoxelSet(HORIZONTAL_STEPS, BANDS.length, HORIZONTAL_STEPS);
        for (int y = 0; y < BANDS.length; y++) {
            double[] band = BANDS[y];
            yGrid[y + 1] = band[0] * scale;
            double outer = band[1] * scale;
            double inner = band[2] * scale;
            for (int x = 0; x < HORIZONTAL_STEPS; x++) {
                double xNear = nearCenter(xGrid, x);
                double xFar = farFromCenter(xGrid, x);
                for (int z = 0; z < HORIZONTAL_STEPS; z++) {
                    double zNear = nearCenter(zGrid, z);
                    double zFar = farFromCenter(zGrid, z);
                    if (xNear * xNear + zNear * zNear < outer * outer
                            && xFar * xFar + zFar * zFar > inner * inner) {
                        stone.set(x, y, z);
                    }
                }
            }
        }
        VoxelShape shell = new RadialVoxels(stone, xGrid, yGrid, zGrid);
        FountainWaterShape water = FountainWaterShape.LOUTERION;
        double level = water.surfaceY() * scale;
        VoxelShape wetOutline = VoxelShapes.combine(shell,
                water.prism(scale, level - scale / 128.0D, level, true), BooleanBiFunction.OR);
        return new Profile(shell, wetOutline);
    }

    private static double[] horizontalGrid(double halfExtent, double scale) {
        double[] grid = new double[HORIZONTAL_STEPS + 1];
        for (int step = 0; step <= HORIZONTAL_STEPS; step++) {
            grid[step] = 0.5D + scale * halfExtent * (2.0D * step / HORIZONTAL_STEPS - 1.0D);
        }
        return grid;
    }

    private static double nearCenter(double[] grid, int i) {
        return Math.min(Math.abs(grid[i] - 0.5D), Math.abs(grid[i + 1] - 0.5D));
    }

    private static double farFromCenter(double[] grid, int i) {
        return Math.max(Math.abs(grid[i] - 0.5D), Math.abs(grid[i + 1] - 0.5D));
    }

    private static VoxelShape[] split(VoxelShape whole) {
        VoxelShape[] result = new VoxelShape[PART_COUNT];
        for (int y = 0; y < 2; y++) {
            for (int z = -1; z <= 1; z++) {
                for (int x = -1; x <= 1; x++) {
                    result[partIndex(x, y, z)] = FountainBasinGeometry.clipToPart(whole, x, y, z);
                }
            }
        }
        return result;
    }

    private static List<BlockPos> occupiedOffsets() {
        List<BlockPos> offsets = new ArrayList<>(PART_COUNT - 1);
        for (int y = 0; y < 2; y++) {
            for (int z = -1; z <= 1; z++) {
                for (int x = -1; x <= 1; x++) {
                    if ((x != 0 || y != 0 || z != 0) && !part(true, x, y, z).isEmpty()) {
                        offsets.add(new BlockPos(x, y, z));
                    }
                }
            }
        }
        return List.copyOf(offsets);
    }

    private static int partIndex(int x, int y, int z) {
        return y * 9 + (z + 1) * 3 + x + 1;
    }

    private record Profile(VoxelShape stone, VoxelShape wetOutline) { }

    // Expose Minecraft's irregular-height voxel constructor without repeating shape unions for every band.
    private static final class RadialVoxels extends ArrayVoxelShape {
        private RadialVoxels(BitSetVoxelSet set, double[] x, double[] y, double[] z) {
            super(set, x, y, z);
        }
    }
}
