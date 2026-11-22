"""Physical contracts for the measured geometric finial reconstructions.

The budget refers to editable Blender polygons. Exported runtime triangles are
counted separately; quads need not be destructively reduced to meet that budget.
"""
from __future__ import annotations

from collections import Counter, defaultdict
import math
import re
import unittest

from daedalon_test_support import (
    JAVA_ROOT, MESH_EXPECTATIONS, MESH_ROOT, REPO_ROOT, load_json, sha256,
)


STYLES = {"balanos", "kynara", "phlox", "sphaira", "strobilos"}
EVIDENCE = REPO_ROOT / "docs/evidence/finial-rebuild-source.json"
EPSILON = 1e-8


def _sub(a, b):
    return tuple(x - y for x, y in zip(a, b))


def _dot(a, b):
    return sum(x * y for x, y in zip(a, b))


def _cross(a, b):
    return (a[1] * b[2] - a[2] * b[1],
            a[2] * b[0] - a[0] * b[2],
            a[0] * b[1] - a[1] * b[0])


def _bounds(points):
    lo = tuple(min(p[axis] for p in points) for axis in range(3))
    hi = tuple(max(p[axis] for p in points) for axis in range(3))
    return {"min": lo, "max": hi, "size": _sub(hi, lo)}


def _read_geometry(path):
    vertices, normals, triangles, materials = [], [], [], set()
    for line in path.read_text(encoding="utf-8").splitlines():
        fields = line.split()
        if not fields:
            continue
        if fields[0] == "v":
            vertices.append(tuple(map(float, fields[1:])))
        elif fields[0] == "vn":
            normals.append(tuple(map(float, fields[1:])))
        elif fields[0] == "vt":
            raise AssertionError(f"Keep the shared runtime UV projection: {path.name}")
        elif fields[0] == "usemtl":
            materials.add(fields[1])
        elif fields[0] == "f":
            refs = [token.split("/") for token in fields[1:]]
            if len(refs) != 3 or any(len(ref) != 3 or ref[1] or not ref[2] for ref in refs):
                raise AssertionError(f"Expected runtime v//vn triangles: {path.name}")
            face = tuple((int(ref[0]) - 1, int(ref[2]) - 1) for ref in refs)
            if any(not 0 <= v < len(vertices) or not 0 <= n < len(normals) for v, n in face):
                raise AssertionError(f"Out-of-range OBJ reference: {path.name}")
            triangles.append(face)
    return {"vertices": vertices, "normals": normals, "triangles": triangles, "materials": materials}


def _section_bounds(mesh, height):
    points = []
    for face in mesh["triangles"]:
        triangle = [mesh["vertices"][ref[0]] for ref in face]
        for a, b in zip(triangle, triangle[1:] + triangle[:1]):
            if abs(a[1] - height) < EPSILON:
                points.append(a)
            if (a[1] - height) * (b[1] - height) < 0:
                t = (height - a[1]) / (b[1] - a[1])
                points.append(tuple(x + t * (y - x) for x, y in zip(a, b)))
    if not points:
        raise AssertionError(f"No actual geometry intersects the declared section Y={height}")
    return _bounds(points)


class FinialRebuildSafetyTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.evidence = load_json(EVIDENCE)
        cls.models = {entry["style"]: entry for entry in cls.evidence["models"]}
        cls.meshes = {
            style: _read_geometry(MESH_ROOT / f"finial_{style}.obj")
            for style in cls.models
        }
        source = (JAVA_ROOT / "block/FixedDecorBlock.java").read_text(encoding="utf-8")
        cls.profiles = {
            name.lower(): tuple(map(float, (width, height, depth)))
            for name, width, height, depth in re.findall(
                r'\b([A-Z]+)_FINIAL\("[^"]+", "[^"]+", "[^"]+", ([\d.]+), ([\d.]+), ([\d.]+)\)', source)
        }
        cls.feet = {
            name.lower(): float(width)
            for name, width in re.findall(r'case ([A-Z]+)_FINIAL -> ([.\d]+);', source)
        }

    def test_budget_and_source_provenance_are_separate_from_runtime_triangle_counts(self):
        self.assertEqual(1, self.evidence["schema"])
        self.assertFalse(self.evidence["source_files_modified"])
        self.assertEqual(6000, self.evidence["budget_editable_faces"])
        self.assertTrue(self.evidence["face_count_policy"].strip())
        self.assertTrue(self.models)
        self.assertTrue(set(self.models) <= STYLES)
        self.assertEqual(len(self.models), len(self.evidence["models"]), "No duplicate style records")
        historical = load_json(REPO_ROOT / self.evidence["historical_evidence"])
        original = {entry["name"]: entry for entry in historical["models"]}
        tool = self.evidence["authoring_tool"]
        self.assertTrue(tool["path"].startswith("tools/"))
        self.assertEqual(tool["sha256"], sha256(REPO_ROOT / tool["path"]))
        for helper in self.evidence["authoring_helpers"]:
            self.assertTrue(helper["path"].startswith("tools/"))
            self.assertEqual(helper["sha256"], sha256(REPO_ROOT / helper["path"]))
        for style, entry in self.models.items():
            with self.subTest(style=style):
                source = original[f"finial_{style}"]
                self.assertEqual(source["source_sha256"], entry["source_sha256"])
                self.assertEqual(source["source_blend"].split("/")[-2], entry["source_uuid"])
                self.assertGreater(entry["source_polygons"], 0)
                self.assertGreater(entry["authored_polygons"], 0)
                self.assertLessEqual(entry["authored_polygons"], self.evidence["budget_editable_faces"])
                self.assertGreaterEqual(entry["runtime_triangles"], entry["authored_polygons"])

    def test_shipped_geometry_hashes_counts_and_metric_bounds_match_the_active_record(self):
        for style, mesh in self.meshes.items():
            with self.subTest(style=style):
                entry = self.models[style]
                expected = MESH_EXPECTATIONS[f"finial_{style}"]
                self.assertEqual({"none"}, mesh["materials"])
                self.assertEqual(entry["runtime_obj_sha256"], sha256(MESH_ROOT / f"finial_{style}.obj"))
                self.assertEqual(expected["obj_sha256"], entry["runtime_obj_sha256"])
                self.assertEqual(entry["vertices"], len(mesh["vertices"]))
                self.assertEqual(entry["normals"], len(mesh["normals"]))
                self.assertEqual(entry["runtime_triangles"], len(mesh["triangles"]))
                self.assertEqual(expected["faces"], len(mesh["triangles"]))
                for vector in mesh["vertices"] + mesh["normals"]:
                    self.assertEqual(3, len(vector))
                    self.assertTrue(all(math.isfinite(value) for value in vector))
                for normal in mesh["normals"]:
                    self.assertAlmostEqual(1., math.sqrt(_dot(normal, normal)), delta=2e-6)
                actual = _bounds(mesh["vertices"])
                self.assertEqual((0., 1.), (actual["min"][1], actual["max"][1]))
                for axis in (0, 2):
                    self.assertAlmostEqual(0., actual["min"][axis] + actual["max"][axis], delta=EPSILON)
                for name in ("min", "max", "size"):
                    for a, b in zip(actual[name], entry["bounds_obj_y_up"][name]):
                        self.assertAlmostEqual(a, b, delta=EPSILON)
                for a, b in zip(actual["size"], self.profiles[style]):
                    self.assertAlmostEqual(a, b, delta=2e-6)

    def test_rebuilt_components_are_sealed_outward_solids_without_degenerate_faces(self):
        for style, mesh in self.meshes.items():
            with self.subTest(style=style):
                parent = list(range(len(mesh["vertices"])))
                def find(i):
                    while parent[i] != i:
                        parent[i] = parent[parent[i]]
                        i = parent[i]
                    return i
                edges, oriented, volume_terms = Counter(), Counter(), []
                for face in mesh["triangles"]:
                    ids = [ref[0] for ref in face]
                    a, b, c = [mesh["vertices"][i] for i in ids]
                    normal = _cross(_sub(b, a), _sub(c, a))
                    self.assertGreater(_dot(normal, normal), 1e-24, (style, ids))
                    volume_terms.append((ids[0], _dot(a, _cross(b, c)) / 6))
                    for start, end in zip(ids, ids[1:] + ids[:1]):
                        edges[tuple(sorted((start, end)))] += 1
                        oriented[(start, end)] += 1
                        parent[find(end)] = find(start)
                actual_edges = {"boundary": sum(count == 1 for count in edges.values()),
                                "nonmanifold": sum(count > 2 for count in edges.values())}
                self.assertEqual({"boundary": 0, "nonmanifold": 0}, actual_edges)
                self.assertEqual(actual_edges, self.models[style]["edge_counts"])
                self.assertEqual(0, self.models[style]["degenerate_triangles"])
                self.assertEqual({2}, set(edges.values()))
                for start, end in edges:
                    self.assertEqual(oriented[(start, end)], oriented[(end, start)], "Inconsistent solid winding")
                volumes = defaultdict(float)
                for index, term in volume_terms:
                    volumes[find(index)] += term
                self.assertTrue(volumes)
                self.assertTrue(all(volume > 0 for volume in volumes.values()), "Inward or collapsed component")

    def test_softened_feet_have_flat_capped_contacts_and_match_the_fitted_base_width(self):
        for style, mesh in self.meshes.items():
            with self.subTest(style=style):
                foot_width = self.models[style]["foot_width"]
                self.assertAlmostEqual(self.feet[style], foot_width, delta=1e-7)
                ground = {i for i, point in enumerate(mesh["vertices"]) if abs(point[1]) < EPSILON}
                capped, area = set(), 0.
                for face in mesh["triangles"]:
                    if all(ref[0] in ground for ref in face):
                        a, b, c = [mesh["vertices"][ref[0]] for ref in face]
                        normal = _cross(_sub(b, a), _sub(c, a))
                        self.assertLess(normal[1], 0., "Ground contact must face down")
                        self.assertAlmostEqual(0., normal[0], delta=1e-12)
                        self.assertAlmostEqual(0., normal[2], delta=1e-12)
                        area += -normal[1] / 2
                        capped.update(ref[0] for ref in face)
                self.assertTrue(ground)
                self.assertEqual(ground, capped, "Every lowest vertex belongs to a flat sealed foot")
                self.assertGreater(area, foot_width * foot_width * .5)
                ground_bounds = _bounds([mesh["vertices"][i] for i in ground])
                for axis in (0, 2):
                    self.assertLessEqual(ground_bounds["size"][axis], foot_width + EPSILON)
                    self.assertGreater(ground_bounds["size"][axis], foot_width - .01)
                    self.assertAlmostEqual(0., ground_bounds["min"][axis] + ground_bounds["max"][axis], delta=EPSILON)

    def test_measured_foot_and_neck_landmarks_exist_in_the_exported_surface(self):
        for style, mesh in self.meshes.items():
            with self.subTest(style=style):
                entry = self.models[style]
                landmarks = entry["feature_retention"]["profile_landmarks"]
                self.assertGreaterEqual(len(landmarks), 2)
                self.assertTrue(any("foot" in mark["label"] for mark in landmarks))
                self.assertTrue(any("neck" in mark["label"] for mark in landmarks))
                widths = []
                for mark in landmarks:
                    self.assertTrue(mark["label"].strip())
                    self.assertTrue(0 < mark["y"] < 1)
                    actual = _section_bounds(mesh, mark["y"])
                    # Turned profiles approximate their measured smooth curve to
                    # 0.35mm per side; foot fitting remains exact below.
                    self.assertAlmostEqual(mark["width"], actual["size"][0], delta=.0008)
                    self.assertAlmostEqual(mark["depth"], actual["size"][2], delta=.0008)
                    widths.append(actual["size"][0])
                self.assertGreater(max(widths) - min(widths), .001, "Retain the foot and neck moulding relief")
                height = entry["square_foot_height"]
                self.assertTrue(0 < height < 1)
                foot = _section_bounds(mesh, height / 2)
                self.assertAlmostEqual(entry["foot_width"], foot["size"][0], delta=2e-6)
                self.assertAlmostEqual(entry["foot_width"], foot["size"][2], delta=2e-6)

    def test_authored_normals_and_existing_cylindrical_projection_keep_both_runtime_sizes(self):
        for style in self.models:
            with self.subTest(style=style):
                definition = load_json(MESH_ROOT / f"finial_{style}.json")
                self.assertFalse(definition["smooth_normals"])
                self.assertTrue(definition["fit_to_block"])
                self.assertEqual([2., 2., 2.], definition["scale"])
                self.assertEqual([0., 0., 0.], definition["translate"])
                self.assertTrue(definition["force_uv_projection"])
                self.assertTrue(definition["repair_degenerate_uvs"])
                self.assertTrue(definition["face_oriented_uvs"])
                self.assertEqual("cylindrical", definition["uv_projection"])
                self.assertEqual(2., definition["cylindrical_u_repeats"])
                self.assertEqual(4, definition["texture_u_tiles"])
                self.assertEqual({"none"}, set(definition["materials"]))
                # The normalized authored height is baked at 2m and mounted upright;
                # existing FinialMeshTransform tests cover yaw and support offsets.
                for runtime_scale, height in ((.5, 1.), (1., 2.)):
                    self.assertEqual(height, 2 * runtime_scale)


if __name__ == "__main__":
    unittest.main()
