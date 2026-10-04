package com.oliver.daedalon.compat.jei;

import com.oliver.daedalon.client.search.PlinthSearchVocabulary;
import com.oliver.daedalon.registry.ModItems;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.registration.IIngredientAliasRegistration;
import net.minecraft.util.Identifier;

@JeiPlugin
public final class DaedalonJeiPlugin implements IModPlugin {
    @Override
    public Identifier getPluginUid() {
        return new Identifier("daedalon", "jei");
    }

    @Override
    public void registerIngredientAliases(IIngredientAliasRegistration registration) {
        for (var item : ModItems.blockItems()) {
            var stack = item.getDefaultStack();
            var terms = PlinthSearchVocabulary.terms(stack);
            if (!terms.isEmpty()) registration.addAliases(VanillaTypes.ITEM_STACK, stack, terms);
        }
    }
}
