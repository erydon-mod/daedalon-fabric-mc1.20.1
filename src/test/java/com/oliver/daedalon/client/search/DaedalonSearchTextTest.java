package com.oliver.daedalon.client.search;

import net.minecraft.client.search.SuffixArray;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

final class DaedalonSearchTextTest {
    @Test
    void realVanillaTextIndexFindsBothModSpellingsAndSpecificAliasesWithoutBroadBlockNoise() throws Exception {
        try (var input = getClass().getResourceAsStream("/assets/daedalon/search_vocabulary.json")) {
            assertNotNull(input);
            var vocabulary = DaedalonSearchIndex.read(new InputStreamReader(input, StandardCharsets.UTF_8));
            List<String> decor = List.of("aganite_hedra", "aganite_zeus_statue",
                    "statue_spartan_promachos_aganite_aged", "silver_gothic_wall_panel");
            SuffixArray<String> index = new SuffixArray<>();
            for (String path : decor) {
                String cached = DaedalonSearchVocabulary.appendTerms("existing family text", vocabulary.terms(path));
                assertTrue(cached.startsWith("existing family text"), path);
                index.add(path, cached);
            }
            String foreign = DaedalonSearchVocabulary.appendTerms("Quartz Block",
                    vocabulary.terms("minecraft", "aganite_hedra"));
            assertEquals("Quartz Block", foreign);
            index.add("minecraft:quartz_block", foreign.toLowerCase(java.util.Locale.ROOT));
            index.build();
            assertEquals(Set.copyOf(decor), Set.copyOf(index.findAll("daedalon")));
            assertEquals(Set.copyOf(decor), Set.copyOf(index.findAll("deadalon")));
            assertEquals(List.of("aganite_zeus_statue"), index.findAll("jupiter"));
            assertEquals(List.of("aganite_hedra"), index.findAll("bench"));
            assertEquals(List.of("minecraft:quartz_block"), index.findAll("block"));
        }
    }

    @Test
    void realTextIndexFindsAllFinishWordsOnlyForFreshNamedStone() {
        var vocabulary = DaedalonSearchVocabulary.index();
        SuffixArray<String> index = new SuffixArray<>();
        for (String path : List.of("glacium_hedra", "statue_spartan_promachos_glacium",
                "glacium_aged_hedra", "white_gothic_wall_panel", "bronze_zeus_statue", "silver_gothic_frieze")) {
            index.add(path, DaedalonSearchVocabulary.appendTerms("", vocabulary.terms(path)));
        }
        index.build();
        for (String word : List.of("honed", "polished", "mirror")) {
            assertEquals(Set.of("glacium_hedra", "statue_spartan_promachos_glacium"),
                    Set.copyOf(index.findAll(word)), word);
        }
    }

    @Test
    void composingWithAnAlreadyReturnedForeignModResultRetainsBothSetsOfTerms() {
        String delegated = "base name\u0000another mod alias";
        String result = DaedalonSearchVocabulary.appendTerms(delegated, List.of("Daedalon", "Deadalon", "Jupiter"));
        assertTrue(result.contains("another mod alias"));
        assertTrue(result.contains("daedalon"));
        assertTrue(result.contains("deadalon"));
        assertTrue(result.contains("jupiter"));
        assertFalse(result.contains("block"));
        assertEquals(delegated, DaedalonSearchVocabulary.appendTerms(delegated, List.of()));
    }
}
