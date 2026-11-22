"""Face-efficient foliage, scrolls and dentils following Oliver's fifth panel."""
from __future__ import annotations

import math


def renaissance(api):
    relief = api.Relief()
    vector = api.Vector
    centre = vector((.5, .5))
    profile = ((.5, .016), (.494, .024), (.484, .026), (.474, .021), (.468, .019),
               (.414, .019), (.406, .027), (.395, .029), (.384, .024), (.374, .020),
               (.366, .031), (.355, .034), (.343, .023), (.333, .016))
    bottom = api.square_ring(.5, 0)
    rings = [api.square_ring(radius, z) for radius, z in profile]
    triangles = []
    for i in range(4):
        j = (i + 1) % 4
        triangles += api.quad(bottom[i], bottom[j], rings[0][j], rings[0][i])
    for a, b in zip(rings, rings[1:]):
        for i in range(4):
            j = (i + 1) % 4
            triangles += api.quad(a[i], a[j], b[j], b[i])
    triangles += api.quad(*rings[-1])
    triangles += api.quad(bottom[3], bottom[2], bottom[1], bottom[0])
    relief.add("backing_renaissance_panel", triangles)

    # Low-cost closed dentils retain the strong repeated outer frame.
    for edge in range(4):
        angle = edge * math.pi / 2
        along = vector((math.cos(angle), math.sin(angle)))
        across = vector((-along.y, along.x))
        for index in range(12):
            location = centre + along * (-.355 + .71 * index / 11) + across * .446
            bottom, shoulder, top = [], [], []
            for u, v in ((-1, -1), (1, -1), (1, 1), (-1, 1)):
                point = location + along * (.013 * u) + across * (.026 * v)
                bottom.append(vector((point.x, point.y, api.EMBEDDED_Z)))
                shoulder.append(vector((point.x, point.y, .040)))
                crown = location + along * (.010 * u) + across * (.023 * v)
                top.append(vector((crown.x, crown.y, .044)))
            triangles = api.quad(*top)
            triangles += api.quad(bottom[3], bottom[2], bottom[1], bottom[0])
            for i in range(4):
                j = (i + 1) % 4
                triangles += api.quad(bottom[i], bottom[j], shoulder[j], shoulder[i])
                triangles += api.quad(shoulder[i], shoulder[j], top[j], top[i])
            relief.add(f"dentil_{edge}_{index}", triangles)

    # The four cardinal acanthus sprays preserve the reference's floral cross.
    for branch in range(4):
        angle = branch * math.pi / 2
        along = vector((math.cos(angle), math.sin(angle)))
        across = vector((-along.y, along.x))

        def point(u, v=0):
            return tuple(centre + along * u + across * v)

        relief.leaf(f"acanthus_crown_{branch}", point(.045), point(.337), .048, .051,
                    ornate=True)
        for side in (-1, 1):
            relief.leaf(f"acanthus_side_{branch}_{side}", point(.08), point(.268, .079 * side),
                        .034, .043, ornate=True, bend=.010 * side)
            relief.leaf(f"acanthus_outer_{branch}_{side}", point(.18, .025 * side),
                        point(.307, .064 * side), .022, .030)
            relief.leaf(f"acanthus_lower_{branch}_{side}", point(.10, .02 * side),
                        point(.211, .112 * side), .022, .027)
        ribs = []
        for t in (.12, .25, .38, .51, .64, .77, .89):
            p = vector(point(.045 + (.337 - .045) * t))
            ribs.append((p.x, p.y, api.EMBEDDED_Z + .051 * math.sin(math.pi * t) ** .7 + .0003))
        relief.tube(f"acanthus_crown_rib_{branch}", ribs, .0017)

    # Eight generous scrolls occupy the quadrants rather than replacing them
    # with a dense, noisy surface. Fine subsidiary leaves retain the foliage.
    for quadrant in range(4):
        angle = quadrant * math.pi / 2
        along = vector((math.cos(angle), math.sin(angle)))
        across = vector((-along.y, along.x))
        for mirror in (-1, 1):
            anchor = centre + along * .188 + across * (.176 * mirror)
            points = []
            for index in range(17):
                t = index / 16
                theta = mirror * (-.7 + t * 2 * math.pi * 1.15)
                radius = .079 * (1 - t) + .013
                position = anchor + along * (radius * math.cos(theta)) + across * (radius * math.sin(theta))
                points.append((position.x, position.y, .030))
            relief.tube(f"foliage_scroll_{quadrant}_{mirror}", points, .0055, sides=4)
            for index in range(4):
                t = .07 + .13 * index
                theta = mirror * (-.7 + t * 2 * math.pi * 1.15)
                radius = .079 * (1 - t) + .013
                direction = along * math.cos(theta) + across * math.sin(theta)
                position = anchor + direction * radius
                start = position - direction * .008
                end = position + direction * .038
                relief.leaf(f"scroll_leaf_{quadrant}_{mirror}_{index}", tuple(start), tuple(end),
                            .014, .029, bend=.006 * mirror)

    # A layered rosette and a clustered centre keep the flower recognisable.
    for index in range(12):
        angle = index * 2 * math.pi / 12
        direction = vector((math.cos(angle), math.sin(angle)))
        relief.leaf(f"rosette_underleaf_{index}", tuple(centre + direction * .018),
                    tuple(centre + direction * .123), .025, .032)
        rotated = vector((math.cos(angle + math.pi / 12), math.sin(angle + math.pi / 12)))
        relief.leaf(f"rosette_petals_{index}", tuple(centre + rotated * .018),
                    tuple(centre + rotated * .091), .019, .050)
    relief.dome("rosette_centre", (.5, .5), .019, .019, .074, segments=10)
    for index in range(10):
        angle = index * 2 * math.pi / 10
        relief.dome(f"rosette_seeds_{index}", (.5 + .024 * math.cos(angle), .5 + .024 * math.sin(angle)),
                    .006, .006, .069, segments=5)
    for sx, sy in ((-1, -1), (1, -1), (1, 1), (-1, 1)):
        anchor = centre + vector((sx * .168, sy * .168))
        for index in range(8):
            angle = index * math.pi / 4
            direction = vector((math.cos(angle), math.sin(angle)))
            position = anchor + direction * .020
            relief.dome(f"small_floret_{sx}_{sy}_{index}", tuple(position), .017, .0075,
                        .034, angle=angle, segments=8)
        relief.dome(f"small_floret_boss_{sx}_{sy}", tuple(anchor), .009, .009, .045, segments=6)
    relief.motifs = {"dentils": 48, "acanthus_crowns": 4, "side_acanthus_leaves": 8,
                     "outer_acanthus_leaves": 8, "lower_acanthus_leaves": 8, "leaf_ribs": 4,
                     "foliage_scrolls": 8, "scroll_leaves": 32, "small_florets": 4,
                     "rosette_layers": 2, "rosette_petals": 24, "centre_seeds": 10}
    return relief
