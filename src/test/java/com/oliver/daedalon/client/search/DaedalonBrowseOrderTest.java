package com.oliver.daedalon.client.search;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

final class DaedalonBrowseOrderTest {
    private static final DaedalonSearchIndex INDEX = DaedalonSearchIndex.read(new StringReader("""
            {"materials":{"aganite":[],"glacium":[],"silver":[],"white":[],"black":[],"bronze":[]},
             "families":{"zeus_bust":[],"zeus_statue":[],"aphrodite_bust":[],"aphrodite_statue":[],
                         "olympian_god_statue":[],"kylix_urn":[],"amphora_urn":[],
                         "ionic_capital":[],"baroque_corbel":[]}}
            """));

    @Test
    void subjectsKeepTheirStatuesAndBustsTogetherBeforeTheOtherForms() {
        List<String> source = List.of("silver_zeus_bust", "glacium_kylix_urn", "aganite_zeus_statue",
                "glacium_baroque_corbel", "glacium_aphrodite_bust", "glacium_ionic_capital",
                "bronze_olympian_god_statue", "glacium_amphora_urn", "glacium_aphrodite_statue");
        var order = DaedalonBrowseOrder.catalogue(INDEX, source, id -> "daedalon:" + id);
        assertEquals(List.of("bronze_olympian_god_statue", "glacium_aphrodite_statue",
                "glacium_aphrodite_bust", "aganite_zeus_statue", "silver_zeus_bust",
                "glacium_amphora_urn", "glacium_kylix_urn", "glacium_ionic_capital",
                "glacium_baroque_corbel"), order.entries());
        assertTrue(order.changed());
    }

    @Test
    void freshAndAgedStonePairsPrecedeWhiteBlackBronzeAndSilverWithoutReplacingNbtVariants() {
        Entry named = new Entry("daedalon:aganite_zeus_statue", "named statue");
        Entry sized = new Entry("daedalon:aganite_zeus_statue", "large offset statue");
        Entry aged = new Entry("daedalon:aganite_aged_zeus_statue", "");
        Entry glacium = new Entry("daedalon:glacium_zeus_statue", "");
        Entry white = new Entry("daedalon:white_zeus_statue", "");
        Entry black = new Entry("daedalon:black_zeus_statue", "");
        Entry bronze = new Entry("daedalon:bronze_zeus_statue", "");
        Entry silver = new Entry("daedalon:silver_zeus_statue", "");
        var order = DaedalonBrowseOrder.catalogue(INDEX,
                List.of(silver, black, white, aged, glacium, named, bronze, sized), Entry::id);
        List<Entry> expected = List.of(named, sized, aged, glacium, white, black, bronze, silver);
        for (int i = 0; i < expected.size(); i++) assertSame(expected.get(i), order.entries().get(i));
        assertFalse(order.members().contains(new Entry(named.id(), named.nbt())), "Removal must use identity");
    }

    @Test
    void eachBundledFriezeKeepsItsCompleteFinishRunAndPlainWhiteBlackPair() throws Exception {
        try (var vocabulary = getClass().getResourceAsStream("/assets/daedalon/search_vocabulary.json");
             var inventory = getClass().getResourceAsStream("/data/daedalon/tags/items/daedalon_items.json")) {
            assertNotNull(vocabulary);
            assertNotNull(inventory);
            var index = DaedalonSearchIndex.read(new InputStreamReader(vocabulary, StandardCharsets.UTF_8));
            List<String> source = new ArrayList<>();
            JsonParser.parseReader(new InputStreamReader(inventory, StandardCharsets.UTF_8)).getAsJsonObject()
                    .getAsJsonArray("values").forEach(value -> source.add(value.getAsString()));
            var ordered = DaedalonBrowseOrder.catalogue(index, source, id -> id).entries();
            for (String family : List.of("byzantine_frieze", "corinthian_frieze", "gothic_frieze", "ionic_frieze")) {
                List<String> expected = source.stream().filter(id ->
                        index.entry(id.substring("daedalon:".length())).family().equals(family)).toList();
                List<String> actual = ordered.stream().filter(id ->
                        index.entry(id.substring("daedalon:".length())).family().equals(family)).toList();
                assertEquals(58, expected.size(), family);
                assertEquals(new HashSet<>(expected), new HashSet<>(actual), family);
                int start = ordered.indexOf(actual.get(0));
                assertEquals(actual, ordered.subList(start, start + actual.size()), family);
                assertEquals(List.of("daedalon:white_" + family, "daedalon:black_" + family,
                                "daedalon:bronze_" + family, "daedalon:silver_" + family),
                        actual.subList(actual.size() - 4, actual.size()), family);
                assertEquals("friezes", DaedalonCatalogue.form(family));
            }
        }
    }

