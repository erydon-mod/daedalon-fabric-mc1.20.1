"""Clean, editable finial ornament profiles measured against Oliver's Meshy sources.

This module only appends portable geometry to a builder exposing ``vertex`` and
``face``. It never reads or changes the supplied originals or activates assets.
Coordinates are Blender Z-up, in the caller's one-metre authoring space.
"""

from __future__ import annotations

import math


def _mix(a, b, amount):
    return a + (b - a) * amount


def _profile(points, value):
    """Monotone piecewise cubic interpolation without sculpted-surface noise."""
    if value <= points[0][0]:
        return points[0][1]
    for (start, low), (end, high) in zip(points, points[1:]):
        if value <= end:
            t = (value - start) / (end - start)
            return _mix(low, high, t * t * (3 - 2 * t))
    return points[-1][1]


def _point(angle, radius, height):
    return (radius * math.cos(angle), radius * math.sin(angle), height)


def _lathe(builder, profile, segments=48):
    rings = []
    for z, radius in profile:
        rings.append([builder.vertex(_point(i * math.tau / segments, radius, z)) for i in range(segments)])
    for lower, upper in zip(rings, rings[1:]):
        for j in range(segments):
            k = (j + 1) % segments
            builder.face((lower[j], lower[k], upper[k], upper[j]))
    builder.face(tuple(reversed(rings[0])))
    builder.face(tuple(rings[-1]))


def _faceted_radius(profile, segments, angle, height):
    """Actual intersection with a linear-ring polygonal core, not its ideal curve."""
    radius = profile[-1][1]
    for (low_z, low_r), (high_z, high_r) in zip(profile, profile[1:]):
        if height <= high_z:
            radius = _mix(low_r, high_r, max(0., (height-low_z)/(high_z-low_z)))
            break
    half_sector = math.pi / segments
    local = (angle % (2 * half_sector)) - half_sector
    return radius * math.cos(half_sector) / math.cos(local)


def _leaf_width(t, half_angle, radius, straight_hem=False):
    tip_angle = min(half_angle * .09, .0015 / max(radius, .006))
    if straight_hem:
        spread = math.cos(math.pi*t/2) ** .66
    else:
        # Every upper leaf begins as a broad plate hidden behind the preceding
        # tier. A second pointed end left triangular slots between staggered
        # rows, even when the ornament had a closed supporting core.
        spread = 1. if t <= .5 else math.sin(math.pi*t) ** .66
    return tip_angle + (half_angle - tip_angle) * spread


def _leaf_offset(t, flare, ornament_t):
    root_blend = min(1., t / .15)
    root_blend = root_blend * root_blend * (3 - 2 * root_blend)
    offset = .018 + .010 * root_blend + flare * t ** 2.2
    # The upper leaves converge into a dressed crown instead of ending as four
    # separate raised prongs around a much thinner central pin.
    crown = max(0., min(1., (ornament_t - .88) / .12))
    crown = crown * crown * (3 - 2 * crown)
    return _mix(offset, .0015, crown), 1 - crown


def _core_surface(rings, angle, height):
    """Intersect the actual angular backing rings, including their interpolation."""
    lower, upper = rings[0], rings[-1]
    for first, second in zip(rings, rings[1:]):
        if height <= second[0]:
            lower, upper = first, second
            break
    t = max(0., min(1., (height - lower[0]) / (upper[0] - lower[0])))
    count = len(lower[1])
    sector = math.tau / count
    phase = (angle % math.tau) / sector
    index = int(phase) % count
    along = (phase - int(phase)) * sector
    first = _mix(lower[1][index], upper[1][index], t)
    following = (index + 1) % count
    second = _mix(lower[1][following], upper[1][following], t)
    return first * second * math.sin(sector) / (second * math.sin(sector-along) + first * math.sin(along))


