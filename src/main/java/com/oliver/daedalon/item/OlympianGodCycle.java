package com.oliver.daedalon.item;

import java.util.List;

/** The twelve-statue placement order; independent of material and registry order. */
public final class OlympianGodCycle {
    public static final List<String> SUBJECTS = List.of(
            "zeus", "hera", "poseidon", "demeter", "athena", "apollo",
            "artemis", "ares", "aphrodite", "hephaestus", "hermes", "dionysus"
    );

    private OlympianGodCycle() { }

    /** A missing or invalid saved cursor starts a new cycle with Zeus. */
    public static int index(int saved) {
        return saved >= 0 && saved < SUBJECTS.size() ? saved : 0;
    }

    public static int next(int saved) {
        return (index(saved) + 1) % SUBJECTS.size();
    }

    public static String subject(int saved) {
        return SUBJECTS.get(index(saved));
    }
}
