package com.oliver.daedalon.mixin.client.rei;

import com.oliver.daedalon.client.search.DaedalonSearchVocabulary;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import me.shedaniel.rei.api.common.entry.EntryStack;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "me.shedaniel.rei.impl.client.search.argument.type.TextArgumentType", remap = false)
public abstract class PlinthTextSearchMixin {
    // Add aliases while building the cache value, before any other mod's
    // cancellable RETURN injector can return that value early.
    @ModifyExpressionValue(method = "cacheData(Lme/shedaniel/rei/api/common/entry/EntryStack;)Ljava/lang/String;",
            at = @At(value = "INVOKE", target = "Lme/shedaniel/rei/api/common/util/FormattingUtils;stripFormatting(Ljava/lang/String;)Ljava/lang/String;",
                    remap = false), require = 0, remap = false)
    private String daedalon$appendCatalogueSearch(String cached, EntryStack<?> entry) {
        if (entry.getValue() instanceof ItemStack stack) {
            return DaedalonSearchVocabulary.appendTerms(cached, stack);
        }
        return cached;
    }
}
