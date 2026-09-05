package com.oliver.daedalon.block;

import net.minecraft.util.StringIdentifiable;

/** Visual turns of the same capital mesh, clockwise around its vertical centre. */
public enum CapitalOrientation implements StringIdentifiable {
    STRAIGHT("straight", 0, 1.0F, 0.0F),
    DIAGONAL("diagonal", 45, diagonal(), diagonal()),
    STRAIGHT_90("straight_90", 90, 0.0F, 1.0F),
    DIAGONAL_135("diagonal_135", 135, -diagonal(), diagonal());

    private final String id;
    public final int degrees;
    public final float cosine;
    public final float sine;

    CapitalOrientation(String id, int degrees, float cosine, float sine) {
        this.id = id;
        this.degrees = degrees;
        this.cosine = cosine;
        this.sine = sine;
    }

    private static float diagonal() { return (float) Math.sqrt(0.5); }

    @Override
    public String asString() { return id; }

    public CapitalOrientation turn(int degrees, boolean ionic) {
        return values()[Math.floorMod(this.degrees + degrees, ionic ? 180 : 90) / 45];
    }
}
