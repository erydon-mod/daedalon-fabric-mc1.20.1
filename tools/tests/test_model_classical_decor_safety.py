from __future__ import annotations

import unittest
from collections import Counter

from daedalon_test_support import (
    DAEDALON_ASSETS,
    DAEDALON_DATA,
    GENERATOR,
    JAVA_ROOT,
    MESH_EXPECTATIONS,
    MESH_ROOT,
    REPO_ROOT,
    RESOURCES,
    all_block_ids,
    expected_material_texture_names,
    load_json,
    parse_obj,
    png_dimensions,
    sha256,
)


class ClassicalDecorSafetyTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.urn_families = tuple(GENERATOR.URN_FAMILIES)
        cls.urn_ids = [
            block_id
            for family in cls.urn_families
            for block_id in GENERATOR.family_block_ids(family)
        ]
        cls.plinth_families = tuple(GENERATOR.PLINTH_FAMILIES)
        cls.plinth_ids = [
            block_id
            for family in cls.plinth_families
            for block_id in GENERATOR.family_block_ids(family)
        ]

    def test_exact_4046_id_manifest(self) -> None:
        block_ids = all_block_ids()
        self.assertEqual(4046, len(block_ids))
        self.assertEqual(4046, len(set(block_ids)))
        self.assertEqual(605, len(self.urn_ids))
        self.assertEqual(270, len(self.plinth_ids))
        self.assertIn("aganite_amphora_urn", block_ids)
        self.assertIn("aganite_aged_amphora_urn", block_ids)
        self.assertNotIn("aganite_urn_amphora", block_ids)
        self.assertNotIn("aganite_urn_amphora_aged", block_ids)
        excluded = ("ashlar", "herringbone", "rusticated", "hewn", "weave")
        self.assertFalse(
            any(term in block_id for block_id in block_ids for term in excluded)
        )

        expected_files = {f"{block_id}.json" for block_id in block_ids}
        self.assertEqual(
            expected_files | {"fountain_basin_part.json", "monopteros_part.json"},
            {
                path.name
                for path in (DAEDALON_ASSETS / "blockstates").glob("*.json")
            },
        )
        self.assertNotIn(
            "fountain_basin_part.json",
            {
                path.name
                for path in (DAEDALON_ASSETS / "models/item").glob("*.json")
            },
        )
        self.assertEqual(
            expected_files | {"emblem.json"},
            {
                path.name
                for path in (DAEDALON_ASSETS / "models/item").glob("*.json")
            },
        )
        no_drop_files = {
            f"{block_id}.json"
            for family in GENERATOR.NO_DROP_FAMILIES
            for block_id in GENERATOR.family_block_ids(family)
        }
        self.assertEqual(
            expected_files - no_drop_files,
            {
                path.name
                for path in (DAEDALON_DATA / "loot_tables/blocks").glob("*.json")
            },
        )

    def test_exact_86_mesh_manifests_and_hashes(self) -> None:
        self.assertEqual(86, len(MESH_EXPECTATIONS))
        self.assertEqual(
            {expectation["obj"] for expectation in MESH_EXPECTATIONS.values()},
            {path.name for path in MESH_ROOT.glob("*.obj")},
        )
        self.assertEqual(
            {
                expectation["mtl"]
                for expectation in MESH_EXPECTATIONS.values()
                if expectation["mtl"] is not None
            },
            {path.name for path in MESH_ROOT.glob("*.mtl")},
        )
        self.assertEqual(
            {
                expectation["definition"]
                for expectation in MESH_EXPECTATIONS.values()
            },
            {path.name for path in MESH_ROOT.glob("*.json")},
        )
        display_root = DAEDALON_ASSETS / "models/block/mesh"
        self.assertEqual(
            {
                expectation["display"]
                for expectation in MESH_EXPECTATIONS.values()
            },
            {path.name for path in display_root.glob("*_display.json")},
        )

        for family, expected in MESH_EXPECTATIONS.items():
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
                self.assertEqual(expected[key], actual[key], (family, key))
            self.assertEqual(expected["obj_sha256"], sha256(obj_path), family)
            if expected["mtl"] is not None:
                self.assertEqual(
                    expected["mtl_sha256"],
                    sha256(MESH_ROOT / expected["mtl"]),
                    family,
                )

            definition = load_json(MESH_ROOT / expected["definition"])
            self.assertEqual(
                f"daedalon:models/mesh/{expected['obj']}", definition["obj"]
            )
            if expected["mtl"] is None:
                self.assertNotIn("mtl", definition)
            else:
                self.assertEqual(
                    f"daedalon:models/mesh/{expected['mtl']}", definition["mtl"]
                )
            encoded = str(definition)
            self.assertNotIn("erydon:", encoded)
            self.assertNotIn("themelios:", encoded)

    def test_urn_and_plinth_states_items_loot_languages_and_tags(self) -> None:
        languages = {
            name: load_json(DAEDALON_ASSETS / f"lang/{name}.json")
            for name in ("en_us", "de_de", "es_es")
        }
        expected_urn_states = {
            f"size={size},offset={offset},facing={facing}"
            for size in ("small", "medium", "large")
            for offset in ("false", "true")
            for facing in ("north", "east", "south", "west")
        }
        expected_plinth_states = {
            f"size={size},offset={offset},facing={facing}"
            for size in ("small", "medium", "large")
            for offset in ("false", "true")
            for facing in ("north", "east", "south", "west")
        }

        for family in self.urn_families:
            suffix = GENERATOR.URN_FAMILIES[family][0]
            tag_name = f"{family}_urn"
            family_ids = GENERATOR.family_block_ids(family)
            expected_refs = {f"daedalon:{block_id}" for block_id in family_ids}
            for kind in ("blocks", "items"):
                self.assertEqual(
                    expected_refs,
                    set(
                        load_json(
                            DAEDALON_DATA / f"tags/{kind}/{tag_name}.json"
                        )["values"]
                    ),
                )
            for block_id in family_ids:
                blockstate = load_json(
                    DAEDALON_ASSETS / f"blockstates/{block_id}.json"
                )
                self.assertEqual(
                    expected_urn_states, set(blockstate["variants"]), block_id
                )
                self.assertEqual(
                    {f"daedalon:mesh/{block_id}"},
                    {
                        variant["model"]
                        for variant in blockstate["variants"].values()
                    },
                )
                self.assertEqual(
                    f"daedalon:block/mesh/{suffix}_display",
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
                        language.get(f"block.daedalon.{block_id}", "").strip()
                        for language in languages.values()
                    )
                )

        expected_all_urn_refs = {
            f"daedalon:{block_id}" for block_id in self.urn_ids
        }

        for family in self.plinth_families:
            for block_id in GENERATOR.family_block_ids(family):
                blockstate = load_json(
                    DAEDALON_ASSETS / f"blockstates/{block_id}.json"
                )
                self.assertEqual(
                    expected_plinth_states, set(blockstate["variants"]), block_id
                )
        for kind in ("blocks", "items"):
            self.assertEqual(
                expected_all_urn_refs,
                set(
                    load_json(DAEDALON_DATA / f"tags/{kind}/urn.json")[
                        "values"
                    ]
                ),
            )
            for synonym in ("vase", "pottery", "vessel"):
                self.assertEqual(
                    ["#daedalon:urn"],
                    load_json(
                        DAEDALON_DATA / f"tags/{kind}/{synonym}.json"
                    )["values"],
                )

        expected_plinth_refs = {f"daedalon:{block_id}" for block_id in self.plinth_ids}
        for kind in ("blocks", "items"):
            tag_root = DAEDALON_DATA / f"tags/{kind}"
            self.assertEqual(
                expected_plinth_refs,
                set(load_json(tag_root / "plinth.json")["values"]),
            )
            self.assertFalse((tag_root / "georgian_plinth.json").exists())
            for family in self.plinth_families:
                family_ids = GENERATOR.family_block_ids(family)
                self.assertEqual(
                    {f"daedalon:{block_id}" for block_id in family_ids},
                    set(load_json(tag_root / f"{family}.json")["values"]),
                )
        for family in self.plinth_families:
            for block_id in GENERATOR.family_block_ids(family):
                variants = load_json(
                    DAEDALON_ASSETS / f"blockstates/{block_id}.json"
                )["variants"]
                self.assertEqual(expected_plinth_states, set(variants))
                self.assertEqual(
                    {f"daedalon:mesh/{block_id}"},
                    {value["model"] for value in variants.values()},
                )
                self.assertEqual(
                    "daedalon:block/mesh/plinth_display",
                    load_json(
                        DAEDALON_ASSETS / f"models/item/{block_id}.json"
                    )["parent"],
                )

    def test_native_16px_material_and_urn_texture_contract(self) -> None:
        texture_root = DAEDALON_ASSETS / "textures/block"
        root_material_names = expected_material_texture_names(suffix="_block")
        statue_names = expected_material_texture_names(
            "statue_spartan_promachos_"
        )
        handle_names = {
            "urn_diota_bronze_handles.png",
            "urn_diota_bronze_handles_s.png",
        }
        bronze_names = {"bronze.png", "bronze_s.png"}
        self.assertEqual(
            root_material_names | statue_names | handle_names | bronze_names,
            {path.name for path in texture_root.glob("*.png")},
        )
        urn_surface_root = texture_root / "urn_surface"
        self.assertEqual(
            root_material_names,
            {path.name for path in urn_surface_root.glob("*.png")},
        )

        for filename in root_material_names:
            self.assertEqual((16, 16), png_dimensions(texture_root / filename))
            self.assertEqual(
                (64, 16), png_dimensions(urn_surface_root / filename)
            )
        for filename in statue_names:
            self.assertEqual((192, 96), png_dimensions(texture_root / filename))
        for filename in handle_names:
            self.assertEqual((16, 16), png_dimensions(texture_root / filename))
        for filename in bronze_names:
            self.assertEqual((16, 16), png_dimensions(texture_root / filename))

    def test_bronze_finish_is_selected_non_aged_metal_with_locked_pbr_pixels(self) -> None:
        bronze_ids = {
            GENERATOR.bronze_block_id(family)
            for family in GENERATOR.BRONZE_FAMILIES
        }
        self.assertEqual(50, len(bronze_ids))
        self.assertTrue(bronze_ids <= set(all_block_ids()))
        self.assertFalse(any("aged" in block_id for block_id in bronze_ids))
        self.assertEqual(
            {
                "bronze_spartan_promachos_statue",
                "bronze_zeus_statue",
                "bronze_aphrodite_bust",
                "bronze_amphora_urn",
                "bronze_obeliskos_monument",
            },
            {
                block_id
                for block_id in bronze_ids
                if block_id in {
                    "bronze_spartan_promachos_statue",
                    "bronze_zeus_statue",
                    "bronze_aphrodite_bust",
                    "bronze_amphora_urn",
                    "bronze_obeliskos_monument",
                }
            },
        )

        texture_root = DAEDALON_ASSETS / "textures/block"
        self.assertEqual(
            "25dd97c806087f63358edbdff2e4eb890d4d3f28d7dc9e96838bed3e141da4b7",
            sha256(texture_root / "bronze.png"),
        )
        self.assertEqual(
            "097f017c6ebe2dfc2ec986b6fd9d3ed0731390f1e0f1ea1a0fd7183832ffe86f",
            sha256(texture_root / "bronze_s.png"),
        )
        self.assertFalse((texture_root / "bronze_aged.png").exists())

        for kind in ("blocks", "items"):
            tag_root = DAEDALON_DATA / f"tags/{kind}"
            bronze_refs = {f"daedalon:{block_id}" for block_id in bronze_ids}
            self.assertEqual(
                bronze_refs,
                set(load_json(tag_root / "bronze.json")["values"]),
            )
            self.assertEqual(
                ["#daedalon:bronze"],
                load_json(tag_root / "metal.json")["values"],
            )
            self.assertEqual(
                bronze_refs,
                set(
                    load_json(
                        DAEDALON_DATA / f"../material/tags/{kind}/metal.json"
                    )["values"]
                ),
            )
            stone_refs = set(
                load_json(
                    DAEDALON_DATA / f"../material/tags/{kind}/stone.json"
                )["values"]
            )
            self.assertTrue(bronze_refs.isdisjoint(stone_refs))

    def test_diota_bronze_handles_remain_isolated_and_yellow_specular(self) -> None:
        actual = parse_obj(MESH_ROOT / "urn_diota.obj")
        self.assertEqual(
            Counter({"none": 1671, "bronze_handles": 320}),
            actual["material_faces"],
        )
        mtl = (MESH_ROOT / "urn_diota.mtl").read_text(encoding="utf-8")
        self.assertIn("newmtl bronze_handles", mtl)
        self.assertIn(
            "map_Kd ../../textures/block/urn_diota_bronze_handles.png", mtl
        )
        texture_root = DAEDALON_ASSETS / "textures/block"
        self.assertEqual(
            "4914ed6e10797a09b4504400bc90832d51e9ec5caa6eb99ec1d17c866b8ec1e0",
            sha256(texture_root / "urn_diota_bronze_handles.png"),
        )
        self.assertEqual(
            "097f017c6ebe2dfc2ec986b6fd9d3ed0731390f1e0f1ea1a0fd7183832ffe86f",
            sha256(texture_root / "urn_diota_bronze_handles_s.png"),
        )

    def test_renderer_registration_and_three_height_plinth_contract(self) -> None:
        plugin = (
            JAVA_ROOT / "client/model/obj/ObjMeshModelLoadingPlugin.java"
        ).read_text(encoding="utf-8")
        baked = (
            JAVA_ROOT / "client/model/obj/ObjMeshBakedModel.java"
        ).read_text(encoding="utf-8")
        block = (JAVA_ROOT / "block/UrnBlock.java").read_text(encoding="utf-8")
        registrations = (JAVA_ROOT / "registry/ModBlocks.java").read_text(
            encoding="utf-8"
        )

        self.assertIn(
            "for (UrnBlock.UrnStyle style : UrnBlock.UrnStyle.values())",
            plugin,
        )
        self.assertIn("families.add(createUrnFamily(style));", plugin)
        self.assertIn("style.displayName() + \" Urn\"", plugin)
        self.assertIn("style.resourceStem() + \".json\"", plugin)
        self.assertIn("material + \"_\" + shapeId + \"_urn\"", plugin)
        self.assertIn("material + \"_aged_\" + shapeId + \"_urn\"", plugin)
        self.assertIn("PlinthBlock.Style.values()", plugin)
        self.assertIn('style.displayName() + " Plinth"', plugin)
        self.assertIn('id("block/mesh/plinth_display")', plugin)
        self.assertIn('createDetailedVariants(style.styleId() + "_plinth")', plugin)
        self.assertIn('id("block/" + detailedTextureBlockId(textureBlockId))', plugin)
        self.assertIn("WorldTexturePhase.urnSurface()", plugin)
        self.assertIn("definition.materialOverrides().get(materialName)", plugin)
        self.assertIn("materialTextures.get(materialName)", plugin)
        self.assertIn("UrnTransform.forState(state)", baked)
        self.assertIn(
            "quad.spriteBake(sprite, MutableQuadView.BAKE_NORMALIZED)", baked
        )
        self.assertIn('BooleanProperty.of("offset")', block)
        self.assertIn('EnumProperty.of("size", UrnSize.class)', block)
        self.assertIn("builder.add(SIZE, OFFSET, FACING)", block)
        self.assertIn(".with(OFFSET, false)", block)
        self.assertIn(".with(SIZE, UrnSize.MEDIUM)", block)

        plinth_block = (JAVA_ROOT / "block/PlinthBlock.java").read_text(encoding="utf-8")
        self.assertIn("PlinthBlock.Style.values()", registrations)
        self.assertIn("new PlinthBlock(decorSettings(), style, fountainMaterialKey)", registrations)
        self.assertIn('SMALL("small", 0.25F, 0.5F)', plinth_block)
        self.assertIn('MEDIUM("medium", 0.5F, 1.0F)', plinth_block)
        self.assertIn('LARGE("large", 1.0F, 2.0F)', plinth_block)
        self.assertIn("builder.add(SIZE, OFFSET, FACING)", plinth_block)
        self.assertIn("PlinthTransform.forState(state, 0.0F)", baked)
        self.assertIn("quad.uv(vertex,", baked)
        plinth_transform = baked[
            baked.index("private record PlinthTransform") :
            baked.index("private record GroundScaleTransform")
        ]
        self.assertIn("0.5F + (quad.u(vertex) - 0.5F) * scale", plinth_transform)
        self.assertIn("0.5F + (quad.v(vertex) - 0.5F) * scale", plinth_transform)
        ground_transform = baked[
            baked.index("private record GroundScaleTransform") :
            baked.index("private static final class SmoothNormals")
        ]
        self.assertIn("0.5F + (quad.u(vertex) - 0.5F) * scale", ground_transform)
        self.assertIn("0.5F + (quad.v(vertex) - 0.5F) * scale", ground_transform)
        mesh_root = DAEDALON_ASSETS / "models/mesh"
        scalable_stems = [
            GENERATOR.FIXED_DECOR_FAMILIES[family][0]
            for family in GENERATOR.FINIAL_FAMILIES
        ] + [
            GENERATOR.PLINTH_FAMILIES[family][0]
            for family in GENERATOR.PLINTH_FAMILIES
        ]
        for stem in scalable_stems:
            definition = load_json(mesh_root / f"{stem}.json")
            self.assertGreaterEqual(min(definition["scale"]), 2.0, stem)
        self.assertEqual(5, plinth_block.count('"plinth_'))
        self.assertNotIn("GeorgianPlinth", registrations)
        self.assertIn("EXPECTED_BLOCK_COUNT", registrations)
        self.assertIn("exactly 4046 decor blocks and items", registrations)

    def test_urn_source_and_decimation_evidence_is_locked(self) -> None:
        evidence = load_json(REPO_ROOT / "docs/evidence/urn-batch-source.json")
        self.assertEqual(["diota"], evidence["retained_existing_models"])
        preparation = evidence["preparation"]
        self.assertEqual("tools/prepare_urn_mesh.py", preparation["tool"])
        self.assertEqual("5.2.0 LTS", preparation["blender_version"])
        self.assertEqual("fbe6228777e7", preparation["blender_build_hash"])
        self.assertEqual(3200, preparation["target_faces"])
        self.assertFalse(preparation["source_files_modified"])

        models = {model["shape"]: model for model in evidence["models"]}
        self.assertEqual(set(self.urn_families) - {"diota"}, set(models))
        for shape, model in models.items():
            expected = MESH_EXPECTATIONS[shape]
            self.assertRegex(model["source_sha256"], r"^[0-9a-f]{64}$")
            self.assertEqual(expected["vertices"], model["runtime_vertices"])
            self.assertEqual(expected["obj_sha256"], model["runtime_obj_sha256"])
            self.assertEqual(3200, expected["faces"])

    def test_plinth_source_identification_and_decimation_evidence_is_locked(self) -> None:
        evidence = load_json(REPO_ROOT / "docs/evidence/plinth-batch-source.json")
        self.assertEqual(5, evidence["source_file_count"])
        self.assertFalse(evidence["source_files_modified"])
        self.assertEqual("fbe6228777e7", evidence["preparation"]["blender_build_hash"])
        self.assertEqual(6000, evidence["preparation"]["target_faces"])
        self.assertFalse(evidence["preparation"]["authored_uv_search"])
        self.assertFalse(evidence["retired_unpublished_family"]["aliases_required"])
        models = {model["style"]: model for model in evidence["models"]}
        self.assertEqual(
            {family.removesuffix("_plinth") for family in self.plinth_families},
            set(models),
        )
        for family in self.plinth_families:
            style = family.removesuffix("_plinth")
            stem = GENERATOR.PLINTH_FAMILIES[family][0]
            expected = MESH_EXPECTATIONS[stem]
            model = models[style]
            self.assertEqual(expected["vertices"], model["runtime_vertices"])
            self.assertEqual(expected["faces"], model["runtime_faces"])
            self.assertEqual(expected["obj_sha256"], model["runtime_obj_sha256"])

    def test_restricted_license_and_model_attribution_are_packaged(self) -> None:
        fabric_mod = load_json(RESOURCES / "fabric.mod.json")
        self.assertEqual("LicenseRef-Oliver-Restricted-1.0", fabric_mod["license"])
        license_text = (REPO_ROOT / "LICENSE").read_text(encoding="utf-8")
        self.assertIn("may not:", license_text)
        self.assertIn("sell, rent, sublicense, or charge", license_text)
        self.assertIn("modify, adapt, translate", license_text)
        self.assertIn("<Product name> by Oliver", license_text)

        notices = (
            RESOURCES / "META-INF/THIRD_PARTY_NOTICES.md"
        ).read_text(encoding="utf-8")
        self.assertIn('"Neoclassical Urn on Pedestal | Lowpoly Asset"', notices)
        self.assertIn("tina.hill", notices)
        self.assertIn("https://skfb.ly/pGAGK", notices)
        self.assertIn(
            "https://creativecommons.org/licenses/by/4.0/", notices
        )


if __name__ == "__main__":
    unittest.main()
