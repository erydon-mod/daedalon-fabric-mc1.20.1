package com.oliver.daedalon.block;

import net.minecraft.util.function.BooleanBiFunction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;

import java.util.Arrays;

/** Cached, registry-independent geometry shared by the basin anchor and its parts. */
final class FountainBasinGeometry {
    static final int PART_RADIUS = 2;
    static final int PART_DIAMETER = PART_RADIUS * 2 + 1;
    /** The shallow basin plus the fixed Large -> Medium -> Small stack reaches cell six. */
    static final int MAX_PART_Y = 5;
    static final int PART_SHAPE_COUNT = PART_DIAMETER * PART_DIAMETER * (MAX_PART_Y + 1);

    private static final int OCTAGON_SLOPE_STEPS = 16;
    private static final double WATER_PICK_THICKNESS = 1.0D / 64.0D;
    private static final double WATER_INTERSECTION_EPSILON = 1.0E-7D;

    private static final VoxelShape[][] COLLISION_SHAPES = createCollisionShapes();
    private static final VoxelShape[][] DRY_OUTLINE_SHAPES = createDryOutlineShapes();
    private static final VoxelShape[][] WET_OUTLINE_SHAPES = createWetOutlineShapes();
    private static final VoxelShape[][][] COLLISION_PART_SHAPES =
            createPartShapes(COLLISION_SHAPES);
    private static final VoxelShape[][][] DRY_OUTLINE_PART_SHAPES =
            createOutlinePartShapes(DRY_OUTLINE_SHAPES);
    private static final VoxelShape[][][] WET_OUTLINE_PART_SHAPES =
            createOutlinePartShapes(WET_OUTLINE_SHAPES);

    private FountainBasinGeometry() {
    }

    static void initialize() {
        // Calling this during block registration moves the one-time shape bake
        // out of the player's first placement or collision check.
    }

    static VoxelShape collisionShape(FountainBasinBlock.Size size) {
        return collisionShape(FountainBasinBlock.Style.GOTHIC, size);
    }

    static VoxelShape collisionShape(FountainBasinBlock.Style style,
                                     FountainBasinBlock.Size size) {
        return COLLISION_SHAPES[style.ordinal()][size.ordinal()];
    }

    static VoxelShape outlineShape(FountainBasinBlock.Size size, boolean waterlogged) {
        return outlineShape(FountainBasinBlock.Style.GOTHIC, size, waterlogged);
    }

    static VoxelShape outlineShape(FountainBasinBlock.Style style,
                                   FountainBasinBlock.Size size,
                                   boolean waterlogged) {
        return (waterlogged ? WET_OUTLINE_SHAPES : DRY_OUTLINE_SHAPES)
                [style.ordinal()][size.ordinal()];
    }

    static VoxelShape collisionPartShape(FountainBasinBlock.Size size,
                                         int offsetX,
                                         int offsetY,
                                         int offsetZ) {
        return collisionPartShape(
                FountainBasinBlock.Style.GOTHIC,
                size,
                offsetX,
                offsetY,
                offsetZ
        );
    }

    static VoxelShape collisionPartShape(FountainBasinBlock.Style style,
                                         FountainBasinBlock.Size size,
                                         int offsetX,
                                         int offsetY,
                                         int offsetZ) {
        return COLLISION_PART_SHAPES[style.ordinal()][size.ordinal()]
                [partIndex(offsetX, offsetY, offsetZ)];
    }

    static VoxelShape outlinePartShape(FountainBasinBlock.Size size,
                                       boolean waterlogged,
                                       int offsetX,
                                       int offsetY,
                                       int offsetZ) {
        return outlinePartShape(
                FountainBasinBlock.Style.GOTHIC,
                size,
                waterlogged,
                offsetX,
                offsetY,
                offsetZ
        );
    }

    static VoxelShape outlinePartShape(FountainBasinBlock.Style style,
                                       FountainBasinBlock.Size size,
                                       boolean waterlogged,
                                       int offsetX,
                                       int offsetY,
                                       int offsetZ) {
        VoxelShape[][][] shapes = waterlogged
                ? WET_OUTLINE_PART_SHAPES
                : DRY_OUTLINE_PART_SHAPES;
        return shapes[style.ordinal()][size.ordinal()][partIndex(offsetX, offsetY, offsetZ)];
    }

    static VoxelShape[] copyCollisionPartShapes(FountainBasinBlock.Size size) {
        return copyCollisionPartShapes(FountainBasinBlock.Style.GOTHIC, size);
    }

