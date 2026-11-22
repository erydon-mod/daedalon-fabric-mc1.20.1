"""Regular dressed pinecone scales on a smooth, measured Strobilos envelope.

Coordinates are Blender Z-up.  The caller owns the pedestal, final footprint
calibration, shading and export.  This portable helper only needs ``vertex`` and
``face`` methods and does not read or modify any source asset.
"""

from __future__ import annotations

import bisect
import math


ENVELOPE = ((0, .58), (.10, .76), (.24, .91), (.35, .94), (.47, .89),
            (.60, .78), (.72, .61), (.82, .42), (.91, .23),
            (.97, .085), (.993, .018), (1, .006))

# The first fully defined existing ridge row is at t=.711320. Only the seven
# lower rows, ending at t=.641371, need an explicit meridional normal split.
LOWER_RIDGE_LIMIT_FRACTION = .68


def _profile(points, value):
    """Shape-preserving cubic interpolation with a continuous tangent."""
    slopes = [(b[1] - a[1]) / (b[0] - a[0]) for a, b in zip(points, points[1:])]
    tangent = [slopes[0]]
    for left, right in zip(slopes, slopes[1:]):
        tangent.append(0 if left * right <= 0 else
                       math.copysign(min(abs((left + right) / 2), 3 * abs(left), 3 * abs(right)), left))
    tangent.append(slopes[-1])
    i = max(0, min(len(points) - 2, bisect.bisect_right([p[0] for p in points], value) - 1))
    x0, y0 = points[i]
    x1, y1 = points[i + 1]
    t = max(0, min(1, (value - x0) / (x1 - x0)))
    h = x1 - x0
    return ((2*t**3 - 3*t*t + 1)*y0 + (t**3 - 2*t*t + t)*h*tangent[i]
            + (-2*t**3 + 3*t*t)*y1 + (t**3 - t*t)*h*tangent[i + 1])


def _point(angle, radius, height):
    return radius * math.cos(angle), radius * math.sin(angle), height