def _leaf(builder, radial_profile, core_profile, core_rings, z0, z1, centre_angle, half_angle,
          flare, ornament_z0, ornament_span, straight_hem=False, crease_edges=None,
          smooth_front_edges=None):
    """Deeply carved artichoke leaf, with its entire rear buried in the core.

    The broad front panels meet at a raised fold. Their narrow dressed edges
    stand clear of the neighbouring tier rather than reading as lines drawn
    onto a single smooth egg. The rear follows the core even at curled tips.
    """
    heights = [0, .06, .15, .27, .40, .54, .68, .80, .90, .965, 1]
    across = [-1, -.78, 0, .78, 1]
    front, back = [], []
    for t in heights:
        z = _mix(z0, z1, t)
        # The final 1.5mm-wide end is dressed stone rather than a zero-width needle.
        radius = radial_profile(z)
        width = _leaf_width(t, half_angle, radius, straight_hem)
        # Outer artichoke leaves curl outward at their dressed point.
        # Keep a substantial carved ledge along both sides. The leaf rises out
        # of its root and curls forwards most strongly in its upper third.
        offset, crown_relief = _leaf_offset(t, flare, (z - ornament_z0) / ornament_span)
        centre_radius = radius + offset
        front_row, back_row = [], []
        for side in across:
            angle = centre_angle + side * width
            length = math.sin(math.pi * t) ** .7
            # Two broad, almost planar halves and a central fold; a 22%-wide
            # outer bevel gives their edges a readable change of lighting.
            panel = max(0., 1 - abs(side) * .30)
            edge_bevel = max(0., (abs(side) - .78) / .22)
            relief = (.020 * panel - .008 * edge_bevel) * length * crown_relief
            carving_lift = (.003 + .002*t*t) * crown_relief
            # The slight dome across the tip rounds its silhouette as well as its face.
            height = z - .00015 * side * side * t ** 8
            core_radius = _core_surface(core_rings, angle, height)
            # Curl only the visible front. Following that curl with the rear
            # detached the old upper leaves from the core by up to 26mm.
            embedding = min(.012, core_radius * .55)
            # The closed backing stays behind the carving. A genuinely dressed
            # 22mm shoulder prevents its exposed leaf edges dwindling to the
            # old 2.5mm clearance; only the terminal closed crown tapers down.
            exposed = 1. if straight_hem else max(0., min(1., (t-.15)/.25))
            exposed = exposed*exposed*(3-2*exposed)
            minimum_lip = .0025 + (.0025+.017*exposed)*crown_relief
            front_radius = _mix(core_radius+.018,
                                centre_radius+relief+carving_lift, exposed)
            front_radius = max(front_radius,core_radius+minimum_lip)
            front_row.append(builder.vertex(_point(angle, front_radius, height)))
            inner = _faceted_radius(core_profile, 48, angle, height)
            back_radius = min(core_radius-embedding, inner-min(.008, inner*.40))
            back_row.append(builder.vertex(_point(angle, back_radius, height)))
        front.append(front_row)
        back.append(back_row)
    rows, columns = len(heights), len(across)
    if smooth_front_edges is not None:
        # These are curved leaf fronts, not a stack of horizontal stone ledges.
        # Keep their transverse joins and central fold smoothly shaded even
        # where a hidden emergence row crosses the generic sharp-angle gate.
        for i in range(1,rows-1):
            smooth_front_edges.extend((front[i][j],front[i][j+1]) for j in range(columns-1))
        smooth_front_edges.extend((front[i][2],front[i+1][2]) for i in range(rows-1))
    if crease_edges is not None:
        for i in range(rows-1):
            if _mix(z0,z1,(heights[i]+heights[i+1])/2) > ornament_z0+ornament_span*.93:
                continue
            # Keep a firm dressed bevel, while the two broad panels retain the
            # original smooth carved fold rather than an origami-like crease.
            for j in (1,3):
                crease_edges.append((front[i][j],front[i+1][j]))
    for i in range(rows - 1):
        for j in range(columns - 1):
            builder.face((front[i][j], front[i][j + 1], front[i + 1][j + 1], front[i + 1][j]))
            builder.face((back[i + 1][j], back[i + 1][j + 1], back[i][j + 1], back[i][j]))
    boundary = ([(0, j) for j in range(columns)]
                + [(i, columns - 1) for i in range(1, rows)]
                + [(rows - 1, j) for j in range(columns - 2, -1, -1)]
                + [(i, 0) for i in range(rows - 2, 0, -1)])
    first_wall = len(builder.faces)
    for current, following in zip(boundary, boundary[1:] + boundary[:1]):
        i, j = current
        k, l = following
        builder.face((front[i][j], back[i][j], back[k][l], front[k][l]))
    return list(range(first_wall, len(builder.faces)))


