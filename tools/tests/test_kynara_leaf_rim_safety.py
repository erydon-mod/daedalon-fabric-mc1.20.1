"""Protect visible carved Kynara shoulders, rather than buried leaf thickness.

The first bold redesign had a length-weighted exposed-rim 90th percentile of
33mm. Filling its gaps reduced that to 15mm even while the leaf-to-core median
still looked substantial. Rays here exclude the leaf being measured and stop
at the outermost other surface, so hidden rear walls cannot satisfy the test.
"""
from __future__ import annotations

from collections import defaultdict
import math
import unittest

from daedalon_test_support import MESH_ROOT
from test_finial_rebuild_safety import _read_geometry
from test_kynara_leaf_coverage_safety import _components, _cross2


def _front_vertices(vertices, component):
    """Pair collinear front/rear vertices without relying on OBJ index order."""
    rows = defaultdict(set)
    for index in component:
        rows[round(vertices[index][1], 9)].add(index)
    result = set()
    for pending in rows.values():
        while pending:
            first = pending.pop()
            angle = math.atan2(vertices[first][2], vertices[first][0])

            def distance(index):
                other = math.atan2(vertices[index][2], vertices[index][0]) - angle
                return abs(math.atan2(math.sin(other), math.cos(other)))

            if not pending:
                raise AssertionError("A leaf front has no matching radial rear vertex")
            second = min(pending, key=distance)
            if distance(second) > 1e-5:
                raise AssertionError("Leaf front/rear vertices no longer share a radial line")
            pending.remove(second)
            result.add(max((first, second), key=lambda i: math.hypot(vertices[i][0], vertices[i][2])))
    return result


def _section_segments(triangles, height):
    result = []
    for points, lower, upper, component in triangles:
        if not lower < height < upper:
            continue
        hits = []
        for a, b in zip(points, points[1:] + points[:1]):
            if (a[1] - height) * (b[1] - height) < 0:
                t = (height - a[1]) / (b[1] - a[1])
                hits.append((a[0] + t * (b[0] - a[0]), a[2] + t * (b[2] - a[2])))
        if len(hits) == 2:
            result.append((hits[0], hits[1], component))
    return result


def _outer_other_radius(segments, direction, excluded):
    radius = 0.
    for a, b, component in segments:
        if component == excluded:
            continue
        edge = b[0] - a[0], b[1] - a[1]
        denominator = _cross2(direction, edge)
        if abs(denominator) < 1e-12:
            continue
        hit = _cross2(a, edge) / denominator
        fraction = _cross2(a, direction) / denominator
        if hit > radius and -1e-8 <= fraction <= 1 + 1e-8:
            radius = hit
    if radius <= 0:
        raise AssertionError("A visible rim must have a supporting surface behind it")
    return radius


def measured_exposed_leaf_rims(mesh):
    vertices = mesh["vertices"]
    faces = [tuple(ref[0] for ref in face) for face in mesh["triangles"]]
    components = _components(vertices, faces)
    owner = {index: component for component, indices in enumerate(components) for index in indices}
    triangles = []
    by_component = defaultdict(list)
    for face in faces:
        points = tuple(vertices[index] for index in face)
        component = owner[face[0]]
        triangles.append((points, min(p[1] for p in points), max(p[1] for p in points), component))
        by_component[component].append(face)
    leaves = [(i, indices) for i, indices in enumerate(components)
              if min(vertices[index][1] for index in indices) >= .2719
              and .15 < max(vertices[index][1] for index in indices)
              - min(vertices[index][1] for index in indices) < .4]
    sections, samples = {}, []
    for component, indices in leaves:
        front = _front_vertices(vertices, indices)
        edges = set()
        for face in by_component[component]:
            edge = [index for index in face if index in front]
            if len(edge) == 2 and abs(vertices[edge[0]][1] - vertices[edge[1]][1]) > .002:
                edges.add(tuple(sorted(edge)))
        for first, second in edges:
            a, b = vertices[first], vertices[second]
            weight = math.dist(a, b) / 3
            for amount in (.25, .5, .75):
                point = tuple(x + amount * (y - x) for x, y in zip(a, b))
                # Assess the carved body; its deliberately closed apex and cut
                # bottom hem have different geometry and their own regressions.
                if not .35 < point[1] < .90:
                    continue
                key = round(point[1], 10)
                if key not in sections:
                    sections[key] = _section_segments(triangles, point[1])
                radius = math.hypot(point[0], point[2])
                direction = point[0] / radius, point[2] / radius
                supporting_radius = _outer_other_radius(sections[key], direction, component)
                samples.append((radius - supporting_radius, weight, component))
    return {"leaves": len(leaves), "samples": samples}


def _weighted_quantile(samples, quantile):
    target = quantile * sum(weight for depth, weight, component in samples)
    cumulative = 0.
    for depth, weight, component in sorted(samples):
        cumulative += weight
        if cumulative >= target:
            return depth
    raise AssertionError("Cannot measure an empty exposed rim")


class KynaraLeafRimSafetyTests(unittest.TestCase):
    def test_exposed_shoulders_retain_the_bold_leaf_reference(self):
        record = measured_exposed_leaf_rims(_read_geometry(MESH_ROOT / "finial_kynara.obj"))
        self.assertEqual(34, record["leaves"])
        samples = record["samples"]
        visible = [sample for sample in samples if sample[0] > .001]
        self.assertGreaterEqual(len({component for depth, weight, component in visible}), 24,
                                "Read the shoulders across the ornament, not one isolated spike")
        self.assertGreaterEqual(sum(weight for depth, weight, component in visible),
                                .30 * sum(weight for depth, weight, component in samples),
                                "A meaningful length of leaf rim must remain exposed")
        self.assertGreaterEqual(_weighted_quantile(visible, .90), .025,
                                "Retain visible bold rims instead of counting thickness hidden behind other leaves")


if __name__ == "__main__":
    unittest.main()
