package com.oliver.daedalon.registry;

/** Plain finishes shared by every carved frieze and decorative panel. */
public enum ArchitecturalFinish {
    WHITE("white"),
    BLACK("black"),
    BRONZE("bronze"),
    SILVER("silver");

    private final String id;

    ArchitecturalFinish(String id) {
        this.id = id;
    }

    public boolean supportsPanel() {
        return true;
    }

    public String id() {
        return id;
    }
}
