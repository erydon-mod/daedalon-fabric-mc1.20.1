package com.oliver.daedalon.block;

import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.EmptyBlockView;

/** Representative selection bounds, without querying a live world or helper cells. */
public final class DecorPreviewBounds {
    private DecorPreviewBounds() {}

    public static boolean supports(BlockState state) {
        return state != null && state.getBlock().asItem() != Items.AIR
                && Registries.BLOCK.getId(state.getBlock()).getNamespace().equals("daedalon");
    }

    public static Box bounds(BlockState state) {
        if (state.getBlock() instanceof MonopterosBlock) {
            var size = state.get(MonopterosBlock.DIAMETER);
            double radius = size.metres * .5;
            return new Box(.5 - radius, 0, .5 - radius, .5 + radius, size.height, .5 + radius);
        }
        // A copied region already supplies the persisted join; no neighbour refresh is needed.
        if (state.getBlock() instanceof FriezeBlock) state = state.with(FriezeBlock.MANUAL_CORNER, true);
        var shape = state.getBlock() instanceof FountainBasinBlock basin
                ? FountainBasinBlock.collisionShape(basin.style(), state.get(FountainBasinBlock.SIZE))
                : state.getBlock().getCollisionShape(state, EmptyBlockView.INSTANCE,
                        BlockPos.ORIGIN, ShapeContext.absent());
        return shape.isEmpty() ? new Box(0, 0, 0, 1, 1, 1) : shape.getBoundingBox();
    }
}
