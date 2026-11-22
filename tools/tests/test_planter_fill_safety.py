from __future__ import annotations

import functools
import json
import math
import re
import unittest

from daedalon_test_support import JAVA_ROOT, MESH_ROOT, REPO_ROOT, sha256


EVIDENCE = REPO_ROOT / "docs/evidence/anthophoros-fill.json"


@functools.lru_cache(maxsize=3)
def section_segments(width: int, height: float = 0.9):
    """Intersect the actual authored triangles with the fill plane, in metres."""
    vertices, faces = [], []
    for line in (MESH_ROOT / f"anthophoros_{width}m.obj").read_text().splitlines():
        if line.startswith("v "):
            vertices.append(tuple(map(float, line.split()[1:])))
        elif line.startswith("f "):
            faces.append([int(token.split("/")[0]) - 1 for token in line.split()[1:]])
    segments = []
    for face in faces:
        points = [vertices[index] for index in face]
        hits = []
        for a, b in zip(points, points[1:] + points[:1]):
            if a[1] < height <= b[1] or b[1] < height <= a[1]:
                ratio = (height - a[1]) / (b[1] - a[1])
                hits.append((a[0] + ratio * (b[0] - a[0]),
                             a[2] + ratio * (b[2] - a[2])))
        if len(hits) == 2:
            segments.append(tuple(hits))
    return segments


def ray_hits(segments, dx: float, dz: float):
    """Return positive ray distances; no assumed rectangular mesh or voxel boxes."""
    hits = []
    for a, b in segments:
        sx, sz = b[0] - a[0], b[1] - a[1]
        determinant = dx * sz - dz * sx
        if abs(determinant) < 1e-12:
            continue
        distance = (a[0] * sz - a[1] * sx) / determinant
        along = (a[0] * dz - a[1] * dx) / determinant
        if distance > 0 and -1e-9 <= along <= 1 + 1e-9:
            hits.append(distance)
    return sorted(set(round(value, 9) for value in hits))


def boundary_distance(segments, x: float, z: float):
    distances = []
    for a, b in segments:
        sx, sz = b[0] - a[0], b[1] - a[1]
        length_squared = sx * sx + sz * sz
        t = 0 if length_squared == 0 else max(0, min(1, ((x - a[0]) * sx + (z - a[1]) * sz) / length_squared))
        distances.append(math.hypot(x - a[0] - t * sx, z - a[1] - t * sz))
    return min(distances)


class PlanterFillSafetyTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.evidence = json.loads(EVIDENCE.read_text())

    def verify_profile(self, width):
        profile = next(value for value in self.evidence["profiles"] if value["width_m"] == width)
        self.assertEqual(sha256(MESH_ROOT / f"anthophoros_{width}m.obj"), profile["mesh_sha256"])
        self.assertEqual(width / 2 - 0.174, profile["half_width_m"])
        self.assertEqual(0.420, profile["half_depth_m"])
        self.assertEqual(0.900, profile["surface_y_m"])
        self.assertAlmostEqual(0.100, self.evidence["unchanged_shell_height_m"] - profile["surface_y_m"])
        segments = section_segments(width, profile["surface_y_m"])
        for probe in profile["interior_probes"]:
            actual = ray_hits(segments, *probe["direction_xz"])
            self.assertTrue(actual, probe)
            self.assertAlmostEqual(probe["nearest_wall_m"], actual[0], delta=1e-8)
            self.assertAlmostEqual(probe["outer_wall_m"], actual[-1], delta=1e-8)

        # Every boundary sample, including all four corners, enters the shell
        # slightly. None reaches the exterior face or exposes a seam at the rim.
        half_x, half_z = profile["half_width_m"], profile["half_depth_m"]
        for step in range(33):
            t = step / 32
            for x, z in ((half_x, -half_z + 2 * half_z * t),
                         (-half_x, -half_z + 2 * half_z * t),
                         (-half_x + 2 * half_x * t, half_z),
                         (-half_x + 2 * half_x * t, -half_z)):
                hits = ray_hits(segments, x, z)
                self.assertTrue(hits, (width, x, z))
                self.assertLess(hits[0], 1, "Fill must overlap the hidden inner wall instead of leaving a slit")
                self.assertGreater(hits[-1], 1, "Fill must stay inside the outer rim")
                self.assertLess(boundary_distance(segments, x, z), .0021,
                                "Only a concealed sub-2.1mm edge overlap is permitted")
        pixels_x, pixels_z = profile["texture_pixels"]
        self.assertEqual(64, self.evidence["texels_per_metre"])
        self.assertEqual(4, self.evidence["atlas_mip_levels"])
        self.assertEqual(64, self.evidence["tile_pixels"])
        self.assertEqual((64, 64), (pixels_x, pixels_z), "Every actual image is a literal 64x64 seamless tile")
        self.assertEqual((0, 0), (pixels_x % 16, pixels_z % 16),
                         "Soil textures must preserve the atlas's four mip levels")
        self.assertAlmostEqual(half_x / half_z, profile["fill_footprint_aspect"], delta=1e-9)
        strips = profile["tile_strips"]
        self.assertEqual({2: 3, 3: 3, 4: 5}[width], len(strips))
        previous_x, previous_u, total_area = -half_x, None, 0
        for strip in strips:
            x0, x1 = strip["x_bounds_m"]
            u0, v0, u1, v1 = strip["uv_bounds"]
            self.assertAlmostEqual(previous_x, x0, msg="Strips must meet without gaps or overlap")
            self.assertTrue(0 < x1 - x0 <= 1 + 1e-9)
            self.assertTrue(0 <= u0 < u1 <= 1)
            self.assertAlmostEqual(.08, v0)
            self.assertAlmostEqual(.92, v1)
            self.assertAlmostEqual(64, (u1 - u0) * 64 / (x1 - x0))
            self.assertAlmostEqual(64, (v1 - v0) * 64 / (2 * half_z))
            self.assertAlmostEqual(x0 + .5 - strip["tile_index"], u0)
            self.assertAlmostEqual(x1 + .5 - strip["tile_index"], u1)
            if previous_u is not None:
                self.assertAlmostEqual(1, previous_u)
                self.assertAlmostEqual(0, u0)
                self.assertAlmostEqual(round(x0 + .5), x0 + .5)
            if x0 < 0 < x1:
                self.assertAlmostEqual(.5, u0 + (u1 - u0) * -x0 / (x1 - x0))
            previous_x, previous_u = x1, u1
            total_area += (x1 - x0) * (2 * half_z)
        self.assertAlmostEqual(half_x, previous_x)
        self.assertAlmostEqual(4 * half_x * half_z, total_area)
        self.assertEqual(f"daedalon:block/anthophoros_earth_{width}m", profile["soil_texture"])

    def test_01_measured_two_metre_pilot(self):
        self.verify_profile(2)

    def test_02_remaining_widths_reuse_measured_profile(self):
        for width in (3, 4):
            with self.subTest(width=width):
                self.verify_profile(width)

    def test_runtime_profile_matches_locked_measurements(self):
        source = (JAVA_ROOT / "block/AnthophorosFillProfile.java").read_text()
        actual = {
            int(width): tuple(map(float, (half_width, half_depth, height)))
            for width, half_width, half_depth, height in re.findall(
                r"new AnthophorosFillProfile\(\s*(\d+),\s*([\d.]+)F,\s*([\d.]+)F,\s*([\d.]+)F\)", source)
        }
        self.assertEqual({2, 3, 4}, set(actual))
        for profile in self.evidence["profiles"]:
            for expected, runtime in zip((profile["half_width_m"], profile["half_depth_m"], profile["surface_y_m"]),
                                         actual[profile["width_m"]]):
                self.assertAlmostEqual(expected, runtime, delta=1e-6)


if __name__ == "__main__":
    unittest.main()