def build_strobilos(builder, z0, z1, max_radius):
    """Append closed scales with equal meridian halves, seated in a closed core.

    A diamond is constructed in the body's meridian arc length, rather than
    applying a nonlinear height map to an already drawn diamond.  The ten
    continuous staggered columns keep a fixed count and reduce tile size as the
    crown narrows. No row changes its half-column stagger or converges to a tip.
    """
    if not 0 <= z0 < z1 <= 1 or max_radius <= 0:
        raise ValueError("Strobilos requires a positive radius and bounded height band")
    span = z1 - z0

    def radius(t):
        return max_radius * _profile(ENVELOPE, t)

    # A dense table measures the meridian, independent of display tessellation.
    samples = 1600
    heights = [i / samples for i in range(samples + 1)]
    lengths = [0.0]
    for low, high in zip(heights, heights[1:]):
        lengths.append(lengths[-1] + math.hypot(span * (high - low), radius(high) - radius(low)))

    def height_at(arc):
        arc = max(0, min(lengths[-1], arc))
        j = max(1, min(samples, bisect.bisect_left(lengths, arc)))
        f = (arc - lengths[j - 1]) / (lengths[j] - lengths[j - 1])
        return (j - 1 + f) / samples

    def arc_at(t):
        i = min(samples - 1, int(t * samples))
        return lengths[i] + (lengths[i + 1] - lengths[i]) * (t * samples - i)

    def dressed_point(angle, arc, lift):
        t = height_at(arc)
        epsilon = .000001
        slope = (radius(min(1, t+epsilon))-radius(max(0, t-epsilon)))/(2*epsilon*span)
        normal_length = math.hypot(1, slope)
        # Move perpendicular to the meridian. A purely radial lift slides the
        # little crown bevels across their own inset on the steep upper cone.
        return _point(angle, radius(t)+lift/normal_length,
                      z0+span*t-lift*slope/normal_length)

    # Dense enough for a smooth stone envelope, including a finite dressed tip.
    segments = 36
    core_heights = sorted({i / 28 for i in range(29)} | {p[0] for p in ENVELOPE})
    rings = [[builder.vertex(_point(j * math.tau / segments, radius(t), z0 + span*t))
              for j in range(segments)] for t in core_heights]
    for low, high in zip(rings, rings[1:]):
        for j in range(segments):
            k = (j + 1) % segments
            builder.face((low[j], low[k], high[k], high[j]))
    builder.face(tuple(reversed(rings[0])))
    builder.face(tuple(rings[-1]))

    count = 10

    def half_height(arc, count):
        r = radius(height_at(arc))
        # Keep the local diamond aspect close to the source's broad stone scales.
        return 1.08 * r * math.sin(math.pi / count)

    scale_first_face = len(builder.faces)
    flat_faces, rows, ridge_edges, ridge_rows = [], [], [], []
    arc = half_height(0, count)
    for _ in range(8):
        arc = half_height(arc, count) * .975
    row = 0
    # Last tiles stop below the small dressed crown instead of collapsing to it.
    limit = arc_at(.989)
    while arc < limit:
        t = height_at(arc)
        half = half_height(arc, count)
        if arc + half > limit:
            break
        angle_half = math.pi / count * .975
        phase = .5 * (row % 2)
        for column in range(count):
            angle = (column + phase) * math.tau / count
            # Symmetric diamond vertices in arc coordinates preserve both halves.
            diamond = ((-angle_half, arc), (0, arc - half*.975),
                       (angle_half, arc), (0, arc + half*.975))
            outer, bevel, rear = [], [], []
            for da, meridian in diamond:
                edge_t = height_at(meridian)
                local_r = radius(edge_t)
                edge_lift = min(.0007, local_r * .025)
                outer.append(builder.vertex(dressed_point(angle + da, meridian, edge_lift)))
                inset_t = height_at(arc + (meridian - arc)*.89)
                inset_r = radius(inset_t)
                bevel.append(builder.vertex(dressed_point(angle + da*.89,
                                                          arc+(meridian-arc)*.89,
                                                          min(.0022, inset_r*.09))))
                # Every rear vertex follows the local core and stays embedded.
                rear.append(builder.vertex(_point(angle + da, local_r * .94 - min(.003, local_r*.06),
                                                   z0 + span*edge_t)))
            relief = min(max_radius*.064, radius(t)*.13)
            crown = builder.vertex(dressed_point(angle, arc, relief))
            if t < LOWER_RIDGE_LIMIT_FRACTION:
                # Split the existing raised centre into its two curved sides.
                # The left/right cross joins stay smooth; no scale positions,
                # topology, upper rows or silhouette need to change.
                ridge_edges.extend(((crown, bevel[1]), (crown, bevel[3])))
            for j in range(4):
                k = (j + 1) % 4
                flat_faces.extend((len(builder.faces), len(builder.faces) + 1))
                builder.face((outer[j], outer[k], bevel[k], bevel[j]))
                builder.face((bevel[j], bevel[k], crown))
                builder.face((outer[j], rear[j], rear[k], outer[k]))
            builder.face(tuple(reversed(rear)))
        width = 2 * radius(t) * math.sin(angle_half)
        rows.append({"height_fraction": t, "columns": count,
                     "meridian_height": half*1.95, "chord_width": width,
                     "aspect": half*1.95/width, "upper_lower_arc_ratio": 1.0,
                     "angular_half_radians": angle_half})
        if t < LOWER_RIDGE_LIMIT_FRACTION:
            ridge_rows.append(row)
        # Predict the next local half-height to keep neighbouring rows seated.
        next_arc = arc + half
        next_half = half_height(next_arc, count)
        arc += .5 * (half + next_half)
        row += 1

    return {"ornament": "regular meridian-balanced dressed diamond pinecone scales",
            "scale_tiers": len(rows), "scale_count": sum(item["columns"] for item in rows),
            "scales_per_tier": [item["columns"] for item in rows],
            "scale_rows": rows, "tip_dressing_metres": radius(1)*2,
            "sharp_edge_pairs": ridge_edges,
            "lower_ridge_rows": ridge_rows,
            "lower_ridge_limit_fraction": LOWER_RIDGE_LIMIT_FRACTION,
            "lower_ridge_policy": "split only the existing lower meridional ridge normals; horizontal joins, positions and all upper-row normals remain unchanged",
            "scale_first_face": scale_first_face, "flat_face_indices": flat_faces}
