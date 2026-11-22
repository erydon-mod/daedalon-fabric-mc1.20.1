"""Physical lantern contracts, checked against the shipped shared OBJ geometry."""
from __future__ import annotations

from collections import defaultdict
import hashlib
import json
import math
import re
import unittest

from PIL import Image

from daedalon_test_support import (
    DAEDALON_ASSETS, DAEDALON_DATA, GENERATOR, MESH_EXPECTATIONS,
    MESH_ROOT, REPO_ROOT, load_json, parse_obj, sha256,
)

STYLES = ("georgian", "gothic", "byzantine")
MOUNTS = ("pier", "wall", "hanging")
ROLES = ("stone", "lead", "bronze", "glass")
CANONICAL_BURNER_HEIGHT = .2254 / 1.902 * 1.3
APPROVED_BODY_PREFIXES = {
    "georgian": (2501532, "ad334e8a60b75fdb3848ba8e7ed8293859ee8231a54f35ff4a165dbba907ce26"),
    "byzantine": (8114303, "0259d7213355696cbad6d84a3db76caf6941993bc072ccbe7f26ef4ab5bea22a"),
}
GOTHIC_RETAINED_GEOMETRY = {
    "hanging_bronze": "bdf8ce872843930132b37e2381f20ae53a22808dc4c7587842dc3a719d7a2e7f",
    "hanging_glass": "a1da47e51d20baf845dff35c68dc6ca87cf045213c864d0ad23c133700d2d826",
    "hanging_stone": "b5522071e0afa422db3a986ee4f4abfade2a28e35442112c96f1fe7115241a6c",
    "pier_bronze": "07d2c9561441b21a9e7729dc2efd0efbc690c1fceb347b84b56c3115a2d43f73",
    "pier_glass": "f586374bb0ce98a2e8c87b1d0c033b571b1d35ee2153f7d30d2745c2c3ccbf8b",
    "pier_stone": "909f6eadd9856a55ea9fa03de0f04df2057560d1be982ce0f88d23b790e66244",
    "wall_bronze": "df3a61d9571a32215d5b8c97a057f48898a144d0ab68385d19b22fcbef6008c8",
    "wall_glass": "0e030882f1bf03e2f137f2c363cfea65bbe2c0cef56d6920215cd7cf762ee490",
    "wall_stone": "6e9411058f1d24fd14866e3811e9897ffc3ac4b719e5bfbce9379629f83fb510",
    "pier_flame": "7034ef2d8485eb7a61d80bfa6852c1dfd9bdcd9b22d76b24dabfda5756a2af6a",
    "wall_flame": "f18994ddf18700240fccb7a6d205f3dd1a6f2163c32cec6563b7da7a8c8d42c5",
    "hanging_flame": "bd751f95bc0a49ea9526bec96d66a075d6f466ec70e970e27b6b1becb2bbd237",
}


def read_geometry(path):
    vertices, normals, uvs = [], [], []
    groups, materials = defaultdict(list), defaultdict(set)
    group, material = None, None
    for line in path.read_text(encoding="utf-8").splitlines():
        fields = line.split()
        if not fields:
            continue
        if fields[0] == "v":
            vertices.append(tuple(map(float, fields[1:])))
        elif fields[0] == "vn":
            normals.append(tuple(map(float, fields[1:])))
        elif fields[0] == "vt":
            uvs.append(tuple(map(float, fields[1:])))
        elif fields[0] in ("o", "g"):
            group = fields[1]
        elif fields[0] == "usemtl":
            material = fields[1]
        elif fields[0] == "f":
            refs = [token.split("/") for token in fields[1:]]
            groups[group].append(tuple((int(v[0])-1, int(v[1])-1 if v[1] else None, int(v[2])-1) for v in refs))
            materials[group].add(material)
    return {"vertices": vertices, "normals": normals, "uvs": uvs, "groups": groups, "materials": materials}


def used_points(mesh, groups):
    indices = {v[0] for name in groups for face in mesh["groups"][name] for v in face}
    return [mesh["vertices"][i] for i in indices]


def bounds(points):
    return tuple(tuple(fn(p[i] for p in points) for i in range(3)) for fn in (min, max))


def components(faces):
    parent = {}
    def find(i):
        parent.setdefault(i, i)
        while parent[i] != i:
            parent[i] = parent[parent[i]]
            i = parent[i]
        return i
    for face in faces:
        first = find(face[0][0])
        for ref in face[1:]:
            parent[find(ref[0])] = first
    result = defaultdict(set)
    for index in parent:
        result[find(index)].add(index)
    return list(result.values())


def sub(a, b):
    return tuple(x-y for x, y in zip(a, b))


