"""Numeric Georgian panel draft, using Oliver's source 4 as visual reference.

No source mesh is loaded. The shared authoring module supplies materials,
prepared normals and export; this builder supplies one bounded relief design.
"""
from __future__ import annotations

import math


def georgian(api):
    relief = api.Relief()
    vector = api.Vector
    embedded = api.EMBEDDED_Z

    # A closed rectangular support and clean, stepped square moulding.
    profile = ((.5, .016), (.494, .025), (.485, .029), (.476, .025),
               (.469, .020), (.462, .020), (.455, .025), (.447, .025),
               (.438, .016))
    bottom = api.square_ring(.5, 0)
    rings = [api.square_ring(radius, z) for radius, z in profile]
    triangles = []
    for i in range(4):
        j = (i + 1) % 4
        triangles.extend(api.quad(bottom[i], bottom[j], rings[0][j], rings[0][i]))
    for first, second in zip(rings, rings[1:]):
        for i in range(4):
            j = (i + 1) % 4
            triangles.extend(api.quad(first[i], first[j], second[j], second[i]))
    triangles.extend(api.quad(*rings[-1]))
    triangles.extend(api.quad(bottom[3], bottom[2], bottom[1], bottom[0]))
    relief.add("backing_georgian_panel", triangles, smoothing=30)

    def moulding(name, section, segments):
        """Round annular moulding; only its two buried perimeter edges are open."""
        rings, normals = [], []
        for index, (radius, z) in enumerate(section):
            before = section[max(0, index - 1)]
            after = section[min(len(section) - 1, index + 1)]
            nr, nz = after[1] - before[1], before[0] - after[0]
            points, ns = [], []
            for i in range(segments):
                angle = i * math.tau / segments
                c, s = math.cos(angle), math.sin(angle)
                points.append(vector((.5 + radius * c, .5 + radius * s, z)))
                ns.append(vector((nr * c, nr * s, nz)).normalized())
            rings.append(points)
            normals.append(ns)
        triangles, corner_normals = [], []
        for first, second, first_ns, second_ns in zip(rings, rings[1:], normals, normals[1:]):
            for i in range(segments):
                j = (i + 1) % segments
                triangles.extend(api.quad(first[i], first[j], second[j], second[i]))
                corner_normals.extend(api.quad(first_ns[i], first_ns[j], second_ns[j], second_ns[i]))
        relief.add(name, triangles, normals=corner_normals)

    moulding("outer_circular_moulding", ((.468, embedded), (.462, .029),
             (.450, .029), (.443, embedded)), 64)
    moulding("fluted_field_moulding", ((.354, embedded), (.348, .029),
             (.338, .029), (.333, embedded)), 64)
    moulding("flower_surround", ((.137, embedded), (.131, .035),
             (.122, .035), (.117, embedded)), 32)

    # Thirty-two long, round-ended flutes, rather than a texture or a dense
    # decimated fan. Each outline is a small smooth curved strip, open only
    # along its buried inner and outer bases.
    outline = [(.145, -.006), (.302, -.026)]
    outline.extend((.302 + .024 * math.cos(a), .026 * math.sin(a))
                   for a in (-3 * math.pi / 8, -math.pi / 4, -math.pi / 8, 0,
                             math.pi / 8, math.pi / 4, 3 * math.pi / 8, math.pi / 2))
    outline.append((.145, .006))
    outline.extend((.145 + .007 * math.cos(a), .006 * math.sin(a))
                   for a in (3 * math.pi / 4, math.pi, 5 * math.pi / 4))
    for flute in range(32):
        angle = flute * math.tau / 32
        c, s = math.cos(angle), math.sin(angle)
        cross_sections, normals = [], []
        for index, (r, lateral) in enumerate(outline):
            before = vector(outline[index - 1])
            after = vector(outline[(index + 1) % len(outline)])
            tangent = (after - before).normalized()
            across = vector((-tangent.y, tangent.x))
            points, ns = [], []
            for u in (-1, 0, 1):
                rr, ww = r + across.x * .0025 * u, lateral + across.y * .0025 * u
                z = embedded + .014 * math.sqrt(1 - u * u)
                points.append(vector((.5 + rr * c - ww * s, .5 + rr * s + ww * c, z)))
                local = vector((across.x * u / .0025, across.y * u / .0025,
                                (z - embedded) / (.014 * .014)))
                ns.append(vector((local.x * c - local.y * s, local.x * s + local.y * c, local.z)).normalized())
            cross_sections.append(points)
            normals.append(ns)
        triangles, corner_normals = [], []
        for i in range(len(outline)):
            j = (i + 1) % len(outline)
            for across in range(2):
                triangles.extend(api.quad(cross_sections[i][across], cross_sections[j][across],
                                         cross_sections[j][across + 1], cross_sections[i][across + 1]))
                corner_normals.extend(api.quad(normals[i][across], normals[j][across],
                                             normals[j][across + 1], normals[i][across + 1]))
        relief.add(f"radial_flute_{flute}", triangles, normals=corner_normals)

    for egg in range(32):
        angle = (egg + .5) * math.tau / 32
        c, s = math.cos(angle), math.sin(angle)
        relief.dome(f"egg_{egg}", (.5 + .397 * c, .5 + .397 * s),
                    .020, .028, .026, angle + math.pi / 2, segments=10)
        dart_angle = egg * math.tau / 32
        radial = vector((math.cos(dart_angle), math.sin(dart_angle)))
        tangent = vector((-radial.y, radial.x))
        center = vector((.5, .5)) + radial * .397
        base = [vector((*(center + tangent * a + radial * b), embedded))
                for a, b in ((0, -.025), (-.004, 0), (0, .025), (.004, 0))]
        apex = vector((*center, .032))
        relief.add(f"dart_{egg}", [(base[i], base[(i + 1) % 4], apex) for i in range(4)])

    for petal in range(16):
        angle = petal * math.tau / 16
        c, s = math.cos(angle), math.sin(angle)
        relief.dome(f"flower_petal_{petal}", (.5 + .061 * c, .5 + .061 * s),
                    .040, .011, .034, angle, segments=8)
    for bead in range(28):
        angle = bead * math.tau / 28
        relief.dome(f"flower_bead_{bead}", (.5 + .108 * math.cos(angle), .5 + .108 * math.sin(angle)),
                    .0065, .0065, .025, segments=6)
    relief.dome("flower_central_boss", (.5, .5), .020, .020, .040, segments=12)

    def scroll(sx, sy, transpose):
        control = ((.321, .313), (.352, .283), (.429, .293), (.415, .255))
        points = []
        for t in (0, 1 / 6, 2 / 6, 3 / 6, 4 / 6, 5 / 6, 1):
            r = 1 - t
            x = r ** 3 * control[0][0] + 3 * r * r * t * control[1][0] + 3 * r * t * t * control[2][0] + t ** 3 * control[3][0]
            y = r ** 3 * control[0][1] + 3 * r * r * t * control[1][1] + 3 * r * t * t * control[2][1] + t ** 3 * control[3][1]
            points.append((x, y))
        for i in range(1, 13):
            angle = -i / 12 * math.pi * 1.7
            radius = .020 - .016 * i / 12
            points.append((.395 + radius * math.cos(angle), .255 + radius * math.sin(angle)))
        if transpose:
            points = [(y, x) for x, y in points]
        return [(.5 + sx * x, .5 + sy * y, .020) for x, y in points]

    def volute(name, centers):
        """Smooth curved ribbon; both ends and longitudinal edges are buried."""
        centers = [vector(p) for p in centers]
        rings, normals = [], []
        for index, center in enumerate(centers):
            tangent = centers[min(index + 1, len(centers) - 1)] - centers[max(index - 1, 0)]
            across = vector((-tangent.y, tangent.x, 0)).normalized()
            t = index / (len(centers) - 1)
            width = .0045 * (1 - .5 * t)
            height = .013 * min(1, 6 * min(t, 1 - t))
            points, ns = [], []
            for u in (-1, 0, 1):
                point = center + across * width * u
                point.z = embedded + height * math.sqrt(1 - u * u)
                points.append(point)
                ns.append(vector((0, 0, 1)) if u == 0 else across * u)
            rings.append(points)
            normals.append(ns)
        triangles, corner_normals = [], []
        for first, second, first_ns, second_ns in zip(rings, rings[1:], normals, normals[1:]):
            for across in range(2):
                triangles.extend(api.quad(first[across], second[across], second[across + 1], first[across + 1]))
                corner_normals.extend(api.quad(first_ns[across], second_ns[across], second_ns[across + 1], first_ns[across + 1]))
        relief.add(name, triangles, normals=corner_normals)

    for sx, sy in ((-1, -1), (1, -1), (1, 1), (-1, 1)):
        start = (.5 + sx * .318, .5 + sy * .318)
        for leaf, (x, y) in enumerate(((.425, .370), (.425, .399), (.425, .425), (.399, .425), (.370, .425))):
            end = vector((.5 + sx * x, .5 + sy * y))
            center = (vector(start) + end) * .5
            delta = end - vector(start)
            relief.dome(f"corner_palmette_{sx}_{sy}_{leaf}", center,
                        delta.length * .5, .011, .026, math.atan2(delta.y, delta.x), segments=8)
        for transpose in (False, True):
            volute(f"corner_scroll_{sx}_{sy}_{int(transpose)}", scroll(sx, sy, transpose))
        relief.dome(f"corner_boss_{sx}_{sy}", start, .010, .010, .027, segments=8)

    relief.motifs = {"radial_flutes": 32, "egg_and_dart": 32, "flower_petals": 16,
                     "flower_beads": 28, "central_boss": 1, "corner_palmettes": 4,
                     "corner_palmette_leaves": 20, "corner_scrolls": 8, "corner_bosses": 4}
    return relief
