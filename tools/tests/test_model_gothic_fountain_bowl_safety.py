from __future__ import annotations

import re
import unittest

from daedalon_test_support import (
    DAEDALON_ASSETS,
    DAEDALON_DATA,
    GENERATOR,
    JAVA_ROOT,
    MESH_EXPECTATIONS,
    MESH_ROOT,
    REPO_ROOT,
    all_block_ids,
    load_json,
    parse_obj,
    sha256,
)


class GothicFountainBowlSafetyTests(unittest.TestCase):
    MESH = "fountain_gothic_bowl"

    def test_bowls_are_internal_models_without_block_or_item_ids(self) -> None:
        self.assertNotIn("gothic_fountain_bowl", GENERATOR.ALL_FAMILIES)
        self.assertFalse(
            any(block_id.endswith("_gothic_fountain_bowl") for block_id in all_block_ids())
        )
        languages = {
            name: load_json(DAEDALON_ASSETS / f"lang/{name}.json")
            for name in ("en_us", "de_de", "es_es")
        }
        for material in GENERATOR.MATERIALS:
            for block_id in (
                f"{material}_gothic_fountain_bowl",
                f"{material}_aged_gothic_fountain_bowl",
            ):
                self.assertFalse(
                    (DAEDALON_ASSETS / f"blockstates/{block_id}.json").exists()
                )
                self.assertFalse(
                    (DAEDALON_ASSETS / f"models/item/{block_id}.json").exists()
                )
                self.assertFalse(
                    (DAEDALON_DATA / f"loot_tables/blocks/{block_id}.json").exists()
                )
                for language in languages.values():
                    self.assertNotIn(f"block.daedalon.{block_id}", language)

        for kind in ("blocks", "items"):
            tag_root = DAEDALON_DATA / f"tags/{kind}"
            for tag in ("gothic_fountain_bowl", "bowl", "fountain_tier"):
                self.assertFalse((tag_root / f"{tag}.json").exists())

    def test_supplied_mesh_and_measured_inset_are_hash_locked(self) -> None:
        expected = MESH_EXPECTATIONS[self.MESH]
        evidence = load_json(
            REPO_ROOT / "docs/evidence/gothic-fountain-bowl-source.json"
        )
        self.assertEqual("fountain_gothic_bowl.obj", evidence["source"]["file"])
        self.assertEqual(
            "6cdb2ea453828aecea485d0166a0159496c345369464caba73fc80bdf88f3c36",
            evidence["source"]["sha256"],
        )
        self.assertTrue(evidence["source"]["provenance"])
        self.assertNotIn(":\\", evidence["source"]["file"])
        self.assertEqual(
            evidence["source"]["triangles"],
            evidence["preparation"]["target_faces"],
        )
        self.assertEqual(
            "preserve exact supplied geometry",
            evidence["preparation"]["face_policy"],
        )
        self.assertEqual(evidence["source"]["triangles"], evidence["runtime"]["faces"])
        self.assertEqual(evidence["source"]["sha256"], evidence["runtime"]["obj_sha256"])
        self.assertEqual(
            {"small": 1.0, "medium": 1.5, "large": 2.0},
            {
                name: values["diameter_blocks"]
                for name, values in evidence["placement"]["sizes"].items()
            },
        )

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
        self.assertEqual(expected["mtl_sha256"], sha256(MESH_ROOT / expected["mtl"]))

        definition = load_json(MESH_ROOT / expected["definition"])
        self.assertEqual([2.0, 2.0, 2.0], definition["scale"])
        self.assertEqual("axis_stabilized_box", definition["uv_projection"])
        self.assertTrue(definition["force_uv_projection"])
        self.assertFalse(definition["smooth_normals"])
        self.assertEqual(80.0, definition["smooth_angle_degrees"])
        display = load_json(
            DAEDALON_ASSETS / "models/block/mesh/fountain_bowl_display.json"
        )
        self.assertEqual([0.3, 0.3, 0.3], display["display"]["gui"]["scale"])

    def test_magic_plinth_sequence_is_persistent_fixed_and_immediately_synced(self) -> None:
        bowl = (JAVA_ROOT / "block/FountainBowlModel.java").read_text(encoding="utf-8")
        layout = (JAVA_ROOT / "block/FountainAssemblyLayout.java").read_text(encoding="utf-8")
        placement = (JAVA_ROOT / "block/FountainAssemblyPlacement.java").read_text(encoding="utf-8")
        basin = (JAVA_ROOT / "block/FountainBasinBlock.java").read_text(encoding="utf-8")
        block_entity = (
            JAVA_ROOT / "block/entity/FountainBasinBlockEntity.java"
        ).read_text(encoding="utf-8")
        registry = (JAVA_ROOT / "registry/ModBlockEntities.java").read_text(encoding="utf-8")
        initializer = (JAVA_ROOT / "Daedalon.java").read_text(encoding="utf-8")
        block_item_mixin = (JAVA_ROOT / "mixin/BlockItemMixin.java").read_text(
            encoding="utf-8"
        )
        baked = (JAVA_ROOT / "client/model/obj/ObjMeshBakedModel.java").read_text(
            encoding="utf-8"
        )
        loader = (
            JAVA_ROOT / "client/model/obj/ObjMeshModelLoadingPlugin.java"
        ).read_text(encoding="utf-8")
        debug_mixin = (JAVA_ROOT / "mixin/DebugStickItemMixin.java").read_text(
            encoding="utf-8"
        )

        self.assertIn("public static List<Size> sequence(TierCount tierCount)", bowl)
        self.assertIn('NONE("none", List.of())', bowl)
        self.assertIn('ONE("one", List.of(Size.SMALL))', bowl)
        self.assertIn('TWO("two", List.of(Size.MEDIUM, Size.SMALL))', bowl)
        self.assertIn(
            'THREE("three", List.of(Size.LARGE, Size.MEDIUM, Size.SMALL))',
            bowl,
        )
        self.assertNotIn("sequence(PlinthBlock.Size", bowl)
        self.assertIn("SMALL(1.0F)", bowl)
        self.assertIn("MEDIUM(1.5F)", bowl)
        self.assertIn("LARGE(2.0F)", bowl)
        self.assertIn("WATER_FLOOR_PER_DIAMETER = 0.6430F", bowl)
        self.assertIn("WATER_HALF_WIDTH_PER_DIAMETER = 0.3700F", bowl)
        self.assertIn("WATER_BEVEL_PER_DIAMETER = 0.1400F", bowl)
        self.assertIn("style.waterFloorY(size)", bowl)
        self.assertIn("enum TierCount implements StringIdentifiable", bowl)
        self.assertIn("public static List<TierCount> allowed()", bowl)
        self.assertNotIn("allowed(PlinthBlock.Size", bowl)
        self.assertNotIn("extends Block", bowl)
        self.assertNotIn("Waterloggable", bowl)

        self.assertIn("BlockState rawPlinthState", layout)
        self.assertIn("int requestedBowlCount", layout)
        self.assertIn("FountainBowlModel.sequence", layout)
        self.assertIn("baseY = connectionY", layout)
        self.assertNotIn("List<BlockState> states", layout)
        self.assertIn("FountainBasinGeometry.copyCollisionPartShapes", layout)
        self.assertIn("mergeIntoParts", layout)
        self.assertIn("MAX_PART_Y = 5", (JAVA_ROOT / "block/FountainBasinGeometry.java").read_text(encoding="utf-8"))

        self.assertIn('PLINTH_NBT = "Plinth"', block_entity)
        self.assertIn('BOWL_COUNT_NBT = "BowlCount"', block_entity)
        self.assertIn("addPlinthUse", block_entity)
        self.assertIn("maximumBowlCount", block_entity)
        self.assertIn("allowedTierCounts", block_entity)
        self.assertIn("replaceTierCount", block_entity)
        self.assertIn("MathHelper.clamp", block_entity)
        self.assertNotIn("maximumBowlCount(normalized)", block_entity)
        self.assertNotIn("maximumBowlCount(plinthState)", block_entity)
        self.assertNotIn("getChunkManager().markForUpdate", block_entity)
        self.assertIn("changed(candidateLayout)", block_entity)
        self.assertNotIn("MAX_COMPONENTS", block_entity)
        self.assertIn("NbtHelper.fromBlockState", block_entity)
        self.assertIn("NbtHelper.toBlockState", block_entity)
        self.assertIn("toUpdatePacket", block_entity)
        self.assertIn("toInitialChunkDataNbt", block_entity)
        self.assertIn("implements Waterloggable, BlockEntityProvider", basin)
        self.assertIn("new FountainBasinBlockEntity", basin)
        self.assertNotIn("takePlinthUses", basin)
        self.assertIn("BlockEntityType.Builder.create", registry)
        self.assertLess(
            initializer.index("ModBlocks.register();"),
            initializer.index("ModBlockEntities.register();"),
        )

        self.assertIn("FountainAssemblyPlacement.tryAttach", block_item_mixin)
        self.assertIn("requestedBlock instanceof PlinthBlock", placement)
        self.assertNotIn("RECOVERY_SEARCH_DEPTH", placement)
        self.assertIn("context.getStack().decrement(1)", placement)
        self.assertIn("message.daedalon.fountain_bowl_added", placement)
        self.assertIn("message.daedalon.fountain_assembly_complete", placement)
        self.assertNotIn("FountainBasinGeometry.clipToPart", basin)
        self.assertFalse((JAVA_ROOT / "mixin/client/BlockViewMixin.java").exists())

        self.assertIn("createInternalFountainBowlVariants", loader)
        self.assertIn("mesh/internal/", loader)
        self.assertIn("filter(MeshVariant::publicBlockModel)", loader)
        self.assertIn("emitBasinComponents", baked)
        self.assertIn("layout.bowls()", baked)
        self.assertIn("BowlTransform.forSize", baked)
        self.assertIn("emitContainedBowlWater", baked)
        self.assertNotIn("FountainBowlBlock", baked)

        self.assertIn('BASIN_SIZE_CONTROL = "basin_size"', debug_mixin)
        self.assertIn('PLINTH_SIZE_CONTROL = "plinth_size"', debug_mixin)
        self.assertIn('TIER_CONTROL = "tiers"', debug_mixin)
        self.assertIn('"bowls".equals(rememberedName)', debug_mixin)
        self.assertNotIn("BOWL_CONTROL", debug_mixin)
        self.assertIn('WATERLOGGED_CONTROL = "waterlogged"', debug_mixin)
        self.assertIn("basin.allowedTierCounts()", debug_mixin)
        self.assertIn("basin.tierCount()", debug_mixin)
        self.assertIn("basin.replaceTierCount(updated)", debug_mixin)
        self.assertLess(
            debug_mixin.index("BASIN_SIZE_CONTROL,"),
            debug_mixin.index("PLINTH_SIZE_CONTROL,"),
        )
        self.assertLess(
            debug_mixin.index("PLINTH_SIZE_CONTROL,"),
            debug_mixin.index("TIER_CONTROL,"),
        )
        self.assertLess(
            debug_mixin.index("TIER_CONTROL,"),
            debug_mixin.index("WATERLOGGED_CONTROL,"),
        )

    def test_remeasured_water_stays_inside_the_deeper_bowl_inset(self) -> None:
        source = (JAVA_ROOT / "block/FountainBowlModel.java").read_text(
            encoding="utf-8"
        )

        def constant(name: str) -> float:
            match = re.search(rf"{name} = ([0-9.]+)F", source)
            self.assertIsNotNone(match, name)
            return float(match.group(1))

        water_floor = constant("WATER_FLOOR_PER_DIAMETER")
        water_surface = constant("WATER_SURFACE_PER_DIAMETER")
        self.assertEqual(constant("CONNECTION_RISE_PER_DIAMETER"), water_floor)
        self.assertLess(water_floor, water_surface)
        self.assertLess(water_surface, constant("NORMALIZED_MODEL_HEIGHT"))
        profile = load_json(DAEDALON_DATA / "fountain_water/gothic_bowl.json")
        self.assertEqual(profile["water_surface_y"], water_surface)
        self.assertGreaterEqual(profile["minimum_clearance"], 0.001)
        self.assertLessEqual(profile["maximum_clearance"], 0.005)


if __name__ == "__main__":
    unittest.main()
