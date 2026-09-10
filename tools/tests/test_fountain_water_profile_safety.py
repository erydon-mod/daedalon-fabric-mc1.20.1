from __future__ import annotations

import subprocess
import sys
import unittest

from daedalon_test_support import DAEDALON_DATA, JAVA_ROOT, REPO_ROOT, load_json


class FountainWaterProfileSafetyTests(unittest.TestCase):
    def test_stored_profiles_match_dense_measurements_of_the_original_meshes(self):
        result = subprocess.run([sys.executable, "tools/generate_fountain_water_profiles.py"],
                                cwd=REPO_ROOT, capture_output=True, text=True, timeout=30)
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertIn("georgian: 73 edges", result.stdout)
        self.assertIn("greek: 121 edges", result.stdout)
        self.assertIn("gothic_bowl: 77 edges", result.stdout)
        self.assertIn("georgian_bowl: 154 edges", result.stdout)
        self.assertIn("greek_bowl: 84 edges", result.stdout)

    def test_bowl_profiles_are_near_the_brim_and_scale_with_all_three_sizes(self):
        for style in ("gothic", "georgian", "greek"):
            profile = load_json(DAEDALON_DATA / f"fountain_water/{style}_bowl.json")
            evidence = load_json(REPO_ROOT / f"docs/evidence/{style}-fountain-bowl-source.json")
            width = max(abs(c) for point in profile["points"] for c in point)
            for size in evidence["placement"]["sizes"].values():
                scale = size["diameter_blocks"]
                self.assertAlmostEqual(profile["water_surface_y"] * scale, size["water_surface_y"])
                self.assertAlmostEqual(width * scale, size["water_half_width"])
                gap = (size["height_blocks"] - size["water_surface_y"]) / scale
                self.assertGreater(gap, 0.005)
                self.assertLess(gap, 0.012)
            # emitWaterTriangle reverses these X/Z points for an upward-facing fan.
            points = profile["points"]
            for a, b in zip(points, points[1:] + points[:1]):
                self.assertGreater(a[0] * b[1] - a[1] * b[0], 0)

    def test_rendered_water_and_immersion_share_the_measured_profile(self):
        baked = (JAVA_ROOT / "client/model/obj/ObjMeshBakedModel.java").read_text()
        geometry = (JAVA_ROOT / "block/FountainBasinGeometry.java").read_text()
        self.assertIn("FountainWaterShape outline = style.waterOutline()", baked)
        self.assertIn("outline.vertexCount()", baked)
        self.assertIn("waterOutline.intersects(", geometry)
        self.assertIn("outline.prism(", geometry)
        bowl = (JAVA_ROOT / "block/FountainBowlModel.java").read_text()
        self.assertIn("style.waterOutline().intersects(", bowl)
        self.assertIn("style.waterOutline().prism(", bowl)
        rendered_bowl = baked[baked.index("private void emitContainedBowlWater"):
                              baked.index("private void emitWaterSurface")]
        self.assertIn("emitWaterProfile(", rendered_bowl)
        self.assertIn("style.waterOutline()", rendered_bowl)
        shape = (JAVA_ROOT / "block/FountainWaterShape.java").read_text()
        self.assertNotIn(".obj", shape)
        self.assertNotIn("getBlockState", shape)


if __name__ == "__main__":
    unittest.main()
