from __future__ import annotations

import unittest

from daedalon_test_support import (
    DAEDALON_ASSETS,
    DAEDALON_DATA,
    GENERATOR,
    JAVA_ROOT,
    MESH_EXPECTATIONS,
    MESH_ROOT,
    load_json,
    parse_obj,
    sha256,
)


class ZeusStatueSafetyTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.block_ids = GENERATOR.family_block_ids("zeus")

    def test_exact_canonical_material_first_ids(self) -> None:
        expected: list[str] = []
        for material in GENERATOR.MATERIALS:
            expected.extend(
                (f"{material}_zeus_statue", f"{material}_aged_zeus_statue")
            )
        expected.append("bronze_zeus_statue")
        self.assertEqual(expected, self.block_ids)
        self.assertEqual(55, len(self.block_ids))
        self.assertEqual(55, len(set(self.block_ids)))
        self.assertFalse(any(block_id.startswith("statue_zeus_") for block_id in self.block_ids))
        self.assertFalse(any("_statue_zeus" in block_id for block_id in self.block_ids))
        self.assertFalse(any(block_id.endswith("_aged") for block_id in self.block_ids))

    def test_authored_uv_mesh_is_locked(self) -> None:
        expected = MESH_EXPECTATIONS["zeus"]
        obj_path = MESH_ROOT / expected["obj"]
        actual = parse_obj(obj_path)
        for key in (
            "vertices",
            "uvs",
            "normals",
            "faces",
            "face_sizes",
            "face_index_styles",
            "bounds",
        ):
            self.assertEqual(expected[key], actual[key], key)
        self.assertEqual(expected["obj_sha256"], sha256(obj_path))
        self.assertIsNone(expected["mtl"])
        self.assertFalse((MESH_ROOT / "zeus.mtl").exists())

    def test_states_items_loot_languages_and_tags_are_complete(self) -> None:
        expected_refs = {f"daedalon:{block_id}" for block_id in self.block_ids}
        expected_states = {
            f"size={size},facing={facing}"
            for size in ("small", "medium", "large")
            for facing in ("north", "east", "south", "west")
        }
        languages = {
            name: load_json(DAEDALON_ASSETS / f"lang/{name}.json")
            for name in ("en_us", "de_de", "es_es")
        }
        for block_id in self.block_ids:
            blockstate = load_json(
                DAEDALON_ASSETS / f"blockstates/{block_id}.json"
            )
            self.assertEqual(expected_states, set(blockstate["variants"]), block_id)
            self.assertEqual(
                {f"daedalon:mesh/{block_id}"},
                {variant["model"] for variant in blockstate["variants"].values()},
            )
            self.assertEqual(
                "daedalon:block/mesh/zeus_statue_display",
                load_json(
                    DAEDALON_ASSETS / f"models/item/{block_id}.json"
                )["parent"],
            )
            self.assertEqual(
                f"daedalon:{block_id}",
                load_json(
                    DAEDALON_DATA / f"loot_tables/blocks/{block_id}.json"
                )["pools"][0]["entries"][0]["name"],
            )
            self.assertTrue(
                all(
                    f"block.daedalon.{block_id}" in language
                    for language in languages.values()
                )
            )

        block_tags = DAEDALON_DATA / "tags/blocks"
        self.assertEqual(
            expected_refs,
            set(load_json(block_tags / "zeus_statue.json")["values"]),
        )
        self.assertTrue(
            expected_refs <= set(load_json(block_tags / "statue.json")["values"])
        )
        expected_aged_refs = {
            ref for ref in expected_refs if "_aged_" in ref
        }
        self.assertTrue(
            expected_aged_refs
            <= set(load_json(block_tags / "aged.json")["values"])
        )
        self.assertEqual(
            {"#daedalon:zeus_bust", "#daedalon:zeus_statue"},
            set(load_json(block_tags / "zeus.json")["values"]),
        )
        self.assertIn(
            "#daedalon:zeus_statue",
            load_json(block_tags / "greek.json")["values"],
        )

    def test_profile_projection_cache_and_texture_reuse_contract(self) -> None:
        definition = load_json(MESH_ROOT / "zeus_statue.json")
        self.assertNotIn("mtl", definition)
        self.assertFalse(definition["repair_degenerate_uvs"])
        self.assertFalse(definition["force_uv_projection"])
        self.assertEqual("axis_stabilized_box", definition["uv_projection"])
        self.assertTrue(definition["face_oriented_uvs"])
        self.assertTrue(definition["smooth_normals"])
        self.assertEqual([3.0, 3.0, 3.0], definition["scale"])

        block = (JAVA_ROOT / "block/ZeusStatueBlock.java").read_text(
            encoding="utf-8"
        )
        shared_block = (JAVA_ROOT / "block/StatueBlock.java").read_text(
            encoding="utf-8"
        )
        plugin = (
            JAVA_ROOT / "client/model/obj/ObjMeshModelLoadingPlugin.java"
        ).read_text(encoding="utf-8")
        baked = (
            JAVA_ROOT / "client/model/obj/ObjMeshBakedModel.java"
        ).read_text(encoding="utf-8")

        self.assertIn("MODEL_SUPPORT_CENTER_X = 0.5041394F", block)
        self.assertIn("MODEL_SUPPORT_CENTER_Z = 0.4360307F", block)
        self.assertIn("MODEL_GROUND_CONTACT_Y = 0.0F", block)
        self.assertIn("Direction.NORTH", block)
        self.assertIn("private static final Profile PROFILE", block)
        self.assertIn("super(settings, PROFILE)", block)
        self.assertIn('SMALL("small", 1)', shared_block)
        self.assertIn('MEDIUM("medium", 2)', shared_block)
        self.assertIn('LARGE("large", 3)', shared_block)
        self.assertIn("new ConcurrentHashMap<>()", baked)
        self.assertIn("Map<StatueBlock.Profile, StatueTransform[][][]>", baked)
        self.assertIn("computeIfAbsent(\n                    profile,", baked)
        self.assertNotIn("Map<StatueBlock, StatueTransform[][][]>", baked)
        self.assertIn("scaleUvFromCenter(quad.u(vertex), scale)", baked)
        self.assertIn("scaleUvFromCenter(quad.v(vertex), scale)", baked)
        self.assertIn("0.5F + (uv - 0.5F) * scale", baked)
        self.assertIn('material + "_zeus_statue"', plugin)
        self.assertIn('material + "_aged_zeus_statue"', plugin)
        self.assertIn("SPARTAN_BLOCK_PREFIX + material", plugin)
        self.assertFalse(
            any(
                path.name.startswith("zeus_statue_")
                for path in (DAEDALON_ASSETS / "textures/block").glob("*.png")
            )
        )

    def test_size_uv_span_keeps_constant_world_texture_density(self) -> None:
        source_height = 3.0
        source_uv_span = 1.0
        expected_density = source_uv_span / source_height
        for scale in (1.0 / 3.0, 2.0 / 3.0, 1.0):
            world_height = source_height * scale
            scaled_uv_span = source_uv_span * scale
            self.assertAlmostEqual(
                expected_density,
                scaled_uv_span / world_height,
            )


if __name__ == "__main__":
    unittest.main()
