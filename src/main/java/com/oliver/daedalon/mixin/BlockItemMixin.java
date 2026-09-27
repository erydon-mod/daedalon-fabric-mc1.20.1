package com.oliver.daedalon.mixin;

import com.oliver.daedalon.block.FountainAssemblyPlacement;
import com.oliver.daedalon.block.CapitalSupportPlacement;
import com.oliver.daedalon.block.TallDecorPlacement;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.util.ActionResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockItem.class)
abstract class BlockItemMixin {
    @Inject(method = "useOnBlock", at = @At("HEAD"), cancellable = true)
    private void daedalon$attachFountainComponent(
            ItemUsageContext context,
            CallbackInfoReturnable<ActionResult> callback
    ) {
        ActionResult result = FountainAssemblyPlacement.tryAttach(
                context,
                ((BlockItem) (Object) this).getBlock()
        );
        if (result != null) {
            callback.setReturnValue(result);
        }
    }

    @ModifyVariable(
            method = "place(Lnet/minecraft/item/ItemPlacementContext;)Lnet/minecraft/util/ActionResult;",
            at = @At("HEAD"),
            argsOnly = true
    )
    private ItemPlacementContext daedalon$placeAboveTallDecor(ItemPlacementContext context) {
        return CapitalSupportPlacement.redirect(TallDecorPlacement.redirect(context),
                ((BlockItem) (Object) this).getBlock());
    }
}
