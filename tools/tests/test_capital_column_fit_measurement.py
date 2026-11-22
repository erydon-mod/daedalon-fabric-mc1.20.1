"""Geometry evidence for shaft matching, including holes and concave silhouettes."""
from __future__ import annotations

import json
from pathlib import Path
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
try:
    import numpy as np
    from shapely.geometry import Point, box
    import measure_capital_shaft_fit as fit
except ImportError:
    fit = None


@unittest.skipIf(fit is None, "Offline authoring geometry libraries are not installed")
class CapitalColumnFitMeasurementTests(unittest.TestCase):
    @staticmethod
    def prism(rings, bottom_scale=1, top_scale=1):
        vertices, triangles = [], []
        for ring in rings:
            start, count = len(vertices), len(ring)
            for height, factor in ((0, bottom_scale), (1, top_scale)):
                vertices.extend((.5 + (x - .5) * factor, height, .5 + (z - .5) * factor)
                                for x, z in ring)
            for i in range(count):
                a, b = start + i, start + (i + 1) % count
                triangles.extend(((a, b + count, b), (a, a + count, b + count)))
        return fit.Source(np.array(vertices), np.array(triangles))

    def test_concave_section_keeps_the_missing_corner(self):
        source = self.prism([[(.1, .1), (.9, .1), (.9, .4), (.4, .4), (.4, .9), (.1, .9)]])
        region, diagnostic = source.section(1.5)
        self.assertGreater(region.convex_hull.area, region.area)
        self.assertFalse(region.covers(Point(.8, .8)))
        self.assertTrue(region.covers(Point(.1, .1)))
        self.assertEqual(0, diagnostic["section_dangle_length"])

    def test_winding_preserves_an_enclosed_hole(self):
        outer = [(.1, .1), (.9, .1), (.9, .9), (.1, .9)]
        inner = [(.3, .3), (.3, .7), (.7, .7), (.7, .3)]
        source = self.prism([outer, inner])
        region, _ = source.section(1.5)
        self.assertAlmostEqual(1.2 ** 2 - .6 ** 2, region.area, places=8)
        self.assertFalse(fit.covers(region, box(.49, .49, .51, .51), [0, 45]))
        self.assertTrue(region.covers(Point(.1, .1)))
        # A consistently reversed source still retains the same physical void.
        reversed_source = fit.Source(source.positions, source.triangles[:, ::-1])
        self.assertAlmostEqual(region.area, reversed_source.section(1.5)[0].area)

    def test_top_anchor_selects_the_correct_neck_height(self):
        source = self.prism([[(-.5, -.5), (1.5, -.5), (1.5, 1.5), (-.5, 1.5)]], .1, .4)
        region, _ = source.section(1.5, 0)
        self.assertAlmostEqual(.6, region.bounds[2] - region.bounds[0], places=8)
        self.assertAlmostEqual(.2, region.bounds[0], places=8)
        top, _ = source.section(1.5, 1)
        self.assertAlmostEqual(1.2, top.bounds[2] - top.bounds[0], places=8)

    def test_active_resource_matches_all_real_sources_and_dense_bands(self):
        evidence = json.loads((fit.ROOT / "docs/evidence/capital-shaft-fit.json").read_text())
        resource = json.loads((fit.ASSETS / "geometry/capital_column_fits.json").read_text())
        self.assertEqual(1176, fit.verify(evidence, resource))
        self.assertEqual(0, evidence["coverage_tolerance_blocks_normalized"])
        for style in fit.STYLES:
            for head in ("square", "gothic"):
                self.assertEqual({"standard": 1., "double": 1.}, resource["styles"][style][head])


if __name__ == "__main__":
    unittest.main()
