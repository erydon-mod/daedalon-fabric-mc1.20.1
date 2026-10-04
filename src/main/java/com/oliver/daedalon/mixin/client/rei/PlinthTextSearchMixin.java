package com.oliver.daedalon.mixin.client.rei;

import com.oliver.daedalon.client.search.PlinthSearchVocabulary;
import me.shedaniel.rei.api.common.entry.EntryStack;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "me.shedaniel.rei.impl.client.search.argument.type.TextArgumentType", remap = false)
public abstract class PlinthTextSearchMixin {
    @Inject(method = "cacheData(Lme/shedaniel/rei/api/common/entry/EntryStack;)Ljava/lang/String;",
            at = @At("RETURN"), cancellable = true, require = 0, remap = false)
    private void daedalon$appendFountainSearch(EntryStack<?> entry, CallbackInfoReturnable<String> callback) {
        if (entry.getValue() instanceof ItemStack stack) {
            callback.setReturnValue(PlinthSearchVocabulary.appendTerms(callback.getReturnValue(), stack));
        }
    }
}
