package com.oliver.daedalon.block;

import net.minecraft.state.property.EnumProperty;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.StringIdentifiable;
import net.minecraft.util.math.Direction;

/** An optional half step between the four existing cardinal facings. */
public enum DecorOrientation implements StringIdentifiable {
    STRAIGHT("straight"), DIAGONAL("diagonal");

    public static final EnumProperty<DecorOrientation> PROPERTY =
            EnumProperty.of("orientation", DecorOrientation.class);
    private static final float D = (float) Math.sqrt(0.5);
    private static final float[] COS = {1, D, 0, -D, -1, -D, 0, D};
    private static final float[] SIN = {0, D, 1, D, 0, -D, -1, -D};
    private final String id;

    DecorOrientation(String id) { this.id = id; }
    @Override public String asString() { return id; }

    public static float rotateX(float x, float z, int eighthTurns) {
        int turn = Math.floorMod(eighthTurns, 8);
        return COS[turn] * x - SIN[turn] * z;
    }
    public static float rotateZ(float x, float z, int eighthTurns) {
        int turn = Math.floorMod(eighthTurns, 8);
        return SIN[turn] * x + COS[turn] * z;
    }

    public Direction mirrorFacing(Direction facing, BlockMirror mirror) {
        Direction reflected = mirror.getRotation(facing).rotate(facing);
        // Reflection reverses the half step: -45 degrees is +45 from the
        // preceding cardinal facing. Offset statues follow that same facing.
        return this == DIAGONAL && mirror != BlockMirror.NONE
                ? reflected.rotateYCounterclockwise() : reflected;
    }
}
