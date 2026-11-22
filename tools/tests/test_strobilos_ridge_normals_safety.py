"""Read real exported ridge normals, preserving the accepted Strobilos crown.

The broad lower scales already have geometric meridian ridges around 36–42
degrees. Their original imported corner normals were identical across several
rows, so shader interpolation hid that geometry. Builder face flags alone did
not catch the collapse introduced by Blender's angle-based smoothing pass.
"""
from __future__ import annotations

from collections import defaultdict
import hashlib
import json
import math
import unittest

from daedalon_test_support import MESH_ROOT
from test_finial_rebuild_safety import _read_geometry, _cross, _sub, _dot
from test_finial_refinement_safety import _section_radius


def _unit(vector):
    length = math.sqrt(_dot(vector, vector))
    return tuple(value / length for value in vector)


def _angle(first, second):
    return math.degrees(math.acos(max(-1., min(1., _dot(_unit(first), _unit(second))))))


def _components(faces):
    neighbours = defaultdict(set)
    for face in faces:
        for a, b in zip(face, face[1:] + face[:1]):
            neighbours[a].add(b)
            neighbours[b].add(a)
    remaining, result = set(neighbours), []
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


def measured_strobilos_ridges(mesh):
    vertices = mesh["vertices"]
    faces = [tuple(ref[0] for ref in face) for face in mesh["triangles"]]
    components = _components(faces)
    owner = {index: number for number, component in enumerate(components) for index in component}
    by_component = defaultdict(list)
    for face in mesh["triangles"]:
        by_component[owner[face[0][0]]].append(face)
    core, = [component for component in components
             if min(vertices[i][1] for i in component) > .27
             and max(vertices[i][1] for i in component) - min(vertices[i][1] for i in component) > .7]
    rings = defaultdict(list)
    for index in core:
        x, y, z = vertices[index]
        rings[y].append((x, -z, y))
    for ring in rings.values():
        ring.sort(key=lambda point: math.atan2(point[1], point[0]))

    def standout(index):
        x, y, z = vertices[index]
        return math.hypot(x, z) - _section_radius(rings, y, math.atan2(-z, x))

    def geometric_normal(face):
        a, b, c = [vertices[ref[0]] for ref in face]
        return _unit(_cross(_sub(b, a), _sub(c, a)))

    def interpolated_edge_normal(face, a, b):
        normals = {index: mesh["normals"][normal] for index, normal in face}
        return _unit(tuple(x + y for x, y in zip(normals[a], normals[b])))

    scales = []
    for component in components:
        if len(component) != 13:
            continue
        # The dressed centre is the vertex standing furthest above the actual
        # supporting body; no OBJ index or authoring feature list is trusted.
        apex = max(component, key=standout)
        front = [face for face in by_component[owner[apex]] if any(ref[0] == apex for ref in face)]
        bevel = {ref[0] for face in front for ref in face} - {apex}
        if len(front) != 4 or len(bevel) != 4:
            raise AssertionError("Expected a four-facet diamond with one raised centre")
        bottom = min(bevel, key=lambda index: vertices[index][1])
        top = max(bevel, key=lambda index: vertices[index][1])
        ridges = []
        for end in (bottom, top):
            first, second = [face for face in front if any(ref[0] == end for ref in face)]
            physical = _angle(geometric_normal(first), geometric_normal(second))
            rendered = _angle(interpolated_edge_normal(first, apex, end),
                              interpolated_edge_normal(second, apex, end))
            ridges.append((physical, rendered))
        scales.append({"component": owner[apex], "height": vertices[apex][1], "ridges": ridges})
    return {"scales": scales, "owner": owner, "faces": faces}


def _digest(value):
    return hashlib.sha256(json.dumps(value, separators=(",", ":")).encode()).hexdigest()


def unchanged_geometry_and_normals(mesh, record):
    # The first fully defined existing row has its raised centres near Y=.798.
    # The preceding affected row is near .746. Preserve every other component,
    # including the rounded tiny crown tiles, body, pedestal and mouldings.
    lower = {scale["component"] for scale in record["scales"] if scale["height"] < .77}
    untouched = [[(position, mesh["normals"][normal]) for position, normal in face]
                 for face in mesh["triangles"] if record["owner"][face[0][0]] not in lower]
    return _digest([mesh["vertices"], record["faces"]]), _digest(untouched)


class StrobilosRidgeNormalsSafetyTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.mesh = _read_geometry(MESH_ROOT / "finial_strobilos.obj")
        cls.record = measured_strobilos_ridges(cls.mesh)

    def test_lower_meridian_ridges_survive_exported_normal_interpolation(self):
        self.assertEqual(210, len(self.record["scales"]))
        lower = [scale for scale in self.record["scales"] if scale["height"] < .77]
        self.assertEqual(70, len(lower))
        for scale in lower:
            for physical, rendered in scale["ridges"]:
                self.assertGreater(physical, 35., "Retain the authored raised diamond geometry")
                self.assertGreaterEqual(rendered, .75 * physical,
                                        ("The imported normals must not average away the vertical ridge",
                                         scale["height"], physical, rendered))

    def test_approved_geometry_upper_normals_and_base_normals_remain_exact(self):
        geometry, untouched = unchanged_geometry_and_normals(self.mesh, self.record)
        self.assertEqual("00272fb39581e0e97e438db99000f79bdb3628fee5ce8409ccdf0d92dfdc2da5", geometry)
        self.assertEqual("062db0e1ac96fcb1ec722ada3e57fb85c2b1bc79a16fd9bceba5e65c5961dc25", untouched)


if __name__ == "__main__":
    unittest.main()