    @Test
    void onlyRecognisedCatalogueObjectsMoveAndAnAlreadyOrderedSelectionIsLeftAlone() {
        Entry vanilla = new Entry("minecraft:glacium_zeus_statue", "foreign namespace");
        Entry emblem = new Entry("daedalon:emblem", "");
        Entry unknown = new Entry("daedalon:glacium_future_unknown", "");
        Entry zeus = new Entry("daedalon:glacium_zeus_statue", "saved block state");
        Entry aphrodite = new Entry("daedalon:glacium_aphrodite_statue", "");
        List<Entry> source = List.of(vanilla, zeus, emblem, aphrodite, unknown);
        var order = DaedalonBrowseOrder.catalogue(INDEX, source, Entry::id);
        assertEquals(List.of(aphrodite, zeus), order.entries());
        List<Entry> retained = new ArrayList<>(source);
        retained.removeIf(order.members()::contains);
        assertEquals(List.of(vanilla, emblem, unknown), retained, "Unowned relative order must stay intact");
        retained.addAll(order.entries());
        assertEquals(source.size(), retained.size());
        for (Entry entry : source) assertTrue(retained.stream().anyMatch(result -> result == entry));
        var unchanged = DaedalonBrowseOrder.catalogue(INDEX,
                List.of(emblem, aphrodite, vanilla, zeus, unknown), Entry::id);
        assertFalse(unchanged.changed(), "Do not rewrite an already ordered catalogue selection");
    }

    @Test
    void everyBundledDesignRetainsAllItsItemsAndPairsItsStatueAndBustSubjects() throws Exception {
        try (var vocabulary = getClass().getResourceAsStream("/assets/daedalon/search_vocabulary.json");
             var inventory = getClass().getResourceAsStream("/data/daedalon/tags/items/daedalon_items.json")) {
            assertNotNull(vocabulary);
            assertNotNull(inventory);
            var index = DaedalonSearchIndex.read(new InputStreamReader(vocabulary, StandardCharsets.UTF_8));
            List<String> source = new ArrayList<>();
            JsonParser.parseReader(new InputStreamReader(inventory, StandardCharsets.UTF_8)).getAsJsonObject()
                    .getAsJsonArray("values").forEach(value -> source.add(value.getAsString()));
            var order = DaedalonBrowseOrder.catalogue(index, source, id -> id);
            assertEquals(source.size(), order.entries().size());
            assertEquals(new HashSet<>(source), new HashSet<>(order.entries()));
            assertFalse(DaedalonBrowseOrder.catalogue(index, order.entries(), id -> id).changed());
            List<String> families = order.entries().stream()
                    .map(id -> index.entry(id.substring("daedalon:".length())).family()).toList();
            assertEquals(new HashSet<>(index.families()), new HashSet<>(families));
            int pairedSubjects = 0;
            for (String family : index.families()) {
                if (!family.endsWith("_statue")) continue;
                String bust = family.substring(0, family.length() - "_statue".length()) + "_bust";
                if (!index.families().contains(bust)) continue;
                assertEquals(families.lastIndexOf(family) + 1, families.indexOf(bust), family);
                pairedSubjects++;
            }
            assertEquals(13, pairedSubjects);
        }
    }

    private record Entry(String id, String nbt) { }
}
