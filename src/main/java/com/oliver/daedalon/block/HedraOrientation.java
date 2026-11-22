package com.oliver.daedalon.block;

import net.minecraft.util.StringIdentifiable;

/** Four player-selectable axes; reverse facings share the same symmetrical bench. */
public enum HedraOrientation implements StringIdentifiable {
    STRAIGHT("straight", 0), DIAGONAL("diagonal", 1), STRAIGHT_90("straight_90", 2),
    DIAGONAL_135("diagonal_135", 3);

    private static final HedraOrientation[] AXES = values();
    private final String id;
    public final int eighthTurns;

    HedraOrientation(String id, int eighthTurns) {
        this.id = id;
        this.eighthTurns = eighthTurns;
    }

    @Override public String asString() { return id; }

    public static HedraOrientation fromEighthTurns(int eighthTurns) {
        return AXES[Math.floorMod(eighthTurns, AXES.length)];
    }

    /** A fresh bench is perpendicular to the player's look, snapped to the nearest 45 degrees. */
    public static HedraOrientation fromPlayerYaw(float yaw) {
        return fromEighthTurns(Math.round(yaw / 45.0F));
    }
}