def _kynara(builder, z0, z1, max_radius):
    span = z1 - z0
    envelope = [(0, .51), (.12, .67), (.27, .75), (.40, .75), (.54, .68),
                (.67, .56), (.78, .39), (.88, .22), (.96, .065), (1, .006)]

    def radius(z):
        return max_radius * _profile(envelope, (z - z0) / span)

    tiers = [(8, .00, .30, .110), (8, .13, .50, .100),
             (8, .33, .70, .085), (6, .54, .87, .065), (4, .75, .999, .040)]
    core_profile = [(z0 + span*i/24, radius(z0 + span*i/24)) for i in range(25)]

    def boundary_width(t, tier, count, bottom, top, flare):
        # Leaf sides are straight chords between authored rows, not the ideal
        # sine curve. Use those same chord endpoints to avoid promising cover
        # where a tapered triangle has already ended.
        heights = [0, .06, .15, .27, .40, .54, .68, .80, .90, .965, 1]
        low, high = 0., 1.
        for first, second in zip(heights, heights[1:]):
            if t <= second:
                low, high = first, second
                break
        points = []
        for local_t in (low, high):
            global_t = _mix(bottom, top, local_t)
            r = radius(z0+span*global_t)
            width = _leaf_width(local_t, math.pi/count*1.08, r, tier == 0)
            offset, attenuation = _leaf_offset(local_t, max_radius*flare, global_t)
            edge = r+offset+.0035*math.sin(math.pi*local_t)**.7*attenuation
            points.append((edge*math.cos(width), edge*math.sin(width)))
        amount = (t-low)/(high-low)
        return max(.00001, math.atan2(_mix(points[0][1],points[1][1],amount),
                                      _mix(points[0][0],points[1][0],amount))-.002)

    def backing(z, angle):
        ornament_t = (z - z0) / span
        clearance, ledge, foremost = -1., 0., -1.
        for tier, (count, bottom, top, flare) in enumerate(tiers):
            if not bottom <= ornament_t <= top:
                continue
            t = (ornament_t - bottom) / (top - bottom)
            width = boundary_width(t, tier, count, bottom, top, flare)
            period = math.tau / count
            phase = (tier % 2) * .5 * period
            distance = abs((angle-phase + period/2) % period - period/2)
            offset, attenuation = _leaf_offset(t, max_radius * flare, ornament_t)
            edge = offset + .0035 * math.sin(math.pi*t) ** .7 * attenuation
            ledge = max(ledge, edge - min(.008, edge * .30))
            if distance <= width:
                side = distance/width
                panel = max(0., 1-side*.30)
                bevel = max(0., (side-.78)/.22)
                front = offset+(.015*panel-.007*bevel)*math.sin(math.pi*t)**.7*attenuation
                if front > foremost:
                    foremost, clearance = front, (width-distance)/width
        # Lift only the narrow exposed seams; broad carved leaf faces keep
        # their full depth over the actual body. This is the body itself, not
        # a second shell masking an obsolete deep core.
        fill = max(0., min(1., (.24-clearance) / .24))
        fill = fill * fill * (3 - 2 * fill)
        crown = max(0., min(1., (ornament_t-.96)/.04))
        return radius(z) + ledge * fill + .0015*crown*crown*(3-2*crown)

    carving_core_rings = [(z0 + span * i / 24,
                           [backing(z0 + span * i / 24, a * math.tau / 48) for a in range(48)])
                          for i in range(25)]

    def local_infill(z, angle):
        # Fill only the short seam immediately before the second tier starts.
        # The approved leaf faces keep their existing coordinates and normals.
        height = max(0., 1-abs(z-.363)/.025)
        distance = abs((angle-math.pi/8+math.pi/8) % (math.pi/4)-math.pi/8)
        around = max(0., 1-distance/(math.pi/12))
        around = around*around*(3-2*around)
        upper_height = max(0.,min(1.,(z-.79)/.02,(.875-z)/.025))
        upper_distance = abs((angle+math.pi/6) % (math.pi/3)-math.pi/6)
        upper_around = max(0.,1-upper_distance/(math.pi/12))
        upper_around = upper_around*upper_around*(3-2*upper_around)
        crown = max(0., min(1., (z-.95)/.05))
        crown = crown*crown*(3-2*crown)
        return .0045*height*around + .004*upper_height*upper_around + .0005*crown

    core_rings = [(z,[r+local_infill(z,a*math.tau/48) for a,r in enumerate(radii)])
                  for z,radii in carving_core_rings]
    core_vertices = [[builder.vertex(_point(a * math.tau / 48, r, z))
                      for a, r in enumerate(radii)] for z, radii in core_rings]
    for lower, upper in zip(core_vertices, core_vertices[1:]):
        for a in range(48):
            b = (a + 1) % 48
            builder.face((lower[a], lower[b], upper[b], upper[a]))
    builder.face(tuple(reversed(core_vertices[0])))
    builder.face(tuple(core_vertices[-1]))
    flat_faces, crease_edges, smooth_front_edges = [], [], []
    for tier, (count, bottom, top, flare) in enumerate(tiers):
        for leaf in range(count):
            flat_faces.extend(_leaf(
                builder, radius, core_profile, carving_core_rings, z0 + span * bottom, z0 + span * top,
                (leaf + (tier % 2) * .5) * math.tau / count,
                math.pi / count * 1.08, max_radius * flare, z0, span, tier == 0,
                crease_edges,smooth_front_edges))
    return {"ornament": "five staggered tiers of deeply carved overlapping artichoke leaves with dressed edges and curled tips",
            "petal_tiers": [item[0] for item in tiers], "petal_count": sum(item[0] for item in tiers),
            "leaf_core_profile": [(z, radius(z)) for z, _ in core_rings],
            "leaf_core_profile_role": "recessed baseline; actual outer support uses leaf_backing_rings",
            "leaf_core_segments": 48,
            "leaf_backing_rings": core_rings,
            "leaf_backing_target_channel_metres": .008,
            "body_local_infill": "short lower-tier and upper-root seams plus terminal crown only; approved carved leaf vertices and normals unchanged",
            "lower_seam_infill_maximum_metres": .0045,
            "upper_root_infill_maximum_metres": .004,
            "terminal_crown_infill_maximum_metres": .0005,
            "lowest_leaf_hem_y": z0, "lowest_leaf_hem": "continuous horizontal full-width cut",
            "crown": "overlapping upper leaves converge onto integrated filled body",
            "leaf_embedding_metres": .012, "leaf_root_standout_metres": .018,
            "leaf_side_standout_metres": .028,
            "leaf_centre_fold_metres": .020,
            "leaf_edge_bevel_metres": .008,
            "leaf_front_additional_lift_metres": .003,
            "leaf_tip_additional_curl_metres": .002,
            "leaf_visible_lip_target_metres": .022,
            "leaf_hidden_root_clearance_metres": .018,
            "leaf_visible_front_rise": "upper-tier roots stay behind the previous tier; the rise completes beneath that tier over local leaf height .15 to .40",
            "leaf_lip_taper": "only the closed terminal crown above normalized ornament height .88",
            "sharp_edge_pairs": crease_edges,
            "smooth_front_edge_pairs": smooth_front_edges,
            "leaf_tip_flare_fractions": [item[3] for item in tiers],
            "tip_dressing_metres": .0015, "flat_face_indices": flat_faces}


def build_ornament(style, builder, z0, z1, max_radius):
    """Append a measured ornament, leaving the common pedestal to the caller."""
    if not 0 <= z0 < z1 <= 1 or max_radius <= 0:
        raise ValueError("Ornaments require a positive radius and a bounded one-metre height band")
    functions = {"kynara": _kynara}
    try:
        function = functions[style]
    except KeyError as exception:
        raise ValueError("Unsupported analytical finial ornament: " + style) from exception
    return function(builder, z0, z1, max_radius)
