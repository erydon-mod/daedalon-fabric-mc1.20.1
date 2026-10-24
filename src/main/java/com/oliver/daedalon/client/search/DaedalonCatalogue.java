package com.oliver.daedalon.client.search;

import com.oliver.daedalon.registry.ModItems;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** Immutable browser groups, assembled once from the shared bundled catalogue. */
public final class DaedalonCatalogue {
    public static final List<String> FORMS = List.of("statues", "busts", "urns", "capitals", "corbels",
            "plinths", "finials", "friezes", "panels", "benches", "planters", "domes", "basins",
            "fountains", "monuments");

    private DaedalonCatalogue() { }

    public static Groups groups() { return Holder.GROUPS; }

    public static Text designName(String family) {
        return Text.translatable("catalogue.daedalon.design." + family);
    }

    public static Text formName(String form) {
        return Text.translatable("catalogue.daedalon.form." + form);
    }

    public static Text materialName(String material) {
        return Text.translatable("catalogue.daedalon.material." + material);
    }

    /** Only changes a newly built group's member list, never the registry or Creative order. */
    public static <T> boolean preferAppendedGlacium(DaedalonSearchIndex index, List<T> group,
                                                   Function<T, String> path) {
        if (group.size() < 2) return false;
        int last = group.size() - 1;
        String candidatePath = path.apply(group.get(last));
        // Most appends need no parsing or allocation. Published Spartan IDs are form-first.
        if (candidatePath == null || !(candidatePath.startsWith("glacium_")
                || candidatePath.startsWith("statue_spartan_promachos_glacium"))) return false;
        var candidate = index.entry(candidatePath);
        if (candidate == null || !candidate.material().equals("glacium") || candidate.aged()) return false;
        var first = index.entry(path.apply(group.get(0)));
        if (first == null || !first.family().equals(candidate.family())) return false;
        T representative = group.remove(last);
        group.add(0, representative);
        return true;
    }

    public static String form(String family) {
        if (family.equals("hedra") || family.equals("exedra")) return "benches";
        for (String form : FORMS) {
            String suffix = form.equals("busts") ? "bust" : form.substring(0, form.length() - 1);
            if (family.endsWith("_" + suffix)) return form;
        }
        throw new IllegalArgumentException("Unclassified Daedalon catalogue design: " + family);
    }

    /** Accepting an explicit list also lets browser registration tests use the real grouping path. */
    public static Groups group(DaedalonSearchIndex index, List<Item> items) {
        Map<String, List<ItemStack>> designs = orderedGroups(index.families());
        Map<String, List<ItemStack>> forms = orderedGroups(FORMS);
        Map<String, List<ItemStack>> materials = orderedGroups(index.materials());
        List<ItemStack> ordered = new ArrayList<>(items.size());
        for (Item item : items) {
            var id = Registries.ITEM.getId(item);
            if (!"daedalon".equals(id.getNamespace())) continue;
            var entry = index.entry(id.getPath());
            if (entry == null) continue;
            ItemStack stack = item.getDefaultStack();
            ordered.add(stack);
            designs.get(entry.family()).add(stack);
            forms.get(form(entry.family())).add(stack);
            materials.get(entry.material()).add(stack);
        }
        return new Groups(List.copyOf(ordered), freeze(designs), freeze(forms), freeze(materials));
    }

    private static Map<String, List<ItemStack>> orderedGroups(List<String> keys) {
        Map<String, List<ItemStack>> result = new LinkedHashMap<>();
        for (String key : keys) result.put(key, new ArrayList<>());
        return result;
    }

    private static Map<String, List<ItemStack>> freeze(Map<String, List<ItemStack>> groups) {
        Map<String, List<ItemStack>> result = new LinkedHashMap<>();
        groups.forEach((key, values) -> {
            if (!values.isEmpty()) result.put(key, List.copyOf(values));
        });
        return Collections.unmodifiableMap(result);
    }

    public record Groups(List<ItemStack> ordered, Map<String, List<ItemStack>> designs,
                         Map<String, List<ItemStack>> forms, Map<String, List<ItemStack>> materials) { }

    private static final class Holder {
        private static final Groups GROUPS = group(DaedalonSearchVocabulary.index(), ModItems.catalogueItems());
    }
}
