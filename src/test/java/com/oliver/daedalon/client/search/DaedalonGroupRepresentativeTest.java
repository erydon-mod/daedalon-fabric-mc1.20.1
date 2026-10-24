package com.oliver.daedalon.client.search;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

final class DaedalonGroupRepresentativeTest {
    @Test
    void everyRealDesignUsesItsFreshGlaciumMemberWithoutChangingMembershipOrOtherOrder() throws Exception {
        DaedalonSearchIndex index = bundledIndex();
        List<String> registryOrder;
        try (var input = getClass().getResourceAsStream("/data/daedalon/tags/items/daedalon_items.json")) {
            assertNotNull(input);
            var root = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
            registryOrder = new ArrayList<>();
            root.getAsJsonArray("values").forEach(value ->
                    registryOrder.add(value.getAsString().substring("daedalon:".length())));
        }
        List<String> originalRegistryOrder = List.copyOf(registryOrder);
        Map<String, List<String>> designs = new LinkedHashMap<>();
        for (String path : registryOrder) {
            var entry = index.entry(path);
            assertNotNull(entry, path);
            designs.computeIfAbsent(entry.family(), ignored -> new ArrayList<>()).add(path);
        }
        assertEquals(85, designs.size());
        for (var design : designs.entrySet()) {
            List<String> source = List.copyOf(design.getValue());
            List<String> preferred = source.stream().filter(path -> {
                var entry = index.entry(path);
                return entry.material().equals("glacium") && !entry.aged();
            }).toList();
            assertEquals(1, preferred.size(), design.getKey());
            List<String> collapsed = new ArrayList<>();
            // Reproduce native group construction: one member is appended at a time.
            for (String path : source) {
                collapsed.add(path);
                DaedalonCatalogue.preferAppendedGlacium(index, collapsed, Function.identity());
            }
            String representative = preferred.get(0);
            assertEquals(representative, collapsed.get(0), design.getKey());
            List<String> expected = new ArrayList<>(source);
            expected.remove(representative);
            expected.add(0, representative);
            assertEquals(expected, collapsed, design.getKey());
            assertEquals(source, design.getValue(), "The input catalogue order must remain unchanged");
            if (design.getKey().equals("spartan_promachos_statue")) {
                assertEquals("statue_spartan_promachos_glacium", collapsed.get(0));
            }
        }
        assertEquals(originalRegistryOrder, registryOrder);
        assertEquals(4734, registryOrder.size());
    }

    @Test
    void filteredGroupsNeverAcquireAnExcludedMaterialOrChooseAgedGlacium() throws Exception {
        DaedalonSearchIndex index = bundledIndex();
        for (List<String> source : List.of(
                List.of("bronze_zeus_statue", "silver_zeus_statue"),
                List.of("aganite_aged_zeus_statue", "glacium_aged_zeus_statue"),
                List.of("statue_spartan_promachos_aganite_aged", "statue_spartan_promachos_glacium_aged"),
                List.of("aganite_exedra", "glacium_zeus_statue"),
                List.of("erydon:aganite_exedra", "glacium_exedra"))) {
            List<String> collapsed = new ArrayList<>(source);
            assertFalse(DaedalonCatalogue.preferAppendedGlacium(index, collapsed, Function.identity()), source.toString());
            assertEquals(source, collapsed);
        }
    }

    private DaedalonSearchIndex bundledIndex() throws Exception {
        try (var input = getClass().getResourceAsStream("/assets/daedalon/search_vocabulary.json")) {
            assertNotNull(input);
            return DaedalonSearchIndex.read(new InputStreamReader(input, StandardCharsets.UTF_8));
        }
    }
}
