package com.oliver.daedalon.mixin.client.axiom;

import com.oliver.daedalon.client.compat.AxiomStatueBillboards;
import net.minecraft.block.BlockState;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.moulberry.axiom.render.regions.ChunkedBlockRegion", remap = false)
abstract class ChunkedBlockRegionMixin {
    @Unique private final AxiomStatueBillboards daedalon$billboards = new AxiomStatueBillboards();

    // Explicit named + intermediary descriptors support development and release without
    // a compile dependency on Axiom, and select only the int-coordinate overload.
    @Inject(method = {"addBlock(IIILnet/minecraft/block/BlockState;)V",
            "addBlock(IIILnet/minecraft/class_2680;)V", "addBlockWithoutDirty"}, at = @At("RETURN"))
    private void daedalon$track(int x, int y, int z, BlockState state, CallbackInfo ci) {
        daedalon$billboards.update(x, y, z, state);
    }

    @Inject(method = "unsafeRemoveBlockWithoutDirty", at = @At("RETURN"))
    private void daedalon$remove(int x, int y, int z, CallbackInfo ci) {
        daedalon$billboards.update(x, y, z, null);
    }

    @Inject(method = "clear", at = @At("RETURN"))
    private void daedalon$clear(CallbackInfo ci) { daedalon$billboards.clear(); }

    @Inject(method = {
            "render(Lcom/moulberry/axiom/render/AxiomWorldRenderContext;Lnet/minecraft/util/math/Vec3d;Lorg/joml/Quaternionf;FFZLnet/minecraft/client/gl/Framebuffer;)V",
            "render(Lcom/moulberry/axiom/render/AxiomWorldRenderContext;Lnet/minecraft/class_243;Lorg/joml/Quaternionf;FFZLnet/minecraft/class_276;)V"
    }, at = @At("RETURN"))
    private void daedalon$draw(@Coerce Object context, Vec3d translation, Quaternionf rotation,
                               float blockOpacity, float outlineOpacity, boolean offset,
                               Framebuffer target, CallbackInfo ci) {
        daedalon$billboards.render(context, translation, rotation, blockOpacity, target);
    }
}
