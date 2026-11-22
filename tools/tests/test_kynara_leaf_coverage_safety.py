"""Visible Kynara seams and hem, measured on the shipped triangle surface.

Separate watertight leaves can still expose deep black slots between them.
These checks therefore sample the outer contour from every azimuth and check
the lowest leaves' actual cut edges, rather than accepting component closure.
"""
from __future__ import annotations

from collections import defaultdict
import math
import unittest

from daedalon_test_support import MESH_ROOT
from test_finial_rebuild_safety import _read_geometry


def _components(vertices, triangles):
    neighbours = defaultdict(set)
    for face in triangles:
        for a, b in zip(face, face[1:] + face[:1]):
            neighbours[a].add(b)
            neighbours[b].add(a)
    remaining = set(neighbours)
    result = []
    while remaining:
        pending = [remaining.pop()]
        component = set(pending)
        while pending:
            for following in neighbours[pending.pop()] & remaining:
                remaining.remove(following)
                component.add(following)
                pending.append(following)
        result.append(component)
    return result


def _cross2(a, b):
    return a[0] * b[1] - a[1] * b[0]


def _radial_contour(vertices, triangles, height):
    """Intersect triangle sections with 360 radial rays; zero means no surface.

    The farthest intersection is the visible surface from outside. This uses
    all triangles, including the actual backing, so a hidden obsolete core
    cannot make a pocket look shallower than the geometry a player sees.
    """
    segments = []
    for face in triangles:
        hits = []
        for first, second in zip(face, face[1:] + face[:1]):
            a, b = vertices[first], vertices[second]
            if (a[1] - height) * (b[1] - height) < 0:
                t = (height - a[1]) / (b[1] - a[1])
                hits.append((a[0] + t * (b[0] - a[0]), a[2] + t * (b[2] - a[2])))
        if len(hits) == 2:
            segments.append(hits)
    result = []
    for index in range(360):
        # Avoid sampling only authored symmetry axes and vertices.
        angle = (index + .37) * math.tau / 360
        direction = math.cos(angle), math.sin(angle)
        radii = []
        for a, b in segments:
            edge = b[0] - a[0], b[1] - a[1]
            denominator = _cross2(direction, edge)
            if abs(denominator) < 1e-12:
                continue
            radius = _cross2(a, edge) / denominator
            fraction = _cross2(a, direction) / denominator
            if radius > 0 and -1e-8 <= fraction <= 1 + 1e-8:
                radii.append(radius)
        result.append(max(radii, default=0.))
    return result


class KynaraLeafCoverageSafetyTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        mesh = _read_geometry(MESH_ROOT / "finial_kynara.obj")
        cls.vertices = mesh["vertices"]
        cls.triangles = [[ref[0] for ref in face] for face in mesh["triangles"]]
        cls.components = _components(cls.vertices, cls.triangles)

    def test_lowest_eight_leaves_have_a_continuous_horizontal_cut_hem(self):
        lowest = []
        for component in self.components:
            heights = [self.vertices[index][1] for index in component]
            if abs(min(heights) - .272) < 1e-6 and .45 < max(heights) < .53:
                lowest.append(component)
        self.assertEqual(8, len(lowest), "Retain the complete lowest artichoke tier")
        for component in lowest:
            root = [self.vertices[index] for index in component
                    if abs(self.vertices[index][1] - .272) < 1e-6]
            self.assertGreaterEqual(len(root), 8, "The full root edge must share its cut plane")
            angles = [math.atan2(point[2], point[0]) for point in root]
            centre = math.atan2(sum(math.sin(a) for a in angles), sum(math.cos(a) for a in angles))
            unwrapped = [math.atan2(math.sin(a - centre), math.cos(a - centre)) for a in angles]
            self.assertGreaterEqual(max(unwrapped) - min(unwrapped), .8 * math.tau / 8,
                                    "A bottom leaf must end in a broad cut edge, not a downward point")
        indices = set().union(*lowest)
        triangles = [face for face in self.triangles if set(face) <= indices]
        contour = _radial_contour(self.vertices, triangles, .2725)
        self.assertTrue(all(radius > .1 for radius in contour),
                        "The eight cut roots must form a continuous hem around the collar")

    def test_crown_has_continuous_backing_between_its_leaf_tips(self):
        for height in (.90, .92, .94, .95, .96, .97, .98, .985, .99):
            with self.subTest(height=height):
                contour = _radial_contour(self.vertices, self.triangles, height)
                self.assertTrue(all(contour), "No viewing angle may see through the crown")
                self.assertGreaterEqual(min(contour) / max(contour), .60,
                                        "Leaf tips must not leave deep star-shaped crown slots")

    def test_body_visible_seams_are_filled_behind_the_leaf_rims(self):
        leaves = []
        cores = []
        for component in self.components:
            low = min(self.vertices[index][1] for index in component)
            high = max(self.vertices[index][1] for index in component)
            if low >= .2719 and .15 < high - low < .4:
                leaves.append(component)
            elif low > .27 and high - low > .65 and abs(high - 1.) < 1e-7:
                cores.append(component)
        self.assertEqual(34, len(leaves))
        self.assertEqual(1, len(cores), "Use the actual supporting body, without a hidden duplicate")
        leaf_indices = set().union(*leaves)
        leaf_faces = [face for face in self.triangles if set(face) <= leaf_indices]
        core_faces = [face for face in self.triangles if set(face) <= cores[0]]
        worst = (0., 0., 0.)
        # Sweep through the authored ring levels, and compare which actual
        # surface is outermost. Deep leaf-to-leaf shoulders are legitimate.
        # Apply the recess limit only where the supporting body is exposed;
        # it must fill a window behind the leaf edges rather than leave a
        # narrow black slot down to the recessed structural core.
        for step in range(121):
            height = .35 + .005 * step
            leaf = _radial_contour(self.vertices, leaf_faces, height)
            core = _radial_contour(self.vertices, core_faces, height)
            self.assertTrue(all(core), height)
            for angle, (rim, body) in enumerate(zip(leaf, core)):
                if rim > body + .0001:
                    continue
                left = max(leaf[(angle - offset) % 360] for offset in range(1, 13))
                right = max(leaf[(angle + offset) % 360] for offset in range(1, 13))
                self.assertTrue(left > 0 and right > 0, (height, angle))
                gap = min(left, right) - body
                worst = max(worst, (gap, height, angle + .37))
        # Bold shoulders reach about 30mm. A filled bearing can become the
        # outermost surface above a lower hidden plate, so allow that scale of
        # recess here. The rejected bare-core windows measured about 51mm.
        # This does not cap legitimate leaf-to-leaf shoulder depth.
        self.assertLessEqual(worst[0], .030,
                             ("Fill body-visible gaps without flattening leaf-to-leaf shoulders",
                              "gap metres, height, azimuth degrees", worst))


if __name__ == "__main__":
    unittest.main()
