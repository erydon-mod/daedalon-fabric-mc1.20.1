package com.oliver.daedalon.client.model.obj;

import net.minecraft.util.math.Direction;

import java.util.List;

/** One UV field for the collar and fitted carving; independent of their triangulation. */
final class CapitalProjection {
    static final List<Direction> SIDES = List.of(Direction.EAST, Direction.SOUTH, Direction.WEST, Direction.NORTH);
    private static final double EPSILON = 1e-6;

    private CapitalProjection() { }

    static Direction projectionPlane(double x, double z, double centre) {
        double dx = x - centre, dz = z - centre;
        return Math.abs(dx) >= Math.abs(dz) ? dx >= 0 ? Direction.EAST : Direction.WEST
                : dz >= 0 ? Direction.SOUTH : Direction.NORTH;
    }

    /** Two inclusive half-planes delimit each outward sector. */
    static double sectorDistance(Direction sector, int edge, double x, double z, double centre) {
        double difference = x - z, sum = x + z - 2 * centre;
        return switch (sector) {
            case EAST -> edge == 0 ? difference : sum;
            case SOUTH -> edge == 0 ? -difference : sum;
            case WEST -> edge == 0 ? -difference : -sum;
            case NORTH -> edge == 0 ? difference : -sum;
            default -> throw new IllegalArgumentException("A radial sector must be horizontal");
        };
    }

    static Direction genuineHorizontal(double[] a, double[] b, double[] c) {
        return genuineHorizontal(a[0], a[1], a[2], b[0], b[1], b[2], c[0], c[1], c[2]);
    }

    static Direction genuineHorizontal(double ax, double ay, double az, double bx, double by, double bz,
                                       double cx, double cy, double cz) {
        if (Math.max(ay, Math.max(by, cy)) - Math.min(ay, Math.min(by, cy)) > EPSILON) return null;
        double normalY = (bz - az) * (cx - ax) - (bx - ax) * (cz - az);
        return Math.abs(normalY) <= 1e-16 ? null : normalY >= 0 ? Direction.UP : Direction.DOWN;
    }

    /** Assign an actual triangle on a directional cut once, rather than emitting it twice. */
    static boolean onSectorBoundary(double ax, double az, double bx, double bz,
                                    double cx, double cz, double centre) {
        return Math.abs(ax - az) <= EPSILON && Math.abs(bx - bz) <= EPSILON && Math.abs(cx - cz) <= EPSILON
                || Math.abs(ax + az - 2 * centre) <= EPSILON && Math.abs(bx + bz - 2 * centre) <= EPSILON
                && Math.abs(cx + cz - 2 * centre) <= EPSILON;
    }
}
