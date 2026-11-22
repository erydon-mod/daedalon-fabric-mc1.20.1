package com.oliver.daedalon.compat.rei;

import com.oliver.daedalon.client.search.DaedalonCatalogue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

/** Registration-only family grouping; search and the underlying catalogue remain unchanged. */
final class DaedalonReiGroups {
    private static final Set<String> UNIFIED_FORMS = Set.of(
            "capitals", "lanterns", "friezes", "panels", "corbels", "finials", "plinths",
            "urns", "benches", "fountains", "statues");

    private DaedalonReiGroups() { }

    static <T> List<Group<T>> plan(Map<String, List<T>> designs, Map<String, List<T>> forms) {
        List<Group<T>> result = new ArrayList<>();
        Set<String> emitted = new HashSet<>();
        designs.forEach((design, members) -> {
            String form = DaedalonCatalogue.form(design);
            // Fountain basins share the Fountains group, including Louterion.
            if (form.equals("basins")) form = "fountains";
            boolean unified = UNIFIED_FORMS.contains(form);
            String key = unified ? form : design;
            if (unified && !emitted.add(form)) return;
            List<T> entries = members;
            if (unified) {
                entries = form.equals("fountains")
                        ? Stream.concat(forms.getOrDefault("fountains", List.of()).stream(),
                                forms.getOrDefault("basins", List.of()).stream()).toList()
                        : Objects.requireNonNull(forms.get(form), "Missing catalogue form: " + form);
            }
            if (entries.size() > 1) result.add(new Group<>(key, unified, List.copyOf(entries)));
        });
        return List.copyOf(result);
    }

    record Group<T>(String key, boolean form, List<T> entries) { }
}
