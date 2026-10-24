package com.oliver.daedalon.item;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

final class OlympianGodCycleTest {
    @Test
    void approvedOrderHasTwelveDistinctGodsAndFinishesWithDionysus() {
        assertEquals(List.of("zeus", "hera", "poseidon", "demeter", "athena", "apollo", "artemis",
                "ares", "aphrodite", "hephaestus", "hermes", "dionysus"), OlympianGodCycle.SUBJECTS);
        assertEquals(12, new HashSet<>(OlympianGodCycle.SUBJECTS).size());
        assertFalse(OlympianGodCycle.SUBJECTS.contains("hestia"));
    }

    @Test
    void completedSetsRepeatAndMalformedSavedCursorsRestartAtZeus() {
        int cursor = 0;
        for (int placed = 0; placed < 36; placed++) {
            assertEquals(OlympianGodCycle.SUBJECTS.get(placed % 12), OlympianGodCycle.subject(cursor));
            cursor = OlympianGodCycle.next(cursor);
        }
        assertEquals(0, cursor);
        for (int invalid : new int[]{-1, Integer.MIN_VALUE, 12, Integer.MAX_VALUE}) {
            assertEquals("zeus", OlympianGodCycle.subject(invalid));
            assertEquals(1, OlympianGodCycle.next(invalid));
        }
    }
}
