package com.oliver.daedalon.registry;

/** Plain finishes shared by the carved friezes and Gothic panel. */
public enum ArchitecturalFinish {
    WHITE("white"),
    BRONZE("bronze"),
    GOLD("gold");

    private final String id;

    ArchitecturalFinish(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }
}
