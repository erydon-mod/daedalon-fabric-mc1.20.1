package com.oliver.daedalon.mixin.client.emi;

import com.oliver.daedalon.client.search.DaedalonSearchQueryRules;
import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.item.Item;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "dev.emi.emi.search.LogicalOrQuery", remap = false)
public abstract class EmiDefaultQueryMixin implements DaedalonSearchQueryRules.EmiDefaultQuery {
    @Unique private boolean daedalon$blockTerm;

    @Override public void daedalon$setBlockTerm() { daedalon$blockTerm = true; }

    @Inject(method = {"matches", "matchesUnbaked"}, at = @At("HEAD"), cancellable = true,
            require = 0, remap = false)
    private void daedalon$matchDefaultBlockQuery(EmiStack stack, CallbackInfoReturnable<Boolean> callback) {
        if (daedalon$blockTerm && stack.getKey() instanceof Item item
                && DaedalonSearchQueryRules.isCatalogueItem(item)) {
            callback.setReturnValue(false);
        }
    }
}
