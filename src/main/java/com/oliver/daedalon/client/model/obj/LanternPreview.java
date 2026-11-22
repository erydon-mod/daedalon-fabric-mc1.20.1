package com.oliver.daedalon.client.model.obj;

import com.oliver.daedalon.block.LanternBlock;
import com.oliver.daedalon.block.LanternPlacement;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

/** Material-independent Axiom assembly key; inventory icons still use the pier body. */
public record LanternPreview(LanternBlock.Mount mount, LanternBlock.Size size,
                             Direction facing, int chainCount, boolean canopy, boolean lit, Vec3d supportOffset) {
    public LanternPreview(LanternBlock.Mount mount, LanternBlock.Size size,
                          Direction facing, int chainCount, boolean canopy, boolean lit) {
        this(mount, size, facing, chainCount, canopy, lit, Vec3d.ZERO);
    }
    public LanternPreview {
        supportOffset = supportOffset == null ? Vec3d.ZERO : supportOffset;
        if (!facing.getAxis().isHorizontal()) throw new IllegalArgumentException("Horizontal lantern facing required");
        chainCount = mount == LanternBlock.Mount.HANGING
                ? Math.max(0, Math.min(LanternPlacement.MAX_CHAIN, chainCount)) : 0;
        if (mount != LanternBlock.Mount.HANGING) canopy = false;
    }

    public static LanternPreview of(BlockState state, int chainCount, boolean canopy) {
        return of(state, chainCount, canopy, Vec3d.ZERO);
    }

    public static LanternPreview of(BlockState state, int chainCount, boolean canopy, Vec3d offset) {
        return state != null && state.getBlock() instanceof LanternBlock
                ? new LanternPreview(state.get(LanternBlock.MOUNT), state.get(LanternBlock.SIZE),
                state.get(LanternBlock.FACING), chainCount, canopy, state.get(LanternBlock.LIT), offset) : null;
    }
}
