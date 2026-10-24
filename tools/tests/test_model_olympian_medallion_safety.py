from __future__ import annotations

import unittest

from daedalon_test_support import (
    DAEDALON_ASSETS, DAEDALON_DATA, GENERATOR, JAVA_ROOT, MESH_ROOT,
    REPO_ROOT, load_json, parse_obj, sha256,
)


class OlympianMedallionSafetyTests(unittest.TestCase):
    def test_supplied_mesh_and_authored_normals_are_preserved(self) -> None:
        path = MESH_ROOT / "olympian_god_medallion.obj"
        self.assertEqual("09ca7e114c9fe5a9eb491fbb44e7be8929b7aeb13b2660b51f5c072d142ec117", sha256(path))
        mesh = parse_obj(path)
        self.assertEqual((4799, 9657, 12504, 0),
                         tuple(mesh[key] for key in ("vertices", "faces", "normals", "uvs")))
        self.assertEqual({3}, mesh["face_sizes"])
        self.assertEqual({"v//vn"}, mesh["face_index_styles"])
        self.assertEqual({"": 9657}, mesh["material_faces"])
        self.assertEqual(((-0.951579, -0.952934, -0.094811),
                          (0.946720, 0.951355, 0.099754)), mesh["bounds"])
        evidence = load_json(REPO_ROOT / "docs/evidence/olympian-god-medallion-source.json")
        self.assertEqual(sha256(path), evidence["runtime_source"]["sha256"])
        self.assertEqual(56, evidence["runtime_policy"]["material_variants"])
        self.assertEqual(0, evidence["runtime_policy"]["public_blocks"])
        self.assertEqual("Oliver", evidence["provenance"]["owner"])

    def test_runtime_projection_and_uniform_fit_preserve_medallion_proportions(self) -> None:
        definition = load_json(MESH_ROOT / "olympian_god_medallion.json")
        self.assertEqual("daedalon:models/mesh/olympian_god_medallion.obj", definition["obj"])
        self.assertIs(definition["fit_to_block"], True)
        self.assertIs(definition["force_uv_projection"], True)
        self.assertIs(definition["smooth_normals"], False)
        self.assertIs(definition["repair_degenerate_uvs"], False)
        self.assertNotIn("mtl", definition)
        self.assertEqual("axis_stabilized_box", definition["uv_projection"])
        self.assertEqual([1.0, 1.0, 1.0], definition["scale"])
        low, high = parse_obj(MESH_ROOT / "olympian_god_medallion.obj")["bounds"]
        spans = [high[i] - low[i] for i in range(3)]
        fit = 1 / max(spans)
        self.assertAlmostEqual(1.0, spans[1] * fit)
        self.assertAlmostEqual(0.99685447, spans[0] * fit, places=7)
        self.assertAlmostEqual(0.10217203, spans[2] * fit, places=7)

    def test_gui_has_twenty_degree_y_turn_and_one_shared_geometry_family(self) -> None:
        display = load_json(DAEDALON_ASSETS / "models/item/olympian_god_display.json")
        self.assertEqual([0, 20, 0], display["display"]["gui"]["rotation"])
        normals = [tuple(map(float, line.split()[1:]))
                   for line in (MESH_ROOT / "olympian_god_medallion.obj").read_text().splitlines()
                   if line.startswith("vn ")]
        self.assertGreater(sum(z > .95 for x, y, z in normals), 10 * sum(z < -.95 for x, y, z in normals))
        source = (JAVA_ROOT / "client/model/obj/ObjMeshModelLoadingPlugin.java").read_text()
        self.assertEqual(1, source.count('id("models/mesh/olympian_god_medallion.json")'))
        self.assertIn("Registries.ITEM.containsId(id(variant.sourceBlockId()))", source)
        self.assertIn("if (variant.inventoryModelId() != null)", source)
        self.assertIn(".filter(MeshVariant::publicBlockModel)", source)
        family = source[source.index("private static List<MeshVariant> createOlympianGodItemVariants()"):
                        source.index("private static List<MeshVariant> createBenchVariants(")]
        self.assertIn("withMetals(createDetailedVariants(OlympianGodItem.ID_SUFFIX)", family)
        self.assertIn("variant.sourceBlockId(), false, false", family)

    def test_each_registered_finish_remains_item_only_and_generated(self) -> None:
        expected = GENERATOR.olympian_god_item_ids()
        self.assertEqual(56, len(expected))
        self.assertEqual(56, len(set(expected)))
        self.assertIn("bronze_olympian_god_statue", expected)
        self.assertIn("silver_olympian_god_statue", expected)
        for item_id in expected:
            with self.subTest(item=item_id):
                self.assertEqual({"parent": "daedalon:item/olympian_god"},
                                 load_json(DAEDALON_ASSETS / f"models/item/{item_id}.json"))
                self.assertFalse((DAEDALON_ASSETS / f"blockstates/{item_id}.json").exists())
                self.assertFalse((DAEDALON_DATA / f"loot_tables/blocks/{item_id}.json").exists())
        stale = []
        GENERATOR.generate_olympian_god_items(True, stale)
        self.assertEqual([], stale)
