package com.oliver.daedalon.client.model.obj;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.ArrayList;
import java.util.List;

/** World-metric lantern projection and atlas-safe clipping, independent of the renderer. */
final class LanternWorldUv {
    private static final double MINIMUM_SQUARED_AREA = 1.0E-24;

    private LanternWorldUv() { }

    record Vertex(float x, float y, float z, float nx, float ny, float nz, double s, double t) {
        Vertex lerp(Vertex other, double amount) {
            double inverse = 1.0 - amount;
            double normalX = nx * inverse + other.nx * amount;
            double normalY = ny * inverse + other.ny * amount;
            double normalZ = nz * inverse + other.nz * amount;
            double length = Math.sqrt(normalX * normalX + normalY * normalY + normalZ * normalZ);
            if (length < 1.0E-12) {
                // Antipodal endpoint normals have no unique interpolated direction.
                Vertex endpoint = amount <= 0.5 ? this : other;
                normalX = endpoint.nx;
                normalY = endpoint.ny;
                normalZ = endpoint.nz;
                length = Math.sqrt(normalX * normalX + normalY * normalY + normalZ * normalZ);
                if (length < 1.0E-12) {
                    normalX = 0.0;
                    normalY = 1.0;
                    normalZ = 0.0;
                    length = 1.0;
                }
            }
            return new Vertex((float) (x * inverse + other.x * amount),
                    (float) (y * inverse + other.y * amount),
                    (float) (z * inverse + other.z * amount),
                    (float) (normalX / length), (float) (normalY / length), (float) (normalZ / length),
                    s * inverse + other.s * amount, t * inverse + other.t * amount);
        }
    }

    record Patch(List<Vertex> vertices, double uOrigin, double vOrigin, boolean repeatSheet) {
        Patch {
            vertices = List.copyOf(vertices);
        }

        float u(Vertex vertex) {
            return (float) ((vertex.s - uOrigin) / (repeatSheet ? 12.0 : 1.0));
        }

        float v(Vertex vertex) {
            return (float) ((vertex.t - vOrigin) / (repeatSheet ? 6.0 : 1.0));
        }
    }

    static double s(Direction face, BlockPos origin, double x, double z) {
        double worldX = (double) origin.getX() + x;
        double worldZ = (double) origin.getZ() + z;
        return switch (face) {
            case NORTH -> -worldX;
            case EAST -> -worldZ;
            case WEST -> worldZ;
            default -> worldX;
        };
    }

    static double t(Direction face, BlockPos origin, double y, double z) {
        return switch (face) {
            case UP -> (double) origin.getZ() + z;
            case DOWN -> -((double) origin.getZ() + z);
            default -> 1.0 - ((double) origin.getY() + y);
        };
    }

    static double origin(double minimum, boolean repeatSheet) {
        if (!Double.isFinite(minimum)) {
            throw new IllegalArgumentException("A texture origin needs a finite world coordinate");
        }
        double period = repeatSheet ? 6.0 : 1.0;
        return Math.floor(minimum / period) * period;
    }

    /** Split a convex source polygon without folding UVs or changing its winding. */
    static List<Patch> split(List<Vertex> polygon, boolean repeatSheet) {
        List<Vertex> source = clean(polygon);
        if (!hasArea(source)) {
            return List.of();
        }
        List<Patch> result = new ArrayList<>();
        if (repeatSheet) {
            // The duplicated horizontal period carries a lantern's entire U window.
            // Its unduplicated six rows require a cut at each crossed V period.
            double uOrigin = origin(minimum(source, true), true);
            for (Strip strip : strips(source, false, 6.0)) {
                result.add(new Patch(strip.vertices, uOrigin, strip.origin, true));
            }
        } else {
            for (Strip horizontal : strips(source, true, 1.0)) {
                for (Strip vertical : strips(horizontal.vertices, false, 1.0)) {
                    result.add(new Patch(vertical.vertices, horizontal.origin, vertical.origin, false));
                }
            }
        }
        return List.copyOf(result);
    }

