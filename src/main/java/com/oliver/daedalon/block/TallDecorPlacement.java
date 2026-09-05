package com.oliver.daedalon.block;

import com.oliver.daedalon.Daedalon;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.registry.Registries;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;

/**
 * Places any block item above the complete outline of a tall Daedalon model.
 *
 * <p>Minecraft normally rejects a packet whose hit position is more than one
 * block from the centre of its registered block position. Tall OBJ outlines
 * legitimately extend beyond that range, so the server-side packet check also
 * uses this class to validate hits against the real outline before normalising
 * that one distance measurement.</p>
 */
public final class TallDecorPlacement {
    private static final double HEIGHT_EPSILON = 1.0E-7D;
    private static final double HIT_EPSILON = 1.0E-4D;
    private static final int HORIZONTAL_SEARCH_RADIUS = 3;
    private static final int VERTICAL_SEARCH_DEPTH = 8;

    private TallDecorPlacement() {
    }

    public static ItemPlacementContext redirect(ItemPlacementContext context) {
        if (context.getSide() != Direction.UP) {
            return context;
        }

        Support support = findTallSupport(context);
        if (support == null) {
            return context;
        }

        BlockPos targetPos = support.pos().up(placementRise(support.maximumY()));
        Vec3d originalHit = context.getHitPos();
        BlockHitResult elevatedHit = new BlockHitResult(
                new Vec3d(
                        originalHit.x,
                        support.pos().getY() + support.maximumY(),
                        originalHit.z
                ),
                Direction.UP,
                targetPos.down(),
                false
        );
        return new ElevatedPlacementContext(context, elevatedHit);
    }

    public static Vec3d serverValidationDelta(BlockView world,
                                              Vec3d hit,
                                              Vec3d registeredBlockCenter) {
        Vec3d vanillaDelta = hit.subtract(registeredBlockCenter);
        BlockPos registeredPos = BlockPos.ofFloored(registeredBlockCenter);
        BlockState state = world.getBlockState(registeredPos);
        if (!Daedalon.MOD_ID.equals(Registries.BLOCK.getId(state.getBlock()).getNamespace())) {
            return vanillaDelta;
        }

        VoxelShape outline = state.getOutlineShape(world, registeredPos, ShapeContext.absent());
        boolean validHit = !outline.isEmpty() && containsHit(outline, registeredPos, hit);
        if (!validHit) {
            return vanillaDelta;
        }

        return clampForVanillaPacketCheck(vanillaDelta);
    }

    /**
     * Measures mining reach to the nearest point of an extended Daedalon model,
     * rather than to the centre of the one block that registers that model.
     */
    public static double serverBreakingDistanceSquared(BlockView world,
                                                       Vec3d eye,
                                                       Vec3d registeredBlockCenter) {
        double vanillaDistance = eye.squaredDistanceTo(registeredBlockCenter);
        BlockPos registeredPos = BlockPos.ofFloored(registeredBlockCenter);
        BlockState state = world.getBlockState(registeredPos);
        if (!Daedalon.MOD_ID.equals(Registries.BLOCK.getId(state.getBlock()).getNamespace())) {
            return vanillaDistance;
        }

        VoxelShape outline = state.getOutlineShape(world, registeredPos, ShapeContext.absent());
        if (outline.isEmpty()) {
            return vanillaDistance;
        }
        Vec3d localEye = eye.subtract(
                registeredPos.getX(),
                registeredPos.getY(),
                registeredPos.getZ()
        );
        return outline.getClosestPointTo(localEye)
                .map(closest -> closest.add(
                        registeredPos.getX(),
                        registeredPos.getY(),
                        registeredPos.getZ()
                ))
                .map(eye::squaredDistanceTo)
                .orElse(vanillaDistance);
    }

