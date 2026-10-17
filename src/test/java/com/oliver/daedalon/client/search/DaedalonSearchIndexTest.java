package com.oliver.daedalon.client.search;

import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DaedalonSearchIndexTest {
    private static final DaedalonSearchIndex INDEX = DaedalonSearchIndex.read(new StringReader("""
            {
              "materials": {
                "aganite": ["aganite", "gemstone", "agate"],
                "bronze": ["bronze", "metal", "metallic"],
                "silver": ["silver", "metal", "metallic"]
              },
              "families": {
                "exedra": ["bench", "seat", "seating"],
                "diota": ["urn", "vase", "vessel"],
                "spartan_promachos_statue": ["statue", "sculpture", "spartan", "promachos"],
                "plinth_georgian": ["plinth", "pedestal", "georgian"],
                "byzantine_capital": ["capital", "byzantine", "guilloche", "column_capital"]
              }
            }
            """));

    @Test
    void stoneDecorCombinesMaterialAndPracticalFamilyTerms() {
        List<String> terms = INDEX.terms("aganite_exedra");
        assertTrue(terms.containsAll(List.of("daedalon", "aganite", "gemstone", "agate", "bench", "seat")));
        assertFalse(terms.contains("metal"));
        assertFalse(terms.contains("aged"));
        assertFalse(terms.contains("weathered"));
        assertTrue(INDEX.terms("aganite_diota").containsAll(List.of("urn", "vase", "vessel")));
    }

    @Test
    void canonicalAgedPrefixPreservesFamilyAndAddsWeathering() {
        List<String> terms = INDEX.terms("aganite_aged_spartan_promachos_statue");
        assertTrue(terms.containsAll(List.of("aged", "weathered", "statue", "sculpture", "spartan", "promachos")));
        assertFalse(INDEX.terms("aganite_spartan_promachos_statue").contains("aged"));
        assertTrue(INDEX.terms("aganite_spartan_promachos_statue_aged").isEmpty());
    }

    @Test
    void publishedStoneStatuesAndModernMetalStatuesShareSearchVocabulary() {
        assertEquals(INDEX.terms("aganite_spartan_promachos_statue"),
                INDEX.terms("statue_spartan_promachos_aganite"));
        assertEquals(INDEX.terms("aganite_aged_spartan_promachos_statue"),
                INDEX.terms("statue_spartan_promachos_aganite_aged"));
        assertTrue(INDEX.terms("bronze_spartan_promachos_statue")
                .containsAll(List.of("bronze", "metal", "statue", "sculpture")));
        for (String path : List.of("statue_spartan_promachos_unknown", "statue_spartan_promachos_aganite_extra",
                "statue_spartan_promachos_aganite_aged_extra", "statue_spartan_promachos_aganite_aged_aged")) {
            assertTrue(INDEX.terms(path).isEmpty(), path);
        }
    }

    @Test
    void metalPlinthsRemainDecorativeWithoutFountainAliases() {
        for (String material : List.of("bronze", "silver")) {
            List<String> terms = INDEX.terms(material + "_plinth_georgian");
            assertTrue(terms.containsAll(List.of(material, "metal", "metallic", "plinth", "pedestal")));
            assertFalse(terms.contains("gemstone"));
            assertFalse(terms.contains("fountain"));
            assertFalse(terms.contains("water feature"));
        }
    }

    @Test
    void normalizesWordSeparatorsAndRemovesRepeatedKeys() {
        List<String> terms = INDEX.terms("aganite_byzantine_capital");
        assertTrue(terms.containsAll(List.of("byzantine", "guilloche", "column capital", "byzantine capital")));
        assertFalse(terms.stream().anyMatch(term -> term.contains("_")));
        assertEquals(1, terms.stream().filter("aganite"::equals).count());
    }

    @Test
    void foreignItemsAndNonCatalogueHelpersDoNotAcquireAliases() {
        assertTrue(INDEX.terms("erydon", "aganite_exedra").isEmpty());
        assertEquals(INDEX.terms("aganite_exedra"), INDEX.terms("daedalon", "aganite_exedra"));
        for (String path : List.of("emblem", "internal_aganite_exedra", "aganite_internal_exedra",
                "aganite_fountain_basin_part", "unknown_exedra", "aganite_aged_aged_exedra", "aganite_")) {
            assertTrue(INDEX.terms(path).isEmpty(), path);
        }
        assertTrue(INDEX.terms((String) null).isEmpty());
    }

    @Test
    void bundledMetadataCoversRealStoneMetalAndLegacyFamilies() throws Exception {
        try (var input = getClass().getResourceAsStream("/assets/daedalon/search_vocabulary.json")) {
            assertNotNull(input, "Bundled search vocabulary is missing");
            DaedalonSearchIndex bundled = DaedalonSearchIndex.read(
                    new InputStreamReader(input, StandardCharsets.UTF_8));
            assertTrue(bundled.terms("aganite_exedra").containsAll(List.of("bench", "seat")));
            assertTrue(bundled.terms("bronze_diota_urn").containsAll(List.of("metal", "vase")));
            assertTrue(bundled.terms("aganite_byzantine_capital").containsAll(List.of("byzantine", "guilloche")));
            assertTrue(bundled.terms("statue_spartan_promachos_aganite_aged")
                    .containsAll(List.of("statue", "sculpture", "aged", "weathered")));
            List<String> metalPlinth = bundled.terms("silver_astragalos_plinth");
            assertTrue(metalPlinth.containsAll(List.of("metal", "pedestal")));
            assertFalse(metalPlinth.contains("stone"));
            assertFalse(metalPlinth.contains("fountain"));
        }
    }
}
