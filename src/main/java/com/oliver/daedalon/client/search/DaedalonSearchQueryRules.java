package com.oliver.daedalon.client.search;

import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;

import java.util.Locale;
import java.util.Set;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.function.Predicate;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Keep the administrative daedalon_blocks tag out of a plain full-block search. */
public final class DaedalonSearchQueryRules {
    private static final Set<String> BLOCK_TERMS = Set.of("block", "blocks", "full block", "full blocks",
            "blocke", "blöcke", "vollblock", "vollblöcke", "bloque", "bloques");
    private static final ConcurrentMap<Item, Boolean> CATALOGUE_ITEMS = new ConcurrentHashMap<>();
    private static final ThreadLocal<Boolean> EMI_DEFAULT_BLOCK_TOKEN = ThreadLocal.withInitial(() -> false);

    private DaedalonSearchQueryRules() { }

    public static boolean isGenericBlockTerm(String term) {
        return term != null && BLOCK_TERMS.contains(term.trim().toLowerCase(Locale.ROOT));
    }

    public static Boolean reiPlainBlockResult(Iterable<?> alternatives, boolean catalogueItem) {
        if (!catalogueItem) return null;
        for (Object argument : alternatives) {
            if (argument instanceof ReiTextArgument text && text.daedalon$isBlockTerm()) {
                return !text.daedalon$isRegular();
            }
        }
        return null;
    }

    public static <T> Set<T> jeiPlainBlockResults(String token, boolean unprefixed, Set<T> results,
                                                Predicate<T> catalogueItem) {
        if (!unprefixed || !isGenericBlockTerm(token)) return results;
        Set<T> filtered = Collections.newSetFromMap(new IdentityHashMap<>());
        for (T entry : results) {
            if (!catalogueItem.test(entry)) filtered.add(entry);
        }
        return filtered;
    }

    public static boolean isCatalogueStack(ItemStack stack) {
        return !stack.isEmpty() && isCatalogueItem(stack.getItem());
    }

    public static boolean isCatalogueItem(Item item) {
        return CATALOGUE_ITEMS.computeIfAbsent(item, candidate -> {
            if (!(candidate instanceof BlockItem)) return false;
            var id = Registries.ITEM.getId(candidate);
            return "daedalon".equals(id.getNamespace())
                    && DaedalonSearchVocabulary.index().entry(id.getPath()) != null;
        });
    }

    /** EMI calls this once per parsed token, before building that token's cached query. */
    public static void beginEmiToken(String token) {
        String contents = token;
        if (contents.startsWith("\"") && contents.endsWith("\"") && contents.length() > 1) {
            contents = contents.substring(1, contents.length() - 1);
        }
        // A prefix or /regex/ deliberately bypasses the plain-text rule.
        EMI_DEFAULT_BLOCK_TOKEN.set(isGenericBlockTerm(contents));
    }

    public static boolean consumeEmiBlockToken() {
        boolean result = EMI_DEFAULT_BLOCK_TOKEN.get();
        EMI_DEFAULT_BLOCK_TOKEN.remove();
        return result;
    }

    public interface ReiTextArgument {
        boolean daedalon$isBlockTerm();
        boolean daedalon$isRegular();
    }

    public interface EmiDefaultQuery {
        void daedalon$setBlockTerm();
    }
}
