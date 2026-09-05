package com.oliver.daedalon.mixin;

import com.oliver.daedalon.block.FountainBasinBlock;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Gives contained fountain water normal entity-water behaviour without a fluid block. */
@Mixin(Entity.class)
abstract class EntityMixin {
    @Unique
    private float daedalon$containedFluidHeight;
    @Unique
    private boolean daedalon$containedFluidCell;

    /**
     * Reuses vanilla's existing fluid-cell walk instead of performing a second
     * allocation-heavy world scan for every entity. Only cells already visited
     * by Minecraft are checked for a fountain anchor or one of its proxy parts.
     */
    @Redirect(
            method = "updateMovementInFluid",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/World;getFluidState(Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/fluid/FluidState;"
            )
    )
    private FluidState daedalon$includeContainedFountainWater(World world,
                                                              BlockPos pos,
                                                              TagKey<Fluid> fluidTag,
                                                              double speed) {
        daedalon$containedFluidHeight = 0.0F;
        daedalon$containedFluidCell = false;
        BlockState blockState = world.getBlockState(pos);
        FluidState fluidState = blockState.getFluidState();
        if (!FluidTags.WATER.equals(fluidTag) || !fluidState.isEmpty()) {
            return fluidState;
        }

        Entity entity = (Entity) (Object) this;
        double containedHeight = FountainBasinBlock.containedWaterSurfaceHeight(
                world,
                pos,
                blockState,
                entity.getBoundingBox()
        );
        if (containedHeight <= 0.0D) {
            return fluidState;
        }

        daedalon$containedFluidHeight = (float) containedHeight;
        daedalon$containedFluidCell = true;
        return Fluids.WATER.getStill(false);
    }

    @Redirect(
            method = "updateMovementInFluid",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/fluid/FluidState;getHeight(Lnet/minecraft/world/BlockView;Lnet/minecraft/util/math/BlockPos;)F"
            )
    )
    private float daedalon$useContainedFountainWaterHeight(FluidState state,
                                                           BlockView world,
                                                           BlockPos pos) {
        if (daedalon$containedFluidCell) {
            return daedalon$containedFluidHeight;
        }
        return state.getHeight(world, pos);
    }

    @Redirect(
            method = "updateMovementInFluid",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/fluid/FluidState;getVelocity(Lnet/minecraft/world/BlockView;Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/util/math/Vec3d;"
            )
    )
    private Vec3d daedalon$keepContainedFountainWaterStill(FluidState state,
                                                           BlockView world,
                                                           BlockPos pos) {
        return daedalon$containedFluidCell ? Vec3d.ZERO : state.getVelocity(world, pos);
    }
}
