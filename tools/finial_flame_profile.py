"""Measured, regular six-lobed Phlox ornament; no source mesh is imported.

The unit-height profile and crest rotation were measured with horizontal rays
through Oliver's Phlox source. Smooth analytic sections replace the irregular
Meshy tessellation. All coordinates are Blender Z-up; the caller owns the base,
final footprint calibration, authored normals and OBJ export.
"""

from __future__ import annotations

import bisect
import math


SOURCE_SHA256 = "b6b12ef36b36e91c13098346b352f91fde487d2c2fd7c3dd2fa4875f2846a228"
ANGULAR_SEGMENTS = 108

# Height, clean valley radius, crest radius, unwrapped crest angle, centre X.
# Radial silhouettes are retained; each section has six equal, rounded lobes.
PROFILE = (
    (.220, .1700, .1700, 25.0, .0000),
    (.230, .1860, .1860, 25.0, .0000),
    (.240, .1760, .1925, 24.9, .0000),
    (.260, .1433, .2105, 20.7, .0000),
    (.280, .1530, .2260, 18.0, .0000),
    (.340, .1750, .2576, 15.4, .0000),
    (.400, .1920, .2704, 18.2, .0000),
    (.440, .1928, .2692, 22.4, -.0005),
    (.480, .1884, .2638, 28.3, -.0030),
    (.540, .1748, .2493, 40.3, -.0050),
    (.600, .1598, .2277, 54.4, .0005),
    (.660, .1320, .1969, 65.7, .0134),
    (.700, .1137, .1759, 69.0, .0242),
    (.740, .0983, .1565, 70.0, .0292),
    (.780, .0841, .1367, 70.0, .0201),
    (.820, .0696, .1140, 70.0, .0068),
    (.860, .0613, .0875, 70.0, -.0029),
    (.900, .0506, .0633, 70.0, -.0034),
    (.940, .0367, .0428, 70.0, .0013),
    (.980, .0248, .0248, 70.0, .0078),
)

RING_HEIGHTS = (
    .220, .230, .240, .265, .290, .315, .340, .365, .390, .415,
    .440, .465, .490, .515, .540, .565, .590, .615, .640,
    .665, .690, .715, .740, .765, .790, .820, .850, .880,
    .910, .940, .965, .985, .996, .999,
)


def _curve(column):
    x = tuple(row[0] for row in PROFILE)
    y = tuple(row[column] for row in PROFILE)
    h = tuple(b - a for a, b in zip(x, x[1:]))
    delta = tuple((b - a) / step for a, b, step in zip(y, y[1:], h))
    slopes = [delta[0]]
    for i in range(1, len(x) - 1):
        a, b = delta[i - 1], delta[i]
        if a * b <= 0:
            slopes.append(0.0)
        else:
            w1, w2 = 2 * h[i] + h[i - 1], h[i] + 2 * h[i - 1]
            slopes.append((w1 + w2) / (w1 / a + w2 / b))
    slopes.append(delta[-1])
    return x, y, tuple(slopes)


CURVES = tuple(_curve(column) for column in range(1, 5))


def _evaluate(curve, height):
    x, y, slopes = curve
    if height <= x[0]:
        return y[0]
    if height >= x[-1]:
        return y[-1]
    i = bisect.bisect_right(x, height) - 1
    span = x[i + 1] - x[i]
    t = (height - x[i]) / span
    return ((2 * t**3 - 3 * t**2 + 1) * y[i]
            + (t**3 - 2 * t**2 + t) * span * slopes[i]
            + (-2 * t**3 + 3 * t**2) * y[i + 1]
            + (t**3 - t**2) * span * slopes[i + 1])


def build_flame(builder, z0=.22, z1=1.0, max_radius=.2676):
    """Append one closed outward-wound ornament and return its feature evidence.

    ``builder.vertex((x, y, z))`` returns a vertex index; ``builder.face(ids)``
    accepts polygon indices. The shape uses quads and triangular end caps.
    """
    if not z1 > z0 or not max_radius > 0:
        raise ValueError("A Phlox ornament needs positive height and radius")
    rows = []
    centres = []
    all_positions = []
    radius_scale = max_radius / .2704
    for source_height in RING_HEIGHTS:
        valley, crest, phase, centre_x = (_evaluate(curve, source_height) for curve in CURVES)
        if source_height > .98:
            # A small ellipsoidal crown rounds the stone tip, with a horizontal
            # tangent at its pole, rather than ending the loft in a sharp cone.
            t = (source_height - .98) / .02
            valley = crest = .0248 * math.sqrt(max(0.0, 1 - t * t))
            centre_x = .0078 + .0042 * t
        z = z0 + (source_height - .22) / .78 * (z1 - z0)
        centre_x *= radius_scale
        centres.append((centre_x, 0.0, z))
        row = []
        for i in range(ANGULAR_SEGMENTS):
            angle = math.tau * i / ANGULAR_SEGMENTS
            wave_angle = 6 * (angle - math.radians(phase))
            # The small second harmonic gives broad carved petals and narrower
            # rounded grooves. Its coefficient stays below 0.25, retaining
            # exactly one crest per lobe without secondary ridges.
            wave = (math.cos(wave_angle) - .24 * math.cos(2 * wave_angle) + 1.24) / 2
            radius = (valley + (crest - valley) * wave) * radius_scale
            point = (centre_x + radius * math.cos(angle), radius * math.sin(angle), z)
            all_positions.append(point)
            row.append(builder.vertex(point))
        rows.append(row)
    for lower, upper in zip(rows, rows[1:]):
        for i in range(ANGULAR_SEGMENTS):
            j = (i + 1) % ANGULAR_SEGMENTS
            builder.face((lower[i], lower[j], upper[j], upper[i]))
    bottom = builder.vertex(centres[0])
    top_point = (.0120 * radius_scale, 0.0, z1)
    top = builder.vertex(top_point)
    all_positions.extend((centres[0], top_point))
    for i in range(ANGULAR_SEGMENTS):
        j = (i + 1) % ANGULAR_SEGMENTS
        builder.face((bottom, rows[0][j], rows[0][i]))
        builder.face((rows[-1][i], rows[-1][j], top))
    count = ANGULAR_SEGMENTS * (len(rows) - 1)
    return {
        "method": "Smooth measured six-lobed loft with a rounded crown; no retained source tessellation",
        "source_sha256": SOURCE_SHA256,
        "lobes": 6,
        "twist_degrees": 54.6,
        "twist_reference_heights": [.34, .74],
        "crest_angle_degrees_at_reference_heights": [15.4, 70.0],
        "angular_segments": ANGULAR_SEGMENTS,
        "profile_rings": len(rows),
        "editable_faces": count + 2 * ANGULAR_SEGMENTS,
        "triangles": 2 * count + 2 * ANGULAR_SEGMENTS,
        "vertices": ANGULAR_SEGMENTS * len(rows) + 2,
        "closed_ornament": True,
        "tip_rounding_height": .02 / .78 * (z1 - z0),
        "circular_root": {
            "buried_height": z0,
            "buried_radius": .1700 * radius_scale,
            "raised_height": z0 + .01 / .78 * (z1 - z0),
            "raised_radius": .1860 * radius_scale,
            "purpose": "A round root overlaps the collar before six grooves emerge; no near-coplanar lobed collar joint",
        },
        "centreline_bend_x": [min(p[0] for p in centres), max(p[0] for p in centres)],
        "bottom_z": z0,
        "top_z": z1,
        "uncalibrated_bounds": [
            [min(point[axis] for point in all_positions) for axis in range(3)],
            [max(point[axis] for point in all_positions) for axis in range(3)],
        ],
    }
