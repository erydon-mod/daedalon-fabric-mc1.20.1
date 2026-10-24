package com.oliver.daedalon.compat.jei;

import com.oliver.daedalon.client.search.DaedalonSearchVocabulary;
import com.oliver.daedalon.client.search.DaedalonCatalogue;
import com.oliver.daedalon.registry.ModItems;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.registration.IIngredientAliasRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.text.Text;
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
            var terms = DaedalonSearchVocabulary.terms(stack);
            if (!terms.isEmpty()) registration.addAliases(VanillaTypes.ITEM_STACK, stack, terms);
        }
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        DaedalonCatalogue.groups().designs().forEach((family, stacks) ->
                registration.addItemStackInfo(stacks, Text.translatable("catalogue.daedalon.design_info",
                        DaedalonCatalogue.designName(family))));
    }
}
