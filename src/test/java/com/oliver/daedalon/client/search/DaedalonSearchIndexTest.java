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

    @Test
    void greekAndRomanGodNamesCoverBothFormsAndEveryMaterialClass() throws Exception {
        try (var input = getClass().getResourceAsStream("/assets/daedalon/search_vocabulary.json")) {
            assertNotNull(input, "Bundled search vocabulary is missing");
            DaedalonSearchIndex bundled = DaedalonSearchIndex.read(
                    new InputStreamReader(input, StandardCharsets.UTF_8));
            String[][] equivalents = {
                    {"zeus", "jupiter", "jove"}, {"hera", "juno"},
                    {"poseidon", "neptune"}, {"demeter", "ceres"},
                    {"athena", "minerva"}, {"apollo", "apollon"},
                    {"artemis", "diana"}, {"ares", "mars"},
                    {"aphrodite", "venus"}, {"hephaestus", "vulcan"},
                    {"hermes", "mercury"}, {"dionysus", "bacchus", "liber", "liber pater"},
                    {"hestia", "vesta"}
            };
            for (String[] names : equivalents) {
                for (String material : List.of("aganite", "aganite_aged", "bronze", "silver")) {
                    for (String form : List.of("statue", "bust")) {
                        String path = material + "_" + names[0] + "_" + form;
                        List<String> terms = bundled.terms(path);
                        assertTrue(terms.containsAll(List.of(names)), path + " -> " + terms);
                        assertTrue(terms.containsAll(List.of("greek", "olympian", "god", "deity")), path);
                    }
                }
            }
            assertTrue(bundled.terms("silver_helios_statue").contains("sol"));
            assertTrue(bundled.terms("aganite_heracles_statue").contains("hercules"));
            assertTrue(bundled.terms("bronze_phaeton_statue").contains("phaethon"));
            assertFalse(bundled.terms("aganite_apollo_statue").contains("sol"));
            assertFalse(bundled.terms("bronze_phaeton_statue").contains("sol"));
            assertFalse(bundled.terms("bronze_phaeton_statue").contains("deity"));
        }
    }

    @Test
    void catalogueDesignsKeepAllFinishesTogetherAndFormsSeparate() throws Exception {
        try (var input = getClass().getResourceAsStream("/assets/daedalon/search_vocabulary.json")) {
            assertNotNull(input);
            DaedalonSearchIndex bundled = DaedalonSearchIndex.read(
                    new InputStreamReader(input, StandardCharsets.UTF_8));
            for (String material : List.of("aganite", "aganite_aged", "bronze", "silver")) {
                assertEquals("zeus_statue", bundled.entry(material + "_zeus_statue").family());
                assertEquals("zeus_bust", bundled.entry(material + "_zeus_bust").family());
            }
            assertEquals("spartan_promachos_statue", bundled.entry("statue_spartan_promachos_aganite_aged").family());
            assertEquals("aganite", bundled.entry("statue_spartan_promachos_aganite_aged").material());
            assertTrue(bundled.entry("statue_spartan_promachos_aganite_aged").aged());
            for (String family : bundled.families()) {
                assertTrue(DaedalonCatalogue.FORMS.contains(DaedalonCatalogue.form(family)), family);
            }
            assertEquals("statues", DaedalonCatalogue.form("zeus_statue"));
            assertEquals("busts", DaedalonCatalogue.form("zeus_bust"));
            assertEquals("basins", DaedalonCatalogue.form("gothic_fountain_basin"));
            assertEquals("fountains", DaedalonCatalogue.form("pege_fountain"));
            assertEquals("benches", DaedalonCatalogue.form("exedra"));
        }
    }

    @Test
    void plainWhiteAndPreciousMetalArchitectureHaveAccurateSearchVocabulary() throws Exception {
        try (var input = getClass().getResourceAsStream("/assets/daedalon/search_vocabulary.json")) {
            assertNotNull(input);
            DaedalonSearchIndex bundled = DaedalonSearchIndex.read(
                    new InputStreamReader(input, StandardCharsets.UTF_8));
            for (String family : List.of("byzantine_frieze", "gothic_frieze", "corinthian_frieze",
                    "ionic_frieze", "gothic_wall_panel")) {
                for (String finish : List.of("white", "bronze", "gold")) {
                    String path = finish + "_" + family;
                    assertEquals(family, bundled.entry(path).family());
                    assertEquals(finish, bundled.entry(path).material());
                    List<String> terms = bundled.terms(path);
                    assertTrue(terms.contains(finish), path);
                    assertEquals(!finish.equals("white"), terms.contains("metal"), path);
                    assertFalse(terms.contains("aged"), path);
                    assertFalse(terms.contains("weathered"), path);
                    assertFalse(terms.contains("daedalon blocks"), path);
                }
            }
        }
    }
}
