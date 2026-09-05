from __future__ import annotations

import unittest

from daedalon_test_support import (
    DAEDALON_ASSETS,
    DAEDALON_DATA,
    GENERATOR,
    JAVA_ROOT,
    MESH_EXPECTATIONS,
    MESH_ROOT,
    REPO_ROOT,
    load_json,
    parse_obj,
    sha256,
)


class ArchitecturalDecorSafetyTests(unittest.TestCase):
    def test_source_evidence_and_missing_doric_are_explicit(self) -> None:
        evidence = load_json(REPO_ROOT / "docs/evidence/architectural-decor-batch-source.json")
        models = {model["name"]: model for model in evidence["models"]}
        expected_names = {
            *[values[0] for values in GENERATOR.CORBEL_FAMILIES.values()],
            *[values[0] for values in GENERATOR.CAPITAL_FAMILIES.values()],
        }
        self.assertEqual(expected_names, set(models))
        self.assertEqual(len(expected_names), evidence["source_file_count"])
        self.assertFalse(evidence["source_files_modified"])
        self.assertIn("distribution_rights_status", evidence)
        self.assertIn("exactly one block (16 units) wide", evidence["placement"]["corbels"])

        for name, model in models.items():
            self.assertEqual(64, len(model["source_sha256"]))
            self.assertEqual(MESH_EXPECTATIONS[name]["obj_sha256"], model["runtime_obj_sha256"])
            self.assertEqual(MESH_EXPECTATIONS[name]["faces"], model["runtime_faces"])

        self.assertEqual("Capitals/capital_greek_doric.png", evidence["missing_source"]["reference_image"])
        self.assertNotIn("greek_doric", GENERATOR.CAPITAL_FAMILIES)

    def test_canonical_ids_states_and_search_assets(self) -> None:
        corbel_states = {
            f"size={size},facing={facing}"
            for size in ("small", "medium", "large")
            for facing in ("north", "east", "south", "west")
        }
        for family in GENERATOR.CORBEL_FAMILIES:
            ids = GENERATOR.family_block_ids(family)
            self.assertEqual(54, len(ids))
            self.assertEqual(f"aganite_{family}_corbel", ids[0])
            self.assertEqual(f"aganite_aged_{family}_corbel", ids[1])
            for block_id in ids:
                state = load_json(DAEDALON_ASSETS / f"blockstates/{block_id}.json")
                self.assertEqual(corbel_states, set(state["variants"]))

        for family in GENERATOR.CAPITAL_FAMILIES:
            style = "corinthian" if family == "corinthian_capital" else family
            ids = GENERATOR.family_block_ids(family)
            self.assertEqual(54, len(ids))
            self.assertEqual(f"aganite_{style}_capital", ids[0])
            self.assertEqual(f"aganite_aged_{style}_capital", ids[1])
            for block_id in ids:
                state = load_json(DAEDALON_ASSETS / f"blockstates/{block_id}.json")
                self.assertEqual({""}, set(state["variants"]))

        for language in ("en_us", "de_de", "es_es"):
            entries = load_json(DAEDALON_ASSETS / f"lang/{language}.json")
            for family in (*GENERATOR.CORBEL_FAMILIES, *GENERATOR.CAPITAL_FAMILIES):
                for block_id in GENERATOR.family_block_ids(family):
                    self.assertIn(f"block.daedalon.{block_id}", entries)

        for kind in ("blocks", "items"):
            root = DAEDALON_DATA / f"tags/{kind}"
            for tag in (
                "corbel", "capital", "bracket", "column_capital",
                "baroque_corbel", "greek_ionic_capital", "roman_composite_capital",
            ):
                self.assertTrue((root / f"{tag}.json").is_file(), (kind, tag))

    def test_runtime_meshes_and_definitions_are_locked(self) -> None:
        for stem in (
            *[values[0] for values in GENERATOR.CORBEL_FAMILIES.values()],
            *[values[0] for values in GENERATOR.CAPITAL_FAMILIES.values()],
        ):
            expected = MESH_EXPECTATIONS[stem]
            actual = parse_obj(MESH_ROOT / expected["obj"])
            for key in (
                "vertices", "uvs", "normals", "faces", "face_sizes",
                "face_index_styles", "bounds",
            ):
                self.assertEqual(expected[key], actual[key], (stem, key))
            self.assertEqual(expected["obj_sha256"], sha256(MESH_ROOT / expected["obj"]))
            self.assertEqual(expected["mtl_sha256"], sha256(MESH_ROOT / expected["mtl"]))
            definition = load_json(MESH_ROOT / expected["definition"])
            self.assertTrue(definition["fit_to_block"])
            self.assertTrue(definition["force_uv_projection"])
            self.assertEqual("axis_stabilized_box", definition["uv_projection"])
            self.assertEqual("daedalon:block/aganite_block", definition["materials"]["none"])

    def test_fixed_top_corbel_and_shared_capital_model_contracts(self) -> None:
        corbel = (JAVA_ROOT / "block/CorbelBlock.java").read_text(encoding="utf-8")
        capital = (JAVA_ROOT / "block/CapitalBlock.java").read_text(encoding="utf-8")
        baked = (JAVA_ROOT / "client/model/obj/ObjMeshBakedModel.java").read_text(encoding="utf-8")
        registrations = (JAVA_ROOT / "registry/ModBlocks.java").read_text(encoding="utf-8")

        self.assertIn('EnumProperty.of("size", CorbelSize.class)', corbel)
        self.assertIn("builder.add(SIZE, FACING)", corbel)
        self.assertIn('SMALL("small", 1, 2.0F / 9.0F)', corbel)
        self.assertIn('MEDIUM("medium", 2, 1.0F / 3.0F)', corbel)
        self.assertIn('LARGE("large", 3, 2.0F / 3.0F)', corbel)
        self.assertIn("LARGE_WIDTH_IN_BLOCKS / fullWidth", corbel)
        self.assertIn("style.scaledWidth(size)", corbel)
        self.assertIn("TOP_ANCHOR_Y - style.scaledHeight(size)", corbel)
        self.assertIn("style.scaledDepth(size)", corbel)
        self.assertIn("style.wallAnchorShift(size)", baked)
        self.assertIn("style.modelScale(size)", baked)
        self.assertIn("CorbelTransform[][][] CACHE", baked)
        self.assertIn(
            "(quad.z(vertex) - 0.5F) * scale + wallAnchorShiftZ",
            baked,
        )
        self.assertIn("rotationStepsFromSouth", corbel)
        self.assertIn("(quad.y(vertex) - CorbelBlock.SOURCE_TOP_Y) * scale", baked)
        self.assertIn("scaleUvFromCenter(quad.u(vertex), scale)", baked)
        self.assertIn("CorbelTransform.forState(state)", baked)

        self.assertNotIn("DirectionProperty", capital)
        self.assertIn('"capital_orientation", CapitalOrientation.class', capital)
        self.assertIn("new CorbelBlock(decorSettings(), style)", registrations)
        self.assertIn("CapitalBlock.create(decorSettings(), style)", registrations)
        self.assertNotIn("greek_doric", GENERATOR.CAPITAL_FAMILIES)

    def test_refined_capitals_and_high_resolution_corbel_surface_are_locked(self) -> None:
        evidence = load_json(REPO_ROOT / "docs/evidence/architectural-decor-batch-source.json")
        models = {model["name"]: model for model in evidence["models"]}
        self.assertGreaterEqual(MESH_EXPECTATIONS["capital_byzantine"]["faces"], 17000)
        self.assertGreaterEqual(MESH_EXPECTATIONS["capital_corinthian"]["faces"], 12000)
        self.assertGreaterEqual(MESH_EXPECTATIONS["capital_roman_composite"]["faces"], 11000)
        for name in ("capital_byzantine", "capital_corinthian", "capital_roman_composite"):
            correction = models[name]["top_correction"]
            self.assertEqual(0.0, correction["post_flatten_range"])
            self.assertGreaterEqual(correction["flattened_vertices"], 4)

        plugin = (
            JAVA_ROOT / "client/model/obj/ObjMeshModelLoadingPlugin.java"
        ).read_text(encoding="utf-8")
        self.assertIn("createCorbelVariants(style.styleId())", plugin)
        self.assertIn('SPARTAN_BLOCK_PREFIX + material + "_aged"', plugin)


if __name__ == "__main__":
    unittest.main()
