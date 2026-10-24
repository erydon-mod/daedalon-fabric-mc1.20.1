package com.oliver.daedalon.item;

import com.oliver.daedalon.block.DecorOrientation;
import net.minecraft.block.BlockState;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.Direction;

/** One clockwise eight-position control backed by the existing saved pose. */
public final class DecorRotationControl {
    private static final Direction[] FACINGS = {
            Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST
    };
    private static final String[] NAMES = {
            "north", "north_east", "east", "south_east",
            "south", "south_west", "west", "north_west"
    };

    private DecorRotationControl() { }

    public static boolean appliesTo(BlockState state) {
        return state.contains(Properties.HORIZONTAL_FACING) && state.contains(DecorOrientation.PROPERTY);
    }

    public static BlockState cycle(BlockState state, boolean backwards) {
        int next = Math.floorMod(index(state) + (backwards ? -1 : 1), 8);
        return state.with(Properties.HORIZONTAL_FACING, FACINGS[next / 2])
                .with(DecorOrientation.PROPERTY, next % 2 == 0
                        ? DecorOrientation.STRAIGHT : DecorOrientation.DIAGONAL);
    }

    public static String valueName(BlockState state) {
        return NAMES[index(state)];
    }

    private static int index(BlockState state) {
        int cardinal = switch (state.get(Properties.HORIZONTAL_FACING)) {
            case NORTH -> 0;
            case EAST -> 1;
            case SOUTH -> 2;
            case WEST -> 3;
            default -> throw new IllegalArgumentException("Decor facing must be horizontal");
        };
        return cardinal * 2 + state.get(DecorOrientation.PROPERTY).ordinal();
    }
}