    static VoxelShape[] copyCollisionPartShapes(FountainBasinBlock.Style style,
                                                FountainBasinBlock.Size size) {
        return COLLISION_PART_SHAPES[style.ordinal()][size.ordinal()].clone();
    }

    static VoxelShape[] copyOutlinePartShapes(FountainBasinBlock.Size size,
                                              boolean waterlogged) {
        return copyOutlinePartShapes(FountainBasinBlock.Style.GOTHIC, size, waterlogged);
    }

    static VoxelShape[] copyOutlinePartShapes(FountainBasinBlock.Style style,
                                              FountainBasinBlock.Size size,
                                              boolean waterlogged) {
        VoxelShape[][][] shapes = waterlogged
                ? WET_OUTLINE_PART_SHAPES
                : DRY_OUTLINE_PART_SHAPES;
        return shapes[style.ordinal()][size.ordinal()].clone();
    }

    static VoxelShape clipToPart(VoxelShape fullShape,
                                 int offsetX,
                                 int offsetY,
                                 int offsetZ) {
        partIndex(offsetX, offsetY, offsetZ);
        // Keep the clipped voxel set. Simplifying it expands curved rims back
        // into boxes and repeatedly unions them, making each water toggle costly.
        return VoxelShapes.combine(
                fullShape.offset(-offsetX, -offsetY, -offsetZ),
                VoxelShapes.fullCube(),
                BooleanBiFunction.AND
        );
    }

    /**
     * Splits one assembled shape into local block-cell shapes once. Runtime
     * collision and raycasting can then reuse the results without clipping or
     * simplifying geometry every frame.
     */
    static VoxelShape[] splitIntoParts(VoxelShape fullShape) {
        VoxelShape[] result = new VoxelShape[PART_SHAPE_COUNT];
        Arrays.fill(result, VoxelShapes.empty());
        if (fullShape.isEmpty()) {
            return result;
        }

        int minimumX = Math.max(
                -PART_RADIUS,
                MathHelper.floor(fullShape.getMin(net.minecraft.util.math.Direction.Axis.X))
        );
        int maximumX = Math.min(
                PART_RADIUS,
                MathHelper.ceil(fullShape.getMax(net.minecraft.util.math.Direction.Axis.X)) - 1
        );
        int minimumY = Math.max(
                0,
                MathHelper.floor(fullShape.getMin(net.minecraft.util.math.Direction.Axis.Y))
        );
        int maximumY = Math.min(
                MAX_PART_Y,
                MathHelper.ceil(fullShape.getMax(net.minecraft.util.math.Direction.Axis.Y)) - 1
        );
        int minimumZ = Math.max(
                -PART_RADIUS,
                MathHelper.floor(fullShape.getMin(net.minecraft.util.math.Direction.Axis.Z))
        );
        int maximumZ = Math.min(
                PART_RADIUS,
                MathHelper.ceil(fullShape.getMax(net.minecraft.util.math.Direction.Axis.Z)) - 1
        );

        for (int offsetY = minimumY; offsetY <= maximumY; offsetY++) {
            for (int offsetX = minimumX; offsetX <= maximumX; offsetX++) {
                for (int offsetZ = minimumZ; offsetZ <= maximumZ; offsetZ++) {
                    result[partIndex(offsetX, offsetY, offsetZ)] = clipToPart(
                            fullShape,
                            offsetX,
                            offsetY,
                            offsetZ
                    );
                }
            }
        }
        return result;
    }

    static double containedWaterHeight(BlockPos anchorPos,
                                       FountainBasinBlock.Size size,
                                       Box box) {
        return containedWaterHeight(
                anchorPos,
                FountainBasinBlock.Style.GOTHIC,
                size,
                box
        );
    }

