from __future__ import annotations

import unittest

from daedalon_test_support import (
    AUTHORED_UV_STATUE_FAMILIES,
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


class ClassicalStatueBatchSafetyTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.families = tuple(
            family
            for family in GENERATOR.CANONICAL_STATUE_FAMILIES
            if family != "zeus"
        )
        cls.block_ids = [
            block_id
            for family in cls.families
            for block_id in GENERATOR.family_block_ids(family)
        ]

    def test_exact_material_first_batch_ids(self) -> None:
        self.assertEqual(18, len(self.families))
        self.assertEqual(990, len(self.block_ids))
        self.assertEqual(990, len(set(self.block_ids)))
        self.assertEqual(4317, len(all_block_ids()))
        self.assertEqual(4317, len(set(all_block_ids())))
        for family in self.families:
            expected: list[str] = []
            for material in GENERATOR.MATERIALS:
                expected.extend(
                    (
                        f"{material}_{family}_statue",
                        f"{material}_aged_{family}_statue",
                    )
                )
            expected.append(f"bronze_{family}_statue")
            self.assertEqual(expected, GENERATOR.family_block_ids(family))

    def test_meshes_and_uv_definitions_are_locked(self) -> None:
        for family in self.families:
            expected = MESH_EXPECTATIONS[family]
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
                self.assertEqual(expected[key], actual[key], (family, key))
            self.assertEqual(expected["obj_sha256"], sha256(obj_path), family)

            definition = load_json(MESH_ROOT / expected["definition"])
            if family in AUTHORED_UV_STATUE_FAMILIES:
                self.assertFalse(definition["repair_degenerate_uvs"], family)
                self.assertFalse(definition["force_uv_projection"], family)
            else:
                self.assertTrue(definition["repair_degenerate_uvs"], family)
                self.assertTrue(definition["force_uv_projection"], family)
            self.assertEqual("axis_stabilized_box", definition["uv_projection"])
            self.assertNotIn("cylindrical_u_repeats", definition)
            self.assertNotIn("texture_u_tiles", definition)
            self.assertTrue(definition["face_oriented_uvs"], family)
            self.assertTrue(definition["smooth_normals"], family)
            self.assertEqual([3.0, 3.0, 3.0], definition["scale"], family)

        theseus = MESH_EXPECTATIONS["theseus"]
        self.assertEqual("theseus.mtl", theseus["mtl"])
        self.assertEqual(
            theseus["mtl_sha256"],
            sha256(MESH_ROOT / "theseus.mtl"),
        )
        self.assertIn(
            "mtllib theseus.mtl",
            (MESH_ROOT / "theseus.obj").read_text(encoding="utf-8"),
        )

    def test_states_items_loot_languages_and_search_tags_are_complete(self) -> None:
        expected_states = {
            f"size={size},facing={facing}"
            for size in ("small", "medium", "large")
            for facing in ("north", "east", "south", "west")
        }
        languages = {
            name: load_json(DAEDALON_ASSETS / f"lang/{name}.json")
            for name in ("en_us", "de_de", "es_es")
        }
        tag_root = DAEDALON_DATA / "tags/blocks"
        for family in self.families:
            family_ids = GENERATOR.family_block_ids(family)
            expected_refs = {f"daedalon:{block_id}" for block_id in family_ids}
            self.assertEqual(
                expected_refs,
                set(load_json(tag_root / f"{family}_statue.json")["values"]),
            )
            subject_values = load_json(tag_root / f"{family}.json")["values"]
            self.assertIn(f"#daedalon:{family}_statue", subject_values)
            if f"bust_{family}" in GENERATOR.BUST_FAMILIES:
                self.assertIn(f"#daedalon:{family}_bust", subject_values)
            for block_id in family_ids:
                blockstate = load_json(
                    DAEDALON_ASSETS / f"blockstates/{block_id}.json"
                )
                self.assertEqual(expected_states, set(blockstate["variants"]), block_id)
                self.assertEqual(
                    {f"daedalon:mesh/{block_id}"},
                    {value["model"] for value in blockstate["variants"].values()},
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

        expected_olympians = {
            f"#daedalon:{family}_statue"
            for family in GENERATOR.OLYMPIAN_STATUE_FAMILIES
        } | {
            f"#daedalon:{values[0]}_bust"
            for values in GENERATOR.BUST_FAMILIES.values()
        }
        self.assertEqual(
            expected_olympians,
            set(load_json(tag_root / "olympian.json")["values"]),
        )
        self.assertEqual(
            {f"#daedalon:{family}_statue" for family in GENERATOR.HERO_STATUE_FAMILIES},
            set(load_json(tag_root / "hero.json")["values"]),
        )
        expected_lions = {
            f"#daedalon:{family}_statue"
            for family in GENERATOR.LION_STATUE_FAMILIES
        }
        self.assertEqual(
            expected_lions,
            set(load_json(tag_root / "lion.json")["values"]),
        )
        self.assertEqual(
            expected_lions,
            set(load_json(tag_root / "animal.json")["values"]),
        )

    def test_shared_registration_profiles_textures_and_caches(self) -> None:
        block = (JAVA_ROOT / "block/ClassicalStatueBlock.java").read_text(
            encoding="utf-8"
        )
        registrations = (JAVA_ROOT / "registry/ModBlocks.java").read_text(
            encoding="utf-8"
        )
        plugin = (
            JAVA_ROOT / "client/model/obj/ObjMeshModelLoadingPlugin.java"
        ).read_text(encoding="utf-8")
        baked = (
            JAVA_ROOT / "client/model/obj/ObjMeshBakedModel.java"
        ).read_text(encoding="utf-8")

        self.assertEqual(18, block.count("F, shape("))
        self.assertIn("Direction.NORTH", block)
        self.assertIn("return createProfile(", block)
        self.assertIn("new ClassicalStatueBlock(decorSettings(), style)", registrations)
        self.assertIn("ClassicalStatueBlock.Style.values()", registrations)
        self.assertIn("exactly 4317 decor blocks and items", registrations)
        self.assertIn("ClassicalStatueBlock.Style.values()", plugin)
        self.assertIn('id("block/mesh/zeus_statue_display")', plugin)
        self.assertIn("createClassicalStatueVariants(style.subjectId())", plugin)
        self.assertIn("Map<StatueBlock.Profile, StatueTransform[][][]>", baked)
        self.assertIn("state.get(StatueBlock.OFFSET) ? 1 : 0", baked)
        self.assertIn("scaleUvFromCenter(quad.u(vertex), scale)", baked)

        texture_root = DAEDALON_ASSETS / "textures/block"
        for family in self.families:
            self.assertFalse(any(texture_root.glob(f"*{family}*.png")), family)

    def test_source_archive_and_runtime_repair_are_recorded(self) -> None:
        evidence = load_json(
            REPO_ROOT / "docs/evidence/classical-statue-batch-source.json"
        )
        self.assertEqual(19, evidence["source_obj_count"])
        self.assertEqual(18, evidence["batch_obj_count"])
        self.assertEqual(
            "98c658285fcc1ae3a17f23422c979ec70728aa9da176dbe4aea976f4b21d3fb2",
            evidence["source_archive_sha256"],
        )
        self.assertEqual(0, evidence["mesh_quality"]["degenerate_triangle_count"])
        repairs = {
            repair["subject"]: repair for repair in evidence["runtime_repairs"]
        }
        for family in AUTHORED_UV_STATUE_FAMILIES:
            subject = family.replace("_", " ").title()
            self.assertEqual(
                MESH_EXPECTATIONS[family]["obj_sha256"],
                repairs[subject]["runtime_obj_sha256"],
                family,
            )
        self.assertIn(
            "confirms authorship and authorizes public redistribution",
            evidence["distribution_rights_status"],
        )
        self.assertIn("2026-08-31", evidence["distribution_rights_status"])


if __name__ == "__main__":
    unittest.main()
