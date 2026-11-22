"""Original hanging Diota rings and pierced leaf lugs, authored for Daedalon.

Every point is generated numerically. No existing mesh or image is loaded.
The carved stone eyelets and small bronze drop rings are mechanically linked:
the upper bronze arc passes through the stone hole, while the lower arc hangs
outside the urn shoulder. Coordinates are Y-up metres before normalization.
"""
from __future__ import annotations

import math


STONE = "none"
BRONZE = "bronze_handles"
HOLE_CENTRE = (.235, .850)
HOLE_RADII = (.014, .017)
LUG_SEGMENTS = 24
RING_SEGMENTS = 28
WIRE_SEGMENTS = 6
WIRE_RADIUS = .0045


def _face(builder, indices, sign, material):
    indices = tuple(indices)
    builder.face(indices if sign > 0 else reversed(indices), material)


def _lug_outline(angle):
    """Original teardrop ear: a carved upper eye and long embedded leaf root."""
    s, c = math.sin(angle), math.cos(angle)
    lower = min(s, 0.)
    # A narrow shoulder root leans inward. The upper ear stays broad enough
    # around its true hole to read as carved stone rather than a thin washer.
    x = .235 - .017*lower*lower + .030*c*(.93 + .07*s)
    y = .850 + .041*s + .056*lower**3
    return x, y


def _stone_lug(builder, sign):
    # Cross-section travels around a closed carved annulus. The forward leaf
    # crest is shallow and continuous; the rear stays flatter against the urn.
    sections = ((0., -.010), (.10, -.016), (1., -.010),
                (1., .010), (.55, .021), (.10, .016), (0., .010))
    rings = []
    for t, z in sections:
        ring = []
        for i in range(LUG_SEGMENTS):
            a = 2*math.pi*i/LUG_SEGMENTS
            inner_x = HOLE_CENTRE[0]+HOLE_RADII[0]*math.cos(a)
            inner_y = HOLE_CENTRE[1]+HOLE_RADII[1]*math.sin(a)
            outer_x, outer_y = _lug_outline(a)
            ring.append(builder.vertex((sign*((1-t)*inner_x+t*outer_x),
                                        (1-t)*inner_y+t*outer_y, z)))
        rings.append(ring)
    for k, first in enumerate(rings):
        second = rings[(k+1) % len(rings)]
        for i in range(LUG_SEGMENTS):
            following = (i+1) % LUG_SEGMENTS
            _face(builder, (first[following], second[following], second[i],
                            first[i]), sign, STONE)


def _bronze_drop_ring(builder, sign):
    # Start with a vertical oval, lean its bottom outward by 18 degrees, then
    # turn its horizontal axis by 10 degrees. The little hoop consequently
    # hangs clear of the bowl and reads as an oval in a three-quarter view.
    lean = math.radians(18.)
    turn = math.radians(10.)
    vertical = (-math.sin(lean)*math.cos(turn), math.cos(lean),
                math.sin(lean)*math.sin(turn))
    horizontal = (math.sin(turn), 0., math.cos(turn))
    normal = (math.cos(lean)*math.cos(turn), math.sin(lean),
              -math.cos(lean)*math.sin(turn))
    radius_y = .075/math.cos(lean)
    radius_z = .045
    centre = (.235-radius_y*vertical[0], .780, -radius_y*vertical[2])
    loops = []
    for i in range(RING_SEGMENTS):
        a = 2*math.pi*i/RING_SEGMENTS
        c, s = math.cos(a), math.sin(a)
        point = tuple(centre[j]+radius_y*c*vertical[j]
                      + radius_z*s*horizontal[j] for j in range(3))
        outward = tuple(c/radius_y*vertical[j]+s/radius_z*horizontal[j]
                        for j in range(3))
        length = math.sqrt(sum(value*value for value in outward))
        outward = tuple(value/length for value in outward)
        loop = []
        for k in range(WIRE_SEGMENTS):
            b = 2*math.pi*k/WIRE_SEGMENTS
            p = tuple(point[j]+WIRE_RADIUS*(math.cos(b)*outward[j]
                      + math.sin(b)*normal[j]) for j in range(3))
            loop.append(builder.vertex((sign*p[0], p[1], p[2])))
        loops.append(loop)
    for i, first in enumerate(loops):
        second = loops[(i+1) % RING_SEGMENTS]
        for k in range(WIRE_SEGMENTS):
            following = (k+1) % WIRE_SEGMENTS
            _face(builder, (first[k], second[k], second[following],
                            first[following]), sign, BRONZE)


def build_handles(builder):
    """Append the two pierced stone ears and two small hanging bronze hoops."""
    start_vertices = len(builder.vertices)
    start_faces = len(builder.faces)
    for sign in (1, -1):
        _stone_lug(builder, sign)
        _bronze_drop_ring(builder, sign)
    faces = builder.faces[start_faces:]
    points = builder.vertices[start_vertices:]
    return {
        "design": "Small hanging oval bronze drop rings threaded through pierced carved-stone leaf ears; outward hanging lean with a slightly turned horizontal axis",
        "vertices": len(points),
        "authored_polygons": len(faces),
        "runtime_triangles": sum(len(face)-2 for face in faces),
        "bounds_y_up": {
            "min": [min(point[i] for point in points) for i in range(3)],
            "max": [max(point[i] for point in points) for i in range(3)],
        },
        "components": 4,
        "lug_segments": LUG_SEGMENTS,
        "ring_segments": RING_SEGMENTS,
        "wire_segments": WIRE_SEGMENTS,
        "stone_lug_hole_centre_y": HOLE_CENTRE[1],
        "stone_lug_hole_radii_xy": list(HOLE_RADII),
        "ring_vertical_diameter_m": .150,
        "ring_tangential_diameter_m": .090,
        "ring_wire_diameter_m": WIRE_RADIUS*2,
        "outward_lean_degrees": 18,
        "horizontal_turn_degrees": 10,
        "source_geometry_loaded": False,
    }
