from __future__ import annotations

import math
import unittest

from daedalon_test_support import DAEDALON_ASSETS, load_json


def cross(a, b, c):
    return (b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0])


def contains(outline, point, tolerance=1e-7):
    inside = False
    for a, b in zip(outline, outline[1:] + outline[:1]):
        dx, dz = b[0] - a[0], b[1] - a[1]
        length = dx * dx + dz * dz
        t = max(0, min(1, ((point[0] - a[0]) * dx + (point[1] - a[1]) * dz) / length)) if length else 0
        if math.hypot(point[0] - a[0] - t * dx, point[1] - a[1] - t * dz) < tolerance:
            return True
        if (a[1] > point[1]) != (b[1] > point[1]):
            if point[0] < a[0] + (b[0] - a[0]) * (point[1] - a[1]) / (b[1] - a[1]):
                inside = not inside
    return inside


class CapitalShaftProfilesSafetyTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.profiles = load_json(DAEDALON_ASSETS / "geometry/capital_shaft_profiles.json")["profiles"]

    def test_authored_outlines_and_bottom_faces_cover_the_same_area(self):
        self.assertEqual({"square_shaft", "gothic_shaft", "square_shaft_double", "gothic_shaft_double"}, set(self.profiles))
        for name, profile in self.profiles.items():
            with self.subTest(profile=name):
                points = profile["outline"]
                self.assertTrue(32 < len(points) <= 128)
                self.assertRegex(profile["source_sha256"], r"^[0-9a-f]{64}$")
                self.assertEqual(0.001, profile["simplification_tolerance_pixels"])
                area = sum(cross((0, 0), a, b) for a, b in zip(points, points[1:] + points[:1]))
                triangles = [cross(*(points[i] for i in face)) for face in profile["bottom_triangles"]]
                self.assertTrue(all(value > 0 for value in triangles), "Undersides must face down with no degenerate faces")
                self.assertAlmostEqual(area, sum(triangles), places=9)
                self.assertTrue(all(0 < x < 1 and 0 < z < 1 for x, z in points))

    def test_large_outline_spans_and_covers_each_of_four_real_shaft_tops(self):
        for family in ("square_shaft", "gothic_shaft"):
            single = self.profiles[family]
            large = self.profiles[family + "_double"]
            for x in range(2):
                for z in range(2):
                    for px, pz in single["outline"]:
                        self.assertTrue(contains(large["outline"], ((px + x) / 2, (pz + z) / 2),
                                                 large["simplification_tolerance_pixels"] / 32 + 1e-9), family)
            for axis in range(2):
                small_span = max(p[axis] for p in single["outline"]) - min(p[axis] for p in single["outline"])
                large_span = max(p[axis] for p in large["outline"]) - min(p[axis] for p in large["outline"])
                self.assertAlmostEqual(small_span + 1, large_span * 2, places=8)

    def test_lofts_do_not_self_intersect_between_the_shaft_and_circular_rim(self):
        radius = 11.5 / 32
        for name, profile in self.profiles.items():
            points = profile["outline"]
            angles = profile["circle_angles"]
            self.assertEqual(len(points), len(angles))
            self.assertTrue(all(a < b for a, b in zip(angles, angles[1:])))
            for step in range(21):
                fraction = step / 20
                ring = [(x * (1 - fraction) + (0.5 + math.cos(angle) * radius) * fraction,
                         z * (1 - fraction) + (0.5 + math.sin(angle) * radius) * fraction)
                        for (x, z), angle in zip(points, angles)]
                edges = list(zip(ring, ring[1:] + ring[:1]))
                for i, (a, b) in enumerate(edges):
                    for j in range(i + 2, len(edges)):
                        if i == 0 and j == len(edges) - 1:
                            continue
                        c, d = edges[j]
                        crosses = cross(a, b, c) * cross(a, b, d) < -1e-16 and cross(c, d, a) * cross(c, d, b) < -1e-16
                        self.assertFalse(crosses, f"Self-intersecting collar: {name}, height {fraction}")


if __name__ == "__main__":
    unittest.main()
