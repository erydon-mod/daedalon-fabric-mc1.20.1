package com.oliver.daedalon.block;

import net.minecraft.block.BlockState;
import net.minecraft.util.function.BooleanBiFunction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;

import java.util.ArrayList;
import java.util.List;

/** Fixed plinth-and-bowl placement shared by rendering, collision and water. */
public final class FountainAssemblyLayout {
    private final FountainBasinBlock.Style basinStyle;
    private final FountainBasinBlock.Size basinSize;
    private final PlacedPlinth plinth;
    private final List<PlacedBowl> bowls;
    private final VoxelShape[] collisionParts;
    private final VoxelShape[] outlineParts;
    private final double maximumY;

    private FountainAssemblyLayout(FountainBasinBlock.Style basinStyle,
                                   FountainBasinBlock.Size basinSize,
                                   PlacedPlinth plinth,
                                   List<PlacedBowl> bowls,
                                   VoxelShape[] collisionParts,
                                   VoxelShape[] outlineParts,
                                   double maximumY) {
        this.basinStyle = basinStyle;
        this.basinSize = basinSize;
        this.plinth = plinth;
        this.bowls = List.copyOf(bowls);
        this.collisionParts = collisionParts;
        this.outlineParts = outlineParts;
        this.maximumY = maximumY;
    }

    public static FountainAssemblyLayout create(FountainBasinBlock.Style basinStyle,
                                                FountainBasinBlock.Size basinSize,
                                                boolean filled,
                                                BlockState rawPlinthState,
                                                int requestedBowlCount) {
        VoxelShape[] collisionParts =
                FountainBasinGeometry.copyCollisionPartShapes(basinStyle, basinSize);
        VoxelShape[] outlineParts =
                FountainBasinGeometry.copyOutlinePartShapes(basinStyle, basinSize, filled);
        List<PlacedBowl> bowls = new ArrayList<>(3);
        double maximumY = FountainBasinGeometry.outlineShape(basinStyle, basinSize, filled)
                .getMax(Direction.Axis.Y);

        if (rawPlinthState == null || !(rawPlinthState.getBlock() instanceof PlinthBlock block)) {
            return new FountainAssemblyLayout(
                    basinStyle,
                    basinSize,
                    null,
                    bowls,
                    collisionParts,
                    outlineParts,
                    maximumY
            );
        }

        BlockState plinthState = normalizePlinth(rawPlinthState);
        double baseY = basinStyle.basinFloorY(basinSize);
        double connectionY = baseY + block.connectionRise(plinthState);
        VoxelShape plinthCollision = block.assemblyCollisionShape(plinthState)
                .offset(0.0D, baseY, 0.0D);
        VoxelShape plinthOutline = block.assemblyOutlineShape(plinthState, filled)
                .offset(0.0D, baseY, 0.0D);
        mergeIntoParts(collisionParts, plinthCollision, false);
        mergeIntoParts(outlineParts, plinthOutline, true);
        maximumY = Math.max(maximumY, plinthOutline.getMax(Direction.Axis.Y));
        PlacedPlinth placedPlinth = new PlacedPlinth(plinthState, baseY, connectionY);

        int bowlCount = MathHelper.clamp(
                requestedBowlCount,
                0,
                maximumBowlCount()
        );
        List<FountainBowlModel.Size> sequence = FountainBowlModel.sequence(
                FountainBowlModel.TierCount.fromBowlCount(bowlCount)
        );
        FountainBowlModel.Style bowlStyle = basinStyle.bowlStyle();
        for (int index = 0; index < bowlCount; index++) {
            FountainBowlModel.Size size = sequence.get(index);
            baseY = connectionY;
            connectionY = baseY + bowlStyle.connectionRise(size);
            VoxelShape bowlCollision = FountainBowlModel.collisionShape(bowlStyle, size)
                    .offset(0.0D, baseY, 0.0D);
            VoxelShape bowlOutline = FountainBowlModel.outlineShape(bowlStyle, size, filled)
                    .offset(0.0D, baseY, 0.0D);
            bowls.add(new PlacedBowl(bowlStyle, size, baseY, connectionY));
            mergeIntoParts(collisionParts, bowlCollision, false);
            mergeIntoParts(outlineParts, bowlOutline, true);
            maximumY = Math.max(maximumY, bowlOutline.getMax(Direction.Axis.Y));
        }

        return new FountainAssemblyLayout(
                basinStyle,
                basinSize,
                placedPlinth,
                bowls,
                collisionParts,
                outlineParts,
                maximumY
        );
    }

    public static BlockState normalizePlinth(BlockState state) {
        if (!(state.getBlock() instanceof PlinthBlock)) {
            throw new IllegalArgumentException("A fountain assembly must begin with a plinth");
        }
        return state.with(PlinthBlock.OFFSET, false);
    }

