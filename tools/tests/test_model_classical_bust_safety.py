from __future__ import annotations

import unittest

from daedalon_test_support import (
    BUST_MESHES,
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


class ClassicalBustSafetyTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.families = tuple(GENERATOR.BUST_FAMILIES)
        cls.ids = [
            block_id
            for family in cls.families
            for block_id in GENERATOR.family_block_ids(family)
        ]

    def test_canonical_ids_states_and_search_contract(self) -> None:
        self.assertEqual(12, len(self.families))
        self.assertEqual(660, len(self.ids))
        self.assertEqual(660, len(set(self.ids)))
        expected_states = {
            f"size={size},facing={facing}"
            for size in ("small", "medium", "large")
            for facing in ("north", "east", "south", "west")
        }
        languages = {
            language: load_json(DAEDALON_ASSETS / f"lang/{language}.json")
            for language in ("en_us", "de_de", "es_es")
        }
        for family in self.families:
            subject = GENERATOR.BUST_FAMILIES[family][0]
            ids = GENERATOR.family_block_ids(family)
            self.assertEqual(f"aganite_{subject}_bust", ids[0])
            self.assertEqual(f"aganite_aged_{subject}_bust", ids[1])
            self.assertEqual(f"bronze_{subject}_bust", ids[-1])
            expected_refs = {f"daedalon:{block_id}" for block_id in ids}
            for kind in ("blocks", "items"):
                tags = DAEDALON_DATA / f"tags/{kind}"
                self.assertEqual(
                    expected_refs,
                    set(load_json(tags / f"{subject}_bust.json")["values"]),
                )
                self.assertIn(
                    f"#daedalon:{subject}_bust",
                    load_json(tags / f"{subject}.json")["values"],
                )
            for block_id in ids:
                state = load_json(DAEDALON_ASSETS / f"blockstates/{block_id}.json")
                self.assertEqual(expected_states, set(state["variants"]), block_id)
                self.assertEqual(
                    "daedalon:block/mesh/bust_display",
                    load_json(DAEDALON_ASSETS / f"models/item/{block_id}.json")["parent"],
                )
                self.assertEqual(
                    f"daedalon:{block_id}",
                    load_json(
                        DAEDALON_DATA / f"loot_tables/blocks/{block_id}.json"
                    )["pools"][0]["entries"][0]["name"],
                )
                self.assertTrue(
                    all(
                        f"block.daedalon.{block_id}" in values
                        for values in languages.values()
                    )
                )

        for kind in ("blocks", "items"):
            tags = DAEDALON_DATA / f"tags/{kind}"
            for search in (
                "bust", "classical_bust", "portrait", "head", "sculpture",
                "greek", "olympian", "ornament",
            ):
                self.assertTrue((tags / f"{search}.json").is_file(), (kind, search))

    def test_meshes_definitions_and_source_evidence_are_locked(self) -> None:
        evidence = load_json(REPO_ROOT / "docs/evidence/classical-bust-batch-source.json")
        self.assertEqual(12, evidence["source_file_count"])
        self.assertFalse(evidence["source_files_modified"])
        self.assertEqual(6000, evidence["preparation"]["target_faces"])
        self.assertEqual(0, evidence["preparation"]["authored_uv_attempts"])
        models = {model["subject"]: model for model in evidence["models"]}
        self.assertEqual(set(BUST_MESHES), set(models))
        for subject in BUST_MESHES:
            expected = MESH_EXPECTATIONS[f"bust_{subject}"]
            actual = parse_obj(MESH_ROOT / expected["obj"])
            for key in (
                "vertices", "uvs", "normals", "faces", "face_sizes",
                "face_index_styles", "bounds",
            ):
                self.assertEqual(expected[key], actual[key], (subject, key))
            self.assertEqual(expected["obj_sha256"], sha256(MESH_ROOT / expected["obj"]))
            self.assertEqual(expected["mtl_sha256"], sha256(MESH_ROOT / expected["mtl"]))
            definition = load_json(MESH_ROOT / expected["definition"])
            self.assertTrue(definition["fit_to_block"])
            self.assertEqual([3.0, 3.0, 3.0], definition["scale"])
            self.assertTrue(definition["force_uv_projection"])
            self.assertEqual("axis_stabilized_box", definition["uv_projection"])
            self.assertEqual(
                "daedalon:block/statue_spartan_promachos_aganite",
                definition["materials"]["none"],
            )
            self.assertRegex(models[subject]["source_sha256"], r"^[0-9a-f]{64}$")
            self.assertEqual(expected["obj_sha256"], models[subject]["runtime_obj_sha256"])

    def test_shared_offset_orientation_and_three_size_renderer_profiles(self) -> None:
        block = (JAVA_ROOT / "block/BustBlock.java").read_text(encoding="utf-8")
        statue = (JAVA_ROOT / "block/StatueBlock.java").read_text(encoding="utf-8")
        baked = (
            JAVA_ROOT / "client/model/obj/ObjMeshBakedModel.java"
        ).read_text(encoding="utf-8")
        plugin = (
            JAVA_ROOT / "client/model/obj/ObjMeshModelLoadingPlugin.java"
        ).read_text(encoding="utf-8")
        registrations = (JAVA_ROOT / "registry/ModBlocks.java").read_text(encoding="utf-8")
        self.assertIn("extends StatueBlock", block)
        self.assertEqual(12, block.count('F, 0.'))
        self.assertIn("Direction.NORTH", block)
        self.assertIn("new BustBlock(decorSettings(), style)", registrations)
        self.assertIn("BustBlock.Style.values()", registrations)
        self.assertIn("BustBlock.Style.values()", plugin)
        self.assertIn('id("block/mesh/bust_display")', plugin)
        self.assertIn("createBustVariants(style.subjectId())", plugin)
        self.assertIn('BooleanProperty.of("offset")', statue)
        self.assertIn('EnumProperty.of("size", StatueSize.class)', statue)
        self.assertIn("builder.add(SIZE, OFFSET, FACING)", statue)
        self.assertIn("super(settings, style.profile())", block)
        self.assertIn(".with(SIZE, StatueSize.SMALL)", block)
        self.assertNotIn("lockedSize", statue)
        self.assertIn("effectiveSize(BlockState state)", statue)
        self.assertIn("block.effectiveSize(state)", baked)


if __name__ == "__main__":
    unittest.main()
