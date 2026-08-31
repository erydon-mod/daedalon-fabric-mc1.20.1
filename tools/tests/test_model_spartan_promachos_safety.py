from __future__ import annotations

import unittest

from daedalon_test_support import (
    DAEDALON_ASSETS,
    DAEDALON_DATA,
    GENERATOR,
    JAVA_ROOT,
    MESH_EXPECTATIONS,
    MESH_ROOT,
    RESOURCES,
    expected_material_texture_names,
    load_json,
    parse_obj,
    png_dimensions,
    sha256,
)


class SpartanPromachosSafetyTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.block_ids = GENERATOR.family_block_ids("spartan")

    def test_generator_is_current(self) -> None:
        self.assertEqual(
            [],
            GENERATOR.generate(GENERATOR.ALL_FAMILIES, check=True),
        )

    def test_exact_standard_and_aged_manifest(self) -> None:
        self.assertEqual(27, len(GENERATOR.MATERIALS))
        self.assertEqual(55, len(self.block_ids))
        self.assertEqual(55, len(set(self.block_ids)))
        self.assertEqual("bronze_spartan_promachos_statue", self.block_ids[-1])
        excluded = (
            "ashlar",
            "herringbone",
            "rusticated",
            "hewn",
            "weave",
            "guilloche",
            "trim",
        )
        self.assertFalse(
            any(term in block_id for block_id in self.block_ids for term in excluded)
        )
        for material in GENERATOR.MATERIALS:
            self.assertIn(f"statue_spartan_promachos_{material}", self.block_ids)
            self.assertIn(
                f"statue_spartan_promachos_{material}_aged", self.block_ids
            )
        self.assertFalse(any("bronze_aged" in block_id for block_id in self.block_ids))

    def test_approved_spartan_mesh_is_unchanged(self) -> None:
        expected = MESH_EXPECTATIONS["spartan"]
        obj_path = MESH_ROOT / expected["obj"]
        actual = parse_obj(obj_path)
        for key in (
            "vertices",
            "uvs",
            "normals",
            "faces",
            "face_sizes",
            "face_index_styles",
        ):
            self.assertEqual(expected[key], actual[key], key)
        self.assertEqual(expected["obj_sha256"], sha256(obj_path))
        self.assertEqual(
            expected["mtl_sha256"], sha256(MESH_ROOT / expected["mtl"])
        )

    def test_blockstates_items_loot_languages_and_tags_are_complete(self) -> None:
        expected_refs = {f"daedalon:{block_id}" for block_id in self.block_ids}
        tag_root = DAEDALON_DATA / "tags/blocks"
        self.assertTrue(
            expected_refs <= set(load_json(tag_root / "statue.json")["values"])
        )
        self.assertEqual(
            expected_refs,
            set(load_json(tag_root / "statue_spartan_promachos.json")["values"]),
        )

        languages = {
            name: load_json(DAEDALON_ASSETS / f"lang/{name}.json")
            for name in ("en_us", "de_de", "es_es")
        }
        expected_states = {
            f"size={size},facing={facing}"
            for size in ("small", "medium", "large")
            for facing in ("north", "east", "south", "west")
        }
        for block_id in self.block_ids:
            blockstate = load_json(
                DAEDALON_ASSETS / f"blockstates/{block_id}.json"
            )
            self.assertEqual(expected_states, set(blockstate["variants"]), block_id)
            self.assertEqual(
                {f"daedalon:mesh/{block_id}"},
                {
                    variant["model"]
                    for variant in blockstate["variants"].values()
                },
                block_id,
            )
            item = load_json(DAEDALON_ASSETS / f"models/item/{block_id}.json")
            self.assertEqual(
                "daedalon:block/mesh/statue_spartan_promachos_display",
                item["parent"],
            )
            loot = load_json(
                DAEDALON_DATA / f"loot_tables/blocks/{block_id}.json"
            )
            self.assertEqual(
                f"daedalon:{block_id}",
                loot["pools"][0]["entries"][0]["name"],
            )
            self.assertTrue(
                all(
                    f"block.daedalon.{block_id}" in language
                    for language in languages.values()
                )
            )

    def test_native_spartan_textures_are_built_from_16px_tiles(self) -> None:
        texture_root = DAEDALON_ASSETS / "textures/block"
        actual = {
            path.name
            for path in texture_root.glob("statue_spartan_promachos_*.png")
        }
        expected = expected_material_texture_names("statue_spartan_promachos_")
        self.assertEqual(expected, actual)
        for filename in expected:
            self.assertEqual((192, 96), png_dimensions(texture_root / filename))

    def test_size_facing_cache_and_shader_contract(self) -> None:
        plugin = (
            JAVA_ROOT / "client/model/obj/ObjMeshModelLoadingPlugin.java"
        ).read_text(encoding="utf-8")
        baked = (
            JAVA_ROOT / "client/model/obj/ObjMeshBakedModel.java"
        ).read_text(encoding="utf-8")
        block = (JAVA_ROOT / "block/SpartanStatueBlock.java").read_text(
            encoding="utf-8"
        )
        shared_block = (JAVA_ROOT / "block/StatueBlock.java").read_text(
            encoding="utf-8"
        )
        definition = load_json(
            DAEDALON_ASSETS / "models/mesh/statue_spartan_promachos.json"
        )
        mixins = load_json(RESOURCES / "daedalon.mixins.json")["client"]

        self.assertIn("SharedGeometry", plugin)
        self.assertEqual(1, plugin.count("baked = ObjMeshBakedModel.bakeGeometry("))
        self.assertIn(
            "ObjMeshBakedModel.GeometryBakeResult bakedGeometry = geometry.bake(metadataModel)",
            plugin,
        )
        self.assertEqual(1, plugin.count("guiIconTemplate = ObjGuiIconTemplate.create("))
        self.assertIn("ObjMeshBakedModel.materialize(", plugin)
        self.assertIn(
            "quad.spriteBake(sprite, MutableQuadView.BAKE_NORMALIZED)", baked
        )
        self.assertEqual("planar_with_box_fallback", definition["uv_projection"])
        self.assertEqual([3.0, 3.0, 3.0], definition["scale"])
        self.assertIn('SMALL("small", 1)', shared_block)
        self.assertIn('MEDIUM("medium", 2)', shared_block)
        self.assertIn('LARGE("large", 3)', shared_block)
        self.assertIn("MODEL_SUPPORT_CENTER_X = 0.41134763F", block)
        self.assertIn("MODEL_SUPPORT_CENTER_Z = -0.14984404F", block)
        self.assertIn("MODEL_GROUND_CONTACT_Y = 0.0625F", block)
        self.assertIn("Direction.EAST", block)
        self.assertIn("private static final Profile PROFILE", block)
        self.assertIn("super(settings, PROFILE)", block)
        self.assertIn("Map<StatueBlock.Profile, StatueTransform[][][]>", baked)
        self.assertIn("instanceof StatueBlock", baked)
        self.assertIn("scaleUvFromCenter(quad.u(vertex), scale)", baked)
        self.assertIn("scaleUvFromCenter(quad.v(vertex), scale)", baked)
        self.assertIn("client.indium.TerrainRenderContextMixin", mixins)
        self.assertIn("client.iris.XHFPTerrainVertexMixin", mixins)


if __name__ == "__main__":
    unittest.main()
