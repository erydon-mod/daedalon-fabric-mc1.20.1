package com.oliver.daedalon.mixin;

import com.oliver.daedalon.block.TallDecorPlacement;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(BlockItem.class)
abstract class BlockItemMixin {
    @ModifyVariable(
            method = "place(Lnet/minecraft/item/ItemPlacementContext;)Lnet/minecraft/util/ActionResult;",
            at = @At("HEAD"),
            argsOnly = true
    )
    private ItemPlacementContext daedalon$placeAboveTallDecor(ItemPlacementContext context) {
        return TallDecorPlacement.redirect(context);
    }
}