    private static Support findTallSupport(ItemPlacementContext context) {
        Vec3d hit = context.getHitPos();
        BlockPos placementPos = context.getBlockPos();
        int anchorX = (int) Math.floor(hit.x);
        int anchorZ = (int) Math.floor(hit.z);
        int startY = Math.max(placementPos.getY(), (int) Math.floor(hit.y));
        Support best = null;
        double bestTopDistance = Double.POSITIVE_INFINITY;
        double bestHorizontalDistance = Double.POSITIVE_INFINITY;

        for (int y = startY; y >= startY - VERTICAL_SEARCH_DEPTH; y--) {
            for (int x = anchorX - HORIZONTAL_SEARCH_RADIUS;
                 x <= anchorX + HORIZONTAL_SEARCH_RADIUS;
                 x++) {
                for (int z = anchorZ - HORIZONTAL_SEARCH_RADIUS;
                     z <= anchorZ + HORIZONTAL_SEARCH_RADIUS;
                     z++) {
                    BlockPos candidatePos = new BlockPos(x, y, z);
                    BlockState candidateState = context.getWorld().getBlockState(candidatePos);
                    if (!Daedalon.MOD_ID.equals(
                            Registries.BLOCK.getId(candidateState.getBlock()).getNamespace()
                    )) {
                        continue;
                    }

                    VoxelShape outline = candidateState.getOutlineShape(
                            context.getWorld(),
                            candidatePos,
                            ShapeContext.absent()
                    );
                    if (outline.isEmpty()) {
                        continue;
                    }
                    double maximumY = outline.getMax(Direction.Axis.Y);
                    if (placementRise(maximumY) <= 1
                            || !containsHorizontalHit(outline, candidatePos, hit)) {
                        continue;
                    }

                    double worldTop = candidatePos.getY() + maximumY;
                    if (worldTop + HIT_EPSILON < hit.y) {
                        continue;
                    }
                    double topDistance = Math.abs(worldTop - hit.y);
                    double horizontalDistance = squaredHorizontalDistance(candidatePos, hit);
                    if (topDistance < bestTopDistance - HIT_EPSILON
                            || (Math.abs(topDistance - bestTopDistance) <= HIT_EPSILON
                            && horizontalDistance < bestHorizontalDistance)) {
                        best = new Support(candidatePos, maximumY);
                        bestTopDistance = topDistance;
                        bestHorizontalDistance = horizontalDistance;
                    }
                }
            }
        }
        return best;
    }

    private static boolean containsHorizontalHit(VoxelShape outline, BlockPos pos, Vec3d hit) {
        double localX = hit.x - pos.getX();
        double localZ = hit.z - pos.getZ();
        return contains(
                localX,
                outline.getMin(Direction.Axis.X),
                outline.getMax(Direction.Axis.X)
        ) && contains(
                localZ,
                outline.getMin(Direction.Axis.Z),
                outline.getMax(Direction.Axis.Z)
        );
    }

    static boolean containsHit(VoxelShape outline, BlockPos pos, Vec3d hit) {
        double localX = hit.x - pos.getX();
        double localY = hit.y - pos.getY();
        double localZ = hit.z - pos.getZ();
        boolean[] contained = {false};
        outline.forEachBox((minX, minY, minZ, maxX, maxY, maxZ) -> {
            if (!contained[0]
                    && contains(localX, minX, maxX)
                    && contains(localY, minY, maxY)
                    && contains(localZ, minZ, maxZ)) {
                contained[0] = true;
            }
        });
        return contained[0];
    }

    static boolean contains(double value, double minimum, double maximum) {
        return value >= minimum - HIT_EPSILON && value <= maximum + HIT_EPSILON;
    }

    static double clampForVanillaPacketCheck(double value) {
        return Math.max(-1.0D, Math.min(1.0D, value));
    }

    static Vec3d clampForVanillaPacketCheck(Vec3d delta) {
        return new Vec3d(
                clampForVanillaPacketCheck(delta.x),
                clampForVanillaPacketCheck(delta.y),
                clampForVanillaPacketCheck(delta.z)
        );
    }

    private static double squaredHorizontalDistance(BlockPos pos, Vec3d hit) {
        double deltaX = pos.getX() + 0.5D - hit.x;
        double deltaZ = pos.getZ() + 0.5D - hit.z;
        return deltaX * deltaX + deltaZ * deltaZ;
    }

    static int placementRise(double maximumY) {
        if (!Double.isFinite(maximumY) || maximumY <= 0.0D) {
            return 1;
        }
        return Math.max(1, (int) Math.ceil(maximumY - HEIGHT_EPSILON));
    }

    private record Support(BlockPos pos, double maximumY) {
    }

    private static final class ElevatedPlacementContext extends ItemPlacementContext {
        private ElevatedPlacementContext(ItemPlacementContext original, BlockHitResult hit) {
            super(
                    original.getWorld(),
                    original.getPlayer(),
                    original.getHand(),
                    original.getStack(),
                    hit
            );
            // The virtual support cell can be air because a tall OBJ is still
            // represented by one registered block. Preserve normal top-click
            // placement directions while using the elevated placementPos.
            this.canReplaceExisting = false;
        }
    }
}