    static double containedWaterHeight(BlockPos anchorPos,
                                       FountainBasinBlock.Style style,
                                       FountainBasinBlock.Size size,
                                       Box box) {
        double bottom = anchorPos.getY() + style.basinFloorY(size);
        double surface = anchorPos.getY() + style.waterSurfaceY(size);
        if (box.maxY <= bottom + WATER_INTERSECTION_EPSILON
                || box.minY > surface + WATER_INTERSECTION_EPSILON) {
            return 0.0D;
        }

        double centerX = anchorPos.getX() + 0.5D;
        double centerZ = anchorPos.getZ() + 0.5D;
        FountainWaterShape waterOutline = style.waterOutline();
        if (waterOutline != null) {
            double scale = size.modelScale();
            return waterOutline.intersects((box.minX - centerX) / scale, (box.minZ - centerZ) / scale,
                    (box.maxX - centerX) / scale, (box.maxZ - centerZ) / scale)
                    ? Math.max(0.0D, surface - box.minY) : 0.0D;
        }
        double nearestX = nearestAbsoluteCoordinate(box.minX - centerX, box.maxX - centerX);
        double nearestZ = nearestAbsoluteCoordinate(box.minZ - centerZ, box.maxZ - centerZ);
        double halfWidth = style.waterHalfWidth(size);
        double straightHalfLength = style.waterBevel(size);
        if (nearestX > halfWidth + WATER_INTERSECTION_EPSILON
                || nearestZ > halfWidth + WATER_INTERSECTION_EPSILON
                || nearestX + nearestZ
                > halfWidth + straightHalfLength + WATER_INTERSECTION_EPSILON) {
            return 0.0D;
        }
        return Math.max(0.0D, surface - box.minY);
    }

    private static double nearestAbsoluteCoordinate(double minimum, double maximum) {
        if (minimum <= 0.0D && maximum >= 0.0D) {
            return 0.0D;
        }
        return Math.min(Math.abs(minimum), Math.abs(maximum));
    }

    private static VoxelShape[][] createCollisionShapes() {
        VoxelShape[][] result = new VoxelShape[FountainBasinBlock.Style.values().length]
                [FountainBasinBlock.Size.values().length];
        for (FountainBasinBlock.Style style : FountainBasinBlock.Style.values()) {
            for (FountainBasinBlock.Size size : FountainBasinBlock.Size.values()) {
                double floorY = style.basinFloorY(size);
                double height = style.modelHeight(size);
                VoxelShape base = createOctagonalPrism(
                        style.baseHalfWidth(size),
                        style.baseStraightHalfLength(size),
                        0.0D,
                        floorY,
                        false
                );
                VoxelShape rimOuter = createOctagonalPrism(
                        style.rimHalfWidth(size),
                        style.rimStraightHalfLength(size),
                        floorY,
                        height,
                        false
                );
                VoxelShape interior = createWaterPrism(
                        style, size,
                        floorY,
                        height,
                        true
                );
                VoxelShape hollowRim = VoxelShapes.combine(
                        rimOuter,
                        interior,
                        BooleanBiFunction.ONLY_FIRST
                );
                result[style.ordinal()][size.ordinal()] =
                        VoxelShapes.combine(base, hollowRim, BooleanBiFunction.OR);
            }
        }
        return result;
    }

    private static VoxelShape[][] createWetOutlineShapes() {
        VoxelShape[][] result = new VoxelShape[FountainBasinBlock.Style.values().length]
                [FountainBasinBlock.Size.values().length];
        for (FountainBasinBlock.Style style : FountainBasinBlock.Style.values()) {
            for (FountainBasinBlock.Size size : FountainBasinBlock.Size.values()) {
                double surface = style.waterSurfaceY(size);
                VoxelShape waterSurface = createWaterPrism(
                        style, size,
                        surface - WATER_PICK_THICKNESS,
                        surface,
                        false
                );
                result[style.ordinal()][size.ordinal()] = VoxelShapes.combine(
                        DRY_OUTLINE_SHAPES[style.ordinal()][size.ordinal()],
                        waterSurface,
                        BooleanBiFunction.OR
                );
            }
        }
        return result;
    }

    private static VoxelShape[][] createDryOutlineShapes() {
        VoxelShape[][] result = new VoxelShape[FountainBasinBlock.Style.values().length]
                [FountainBasinBlock.Size.values().length];
        for (FountainBasinBlock.Style style : FountainBasinBlock.Style.values()) {
            for (FountainBasinBlock.Size size : FountainBasinBlock.Size.values()) {
                double floorY = style.basinFloorY(size);
                double height = style.modelHeight(size);
                VoxelShape base = createOctagonalPrism(
                        style.outlineBaseHalfWidth(size),
                        style.outlineBaseStraightHalfLength(size),
                        0.0D,
                        floorY,
                        false
                );
                VoxelShape rimOuter = createOctagonalPrism(
                        style.outlineRimHalfWidth(size),
                        style.outlineRimStraightHalfLength(size),
                        floorY,
                        height,
                        false
                );
                VoxelShape interior = createWaterPrism(
                        style, size,
                        floorY,
                        height,
                        true
                );
                VoxelShape hollowRim = VoxelShapes.combine(
                        rimOuter,
                        interior,
                        BooleanBiFunction.ONLY_FIRST
                );
                result[style.ordinal()][size.ordinal()] =
                        VoxelShapes.combine(base, hollowRim, BooleanBiFunction.OR);
            }
        }
        return result;
    }

