package com.oliver.daedalon.compat.rei;

import com.google.gson.JsonParser;
import com.oliver.daedalon.client.search.DaedalonCatalogue;
import com.oliver.daedalon.client.search.DaedalonSearchIndex;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

final class DaedalonReiGroupsTest {
    private static final Set<String> UNIFIED = Set.of(
            "capitals", "lanterns", "friezes", "panels", "corbels", "finials", "plinths",
            "urns", "benches", "fountains", "statues");

    @Test void everyBundledStyleAndFinishAppearsOnceInItsOneFamilyGroup() throws Exception {
        var catalogue = bundledCatalogue();
        var groups = DaedalonReiGroups.plan(catalogue.designs, catalogue.forms);
        var byKey = groups.stream().collect(Collectors.toMap(DaedalonReiGroups.Group::key, Function.identity()));
        assertEquals(groups.size(), byKey.size());
        assertEquals(UNIFIED, groups.stream().filter(DaedalonReiGroups.Group::form)
                .map(DaedalonReiGroups.Group::key).collect(Collectors.toSet()));
        Set<String> grouped = new HashSet<>();
        for (var group : groups) for (String entry : group.entries())
            assertTrue(grouped.add(entry), "Overlapping REI groups: " + entry);
        Set<String> expected = catalogue.designs.entrySet().stream()
                .filter(design -> design.getValue().size() > 1 || UNIFIED.contains(expectedGroup(design.getKey())))
                .flatMap(design -> design.getValue().stream()).collect(Collectors.toSet());
        assertEquals(expected, grouped, "Grouping must not remove or invent catalogue entries");
        for (String form : UNIFIED) {
            var group = byKey.get(form);
            assertTrue(group.form());
            List<String> formEntries = new ArrayList<>(catalogue.forms.get(form));
            if (form.equals("fountains")) formEntries.addAll(catalogue.forms.get("basins"));
            assertEquals(formEntries, group.entries(), "Complete family and member order: " + form);
            for (var design : catalogue.designs.entrySet()) {
                if (expectedGroup(design.getKey()).equals(form)) {
                    assertFalse(byKey.containsKey(design.getKey()), "Old style group must not overlap: " + design.getKey());
                    assertTrue(group.entries().containsAll(design.getValue()));
                }
            }
        }
        for (var design : catalogue.designs.entrySet()) {
            if (!UNIFIED.contains(expectedGroup(design.getKey())) && design.getValue().size() > 1) {
                assertFalse(byKey.get(design.getKey()).form());
                assertEquals(design.getValue(), byKey.get(design.getKey()).entries(), "Unrelated design changed");
            }
        }
        assertTrue(byKey.get("statues").entries().containsAll(catalogue.designs.get("olympian_god_statue")));
        assertEquals(Set.of("exedra", "hedra"), byKey.get("benches").entries().stream()
                .map(path -> catalogue.index.entry(path).family()).collect(Collectors.toSet()));
        assertFalse(byKey.containsKey("basins"));
        assertThrows(UnsupportedOperationException.class, () -> groups.clear());
        assertThrows(UnsupportedOperationException.class, () -> byKey.get("panels").entries().clear());
    }

    @Test void everyRegisteredFamilyLabelMatchesAllItsMembersInTheItemIndex() throws Exception {
        var catalogue = bundledCatalogue();
        var groups = DaedalonReiGroups.plan(catalogue.designs, catalogue.forms);
        for (var group : groups) {
            if (!group.form()) continue;
            for (String path : group.entries()) {
                assertTrue(catalogue.index.terms(path).contains(group.key()),
                        () -> group.key() + " cannot find " + path);
            }
        }
        assertTrue(catalogue.index.terms("bronze_zeus_bust").containsAll(List.of("statue", "statues", "busts", "olympian")));
        assertFalse(catalogue.index.terms("glacium_exedra").contains("urns"));
        assertFalse(catalogue.index.terms("bronze_diota_urn").contains("benches"));
    }

