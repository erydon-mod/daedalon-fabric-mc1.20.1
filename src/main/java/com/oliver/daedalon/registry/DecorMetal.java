package com.oliver.daedalon.registry;

/** Shared standalone metal finishes; every variant reuses its family's geometry. */
public enum DecorMetal {
    BRONZE("bronze"),
    SILVER("silver");

    private final String id;

    DecorMetal(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }
}
