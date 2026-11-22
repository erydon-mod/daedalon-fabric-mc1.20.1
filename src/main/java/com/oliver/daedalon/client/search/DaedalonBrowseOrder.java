package com.oliver.daedalon.client.search;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/** Reload-only ordering of existing catalogue entries, independent of Minecraft or REI objects. */
public final class DaedalonBrowseOrder {
    private static final String PREFIX = "daedalon:";
    private static final List<String> SPECIAL_FINISHES = List.of("white", "black", "bronze", "silver");
    private static final Comparator<SortKey> KEY_ORDER = Comparator
            .comparingInt(SortKey::form)
            .thenComparing(SortKey::design)
            .thenComparingInt(SortKey::sculptureForm)
            .thenComparingInt(SortKey::material)
            .thenComparing(SortKey::aged);

    private DaedalonBrowseOrder() { }

    /**
     * Selects recognised Daedalon entries and retains their original objects.
     * Equal-ID variants keep their input order. Unrelated entries are never selected.
     */
    public static <T> Order<T> catalogue(DaedalonSearchIndex index, List<T> entries,
                                        Function<T, String> identifier) {
        Map<String, FamilyKey> families = new HashMap<>();
        Map<String, Integer> materials = new HashMap<>();
        for (String material : index.materials()) {
            if (!SPECIAL_FINISHES.contains(material)) {
                materials.put(material, materials.size());
            }
        }
        for (String finish : SPECIAL_FINISHES) {
            materials.put(finish, materials.size());
        }

        List<Candidate<T>> candidates = new ArrayList<>();
        Set<T> members = Collections.newSetFromMap(new IdentityHashMap<>());
        for (T entry : entries) {
            String id = identifier.apply(entry);
            if (id == null || !id.startsWith(PREFIX)) continue;
            var metadata = index.entry(id.substring(PREFIX.length()));
            if (metadata == null) continue;
            FamilyKey family = families.computeIfAbsent(metadata.family(), DaedalonBrowseOrder::familyKey);
            candidates.add(new Candidate<>(entry, new SortKey(family.form(), family.design(),
                    family.sculptureForm(), materials.get(metadata.material()), metadata.aged())));
            members.add(entry);
        }
        List<T> original = candidates.stream().map(Candidate::value).toList();
        // Sort keys are computed once above, rather than parsing IDs in comparator calls.
        candidates.sort(Comparator.comparing(Candidate<T>::key, KEY_ORDER));
        List<T> ordered = candidates.stream().map(Candidate::value).toList();
        boolean changed = false;
        for (int i = 0; i < ordered.size(); i++) {
            if (ordered.get(i) != original.get(i)) { changed = true; break; }
        }
        return new Order<>(ordered, Collections.unmodifiableSet(members), changed);
    }

    private static FamilyKey familyKey(String family) {
        if (family.equals("olympian_god_statue")) return new FamilyKey(0, "", 0);
        String form = DaedalonCatalogue.form(family);
        if (form.equals("statues") || form.equals("busts")) {
            String suffix = form.equals("statues") ? "_statue" : "_bust";
            return new FamilyKey(0, family.endsWith(suffix) ? family.substring(0, family.length() - suffix.length()) : family,
                    form.equals("statues") ? 0 : 1);
        }
        return new FamilyKey(DaedalonCatalogue.FORMS.indexOf(form) - 1, family, 0);
    }

    private record FamilyKey(int form, String design, int sculptureForm) { }
    private record SortKey(int form, String design, int sculptureForm, int material, boolean aged) { }
    private record Candidate<T>(T value, SortKey key) { }

    /** The identity set lets the adapter remove exactly the original selected entries. */
    public record Order<T>(List<T> entries, Set<T> members, boolean changed) { }
}