    /** Water changes selection surfaces only, never collision or tier placement. */
    public FountainAssemblyLayout withWaterlogged(boolean filled) {
        VoxelShape[] updatedOutline = FountainBasinGeometry.copyOutlinePartShapes(
                basinStyle, basinSize, filled);
        if (plinth != null) {
            PlinthBlock block = (PlinthBlock) plinth.state().getBlock();
            mergeIntoParts(updatedOutline, block.assemblyOutlineShape(plinth.state(), filled)
                    .offset(0.0D, plinth.baseY(), 0.0D), true);
        }
        for (PlacedBowl bowl : bowls) {
            mergeIntoParts(updatedOutline, FountainBowlModel.outlineShape(bowl.style(), bowl.size(), filled)
                    .offset(0.0D, bowl.baseY(), 0.0D), true);
        }
        return new FountainAssemblyLayout(basinStyle, basinSize, plinth, bowls,
                collisionParts, updatedOutline, maximumY);
    }

    public static int maximumBowlCount() {
        return FountainBowlModel.TierCount.THREE.bowlCount();
    }

    public PlacedPlinth plinth() {
        return plinth;
    }

    public List<PlacedBowl> bowls() {
        return bowls;
    }

    public VoxelShape collisionPartShape(int offsetX, int offsetY, int offsetZ) {
        return collisionParts[FountainBasinGeometry.partIndex(offsetX, offsetY, offsetZ)];
    }

    public VoxelShape outlinePartShape(int offsetX, int offsetY, int offsetZ) {
        return outlineParts[FountainBasinGeometry.partIndex(offsetX, offsetY, offsetZ)];
    }

    public double maximumY() {
        return maximumY;
    }

    public double containedBowlWaterHeight(BlockPos anchorPos, Box box) {
        double height = 0.0D;
        for (PlacedBowl bowl : bowls) {
            height = Math.max(
                    height,
                    FountainBowlModel.containedWaterHeight(
                            anchorPos,
                            bowl.baseY(),
                            bowl.style(),
                            bowl.size(),
                            box
                    )
            );
        }
        return height;
    }

    static void mergeIntoParts(VoxelShape[] parts,
                               VoxelShape shape,
                               boolean outlineOnly) {
        if (shape.isEmpty()) {
            return;
        }
        int minimumX = Math.max(
                -FountainBasinGeometry.PART_RADIUS,
                MathHelper.floor(shape.getMin(Direction.Axis.X))
        );
        int maximumX = Math.min(
                FountainBasinGeometry.PART_RADIUS,
                MathHelper.ceil(shape.getMax(Direction.Axis.X)) - 1
        );
        int minimumY = Math.max(0, MathHelper.floor(shape.getMin(Direction.Axis.Y)));
        int maximumY = Math.min(
                FountainBasinGeometry.MAX_PART_Y,
                MathHelper.ceil(shape.getMax(Direction.Axis.Y)) - 1
        );
        int minimumZ = Math.max(
                -FountainBasinGeometry.PART_RADIUS,
                MathHelper.floor(shape.getMin(Direction.Axis.Z))
        );
        int maximumZ = Math.min(
                FountainBasinGeometry.PART_RADIUS,
                MathHelper.ceil(shape.getMax(Direction.Axis.Z)) - 1
        );

        for (int offsetY = minimumY; offsetY <= maximumY; offsetY++) {
            for (int offsetX = minimumX; offsetX <= maximumX; offsetX++) {
                for (int offsetZ = minimumZ; offsetZ <= maximumZ; offsetZ++) {
                    VoxelShape addition = FountainBasinGeometry.clipToPart(
                            shape,
                            offsetX,
                            offsetY,
                            offsetZ
                    );
                    if (addition.isEmpty()) {
                        continue;
                    }
                    int index = FountainBasinGeometry.partIndex(
                            offsetX,
                            offsetY,
                            offsetZ
                    );
                    VoxelShape existing = parts[index];
                    parts[index] = outlineOnly
                            ? enclosingBox(existing, addition)
                            : existing.isEmpty()
                                    ? addition
                                    : VoxelShapes.combine(existing, addition, BooleanBiFunction.OR);
                }
            }
        }
    }

    private static VoxelShape enclosingBox(VoxelShape first, VoxelShape second) {
        if (first.isEmpty()) {
            return VoxelShapes.cuboid(
                    second.getMin(Direction.Axis.X),
                    second.getMin(Direction.Axis.Y),
                    second.getMin(Direction.Axis.Z),
                    second.getMax(Direction.Axis.X),
                    second.getMax(Direction.Axis.Y),
                    second.getMax(Direction.Axis.Z)
            );
        }
        return VoxelShapes.cuboid(
                Math.min(first.getMin(Direction.Axis.X), second.getMin(Direction.Axis.X)),
                Math.min(first.getMin(Direction.Axis.Y), second.getMin(Direction.Axis.Y)),
                Math.min(first.getMin(Direction.Axis.Z), second.getMin(Direction.Axis.Z)),
                Math.max(first.getMax(Direction.Axis.X), second.getMax(Direction.Axis.X)),
                Math.max(first.getMax(Direction.Axis.Y), second.getMax(Direction.Axis.Y)),
                Math.max(first.getMax(Direction.Axis.Z), second.getMax(Direction.Axis.Z))
        );
    }

    public record PlacedPlinth(BlockState state, double baseY, double connectionY) {
    }

    public record PlacedBowl(FountainBowlModel.Style style,
                             FountainBowlModel.Size size,
                             double baseY,
                             double connectionY) {
    }
}
