package com.oliver.daedalon.mixin.client.axiom;

import com.oliver.daedalon.Daedalon;
import com.oliver.daedalon.client.model.obj.ObjGuiIconCache;
import net.minecraft.block.BlockState;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Axiom normally renders solid blocks directly into its palette cache. That
 * bypasses Daedalon's bounded GUI icon atlas, so OBJ-backed entries appear
 * blank. Sending only Daedalon blocks through Axiom's item path reuses the
 * same complete-model 2D icons used by the inventory and REI.
 */
@Pseudo
@Mixin(targets = "com.moulberry.axiom.render.BlockRenderCache", remap = false)
abstract class BlockRenderCacheMixin {
    /**
     * Axiom captures an item once, then keeps that texture while it remains
     * visible. Do not let its capture freeze our temporary material-square
     * fallback while the bounded icon worker is still preparing the real icon.
     * Missing palette captures are naturally requested again by the UI.
     */
    @Inject(method = "renderSizedBlock", at = @At("HEAD"), cancellable = true,
            require = 0, remap = false)
    private static void daedalon$waitForCompleteGuiIcon(
            @Coerce Object rawCapture,
            DrawContext context,
            CallbackInfo callbackInfo
    ) {
        var capture = (AxiomPaletteStateAccessor) rawCapture;
        if (!capture.daedalon$forceAsBlock()
                && ObjGuiIconCache.isPendingAxiomPaletteIcon(capture.daedalon$state())) {
            callbackInfo.cancel();
        }
    }

    @Inject(
            method = "shouldRenderAsItem",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private static void daedalon$useGuiIconInAxiom(
            BlockState state,
            ItemStack stack,
            CallbackInfoReturnable<Boolean> callbackInfo
    ) {
        if (!stack.isEmpty()
                && Daedalon.MOD_ID.equals(
                        Registries.ITEM.getId(stack.getItem()).getNamespace()
                )) {
            callbackInfo.setReturnValue(true);
        }
    }
}
