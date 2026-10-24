package com.oliver.daedalon.compat.emi;

import com.oliver.daedalon.client.search.DaedalonSearchVocabulary;
import com.oliver.daedalon.registry.ModItems;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.text.Text;

public final class DaedalonEmiPlugin implements EmiPlugin {
    @Override
    public void register(EmiRegistry registry) {
        for (var item : ModItems.catalogueItems()) {
            var stack = item.getDefaultStack();
            for (String term : DaedalonSearchVocabulary.terms(stack)) {
                registry.addAlias(EmiStack.of(stack), Text.literal(term));
            }
        }
    }
}
