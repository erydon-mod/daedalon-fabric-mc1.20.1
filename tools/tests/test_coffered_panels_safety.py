"""Acceptance checks for the four deliberately authored 6k coffered panels."""
from __future__ import annotations

from collections import Counter, defaultdict
import math
import re
import unittest

from daedalon_test_support import (
    DAEDALON_ASSETS, DAEDALON_DATA, GENERATOR, MESH_ROOT, REPO_ROOT,
    load_json, sha256,
)


EVIDENCE = REPO_ROOT / "docs/evidence/coffered-panels-source.json"
PANEL_BLOCK = REPO_ROOT / "src/main/java/com/oliver/daedalon/block/PanelBlock.java"
REFERENCES = {
    "corinthian": (2, "417076ee6b2f12eae9d27bd0be32f30917296eb74dfecab33b5fb3fca17be7c0"),
    "byzantine": (3, "3eec858bd4b1307b7c4261fec29e695216d1b77ec32724b29ce188968ad31d81"),
    "georgian": (4, "0c19cf91c08bb97ba069810ef943dc98ec3aeca21ebc319f592f9035862cebec"),
    "renaissance": (5, "fcc02190b3dbdc4ae9e27129837151e99aefc3c13e9f0fa7476f620de38db12a"),
}
STYLE_TERMS = {
    "corinthian": {"corinthian", "acanthus"},
    "byzantine": {"byzantine", "guilloche", "interlace"},
    "georgian": {"georgian", "rosette"},
    "renaissance": {"renaissance", "acanthus"},
}
REQUIRED_LOCALES = {"en_us", "de_de", "es_es"}


def read_components(path):
    """Read actual exported triangles and corner normals, independently of authoring code."""
    vertices, normals = [], []
    objects = defaultdict(list)
    name = "default"
    for line in path.read_text(encoding="utf-8").splitlines():
        fields = line.split()
        if not fields:
            continue
        if fields[0] == "v":
            vertices.append(tuple(map(float, fields[1:4])))
        elif fields[0] == "vn":
            normals.append(tuple(map(float, fields[1:4])))
        elif fields[0] == "o":
            name = " ".join(fields[1:])
        elif fields[0] == "f":
            corners = []
            for field in fields[1:]:
                indices = field.split("/")
                if len(indices) != 3 or not indices[2]:
                    raise AssertionError(f"Missing authored corner normal: {path.name}: {line}")
                position, normal = int(indices[0]), int(indices[2])
                if position <= 0 or normal <= 0:
                    raise AssertionError(f"Expected explicit positive OBJ indices: {line}")
                corners.append((position - 1, normal - 1))
            objects[name].append(corners)
    return vertices, normals, objects


def subtract(a, b):
    return tuple(x - y for x, y in zip(a, b))


def cross(a, b):
    return (a[1] * b[2] - a[2] * b[1],
            a[2] * b[0] - a[0] * b[2],
            a[0] * b[1] - a[1] * b[0])


def dot(a, b):
    return sum(x * y for x, y in zip(a, b))


