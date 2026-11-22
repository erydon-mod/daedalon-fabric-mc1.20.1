"""Physical regressions for attached leaves and the shared architectural bases."""
from __future__ import annotations

from collections import defaultdict
import hashlib
import importlib.util
import json
import math
import unittest

from daedalon_test_support import REPO_ROOT


def _load_tool(name):
    spec = importlib.util.spec_from_file_location(name, REPO_ROOT / "tools" / (name + ".py"))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


AUTHOR = _load_tool("rebuild_finial_meshes")
BASES = _load_tool("finial_base_profiles")
LEAVES = _load_tool("finial_ornament_profiles")


def _components(builder):
    neighbours = defaultdict(set)
    for face in builder.faces:
        for a, b in zip(face, face[1:] + face[:1]):
            neighbours[a].add(b)
            neighbours[b].add(a)
    remaining = set(neighbours)
    result = []
    while remaining:
        pending = [remaining.pop()]
        indices = set(pending)
        while pending:
            for following in neighbours[pending.pop()] & remaining:
                remaining.remove(following)
                indices.add(following)
                pending.append(following)
        result.append(indices)
    return result


def _cross2(a, b):
    return a[0] * b[1] - a[1] * b[0]


def _section_radius(rings, height, angle):
    """Intersect a radial ray with actual linearly interpolated polygon edges.

    This deliberately does not call the author's analytical radius helper.
    """
    levels = sorted(rings)
    low, high = levels[0], levels[-1]
    for first, second in zip(levels, levels[1:]):
        if first - 1e-10 <= height <= second + 1e-10:
            low, high = first, second
            break
    t = max(0., min(1., (height - low) / (high - low)))
    lower, upper = rings[low], rings[high]
    points = [tuple(a + (b - a) * t for a, b in zip(first[:2], second[:2]))
              for first, second in zip(lower, upper)]
    direction = math.cos(angle), math.sin(angle)
    hits = []
    for first, second in zip(points, points[1:] + points[:1]):
        edge = tuple(b - a for a, b in zip(first, second))
        denominator = _cross2(direction, edge)
        if abs(denominator) < 1e-12:
            continue
        distance = _cross2(first, edge) / denominator
        fraction = _cross2(first, direction) / denominator
        if distance > 0 and -1e-9 <= fraction <= 1 + 1e-9:
            hits.append(distance)
    if not hits:
        raise AssertionError("No core surface along the sampled radial ray")
    return min(hits)


def _triangles(face):
    return [(face[0], face[index], face[index + 1]) for index in range(1, len(face) - 1)]


def _samples(triangle):
    # Vertices, edge quarters and interior barycentric points exercise the
    # whole rear patch, rather than merely accepting sealed detached shells.
    for first in range(5):
        for second in range(5 - first):
            weights = first / 4, second / 4, 1 - (first + second) / 4
            yield tuple(sum(weight * point[axis] for weight, point in zip(weights, triangle))
                        for axis in range(3))


def _dot(a, b):
    return sum(first * second for first, second in zip(a, b))


def _cross(a, b):
    return (a[1]*b[2]-a[2]*b[1], a[2]*b[0]-a[0]*b[2], a[0]*b[1]-a[1]*b[0])


def _sub(a, b):
    return tuple(first - second for first, second in zip(a, b))


def _segment_pierces_triangle(start, end, triangle):
    """Moller-Trumbore segment test, excluding coplanar and endpoint contacts."""
    origin, second, third = triangle
    direction, edge_a, edge_b = _sub(end, start), _sub(second, origin), _sub(third, origin)
    perpendicular = _cross(direction, edge_b)
    determinant = _dot(edge_a, perpendicular)
    if abs(determinant) < 1e-15:
        return False
    offset = _sub(start, origin)
    u = _dot(offset, perpendicular) / determinant
    q = _cross(offset, edge_a)
    v = _dot(direction, q) / determinant
    t = _dot(edge_b, q) / determinant
    return -1e-9 <= u <= 1+1e-9 and -1e-9 <= v and u+v <= 1+1e-9 and 1e-8 < t < 1-1e-8


class FinialRefinementSafetyTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.leaves = AUTHOR.Builder()
        cls.leaf_record = LEAVES.build_ornament("kynara", cls.leaves, .272, 1., .580038780 / 2)
        components = sorted(_components(cls.leaves), key=len, reverse=True)
        cls.core, cls.leaf_components = components[0], components[1:]
        cls.rings = defaultdict(list)
        for index in cls.core:
            point = cls.leaves.vertices[index]
            cls.rings[point[2]].append(point)
        for ring in cls.rings.values():
            ring.sort(key=lambda point: math.atan2(point[1], point[0]))

    def test_every_leaf_rear_is_continuously_embedded_in_the_actual_faceted_body(self):
        self.assertEqual(34, len(self.leaf_components))
        for leaf_index, component in enumerate(self.leaf_components):
            pairs = defaultdict(list)
            for index in component:
                x, y, z = self.leaves.vertices[index]
                pairs[(round(z, 9), round(math.atan2(y, x), 9))].append(index)
            rear = set()
            for pair in pairs.values():
                self.assertEqual(2, len(pair), (leaf_index, pair))
                pair.sort(key=lambda index: math.hypot(*self.leaves.vertices[index][:2]))
                back, front = [self.leaves.vertices[index] for index in pair]
                rear.add(pair[0])
                thickness = math.hypot(*front[:2]) - math.hypot(*back[:2])
                # Only the last 10mm of the filled, pointed crown tapers down;
                # broad leaf faces retain the existing 10mm minimum wall.
                minimum = .003 if front[2] > .99 else .010
                self.assertGreaterEqual(thickness, minimum, (leaf_index, front[2], thickness))
            rear_faces = [face for face in self.leaves.faces if set(face) <= rear]
            self.assertEqual(40, len(rear_faces))
            for face in rear_faces:
                for indices in _triangles(face):
                    triangle = [self.leaves.vertices[index] for index in indices]
                    for x, y, z in _samples(triangle):
                        surface = _section_radius(self.rings, z, math.atan2(y, x))
                        penetration = surface - math.hypot(x, y)
                        self.assertGreater(penetration, .0005, (leaf_index, penetration))

    def test_leaf_shells_do_not_self_intersect_or_collapse(self):
        for leaf_index, component in enumerate(self.leaf_components):
            triangles = []
            for face in self.leaves.faces:
                if not set(face) <= component:
                    continue
                for indices in _triangles(face):
                    a, b, c = [self.leaves.vertices[index] for index in indices]
                    normal = _cross(_sub(b, a), _sub(c, a))
                    self.assertGreater(sum(value * value for value in normal), 1e-20, leaf_index)
                    lo = tuple(min(point[axis] for point in (a, b, c)) for axis in range(3))
                    hi = tuple(max(point[axis] for point in (a, b, c)) for axis in range(3))
                    triangles.append((set(indices), (a, b, c), lo, hi))
            # A dressed tip may turn down; an outward radial-normal criterion
            # would incorrectly reject it. Check actual spatial intersections.
            for first_index, (first_ids, first, first_lo, first_hi) in enumerate(triangles):
                for second_ids, second, second_lo, second_hi in triangles[first_index+1:]:
                    if first_ids & second_ids or any(first_hi[axis] < second_lo[axis]-1e-10
                                                    or second_hi[axis] < first_lo[axis]-1e-10
                                                    for axis in range(3)):
                        continue
                    for segment, surface in ((first, second), (second, first)):
                        self.assertFalse(any(_segment_pierces_triangle(a, b, surface)
                                             for a, b in zip(segment, segment[1:] + segment[:1])),
                                         (leaf_index, first_ids, second_ids))

    def test_sphaira_base_geometry_is_preserved_exactly(self):
        builder = AUTHOR.Builder()
        BASES.build_base(builder, "sphaira", *AUTHOR.DIMENSIONS["sphaira"])
        payload = json.dumps([builder.vertices, builder.faces], separators=(",", ":")).encode()
        # Recorded from the established Sphaira builder before base extraction.
        self.assertEqual("5354fc7da0f540ec32d225816b9ca31bb7d6f82a6b33b0f08045b4ca17d02a30",
                         hashlib.sha256(payload).hexdigest())

    def test_other_bases_share_three_steps_and_curved_mouldings_without_changing_supports(self):
        for style in ("balanos", "kynara", "phlox", "strobilos"):
            with self.subTest(style=style):
                builder = AUTHOR.Builder()
                width, depth, foot, height = AUTHOR.DIMENSIONS[style]
                record = BASES.build_base(builder, style, width, depth, foot, height)
                self.assertLessEqual(record["base_authored_polygons"], 1300)
                self.assertEqual(3, record["base_step_count"])
                square = record["square_profile"]
                self.assertEqual(height, square[3][0])
                self.assertAlmostEqual(foot, square[1][1] * 2)
                self.assertGreater(square[3][0], 0.)
                self.assertLess(square[3][0], square[5][0])
                self.assertLess(square[5][0], square[8][0])
                self.assertGreater(square[1][1], square[4][1])
                self.assertGreater(square[4][1], square[6][1])
                turned = record["turned_profile"]
                radii = [radius for _, radius in turned]
                slopes = [b-a for a, b in zip(radii, radii[1:]) if abs(b-a) > 1e-7]
                reversals = sum(first * second < 0 for first, second in zip(slopes, slopes[1:]))
                self.assertGreaterEqual(reversals, 3, "Keep lower bead, concave neck and upper bowl")
                self.assertLess(turned[0][0], square[-1][0], "Turned neck must overlap its top step")
                for actual, expected in zip(turned[-1], BASES.CONTACTS[style]):
                    self.assertAlmostEqual(actual, expected, delta=1e-12)
                self.assertEqual(0., min(point[2] for point in builder.vertices))
                support = [point for point in builder.vertices if point[2] <= height]
                for axis in (0, 1):
                    self.assertAlmostEqual(foot, max(point[axis] for point in support)
                                           - min(point[axis] for point in support))


if __name__ == "__main__":
    unittest.main()
