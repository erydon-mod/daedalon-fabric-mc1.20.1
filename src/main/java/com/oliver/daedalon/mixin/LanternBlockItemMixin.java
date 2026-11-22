package com.oliver.daedalon.mixin;

import com.oliver.daedalon.block.LanternPlacement;
import com.oliver.daedalon.block.LanternBlock;
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
abstract class LanternBlockItemMixin {
    @Inject(method="useOnBlock",at=@At("HEAD"),cancellable=true)
    private void daedalon$extendHangingLantern(ItemUsageContext context,CallbackInfoReturnable<ActionResult> callback) {
        BlockItem item=(BlockItem)(Object)this;
        if(item.getBlock() instanceof LanternBlock) callback.setReturnValue(LanternPlacement.use(context,item));
    }
    @ModifyVariable(method="place(Lnet/minecraft/item/ItemPlacementContext;)Lnet/minecraft/util/ActionResult;",at=@At("HEAD"),argsOnly=true)
    private ItemPlacementContext daedalon$placeSeparateLantern(ItemPlacementContext context) {
        return LanternPlacement.redirect(context,((BlockItem)(Object)this).getBlock());
    }
}
