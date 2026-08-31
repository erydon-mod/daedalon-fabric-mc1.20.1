package com.oliver.daedalon.mixin.client.axiom;

import com.oliver.daedalon.Daedalon;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
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
