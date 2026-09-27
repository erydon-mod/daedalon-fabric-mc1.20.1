package com.oliver.daedalon.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.property.IntProperty;
import net.minecraft.state.property.Property;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.StringIdentifiable;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockView;

/** Optional ERYDON support detection without a compile-time dependency on ERYDON. */
public final class CapitalSupportPlacement {
    private CapitalSupportPlacement() {}

    public static ItemPlacementContext redirect(ItemPlacementContext context, Block block) {
        if (!(block instanceof CapitalBlock) || context.getSide() != Direction.UP) return context;
        BlockPos target = context.getBlockPos();
        BlockPos anchor = largeColumnAnchor(context.getWorld(), target);
        if (anchor == null || anchor.equals(target)) return context;
        BlockHitResult alignedHit = new BlockHitResult(
                new Vec3d(anchor.getX() + 0.5, target.getY(), anchor.getZ() + 0.5),
                Direction.UP, anchor.down(), false);
        return new AlignedPlacementContext(context, alignedHit);
    }

    public static boolean isLargeColumnTop(BlockView world, BlockPos target) {
        return largeColumnAnchor(world, target) != null;
    }

    private static BlockPos largeColumnAnchor(BlockView world, BlockPos target) {
        BlockPos supportPos = target.down();
        BlockState selected = world.getBlockState(supportPos);
        Identifier id = Registries.BLOCK.getId(selected.getBlock());
        if (!"erydon".equals(id.getNamespace()) || !id.getPath().endsWith("_column_circular_double")
                || !"capital_upper".equals(section(selected))) return null;
        Integer x = quadrant(selected, "part_x"), z = quadrant(selected, "part_z");
        if (x == null || z == null) return null;
        BlockPos anchor = target.add(-x, 0, -z);
        for (int dx = 0; dx < 2; dx++) for (int dz = 0; dz < 2; dz++) {
            BlockPos cell = anchor.add(dx, -1, dz);
            BlockState state = world.getBlockState(cell);
            if (!state.isOf(selected.getBlock()) || !"capital_upper".equals(section(state))
                    || !Integer.valueOf(dx).equals(quadrant(state, "part_x"))
                    || !Integer.valueOf(dz).equals(quadrant(state, "part_z"))) return null;
            if (world.getBlockState(cell.up()).isOf(selected.getBlock())) return null;
        }
        return anchor;
    }

    private static Integer quadrant(BlockState state, String name) {
        Property<?> property = state.getBlock().getStateManager().getProperty(name);
        return property instanceof IntProperty integer ? state.get(integer) : null;
    }

    private static String section(BlockState state) {
        Property<?> property = state.getBlock().getStateManager().getProperty("section");
        Object value = property == null ? null : state.getEntries().get(property);
        return value instanceof StringIdentifiable named ? named.asString() : "";
    }

    private static final class AlignedPlacementContext extends ItemPlacementContext {
        private AlignedPlacementContext(ItemPlacementContext original, BlockHitResult hit) {
            super(original.getWorld(), original.getPlayer(), original.getHand(), original.getStack(), hit);
            this.canReplaceExisting = false;
        }
    }
}
