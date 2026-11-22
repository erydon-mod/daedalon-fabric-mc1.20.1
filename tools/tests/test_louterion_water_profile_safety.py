"""Check the Louterion waterline against the placed basin mesh."""
from __future__ import annotations

import math
import unittest

from daedalon_test_support import DAEDALON_ASSETS, DAEDALON_DATA, GENERATOR, MESH_ROOT, load_json
from tools.generate_fountain_water_profiles import cross, wall_radius


def fitted_triangles():
    definition = load_json(MESH_ROOT / "basin_louterion.json")
    if not definition["fit_to_block"]:
        raise AssertionError("Recheck the Louterion waterline after changing its block fit")
    mesh = DAEDALON_ASSETS / definition["obj"].split(":", 1)[1]
    vertices, faces = [], []
    for line in mesh.read_text(encoding="utf-8").splitlines():
        if line.startswith("v "):
            vertices.append(tuple(map(float, line.split()[1:4])))
        elif line.startswith("f "):
            faces.append([int(ref.split("/")[0]) - 1 for ref in line.split()[1:]])

    # Match the runtime fit. X/Z are offsets from the waterline's center.
    low = [min(axis) for axis in zip(*vertices)]
    high = [max(axis) for axis in zip(*vertices)]
    center = ((low[0] + high[0]) / 2, low[1], (low[2] + high[2]) / 2)
    span = max(b - a for a, b in zip(low, high))
    fitted = []
    for vertex in vertices:
        axes = zip(vertex, center, definition["scale"], definition["translate"])
        fitted.append(tuple((coord - origin) / span * scale + shift for coord, origin, scale, shift in axes))
    return [tuple(fitted[index] for index in face) for face in faces]


def waterline_section(triangles, level):
    segments = []
    for triangle in triangles:
        hits = []
        for a, b in zip(triangle, triangle[1:] + triangle[:1]):
            if (a[1] - level) * (b[1] - level) < 0:
                t = (level - a[1]) / (b[1] - a[1])
                hits.append((a[0] + t * (b[0] - a[0]), a[2] + t * (b[2] - a[2])))
        if len(hits) == 2:
            segments.append(hits)
    return segments


def center_floor_height(triangles):
    hits = []
    for a, b, c in triangles:
        edge_ab = (b[0] - a[0], b[2] - a[2])
        edge_ac = (c[0] - a[0], c[2] - a[2])
        from_a_to_center = (-a[0], -a[2])
        area = cross(edge_ab, edge_ac)
        if abs(area) < 1e-12:
            continue
        u = cross(from_a_to_center, edge_ac) / area
        v = cross(edge_ab, from_a_to_center) / area
        if u >= 0 and v >= 0 and u + v <= 1:
            hits.append(a[1] + u * (b[1] - a[1]) + v * (c[1] - a[1]))
    return max(hits)


def segments_intersect(a, b, c, d):
    for axis in (0, 1):
        first_min, first_max = sorted((a[axis], b[axis]))
        second_min, second_max = sorted((c[axis], d[axis]))
        if first_min > second_max or second_min > first_max:
            return False

    def orientation(origin, endpoint, point):
        edge = (endpoint[0] - origin[0], endpoint[1] - origin[1])
        delta = (point[0] - origin[0], point[1] - origin[1])
        return cross(edge, delta)

    return (orientation(a, b, c) * orientation(a, b, d) <= 0
            and orientation(c, d, a) * orientation(c, d, b) <= 0)


class LouterionWaterProfileSafetyTests(unittest.TestCase):
    def test_waterline_is_above_the_bowl_floor_and_just_inside_the_wall(self):
        profile = load_json(DAEDALON_DATA / "fountain_water/louterion.json")
        level = profile["water_surface_y"]
        triangles = fitted_triangles()
        self.assertGreater(level - center_floor_height(triangles), 0.04)
        rim = max(point[1] for triangle in triangles for point in triangle)
        self.assertGreater(rim - level, 0.004)
        self.assertLess(rim - level, 0.01)

        section = waterline_section(triangles, level)
        points = profile["points"]
        self.assertGreaterEqual(len(points), 3)
        self.assertLessEqual(len(points), 256)
        clearances = []
        outline = list(zip(points, points[1:] + points[:1]))
        fan = [((0, 0), point) for point in points]
        for a, b in outline + fan:
            self.assertFalse(any(segments_intersect(a, b, c, d) for c, d in section))

        # The lower rim must still enclose the center in every angular span.
        angles = {math.atan2(z, x) for edge in section for x, z in edge}
        angles = sorted(angles | {-math.pi, math.pi})
        for start, end in zip(angles, angles[1:]):
            self.assertGreater(wall_radius(section, (start + end) / 2), 0.4)

        for a, b in outline:
            # The renderer reverses this winding for the upward-facing fan.
            self.assertGreater(cross(a, b), 0)
            for sample in range(33):
                t = sample / 32
                x = a[0] + t * (b[0] - a[0])
                z = a[1] + t * (b[1] - a[1])
                clearances.append(wall_radius(section, math.atan2(z, x)) - math.hypot(x, z))

        self.assertGreater(min(clearances), 0.002)
        self.assertLess(max(clearances), 0.005)
        self.assertAlmostEqual(min(clearances), profile["minimum_clearance"], places=7)
        self.assertAlmostEqual(max(clearances), profile["maximum_clearance"], places=7)

    def test_all_materials_reuse_the_fixed_basin_model_in_both_states(self):
        blockstates = DAEDALON_ASSETS / "blockstates"
        expected = set(GENERATOR.family_block_ids("louterion_basin"))
        actual = {path.stem for path in blockstates.glob("*_louterion_basin.json")}
        self.assertEqual(expected, actual)
        for block_id in sorted(actual):
            with self.subTest(block=block_id):
                variants = load_json(blockstates / f"{block_id}.json")["variants"]
                self.assertEqual({"": {"model": f"daedalon:mesh/{block_id}"}}, variants)
