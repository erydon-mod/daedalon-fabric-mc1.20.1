package com.oliver.daedalon.client.search;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

final class DaedalonSearchQueryRulesTest {
    @Test
    void plainBlockTermsDoNotClaimSpecificNamesTagsIdsOrRegexes() {
        for (String term : List.of("block", "BLOCKS", " full block ", "full blocks", "Blöcke", "bloques")) {
            assertTrue(DaedalonSearchQueryRules.isGenericBlockTerm(term), term);
        }
        for (String term : List.of("blocky", "block bench", "daedalon_blocks", "#block", "$block",
                "@block", "&block", "/block/", "daedalon:aganite_exedra", "byzantine capital")) {
            assertFalse(DaedalonSearchQueryRules.isGenericBlockTerm(term), term);
        }
    }

    @Test
    void reiCachedPlainArgumentsRespectInversionAndLeaveForeignAndExplicitQueriesAlone() {
        var plain = new CachedReiArgument(true, true);
        var inverse = new CachedReiArgument(true, false);
        var explicitTag = new CachedReiArgument(false, true);
        assertEquals(false, DaedalonSearchQueryRules.reiPlainBlockResult(List.of(plain), true));
        assertEquals(true, DaedalonSearchQueryRules.reiPlainBlockResult(List.of(inverse), true));
        assertNull(DaedalonSearchQueryRules.reiPlainBlockResult(List.of(plain), false));
        assertNull(DaedalonSearchQueryRules.reiPlainBlockResult(List.of(explicitTag), true));
        assertNull(DaedalonSearchQueryRules.reiPlainBlockResult(List.of(), true));
    }

    @Test
    void reiAlternativeTypesForOneTokenDoNotOverrideTheSeparateOrBranch() {
        var blockText = new CachedReiArgument(true, true);
        var blockTagInterpretation = new CachedReiArgument(false, true);
        // REI puts text/tag/tooltip interpretations of ONE word in this list.
        assertEquals(false, DaedalonSearchQueryRules.reiPlainBlockResult(
                List.of(blockTagInterpretation, blockText), true));
        var inverseBlockText = new CachedReiArgument(true, false);
        assertEquals(true, DaedalonSearchQueryRules.reiPlainBlockResult(
                List.of(blockTagInterpretation, inverseBlockText), true));
        // Its parser puts the statue side of "block | statue" in another compound.
        var statueBranch = List.of(new CachedReiArgument(false, true));
        assertNull(DaedalonSearchQueryRules.reiPlainBlockResult(statueBranch, true));
    }

    @Test
    void jeiFiltersOnlyTheNativeUnprefixedTokenResultsAndDoesNotMutateTheSearchCache() {
        Object statue = new Object();
        Object bench = new Object();
        Object stoneBlock = new Object();
        Set<Object> cached = Set.of(statue, bench, stoneBlock);
        Set<Object> result = DaedalonSearchQueryRules.jeiPlainBlockResults("block", true, cached,
                item -> item == statue || item == bench);
        assertEquals(Set.of(stoneBlock), result);
        assertEquals(3, cached.size());
        assertSame(cached, DaedalonSearchQueryRules.jeiPlainBlockResults("block", false, cached, item -> true));
        assertSame(cached, DaedalonSearchQueryRules.jeiPlainBlockResults("bench", true, cached, item -> true));
        // JEI applies negation after retrieving this token set, so -block keeps decor.
        assertFalse(result.contains(statue));
    }

    @Test
    void emiMarksOnlyDefaultPlainTokensAndConsumesEachFlagOnce() throws Exception {
        for (String term : List.of("block", "blocks", "\"full block\"", "\"BLOCKS\"")) {
            DaedalonSearchQueryRules.beginEmiToken(term);
            assertTrue(DaedalonSearchQueryRules.consumeEmiBlockToken(), term);
            assertFalse(DaedalonSearchQueryRules.consumeEmiBlockToken());
        }
        for (String term : List.of("#block", "$block", "@block", "/block/", "#daedalon:daedalon_blocks", "bench")) {
            DaedalonSearchQueryRules.beginEmiToken(term);
            assertFalse(DaedalonSearchQueryRules.consumeEmiBlockToken(), term);
        }
        DaedalonSearchQueryRules.beginEmiToken("block");
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            DaedalonSearchQueryRules.beginEmiToken("#block");
            assertFalse(DaedalonSearchQueryRules.consumeEmiBlockToken());
        }).join();
        assertTrue(DaedalonSearchQueryRules.consumeEmiBlockToken());
    }

    private record CachedReiArgument(boolean daedalon$isBlockTerm, boolean daedalon$isRegular)
            implements DaedalonSearchQueryRules.ReiTextArgument { }
}
