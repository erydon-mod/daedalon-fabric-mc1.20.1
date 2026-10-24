package com.oliver.daedalon.mixin.client.jei;

import com.oliver.daedalon.client.search.DaedalonSearchQueryRules;
import mezz.jei.gui.ingredients.IListElement;
import mezz.jei.gui.search.ElementPrefixParser;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Set;

@Pseudo
@Mixin(targets = {"mezz.jei.gui.search.ElementSearch", "mezz.jei.gui.search.ElementSearchLowMem"}, remap = false)
public abstract class JeiGenericBlockSearchMixin {
    @Inject(method = "getSearchResults", at = @At("RETURN"), cancellable = true, require = 0, remap = false)
    private void daedalon$filterPlainBlockToken(ElementPrefixParser.TokenInfo token,
                                               CallbackInfoReturnable<Set<IListElement<?>>> callback) {
        callback.setReturnValue(DaedalonSearchQueryRules.jeiPlainBlockResults(token.token(),
                token.prefixInfo() == ElementPrefixParser.NO_PREFIX, callback.getReturnValue(), element ->
                        element.getTypedIngredient().getItemStack()
                                .map(DaedalonSearchQueryRules::isCatalogueStack).orElse(false)));
    }
}
