package com.oliver.daedalon.block;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;

import java.util.ArrayList;
import java.util.List;

/** A bounded snapshot of one decor object's actual cells, taken only on selection. */
public final class DecorAssemblySelection {
    private DecorAssemblySelection() {}

    public static List<BlockPos> cells(BlockView world, BlockPos hit) {
        BlockState state = world.getBlockState(hit);
        BlockPos anchor = hit;
        if (state.getBlock() instanceof MonopterosPartBlock) {
            anchor = MonopterosPartBlock.resolveAnchorPos(world, hit, state);
        } else if (state.getBlock() instanceof FountainBasinPartBlock) {
            anchor = FountainBasinPartBlock.resolveAnchorPos(world, hit, state);
        }
        if (anchor == null) return List.of();
        BlockState owner = world.getBlockState(anchor);
        boolean dome = owner.getBlock() instanceof MonopterosBlock;
        if (!dome && !(owner.getBlock() instanceof FountainBasinBlock)) return List.of();

        int radius = dome ? MonopterosGeometry.RADIUS : FountainBasinGeometry.PART_RADIUS;
        int height = dome ? MonopterosGeometry.HEIGHT_CELLS : FountainBasinGeometry.MAX_PART_Y + 1;
        List<BlockPos> cells = new ArrayList<>();
        cells.add(anchor.toImmutable());
        for (int y = 0; y < height; y++) {
            for (int z = -radius; z <= radius; z++) {
                for (int x = -radius; x <= radius; x++) {
                    BlockPos pos = anchor.add(x, y, z);
                    BlockState part = world.getBlockState(pos);
                    if (dome ? MonopterosPartBlock.isOwnedBy(part, pos, anchor)
                            : FountainBasinPartBlock.isOwnedBy(part, pos, anchor)) {
                        cells.add(pos);
                    }
                }
            }
        }
        return List.copyOf(cells);
    }
}
