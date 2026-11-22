"""Draft the Byzantine panel from numeric motifs, using the shared authoring API.

Oliver's Meshy coffered reference 3 supplies the design vocabulary only. The
source mesh is never loaded or copied. Activation/export is owned by the shared
authoring tool after acceptance of the Corinthian pilot.
"""
from __future__ import annotations

import math


def _rings(api, center, profile, sides, phase):
    x, y = center
    return [[api.Vector((x + radius * math.cos(phase + 2 * math.pi * i / sides),
                         y + radius * math.sin(phase + 2 * math.pi * i / sides), z))
             for i in range(sides)] for radius, z in profile]


def _plate(api, relief, name, center, profile, sides=8, phase=math.pi / 8):
    """A closed polygonal moulded plate with explicit outward winding."""
    rings = _rings(api, center, profile, sides, phase)
    triangles = []
    for a, b in zip(rings, rings[1:]):
        for i in range(sides):
            j = (i + 1) % sides
            triangles.extend(api.quad(a[i], a[j], b[j], b[i]))
    back = api.Vector((*center, profile[0][1]))
    front = api.Vector((*center, profile[-1][1]))
    for i in range(sides):
        j = (i + 1) % sides
        triangles.extend(((rings[0][j], rings[0][i], back),
                          (rings[-1][i], rings[-1][j], front)))
    relief.add(name, triangles, smoothing=30)


def _annulus(api, relief, name, center, profile, sides=24):
    """Closed circular moulding; its profile includes the buried underside."""
    rings = _rings(api, center, profile, sides, 0)
    triangles = []
    for a, b in zip(rings, rings[1:] + rings[:1]):
        for i in range(sides):
            j = (i + 1) % sides
            triangles.extend(api.quad(a[i], a[j], b[j], b[i]))
    relief.add(name, triangles, smoothing=40)


def _backing(api, relief):
    # Every open decorative skin is rooted at .015, below the .016 minimum
    # front of this complete closed mounting base. Raised straight profiles
    # preserve the reference's square mouldings without costly decimation.
    profile = ((.5, .016), (.496, .021), (.489, .025), (.478, .024),
               (.468, .019), (.455, .018), (.447, .027), (.438, .028),
               (.428, .020), (.423, .016), (.365, .016), (.358, .026),
               (.350, .027), (.341, .018), (.330, .016))
    back = api.square_ring(.5, 0)
    rings = [api.square_ring(radius, z) for radius, z in profile]
    triangles = []
    for i in range(4):
        j = (i + 1) % 4
        triangles.extend(api.quad(back[i], back[j], rings[0][j], rings[0][i]))
    for a, b in zip(rings, rings[1:]):
        for i in range(4):
            j = (i + 1) % 4
            triangles.extend(api.quad(a[i], a[j], b[j], b[i]))
    triangles.extend(api.quad(*rings[-1]))
    triangles.extend(api.quad(back[3], back[2], back[1], back[0]))
    relief.add("backing_byzantine_panel", triangles, smoothing=30)


def byzantine(api):
    """Return one shared, closed-backed panel within the 6,000-triangle cap."""
    relief = api.Relief()
    _backing(api, relief)
    for edge in range(4):
        theta = edge * math.pi / 2
        tangent = api.Vector((math.cos(theta), math.sin(theta)))
        outward = api.Vector((-math.sin(theta), math.cos(theta)))

        def xy(t, radius):
            return api.Vector((.5, .5)) + tangent * t + outward * radius

        # Two closed round strands alternate their depth at each crossing.
        # This is real over/under geometry rather than a painted braid.
        waves, samples = 6, 36
        for strand in range(2):
            sign = 1 if strand == 0 else -1
            centers = []
            for sample in range(samples + 1):
                t = sample / samples
                phase = 2 * math.pi * waves * t
                p = xy(-.365 + .730 * t, .393 + sign * .0175 * math.sin(phase))
                centers.append((p.x, p.y, .040 + sign * .009 * math.cos(phase)))
            relief.tube(f"interlace_{edge}_{strand}", centers, .0048, sides=4)
        for bead in range(10):
            p = xy(-.303 + .606 * bead / 9, .335)
            relief.dome(f"square_bead_{edge}_{bead}", tuple(p), .008, .008, .024, segments=6)

    _plate(api, relief, "octagonal_coffer", (.5, .5),
           ((.356, api.EMBEDDED_Z), (.356, .030), (.348, .037),
            (.337, .037), (.325, .029), (.312, .028), (.298, .035),
            (.288, .035), (.274, .022)))
    _annulus(api, relief, "flower_outer_moulding", (.5, .5),
             ((.255, .015), (.255, .025), (.248, .034),
              (.240, .026), (.240, .015)), sides=32)
    for bead in range(48):
        angle = 2 * math.pi * bead / 48
        relief.dome(f"circle_bead_{bead}", (.5 + .229 * math.cos(angle), .5 + .229 * math.sin(angle)),
                    .0073, .0073, .023, segments=6)

    # Eight broad leaves and eight interstitial round bosses retain the
    # reference's radial floral centre without changing its motif vocabulary.
    for petal in range(8):
        angle = 2 * math.pi * petal / 8
        boss_angle = angle + math.pi / 8
        relief.dome(f"rosette_interstitial_boss_{petal}",
                    (.5 + .170 * math.cos(boss_angle), .5 + .170 * math.sin(boss_angle)),
                    .012, .012, .034, segments=8)
        direction = api.Vector((math.cos(angle), math.sin(angle)))
        origin = api.Vector((.5, .5))
        start, end, height = .055, .208, .047
        relief.leaf(f"rosette_main_{petal}", tuple(origin + direction * start),
                    tuple(origin + direction * end), .044, height)
        centers = []
        # Match the leaf's exact central strip stations so the ridge remains
        # continuously visible instead of clipping through it.
        for t in (.25, .5, .75):
            p = origin + direction * (start + (end - start) * t)
            z = api.EMBEDDED_Z + height * math.sin(math.pi * t) ** .7 + .0015
            centers.append((p.x, p.y, z))
        relief.tube(f"rosette_vein_{petal}", centers, .0013, sides=4)
    _annulus(api, relief, "flower_centre_moulding", (.5, .5),
             ((.057, .015), (.057, .042), (.041, .049), (.041, .015)), sides=20)
    relief.dome("flower_centre_boss", (.5, .5), .034, .034, .060, segments=12)

    for sx, sy in ((-1, -1), (1, -1), (1, 1), (-1, 1)):
        center = (.5 + sx * .393, .5 + sy * .393)
        _plate(api, relief, f"corner_plinth_{sx}_{sy}", center,
               ((.047, .015), (.047, .023), (.040, .026)), sides=4, phase=math.pi / 4)
        relief.dome(f"corner_boss_{sx}_{sy}", center, .020, .020, .034, segments=8)
        start = (.5 + sx * .258, .5 + sy * .258)
        for leaf in range(5):
            angle = math.pi / 4 + (leaf - 2) * .25
            end = (.5 + sx * (.258 + .077 * math.cos(angle)),
                   .5 + sy * (.258 + .077 * math.sin(angle)))
            relief.leaf(f"corner_palmette_{sx}_{sy}_{leaf}", start, end, .010, .025)
    relief.motifs = {"double_braid_strands": 8, "braid_waves_per_side": 6,
                     "square_beads": 40, "circle_beads": 48, "octagonal_coffer": 1,
                     "radial_petals": 8, "interstitial_bosses": 8, "corner_palmette_leaves": 20,
                     "petal_veins": 8, "corner_bosses": 4, "central_boss": 1}
    return relief