    private static VoxelShape createWaterPrism(FountainBasinBlock.Style style,
                                               FountainBasinBlock.Size size,
                                               double bottom, double top, boolean expand) {
        FountainWaterShape outline = style.waterOutline();
        return outline == null
                ? createOctagonalPrism(style.waterHalfWidth(size), style.waterBevel(size), bottom, top, expand)
                : outline.prism(size.modelScale(), bottom, top, expand);
    }

    static VoxelShape createOctagonalPrism(double halfWidth,
                                           double straightHalfLength,
                                           double minimumY,
                                           double maximumY,
                                           boolean expandSlopes) {
        double center = 0.5D;
        VoxelShape shape = VoxelShapes.cuboid(
                center - straightHalfLength,
                minimumY,
                center - halfWidth,
                center + straightHalfLength,
                maximumY,
                center + halfWidth
        );
        double sliceWidth = (halfWidth - straightHalfLength) / OCTAGON_SLOPE_STEPS;
        for (int step = 0; step < OCTAGON_SLOPE_STEPS; step++) {
            double innerX = straightHalfLength + step * sliceWidth;
            double outerX = straightHalfLength + (step + 1) * sliceWidth;
            double sampledX = expandSlopes ? innerX : outerX;
            double zLimit = halfWidth + straightHalfLength - sampledX;
            VoxelShape positive = VoxelShapes.cuboid(
                    center + innerX,
                    minimumY,
                    center - zLimit,
                    center + outerX,
                    maximumY,
                    center + zLimit
            );
            VoxelShape negative = VoxelShapes.cuboid(
                    center - outerX,
                    minimumY,
                    center - zLimit,
                    center - innerX,
                    maximumY,
                    center + zLimit
            );
            shape = VoxelShapes.combine(shape, positive, BooleanBiFunction.OR);
            shape = VoxelShapes.combine(shape, negative, BooleanBiFunction.OR);
        }
        return shape;
    }

    private static VoxelShape[][][] createPartShapes(VoxelShape[][] fullShapes) {
        VoxelShape[][][] result = new VoxelShape[FountainBasinBlock.Style.values().length]
                [FountainBasinBlock.Size.values().length][PART_SHAPE_COUNT];
        for (FountainBasinBlock.Style style : FountainBasinBlock.Style.values()) {
            for (FountainBasinBlock.Size size : FountainBasinBlock.Size.values()) {
                result[style.ordinal()][size.ordinal()] = splitIntoParts(
                        fullShapes[style.ordinal()][size.ordinal()]
                );
            }
        }
        return result;
    }

    private static VoxelShape[][][] createOutlinePartShapes(VoxelShape[][] fullShapes) {
        VoxelShape[][][] result = createPartShapes(fullShapes);
        for (VoxelShape[][] styleParts : result) {
            for (VoxelShape[] sizeParts : styleParts) {
                for (int index = 0; index < sizeParts.length; index++) {
                    VoxelShape part = sizeParts[index];
                    if (part.isEmpty()) {
                        continue;
                    }
                    sizeParts[index] = VoxelShapes.cuboid(
                            part.getMin(net.minecraft.util.math.Direction.Axis.X),
                            part.getMin(net.minecraft.util.math.Direction.Axis.Y),
                            part.getMin(net.minecraft.util.math.Direction.Axis.Z),
                            part.getMax(net.minecraft.util.math.Direction.Axis.X),
                            part.getMax(net.minecraft.util.math.Direction.Axis.Y),
                            part.getMax(net.minecraft.util.math.Direction.Axis.Z)
                    );
                }
            }
        }
        return result;
    }

    static int partIndex(int offsetX, int offsetY, int offsetZ) {
        if (offsetX < -PART_RADIUS || offsetX > PART_RADIUS
                || offsetY < 0 || offsetY > MAX_PART_Y
                || offsetZ < -PART_RADIUS || offsetZ > PART_RADIUS) {
            throw new IllegalArgumentException("Fountain basin part offset is out of range");
        }
        return (offsetY * PART_DIAMETER + offsetX + PART_RADIUS)
                * PART_DIAMETER
                + offsetZ
                + PART_RADIUS;
    }
}
