"""Measure visible leaf relief in the shipped OBJ, against Balanos's carving.

The old Kynara had sealed leaves but only about 13mm of typical projection.
Closed topology alone therefore cannot protect their readability in-game.
These checks intersect the actual supporting meshes instead of trusting the
authoring recipe's depth settings or including its buried rear surfaces.
"""
from __future__ import annotations

from collections import defaultdict
import math
import statistics
import unittest

from daedalon_test_support import MESH_ROOT
from test_finial_rebuild_safety import _read_geometry
from test_finial_refinement_safety import _section_radius


def _components(mesh):
    neighbours = defaultdict(set)
    for face in mesh["triangles"]:
        ids = [ref[0] for ref in face]
        for a, b in zip(ids, ids[1:] + ids[:1]):
            neighbours[a].add(b)
            neighbours[b].add(a)
    remaining = set(neighbours)
    components = []
    while remaining:
        pending = [remaining.pop()]
        component = set(pending)
        while pending:
            for following in neighbours[pending.pop()] & remaining:
                remaining.remove(following)
                component.add(following)
                pending.append(following)
        components.append([mesh["vertices"][index] for index in component])
    return components


def _height_bounds(component):
    return min(p[1] for p in component), max(p[1] for p in component)


def _support_rings(component):
    rings = defaultdict(list)
    for x, y, z in component:
        # Use the section helper's Z-up convention, retaining the OBJ geometry.
        rings[y].append((x, -z, y))
    for points in rings.values():
        points.sort(key=lambda p: math.atan2(p[1], p[0]))
    return rings


def _standout(point, rings):
    x, y, z = point
    return math.hypot(x, z) - _section_radius(rings, y, math.atan2(-z, x))


def measured_leaf_depths():
    """Return positive exposed projection in the normalized 1m Small model."""
    balanos = _components(_read_geometry(MESH_ROOT / "finial_balanos.obj"))
    acorn, = [c for c in balanos if _height_bounds(c)[0] > .29
              and abs(_height_bounds(c)[1] - 1.) < 1e-7]
    cup, = [c for c in balanos if abs(_height_bounds(c)[0] - .300) < 1e-4
            and abs(_height_bounds(c)[1] - .665) < 1e-4]
    acorn_rings = _support_rings(acorn)
    # Exclude the cropped caps and near-coincident inner acorn skin.
    reference = [_standout(p, acorn_rings) for p in cup if .32 < p[1] < .64]
    reference = [depth for depth in reference if depth > .005]

    kynara = _components(_read_geometry(MESH_ROOT / "finial_kynara.obj"))
    core, = [c for c in kynara if _height_bounds(c)[0] > .27
             and abs(_height_bounds(c)[1] - 1.) < 1e-7
             and _height_bounds(c)[1] - _height_bounds(c)[0] > .65]
    leaves = [c for c in kynara if _height_bounds(c)[0] >= .2719
              and _height_bounds(c)[1] > .35
              and .15 < _height_bounds(c)[1] - _height_bounds(c)[0] < .4]
    core_rings = _support_rings(core)
    exposed = [[depth for p in leaf if (depth := _standout(p, core_rings)) > 0]
               for leaf in leaves]
    return reference, exposed


class KynaraLeafDepthSafetyTests(unittest.TestCase):
    def test_all_shipped_leaves_have_balanos_scale_relief(self):
        reference, leaves = measured_leaf_depths()
        self.assertGreater(len(reference), 100, "Measure the exposed carved cup")
        self.assertEqual(34, len(leaves), "Retain all five staggered artichoke tiers")
        reference_depth = statistics.median(reference)
        # Guard the comparison reference itself, in normalized metres.
        self.assertTrue(.035 < reference_depth < .045, reference_depth)
        self.assertTrue(all(leaves), "Every leaf needs an exposed face")
        depth = statistics.median([d for leaf in leaves for d in leaf])
        self.assertGreaterEqual(depth, .8 * reference_depth,
                                "Broad leaf faces must stand proud, not just isolated tips")
        self.assertLessEqual(depth, 1.3 * reference_depth,
                             "Keep relief comparable to Balanos rather than inflating the leaves")
        for index, leaf in enumerate(leaves):
            self.assertGreaterEqual(max(leaf), .9 * reference_depth,
                                    ("A tier has lost its readable leaf edges", index, max(leaf)))
            self.assertLessEqual(max(leaf), 1.25 * max(reference),
                                 ("Avoid exaggerated spikes", index, max(leaf)))


if __name__ == "__main__":
    unittest.main()