    private record Strip(List<Vertex> vertices, double origin) { }

    private static List<Strip> strips(List<Vertex> polygon, boolean horizontal, double period) {
        double minimum = minimum(polygon, horizontal);
        double maximum = maximum(polygon, horizontal);
        double first = Math.floor(minimum / period) * period;
        List<Strip> result = new ArrayList<>();
        for (double lower = first; lower <= maximum; lower += period) {
            List<Vertex> clipped = clip(polygon, horizontal, lower, true);
            clipped = clip(clipped, horizontal, lower + period, false);
            if (hasArea(clipped)) {
                result.add(new Strip(clipped, lower));
            }
            if (lower + period >= maximum) {
                break;
            }
        }
        return result;
    }

    private static List<Vertex> clip(List<Vertex> polygon, boolean horizontal, double plane,
                                     boolean keepGreater) {
        if (polygon.isEmpty()) {
            return List.of();
        }
        List<Vertex> result = new ArrayList<>(polygon.size() + 1);
        Vertex previous = polygon.get(polygon.size() - 1);
        double previousDistance = coordinate(previous, horizontal) - plane;
        boolean previousInside = keepGreater ? previousDistance >= 0.0 : previousDistance <= 0.0;
        for (Vertex current : polygon) {
            double distance = coordinate(current, horizontal) - plane;
            boolean inside = keepGreater ? distance >= 0.0 : distance <= 0.0;
            if (inside != previousInside) {
                double amount = previousDistance / (previousDistance - distance);
                Vertex intersection = previous.lerp(current, amount);
                result.add(new Vertex(intersection.x, intersection.y, intersection.z,
                        intersection.nx, intersection.ny, intersection.nz,
                        horizontal ? plane : intersection.s, horizontal ? intersection.t : plane));
            }
            if (inside) {
                result.add(current);
            }
            previous = current;
            previousDistance = distance;
            previousInside = inside;
        }
        return clean(result);
    }

    private static List<Vertex> clean(List<Vertex> vertices) {
        List<Vertex> result = new ArrayList<>(vertices.size());
        for (Vertex vertex : vertices) {
            if (result.isEmpty() || !samePosition(result.get(result.size() - 1), vertex)) {
                result.add(vertex);
            }
        }
        if (result.size() > 1 && samePosition(result.get(0), result.get(result.size() - 1))) {
            result.remove(result.size() - 1);
        }
        return result;
    }

    private static boolean samePosition(Vertex a, Vertex b) {
        return a.x == b.x && a.y == b.y && a.z == b.z;
    }

    private static boolean hasArea(List<Vertex> polygon) {
        if (polygon.size() < 3) {
            return false;
        }
        Vertex a = polygon.get(0);
        double nx = 0.0, ny = 0.0, nz = 0.0;
        for (int i = 1; i < polygon.size() - 1; i++) {
            Vertex b = polygon.get(i), c = polygon.get(i + 1);
            double bx = (double) b.x - a.x, by = (double) b.y - a.y, bz = (double) b.z - a.z;
            double cx = (double) c.x - a.x, cy = (double) c.y - a.y, cz = (double) c.z - a.z;
            nx += by * cz - bz * cy;
            ny += bz * cx - bx * cz;
            nz += bx * cy - by * cx;
        }
        return nx * nx + ny * ny + nz * nz > MINIMUM_SQUARED_AREA;
    }

    private static double minimum(List<Vertex> vertices, boolean horizontal) {
        double result = Double.POSITIVE_INFINITY;
        for (Vertex vertex : vertices) {
            result = Math.min(result, coordinate(vertex, horizontal));
        }
        return result;
    }

    private static double maximum(List<Vertex> vertices, boolean horizontal) {
        double result = Double.NEGATIVE_INFINITY;
        for (Vertex vertex : vertices) {
            result = Math.max(result, coordinate(vertex, horizontal));
        }
        return result;
    }

    private static double coordinate(Vertex vertex, boolean horizontal) {
        return horizontal ? vertex.s : vertex.t;
    }
}
