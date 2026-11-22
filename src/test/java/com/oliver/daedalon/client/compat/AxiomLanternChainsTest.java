package com.oliver.daedalon.client.compat;

import com.oliver.daedalon.block.LanternBlock;
import com.oliver.daedalon.block.LanternPlacement;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class AxiomLanternChainsTest {
    @Test void outOfOrderRegionInsertionStillFindsOnlyTheContiguousAssembly() {
        var chains = new AxiomLanternChains();
        var size = LanternBlock.Size.LARGE;
        chains.set(5, 13, -8, size, true);
        assertEquals(new AxiomLanternChains.Span(0, false), chains.span(5, 10, -8, size, false));
        chains.set(5, 11, -8, size, false);
        assertEquals(new AxiomLanternChains.Span(1, false), chains.span(5, 10, -8, size, true));
        chains.set(5, 12, -8, size, false);
        assertEquals(new AxiomLanternChains.Span(3, true), chains.span(5, 10, -8, size, false));
        chains.set(5, 12, -8, null, false);
        assertEquals(new AxiomLanternChains.Span(1, false), chains.span(5, 10, -8, size, true));
    }

    @Test void neighbouringColumnsAndWrongSizedLinksCannotChangeThePreview() {
        var chains = new AxiomLanternChains();
        chains.set(0, 1, 0, LanternBlock.Size.SMALL, true);
        chains.set(1, 1, 0, LanternBlock.Size.LARGE, true);
        assertEquals(new AxiomLanternChains.Span(0, false), chains.span(0, 0, 0, LanternBlock.Size.LARGE, false));
        assertEquals(new AxiomLanternChains.Span(1, true), chains.span(0, 0, 0, LanternBlock.Size.SMALL, false));
        assertFalse(chains.set(0, 1, 0, LanternBlock.Size.SMALL, true));
        assertTrue(chains.set(0, 1, 0, LanternBlock.Size.SMALL, false));
        chains.clear();
        assertFalse(chains.contains(0, 1, 0));
        assertEquals(new AxiomLanternChains.Span(0, true), chains.span(0, 0, 0, LanternBlock.Size.SMALL, true));
    }

    @Test void malformedSelectionsCannotTriggerAnUnboundedChainScan() {
        var chains = new AxiomLanternChains();
        for (int y = 1; y <= LanternPlacement.MAX_CHAIN + 10; y++)
            chains.set(0, y, 0, LanternBlock.Size.LARGE, true);
        assertEquals(LanternPlacement.MAX_CHAIN, chains.span(0, 0, 0, LanternBlock.Size.LARGE, false).count());
    }
}
