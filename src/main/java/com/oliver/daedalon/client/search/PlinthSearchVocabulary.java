package com.oliver.daedalon.client.search;

import com.oliver.daedalon.block.PlinthBlock;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

import java.util.List;
import java.util.Locale;

/** Shared fountain aliases for every plinth style and material, including aged variants. */
public final class PlinthSearchVocabulary {
    private PlinthSearchVocabulary() { }

    public static List<String> terms(ItemStack stack) {
        if (!(stack.getItem() instanceof BlockItem item) || !(item.getBlock() instanceof PlinthBlock)) {
            return List.of();
        }
        return List.of("fountain", "fountains", "fountain plinth", "tiered fountain", "water feature",
                Text.translatable("search.daedalon.plinth.fountain").getString());
    }

    public static String appendTerms(String existing, ItemStack stack) {
        List<String> terms = terms(stack);
        if (terms.isEmpty()) return existing;
        return (existing == null ? "" : existing) + '\u0000'
                + String.join("\u0000", terms).toLowerCase(Locale.ROOT);
    }
}