    @Test void sizeMountAndFinishVariantsKeepTheirOriginalObjectsAndOrder() {
        record Variant(String item, String state) { }
        var small = new Variant("glacium_gothic_lantern", "size=small,mount=pier");
        var large = new Variant("glacium_gothic_lantern", "size=large,mount=wall");
        var hanging = new Variant("bronze_georgian_lantern", "size=large,mount=hanging");
        var white = new Variant("white_gothic_wall_panel", "width=3,mount=ceiling");
        var black = new Variant("black_gothic_wall_panel", "width=2,mount=wall");
        var nextDesign = new Variant("black_future_wall_panel", "width=1,mount=wall");
        var smallBasin = new Variant("aganite_louterion_basin", "");
        var largeBasin = new Variant("aganite_louterion_basin", "size=large");
        var fountain = new Variant("aganite_pege_fountain", "");
        var designs = new LinkedHashMap<String, List<Variant>>();
        designs.put("gothic_lantern", List.of(small, large));
        designs.put("georgian_lantern", List.of(hanging));
        designs.put("gothic_wall_panel", List.of(white, black));
        designs.put("future_wall_panel", List.of(nextDesign));
        designs.put("louterion_basin", List.of(smallBasin, largeBasin));
        designs.put("pege_fountain", List.of(fountain));
        List<Variant> lanterns = List.of(hanging, small, large);
        List<Variant> panels = List.of(white, black, nextDesign);
        var groups = DaedalonReiGroups.plan(designs, Map.of("lanterns", lanterns, "panels", panels,
                "basins", List.of(smallBasin, largeBasin), "fountains", List.of(fountain)));
        assertEquals(List.of("lanterns", "panels", "fountains"), groups.stream().map(DaedalonReiGroups.Group::key).toList());
        for (int i = 0; i < lanterns.size(); i++) assertSame(lanterns.get(i), groups.get(0).entries().get(i));
        for (int i = 0; i < panels.size(); i++) assertSame(panels.get(i), groups.get(1).entries().get(i));
        List<Variant> fountains = groups.get(2).entries();
        assertEquals(3, fountains.size());
        assertSame(fountain, fountains.get(0));
        assertSame(smallBasin, fountains.get(1));
        assertSame(largeBasin, fountains.get(2));
        assertEquals(List.of(small, large), designs.get("gothic_lantern"), "Input lists must stay untouched");
    }

    @Test void filteredStyleSearchesKeepOnlyMatchesAndTheirFreshGlaciumRepresentative() throws Exception {
        var catalogue = bundledCatalogue();
        var groups = DaedalonReiGroups.plan(catalogue.designs, catalogue.forms);
        for (var group : groups) {
            if (!group.form()) continue;
            for (String family : catalogue.designs.keySet()) {
                if (!expectedGroup(family).equals(group.key())) continue;
                List<String> expected = catalogue.designs.get(family);
                List<String> matches = group.entries().stream()
                        .filter(path -> catalogue.index.entry(path).family().equals(family)).toList();
                assertEquals(expected, matches, "Style search lost variants: " + family);
                if (matches.size() == 1) continue; // Glaux has one fixed bronze item.
                List<String> displayed = new ArrayList<>();
                for (String path : matches) {
                    displayed.add(path);
                    DaedalonCatalogue.preferAppendedGlacium(catalogue.index, displayed, Function.identity());
                }
                assertEquals(new HashSet<>(expected), new HashSet<>(displayed));
                var first = catalogue.index.entry(displayed.get(0));
                assertEquals(family, first.family());
                assertEquals("glacium", first.material());
                assertFalse(first.aged());
                List<String> specialOnly = group.entries().stream().filter(path -> {
                    var entry = catalogue.index.entry(path);
                    return entry.family().equals(family) && Set.of("white", "black", "bronze", "silver").contains(entry.material());
                }).toList();
                List<String> filtered = new ArrayList<>();
                for (String path : specialOnly) {
                    filtered.add(path);
                    DaedalonCatalogue.preferAppendedGlacium(catalogue.index, filtered, Function.identity());
                }
                assertEquals(specialOnly, filtered, "A finish-only search must not acquire stone variants");
            }
        }
    }

    private static String expectedGroup(String design) {
        String form = DaedalonCatalogue.form(design);
        return form.equals("basins") ? "fountains" : form;
    }

    private Catalogue bundledCatalogue() throws Exception {
        try (var vocabulary = getClass().getResourceAsStream("/assets/daedalon/search_vocabulary.json");
             var inventory = getClass().getResourceAsStream("/data/daedalon/tags/items/daedalon_items.json")) {
            assertNotNull(vocabulary);
            assertNotNull(inventory);
            var index = DaedalonSearchIndex.read(new InputStreamReader(vocabulary, StandardCharsets.UTF_8));
            Map<String, List<String>> designs = new LinkedHashMap<>(), forms = new LinkedHashMap<>();
            JsonParser.parseReader(new InputStreamReader(inventory, StandardCharsets.UTF_8)).getAsJsonObject()
                    .getAsJsonArray("values").forEach(value -> {
                        String id = value.getAsString();
                        assertTrue(id.startsWith("daedalon:"));
                        String path = id.substring("daedalon:".length());
                        var entry = index.entry(path);
                        assertNotNull(entry, path);
                        designs.computeIfAbsent(entry.family(), ignored -> new ArrayList<>()).add(path);
                        forms.computeIfAbsent(DaedalonCatalogue.form(entry.family()), ignored -> new ArrayList<>()).add(path);
                    });
            return new Catalogue(index, designs, forms);
        }
    }

    private record Catalogue(DaedalonSearchIndex index, Map<String, List<String>> designs,
                             Map<String, List<String>> forms) { }
}