class LocalBackingSurface:
    """Exact edge burial against the piecewise planar, front-facing backing.

    Barycentric coordinates and the height difference are affine along an
    edge. Clipping those four inequalities gives the complete supported
    interval for each backing triangle, including every bevel intersection.
    """
    GRID = 16
    HEIGHT_EPSILON = 2e-7

    def __init__(self, triangles):
        self.triangles = []
        self.cells = defaultdict(set)
        for a, b, c in triangles:
            ux, uy = b[0] - a[0], b[1] - a[1]
            vx, vy = c[0] - a[0], c[1] - a[1]
            determinant = ux * vy - uy * vx
            if determinant <= 1e-15:
                continue  # Rear/vertical faces cannot cover a relief surface.
            beta = (vy / determinant, -vx / determinant,
                    (-a[0] * vy + a[1] * vx) / determinant)
            gamma = (-uy / determinant, ux / determinant,
                     (a[0] * uy - a[1] * ux) / determinant)
            xy_tolerances = tuple(self.HEIGHT_EPSILON * math.hypot(x, y) for x, y in (
                (beta[0] + gamma[0], beta[1] + gamma[1]), beta[:2], gamma[:2]))
            index = len(self.triangles)
            self.triangles.append((beta, gamma, a[2], b[2] - a[2], c[2] - a[2], xy_tolerances))
            for cell in self._cells(min(p[0] for p in (a, b, c)), max(p[0] for p in (a, b, c)),
                                    min(p[1] for p in (a, b, c)), max(p[1] for p in (a, b, c))):
                self.cells[cell].add(index)

    @classmethod
    def _cells(cls, min_x, max_x, min_y, max_y):
        def cell(value):
            return max(0, min(cls.GRID - 1, math.floor(value * cls.GRID)))
        return ((x, y) for x in range(cell(min_x - cls.HEIGHT_EPSILON), cell(max_x + cls.HEIGHT_EPSILON) + 1)
                for y in range(cell(min_y - cls.HEIGHT_EPSILON), cell(max_y + cls.HEIGHT_EPSILON) + 1))

    def covers_edge(self, a, b):
        candidates = set()
        for cell in self._cells(min(a[0], b[0]), max(a[0], b[0]), min(a[1], b[1]), max(a[1], b[1])):
            candidates.update(self.cells.get(cell, ()))
        intervals = []
        for index in candidates:
            beta, gamma, base_z, beta_z, gamma_z, tolerances = self.triangles[index]
            beta_a, beta_b = dot(beta, (a[0], a[1], 1)), dot(beta, (b[0], b[1], 1))
            gamma_a, gamma_b = dot(gamma, (a[0], a[1], 1)), dot(gamma, (b[0], b[1], 1))
            constraints = ((1 - beta_a - gamma_a, 1 - beta_b - gamma_b, tolerances[0]),
                           (beta_a, beta_b, tolerances[1]), (gamma_a, gamma_b, tolerances[2]),
                           (base_z + beta_z * beta_a + gamma_z * gamma_a - a[2],
                            base_z + beta_z * beta_b + gamma_z * gamma_b - b[2], self.HEIGHT_EPSILON))
            low, high = 0.0, 1.0
            for start, end, tolerance in constraints:
                delta = end - start
                if abs(delta) < 1e-14:
                    if start < -tolerance:
                        high = -1
                        break
                elif delta > 0:
                    low = max(low, (-tolerance - start) / delta)
                else:
                    high = min(high, (-tolerance - start) / delta)
                if low > high:
                    break
            if low <= high:
                intervals.append((low, high))
        if not intervals:
            return False
        # Permit only sub-export-precision gaps, never a missed bevel segment.
        parameter_epsilon = self.HEIGHT_EPSILON / max(math.sqrt(dot(subtract(b, a), subtract(b, a))),
                                                     self.HEIGHT_EPSILON)
        cursor = 0.0
        for low, high in sorted(intervals):
            if low > cursor + parameter_epsilon:
                return False
            cursor = max(cursor, high)
            if cursor >= 1 - parameter_epsilon:
                return True
        return False


class CofferedPanelSafetyTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.evidence = load_json(EVIDENCE)
        cls.models = cls.evidence["models"]
        # Check every locale actually authored in this checkout, while keeping
        # the panel change independent of separately landing new translations.
        cls.languages = {path.stem: load_json(path)
                         for path in sorted((DAEDALON_ASSETS / "lang").glob("*.json"))}

    def test_exact_requested_references_and_honest_creation_provenance(self):
        self.assertEqual(4, len(self.models))
        self.assertEqual(set(REFERENCES), {model["style"] for model in self.models})
        self.assertEqual({2, 3, 4, 5}, {model["source_index"] for model in self.models})
        profiles = {
            family: (mesh, float(depth))
            for family, mesh, depth in re.findall(
                r'\b[A-Z]+\("([a-z_]+_wall_panel)",\s*"([a-z_]+_panel)",\s*(0\.\d+)\)',
                PANEL_BLOCK.read_text(encoding="utf-8"),
            )
        }
        for model in self.models:
            with self.subTest(style=model["style"]):
                index, digest = REFERENCES[model["style"]]
                self.assertEqual(index, model["source_index"])
                self.assertEqual(digest, model["source_sha256"])
                self.assertFalse(model["source_files_modified"])
                self.assertTrue(model["source_name"].endswith(f"{index}.blend"))
                self.assertIn("meshy", model["provenance"].lower())
                self.assertIn("oliver", model["provenance"].lower())
                creation = model["geometry_provenance"].lower()
                self.assertIn("codex", creation)
                self.assertTrue(any(word in creation for word in ("author", "procedural", "rebuilt")))
                self.assertEqual(6000, model["preparation"]["maximum_triangles"])
                self.assertEqual(model["style"] + "_wall_panel", model["family"])
                self.assertEqual(model["style"] + "_panel", model["mesh_name"])
                self.assertEqual((model["mesh_name"], model["collision_depth"]), profiles[model["family"]])
                for filename, digest in model["outputs"].items():
                    self.assertEqual(digest, sha256(MESH_ROOT / filename), filename)
                self.assertIn(model["mesh_name"] + ".obj", model["outputs"])

    def test_real_exported_geometry_meets_budget_bounds_normals_and_winding(self):
        for model in self.models:
            with self.subTest(style=model["style"]):
                vertices, normals, objects = read_components(MESH_ROOT / (model["mesh_name"] + ".obj"))
                self.assertTrue(vertices)
                self.assertTrue(normals)
                self.assertTrue(all(math.isfinite(value) for vector in (*vertices, *normals) for value in vector))
                for normal in normals:
                    self.assertAlmostEqual(1, math.sqrt(dot(normal, normal)), delta=2e-5)
                faces = [face for component in objects.values() for face in component]
                self.assertEqual(model["triangles"], len(faces))
                self.assertGreater(len(faces), 0)
                self.assertLessEqual(len(faces), 6000)
                bounds = [[min(p[axis] for p in vertices) for axis in range(3)],
                          [max(p[axis] for p in vertices) for axis in range(3)]]
                for measured, recorded in zip(bounds, model["bounds"]):
                    for actual, expected in zip(measured, recorded):
                        self.assertAlmostEqual(actual, expected, delta=2e-7)
                for axis in (0, 1):
                    self.assertGreaterEqual(bounds[0][axis], -2e-7)
                    self.assertLessEqual(bounds[1][axis], 1 + 2e-7)
                    self.assertAlmostEqual(.5, (bounds[0][axis] + bounds[1][axis]) / 2, delta=2e-7)
                self.assertAlmostEqual(1, max(bounds[1][axis] - bounds[0][axis] for axis in (0, 1)),
                                       delta=2e-7, msg="Keep the longest side at one metre without stretching the other")
                self.assertAlmostEqual(0, bounds[0][2], delta=2e-7)
                self.assertGreater(model["collision_depth"], 0)
                self.assertLessEqual(bounds[1][2], model["collision_depth"] + 2e-7)
                for face in faces:
                    self.assertEqual(3, len(face), "The cap counts actual exported triangles")
                    self.assertTrue(all(0 <= v < len(vertices) and 0 <= n < len(normals) for v, n in face))
                    positions = [vertices[v] for v, _ in face]
                    geometric = cross(subtract(positions[1], positions[0]), subtract(positions[2], positions[0]))
                    area_twice = math.sqrt(dot(geometric, geometric))
                    self.assertGreater(area_twice, 1e-12, "Degenerate triangle in actual export")
                    geometric = tuple(value / area_twice for value in geometric)
                    for _, n in face:
                        self.assertGreaterEqual(dot(geometric, normals[n]), -2e-5,
                                                "Corner normal faces behind the geometric winding")

    def test_backing_is_closed_and_open_relief_edges_are_locally_buried(self):
        for model in self.models:
            with self.subTest(style=model["style"]):
                vertices, _, objects = read_components(MESH_ROOT / (model["mesh_name"] + ".obj"))
                surface = LocalBackingSurface(
                    [[vertices[v] for v, _ in face] for name, faces in objects.items()
                     if name.lower().startswith("backing") for face in faces])
                backing_found = False
                for name, faces in objects.items():
                    # Exporters may share vertex indices between components.
                    # Weld positions for topology rather than relying on index allocation.
                    incidence = Counter()
                    orientation = Counter()
                    volume_six = 0
                    for face in faces:
                        p = [vertices[v] for v, _ in face]
                        volume_six += dot(p[0], cross(p[1], p[2]))
                        keys = [tuple(round(c, 7) for c in point) for point in p]
                        for a, b in zip(keys, keys[1:] + keys[:1]):
                            edge = tuple(sorted((a, b)))
                            incidence[edge] += 1
                            orientation[edge] += 1 if (a, b) == edge else -1
                    is_backing = name.lower().startswith("backing")
                    backing_found |= is_backing
                    if is_backing:
                        self.assertTrue(all(count == 2 for count in incidence.values()), name)
                        self.assertGreater(volume_six, 1e-12, "Backing winding must face outwards")
                    for edge, count in incidence.items():
                        self.assertLessEqual(count, 2, f"Non-manifold welded edge: {name}")
                        if count == 1:
                            self.assertFalse(is_backing, name)
                            self.assertTrue(surface.covers_edge(*edge),
                                            f"Relief boundary escapes its actual local backing surface: {name}: {edge}")
                        elif count == 2:
                            self.assertEqual(0, orientation[edge], f"Inconsistent shared-edge winding: {name}")
                self.assertTrue(backing_found, "Name the closed mounting base with a backing prefix")

    def test_local_burial_catches_a_low_bevel_between_buried_edge_endpoints(self):
        # A relief edge crosses a depressed moulding. Its endpoints are buried,
        # but the middle is exposed; endpoint-only checks would accept a hole.
        profile = ((0, .03), (.25, .03), (.5, .01), (.75, .03), (1, .03))
        triangles = []
        for (x0, z0), (x1, z1) in zip(profile, profile[1:]):
            a, b, c, d = (x0, 0, z0), (x1, 0, z1), (x1, 1, z1), (x0, 1, z0)
            triangles.extend(((a, b, c), (a, c, d)))
        surface = LocalBackingSurface(triangles)
        a, b = (.05, .5, .02), (.95, .5, .02)
        self.assertTrue(surface.covers_edge(a, a))
        self.assertTrue(surface.covers_edge(b, b))
        self.assertFalse(surface.covers_edge(a, b))
        self.assertTrue(surface.covers_edge((.05, .5, .005), (.95, .5, .005)))

    def test_each_design_has_one_shared_mesh_and_all_58_material_resources(self):
        self.assertTrue(REQUIRED_LOCALES <= self.languages.keys())
        all_refs = set()
        vocabulary = load_json(DAEDALON_ASSETS / "search_vocabulary.json")
        for model in self.models:
            family = model["family"]
            with self.subTest(family=family):
                ids = GENERATOR.family_block_ids(family)
                expected = {material + suffix + "_" + family
                            for material in GENERATOR.MATERIALS for suffix in ("", "_aged")}
                expected.update(finish + "_" + family for finish in ("white", "black", "bronze", "silver"))
                self.assertEqual(expected, set(ids))
                self.assertEqual(58, len(ids))
                for block_id in ids:
                    ref = "daedalon:" + block_id
                    self.assertNotIn(ref, all_refs)
                    all_refs.add(ref)
                    self.assertEqual({"variants": {"": {"model": "daedalon:mesh/" + block_id}}},
                                     load_json(DAEDALON_ASSETS / f"blockstates/{block_id}.json"))
                    item = load_json(DAEDALON_ASSETS / f"models/item/{block_id}.json")
                    self.assertEqual(GENERATOR.display_parent(family), item["parent"])
                    self.assertFalse((MESH_ROOT / (block_id + ".obj")).exists(), "Geometry must be shared")
                    loot = load_json(DAEDALON_DATA / f"loot_tables/blocks/{block_id}.json")
                    self.assertEqual(ref, loot["pools"][0]["entries"][0]["name"])
                    for locale, labels in self.languages.items():
                        name = labels[f"block.daedalon.{block_id}"]
                        self.assertTrue(name.strip(), (locale, block_id))
                for locale, labels in self.languages.items():
                    self.assertTrue(labels[f"catalogue.daedalon.design.{family}"].strip(), locale)
                terms = STYLE_TERMS[model["style"]] | {
                    "panel", "wall_panel", "ceiling", "coffer", "coffered_ceiling",
                }
                self.assertTrue(terms <= set(vocabulary["families"][family]))
                for kind in ("blocks", "items"):
                    direct = load_json(DAEDALON_DATA / f"tags/{kind}/{family}.json")
                    self.assertEqual({"daedalon:" + value for value in ids}, set(direct["values"]))
                    for term in terms:
                        values = load_json(DAEDALON_DATA / f"tags/{kind}/{term}.json")["values"]
                        self.assertIn("#daedalon:" + family, values, (kind, term))
                definition = load_json(MESH_ROOT / (model["mesh_name"] + ".json"))
                self.assertEqual("daedalon:models/mesh/" + model["mesh_name"] + ".obj", definition["obj"])
                self.assertFalse(definition["fit_to_block"])
                self.assertFalse(definition["smooth_normals"])
                self.assertEqual(6, definition["box_projection_span"])
                self.assertTrue(definition["force_uv_projection"])
                self.assertTrue(definition["face_oriented_uvs"])
        self.assertEqual(232, len(all_refs))


if __name__ == "__main__":
    unittest.main()
