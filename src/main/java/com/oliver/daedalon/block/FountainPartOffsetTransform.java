package com.oliver.daedalon.block;

import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;

/** Pure horizontal transforms for persisted fountain proxy offsets. */
final class FountainPartOffsetTransform {
    private FountainPartOffsetTransform() {
    }

    static int rotatedX(int offsetX, int offsetZ, BlockRotation rotation) {
        return switch (rotation) {
            case NONE -> offsetX;
            case CLOCKWISE_90 -> -offsetZ;
            case CLOCKWISE_180 -> -offsetX;
            case COUNTERCLOCKWISE_90 -> offsetZ;
        };
    }

    static int rotatedZ(int offsetX, int offsetZ, BlockRotation rotation) {
        return switch (rotation) {
            case NONE -> offsetZ;
            case CLOCKWISE_90 -> offsetX;
            case CLOCKWISE_180 -> -offsetZ;
            case COUNTERCLOCKWISE_90 -> -offsetX;
        };
    }

    static int mirroredX(int offsetX, BlockMirror mirror) {
        return mirror == BlockMirror.FRONT_BACK ? -offsetX : offsetX;
    }

    static int mirroredZ(int offsetZ, BlockMirror mirror) {
        return mirror == BlockMirror.LEFT_RIGHT ? -offsetZ : offsetZ;
    }
}