def dot(a, b):
    return sum(x*y for x, y in zip(a, b))


def cross(a, b):
    return (a[1]*b[2]-a[2]*b[1], a[2]*b[0]-a[0]*b[2], a[0]*b[1]-a[1]*b[0])


def normal(points):
    n = cross(sub(points[1], points[0]), sub(points[2], points[0]))
    length = math.sqrt(dot(n, n))
    return tuple(v/length for v in n), length


class LanternSafetyTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.evidence = load_json(REPO_ROOT / "docs/evidence/lantern-source.json")
        cls.meshes = {style: read_geometry(MESH_ROOT / f"lantern_{style}.obj") for style in STYLES}
        cls.suspension = read_geometry(MESH_ROOT / "lantern_suspension.obj")

    def assertBounds(self, expected, actual, message=None):
        for row_a, row_b in zip(expected, actual):
            for a, b in zip(row_a, row_b):
                self.assertAlmostEqual(a, b, delta=1e-6, msg=message)

    def test_full_approved_detail_and_source_provenance_are_locked(self):
        self.assertEqual(set(STYLES), set(self.evidence["families"]))
        for style, mesh in self.meshes.items():
            evidence = self.evidence["families"][style]
            export = evidence["export"]
            self.assertIn("Oliver's own", evidence["provenance"])
            self.assertEqual(sha256(DAEDALON_ASSETS / "textures/block/lantern_flame.png"),
                             evidence["flame_texture"]["sha256"])
            self.assertEqual({f"{mount}_{role}" for mount in MOUNTS for role in (*ROLES, "flame")}, set(mesh["groups"]))
            if style in APPROVED_BODY_PREFIXES:
                prefix_size, prefix_digest = APPROVED_BODY_PREFIXES[style]
                actual_bytes = (MESH_ROOT / f"lantern_{style}.obj").read_bytes()
                self.assertEqual(prefix_digest, hashlib.sha256(actual_bytes[:prefix_size]).hexdigest(),
                                 "Adding a flame must preserve every approved body vertex and face")
            else:
                # The requested Gothic redesign replaces lead tracery only.
                for group, expected in GOTHIC_RETAINED_GEOMETRY.items():
                    corners = [[(mesh["vertices"][v], None if uv is None else mesh["uvs"][uv], mesh["normals"][n])
                                for v, uv, n in face] for face in mesh["groups"][group]]
                    actual = hashlib.sha256(json.dumps(corners, separators=(",", ":")).encode()).hexdigest()
                    self.assertEqual(expected, actual, f"Gothic tracery changes must retain {group} exactly")
            for group, materials in mesh["materials"].items():
                self.assertEqual({group.rsplit("_", 1)[1]}, materials)
            self.assertEqual(export["sha256"], sha256(MESH_ROOT / f"lantern_{style}.obj"))
            self.assertEqual(export["sha256"], MESH_EXPECTATIONS[f"lantern_{style}"]["obj_sha256"])
            self.assertTrue(all(export["validation"][key] for key in ("lf_text", "finite_unit_normals", "triangulated_no_degenerates", "glass_uvs_0_to_1")))
            for report in evidence["mounts"]:
                mount = report["mount"]
                self.assertTrue(report["sources_unchanged"])
                self.assertTrue(report["source_mesh_digests"])
                self.assertTrue(all(len(value) == 64 for value in report["source_mesh_digests"].values()))
                triangles = sum(len(mesh["groups"][f"{mount}_{role}"]) for role in ROLES)
                self.assertEqual(report["authoring_triangles"], triangles, (style, mount, "Full approved geometry must be retained"))
                self.assertEqual(report["triangles"], triangles)
                self.assertTrue(all(not (o["boundary"] or o["nonmanifold"] or o["degenerate"]) for o in report["objects"]))
        self.assertFalse(list(MESH_ROOT.glob("lantern*_item.obj")), "Inventory views must not silently substitute a simplified lantern")

    def test_all_nine_bodies_have_exact_metre_height_and_flush_wall_backs(self):
        profile_source = (REPO_ROOT / "src/main/java/com/oliver/daedalon/block/LanternBlock.java").read_text(encoding="utf-8")
        for style, mesh in self.meshes.items():
            profile = [float(v) for v in re.search(rf"\b{style.upper()}\(([^)]+)\)", profile_source).group(1).split(",")]
            for report in self.evidence["families"][style]["mounts"]:
                mount = report["mount"]
                actual = bounds(used_points(mesh, [f"{mount}_{role}" for role in ROLES]))
                self.assertEqual((0., 1.), (actual[0][1], actual[1][1]), (style, mount))
                self.assertEqual(1., report["body_height_m"])
                self.assertBounds(report["body_bounds"], actual, (style, mount))
                width, depth = profile[{"pier": 0, "wall": 2, "hanging": 4}[mount]:][:2]
                for measured, cached in ((actual[1][0]-actual[0][0], width), (actual[1][2]-actual[0][2], depth)):
                    self.assertGreaterEqual(cached+1e-10, measured, (style, mount, "Cached profile clips the actual mesh"))
                    self.assertAlmostEqual(measured, cached, delta=1e-6)
                self.assertAlmostEqual(0, actual[0][0]+actual[1][0], delta=1e-6)
                if mount == "wall":
                    self.assertEqual(0, actual[0][2], style)
                    self.assertEqual(0, report["wall_rear_z"])
                else:
                    self.assertAlmostEqual(0, actual[0][2]+actual[1][2], delta=1e-6)

    def test_every_burner_is_bronze_thirty_percent_larger_with_its_valve_near_the_base(self):
        reference_widths = None
        for style, mesh in self.meshes.items():
            for report in self.evidence["families"][style]["mounts"]:
                mount, base = report["mount"], report["burner_base_y"]
                name = f"{mount}_bronze"
                self.assertEqual({"bronze"}, mesh["materials"][name])
                points = used_points(mesh, [name]); box = bounds(points)
                self.assertAlmostEqual(CANONICAL_BURNER_HEIGHT, box[1][1]-base, delta=1e-6, msg=(style, mount))
                self.assertAlmostEqual(CANONICAL_BURNER_HEIGHT, report["burner_height_m"], delta=1e-9)
                self.assertGreaterEqual(box[0][1], report["floor_y"]-1e-6)
                widths = sorted((box[1][0]-box[0][0], box[1][2]-box[0][2]))
                if reference_widths is None:
                    reference_widths = widths
                for expected, actual in zip(reference_widths, widths):
                    self.assertAlmostEqual(expected, actual, delta=1e-6, msg=(style, mount, "Burner dimensions changed between lanterns"))
                boxes = [bounds([mesh["vertices"][i] for i in part]) for part in components(mesh["groups"][name])]
                post = next(b for b in boxes if b[1][1]-b[0][1] > .1 and b[1][1] > base+.1)
                axis = ((post[0][0]+post[1][0])/2, (post[0][2]+post[1][2])/2)
                valves = [b for b in boxes if math.hypot((b[0][0]+b[1][0])/2-axis[0], (b[0][2]+b[1][2])/2-axis[1]) > .02]
                self.assertEqual(2, len(valves), (style, mount, "Expected lateral valve stem and wheel"))
                for valve in valves:
                    self.assertGreater(valve[0][1], base+.03)
                    self.assertLess(valve[1][1], base+.07)

    def test_each_bay_has_glass_behind_physically_proud_lead(self):
        pane_counts = {"georgian": (6, 3, 6), "gothic": (6, 4, 6), "byzantine": (6, 4, 6)}
        for style, mesh in self.meshes.items():
            for mount, count in zip(MOUNTS, pane_counts[style]):
                glass_faces, lead_faces = mesh["groups"][f"{mount}_glass"], mesh["groups"][f"{mount}_lead"]
                self.assertEqual({"glass"}, mesh["materials"][f"{mount}_glass"])
                self.assertEqual({"lead"}, mesh["materials"][f"{mount}_lead"])
                panes = components(glass_faces)
                self.assertEqual(count, len(panes), (style, mount))
                for face in glass_faces:
                    for ref in face:
                        self.assertIsNotNone(ref[1], (style, mount, "Glass needs authored planar UVs"))
                        self.assertTrue(all(0 <= value <= 1 for value in mesh["uvs"][ref[1]]))
                lead_triangles = [[mesh["vertices"][ref[0]] for ref in face] for face in lead_faces]
                for pane in panes:
                    points = [mesh["vertices"][i] for i in pane]
                    cap = max(([mesh["vertices"][ref[0]] for ref in face] for face in glass_faces if face[0][0] in pane), key=lambda tri: normal(tri)[1])
                    n, _ = normal(cap)
                    centre = tuple(sum(p[i] for p in points)/len(points) for i in range(3))
                    if n[0]*centre[0]+n[2]*centre[2] < 0:
                        n = tuple(-v for v in n)
                    self.assertLess(abs(n[1]), 1e-5)
                    tangent = (n[2], 0, -n[0])
                    outer = max(dot(p, n) for p in points)
                    u0, u1 = min(dot(p, tangent) for p in points), max(dot(p, tangent) for p in points)
                    y0, y1 = min(p[1] for p in points), max(p[1] for p in points)
                    gaps = []
                    for tri in lead_triangles:
                        fn, _ = normal(tri)
                        mid = tuple(sum(p[i] for p in tri)/3 for i in range(3))
                        if dot(fn, n) > .9 and u0-.02 <= dot(mid, tangent) <= u1+.02 and y0-.015 <= mid[1] <= y1+.015:
                            gap = min(dot(p, n) for p in tri)-outer
                            if -.02 < gap < .03:
                                gaps.append(gap)
                    self.assertTrue(gaps, (style, mount, "No proud front lead surface around pane"))
                    self.assertGreater(min(gaps), .001, (style, mount, "Glass is too close to the visible lead surface"))

    def test_shared_suspension_keeps_exact_anchor_spans_and_round_join_clearance(self):
        evidence = self.evidence["suspension"]
        self.assertEqual(evidence["export"]["sha256"], sha256(MESH_ROOT / "lantern_suspension.obj"))
        self.assertEqual({"half_chain", "full_chain", "canopy", "half_chain_small", "full_chain_small", "canopy_small"}, set(self.suspension["groups"]))
        for name, report in evidence["components"].items():
            self.assertEqual({"lead"}, self.suspension["materials"][name])
            self.assertBounds(report["geometric_bounds"], bounds(used_points(self.suspension, [name])), name)
            scale = .5 if name.endswith("_small") else 1.
            self.assertEqual(scale, report["stock_scale"])
            if name.startswith("canopy"):
                self.assertEqual(0., report["ceiling_contact_y"])
                self.assertEqual(0., report["geometric_bounds"][1][1])
            else:
                span = .5 if name.startswith("half") else 1.
                self.assertEqual(span, report["logical_anchor_span_m"])
                self.assertGreater(report["minimum_stock_clearance_m"], .0003*scale)
                self.assertTrue(report["end_seam_ring_owned"])
                self.assertEqual(report["rectangular_links"]+(1 if name.startswith("half") else 0), report["round_join_rings"])
                self.assertEqual(name.startswith("half"), report["direct_eye"])
                self.assertGreater(report["geometric_bounds"][1][1], span, "The terminal round join must cross its logical seam")
        self.assertGreater(evidence["components"]["full_chain_small"]["rectangular_links"], evidence["components"]["full_chain"]["rectangular_links"])

    def test_shared_mesh_roles_and_all_stone_resources_stay_in_sync(self):
        languages = {locale: load_json(DAEDALON_ASSETS / f"lang/{locale}.json") for locale in ("en_us", "de_de", "es_es")}
        for style in STYLES:
            family = f"{style}_lantern"
            ids = GENERATOR.family_block_ids(family)
            self.assertEqual(56, len(ids))
            self.assertEqual({f"{material}{'_aged' if aged else ''}_{family}" for material in GENERATOR.MATERIALS for aged in (False, True)}
                             | {f"{metal}_{family}" for metal in ("bronze", "silver")}, set(ids))
            definition = load_json(MESH_ROOT / f"lantern_{style}.json")
            self.assertFalse(definition["fit_to_block"])
            self.assertFalse(definition["smooth_normals"])
            self.assertEqual([.5, 0., .5], definition["translate"])
            self.assertEqual({"lead": "daedalon:block/lantern_lead", "bronze": "daedalon:block/bronze", "glass": "daedalon:block/lantern_glazing", "flame": "daedalon:block/lantern_flame"}, definition["materials"])
            for block_id in ids:
                self.assertEqual({"variants": {"": {"model": f"daedalon:mesh/{block_id}"}}}, load_json(DAEDALON_ASSETS / f"blockstates/{block_id}.json"))
                self.assertEqual("daedalon:block/mesh/lantern_display", load_json(DAEDALON_ASSETS / f"models/item/{block_id}.json")["parent"])
                self.assertEqual(f"daedalon:{block_id}", load_json(DAEDALON_DATA / f"loot_tables/blocks/{block_id}.json")["pools"][0]["entries"][0]["name"])
                for language in languages.values():
                    self.assertTrue(language[f"block.daedalon.{block_id}"].strip())
            for kind in ("blocks", "items"):
                root = DAEDALON_DATA / f"tags/{kind}"
                self.assertEqual({f"daedalon:{name}" for name in ids}, set(load_json(root / f"{family}.json")["values"]))
                for name in ("lantern", "pier", "wall_lantern", "hanging_lantern", "gas_burner", "glazing", "chain"):
                    self.assertIn(f"#daedalon:{family}", load_json(root / f"{name}.json")["values"])
        for language in languages.values():
            for key in ("mount", "size", "chain", "light"):
                self.assertTrue(language[f"tooltip.daedalon.lantern.{key}"].strip())
        for helper in ("lantern_part", "lantern_chain"):
            self.assertFalse((DAEDALON_ASSETS / f"models/item/{helper}.json").exists())
            self.assertFalse((DAEDALON_DATA / f"loot_tables/blocks/{helper}.json").exists())

    def test_gas_flames_are_tiny_separate_uv_mapped_components_on_every_nozzle(self):
        for style, mesh in self.meshes.items():
            for mount in MOUNTS:
                group = f"{mount}_flame"
                self.assertEqual({"flame"}, mesh["materials"][group])
                flame_faces = mesh["groups"][group]
                self.assertEqual(12, len(flame_faces), (style, mount, "Three two-sided gas-flame cards"))
                flame_bounds = bounds(used_points(mesh, [group]))
                burner_bounds = bounds(used_points(mesh, [f"{mount}_bronze"]))
                self.assertAlmostEqual(burner_bounds[1][1], flame_bounds[0][1], delta=.001,
                                       msg=(style, mount, "Flame must meet the burner nozzle"))
                self.assertAlmostEqual(.075, flame_bounds[1][1] - flame_bounds[0][1], delta=1e-6)
                self.assertLessEqual(flame_bounds[1][0] - flame_bounds[0][0], .036)
                self.assertLessEqual(flame_bounds[1][2] - flame_bounds[0][2], .036)
                for face in flame_faces:
                    for ref in face:
                        self.assertIsNotNone(ref[1], "Gas-flame cards need authored UVs")
                        self.assertTrue(all(0 <= value <= 1 for value in mesh["uvs"][ref[1]]))
                        vertex_y = mesh["vertices"][ref[0]][1]
                        expected_v = 1.0 if abs(vertex_y - flame_bounds[0][1]) < 1e-6 else 0.0
                        self.assertAlmostEqual(expected_v, mesh["uvs"][ref[1]][1], delta=1e-6,
                                               msg="Minecraft V0 must show the flame tip at the top")

    def test_gas_flame_texture_is_translucent_yellow_with_emission_confined_to_its_silhouette(self):
        texture_root = DAEDALON_ASSETS / "textures/block"
        with Image.open(texture_root / "lantern_flame.png") as colour, Image.open(texture_root / "lantern_flame_s.png") as emission:
            self.assertEqual("RGBA", colour.mode)
            self.assertEqual((64, 128), colour.size)
            self.assertEqual(colour.size, emission.size)
            alpha = colour.getchannel("A").getextrema()
            self.assertEqual(0, alpha[0])
            self.assertTrue(100 < alpha[1] < 255)
            for y in range(colour.height):
                self.assertEqual(0, colour.getpixel((0, y))[3])
                self.assertEqual(0, colour.getpixel((colour.width - 1, y))[3])
                for x in range(colour.width):
                    r, g, b, a = colour.getpixel((x, y))
                    if a:
                        self.assertTrue(r >= g > b, "Gas flame should stay warm yellow and cream, without a dark backing")
                    self.assertEqual((0, 0, 0, 254 if a else 0), emission.getpixel((x, y)))

    def test_actual_export_has_finite_unit_normals_and_nonzero_triangles(self):
        for name, mesh in (*self.meshes.items(), ("suspension", self.suspension)):
            path = MESH_ROOT / f"lantern_{name}.obj"
            self.assertNotIn(b"\r", path.read_bytes())
            for vector in mesh["vertices"]:
                self.assertTrue(all(math.isfinite(value) for value in vector))
            for vector in mesh["normals"]:
                self.assertAlmostEqual(1., math.sqrt(dot(vector, vector)), delta=2e-5)
            for faces in mesh["groups"].values():
                for face in faces:
                    self.assertEqual(3, len(face))
                    # Preserve tiny authored bevel triangles; only truly collapsed
                    # geometry is invalid, regardless of its visual size.
                    self.assertGreater(normal([mesh["vertices"][ref[0]] for ref in face])[1], 0.)

    def test_iris_neutral_fallback_includes_lanterns_and_their_internal_cells(self):
        shader = (REPO_ROOT / "src/main/java/com/oliver/daedalon/mixin/client/iris/WorldRenderingSettingsMixin.java").read_text(encoding="utf-8")
        self.assertIn("PanelShaderMaterialIds.classify", shader)
        self.assertRegex(shader, r"instanceof\s+LanternBlock\b")
        for helper in ("lanternPart", "lanternChain"):
            self.assertIn(f"ModBlocks.{helper}()", shader)


if __name__ == "__main__":
    unittest.main()
