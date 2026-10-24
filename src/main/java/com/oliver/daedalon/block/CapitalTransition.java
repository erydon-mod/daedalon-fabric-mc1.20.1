package com.oliver.daedalon.block;

import net.minecraft.util.StringIdentifiable;

/** Automatically fitted, shallow collars for ERYDON's non-circular columns. */
public enum CapitalTransition implements StringIdentifiable {
    NONE("none", 0.0),
    SQUARE("square", 1.0),
    GOTHIC("gothic", 14.0 / 16.0),
    SQUARE_SHAFT("square_shaft", 11.0 / 16.0),
    GOTHIC_SHAFT("gothic_shaft", 11.8496003069 / 16.0);

    public static final double HEIGHT = 2.0 / 16.0;
    public static final double JOIN_DIAMETER = 11.5 / 16.0;
    private final String id;
    private final double baseWidth;

    CapitalTransition(String id, double baseWidth) {
        this.id = id;
        this.baseWidth = baseWidth;
    }

    @Override public String asString() { return id; }
    public double baseWidth() { return baseWidth; }
    public CapitalTransitionProfile profile() { return CapitalTransitionProfile.forTransition(this); }
    public CapitalTransitionProfile profile(CapitalBlock.Size size) { return CapitalTransitionProfile.forTransition(this, size); }
}
