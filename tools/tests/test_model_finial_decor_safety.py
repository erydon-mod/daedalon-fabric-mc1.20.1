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
    all_block_ids,
    load_json,
    parse_obj,
    sha256,
)


class FinialDecorSafetyTests(unittest.TestCase):
    FAMILIES = {
        "balanos_finial": "finial_balanos",
        "kynara_finial": "finial_kynara",
        "phlox_finial": "finial_phlox",
        "sphaira_finial": "finial_sphaira",
        "strobilos_finial": "finial_strobilos",
        "louterion_basin": "basin_louterion",
        "pege_fountain": "fountain_pege",
        "krene_fountain": "fountain_krene",
        "obeliskos_monument": "monument_obeliskos",
    }

    def test_canonical_ids_are_complete_and_material_first(self) -> None:
        self.assertTrue(set(self.FAMILIES).issubset(GENERATOR.NEW_DECOR_FAMILIES))
        ids = [
            block_id
            for family in self.FAMILIES
            for block_id in GENERATOR.family_block_ids(family)
        ]
        self.assertEqual(487, len(ids))
        self.assertEqual(487, len(set(ids)))
        self.assertTrue(set(ids).issubset(all_block_ids()))
        self.assertIn("aganite_balanos_finial", ids)
        self.assertIn("aganite_aged_balanos_finial", ids)
        self.assertIn("aganite_obeliskos_monument", ids)
        self.assertIn("bronze_obeliskos_monument", ids)
        self.assertNotIn("balanos_finial_aganite", ids)
        self.assertNotIn("aganite_balanos_finial_aged", ids)

    def test_meshes_and_source_evidence_are_hash_locked(self) -> None:
        evidence = load_json(
            REPO_ROOT / "docs/evidence/finial-decor-batch-source.json"
        )
        self.assertEqual(9, evidence["source_file_count"])
        self.assertFalse(evidence["source_files_modified"])
        self.assertFalse(evidence["preparation"]["authored_uv_search"])
        self.assertEqual("fbe6228777e7", evidence["preparation"]["blender_build_hash"])
        evidence_by_name = {entry["name"]: entry for entry in evidence["models"]}
        self.assertEqual(set(self.FAMILIES.values()), set(evidence_by_name))

        for stem in self.FAMILIES.values():
            expected = MESH_EXPECTATIONS[stem]
            entry = evidence_by_name[stem]
            self.assertEqual(expected["faces"], entry["runtime_faces"], stem)
            self.assertEqual(expected["obj_sha256"], entry["runtime_obj_sha256"], stem)
            obj_path = MESH_ROOT / expected["obj"]
            actual = parse_obj(obj_path)
            self.assertEqual(0, actual["uvs"], stem)
            self.assertEqual({"v//vn"}, actual["face_index_styles"], stem)
            self.assertEqual(expected["obj_sha256"], sha256(obj_path), stem)

            definition = load_json(MESH_ROOT / expected["definition"])
            self.assertTrue(definition["force_uv_projection"], stem)
            self.assertTrue(definition["repair_degenerate_uvs"], stem)
            self.assertEqual(
                "cylindrical_with_radial_caps"
                if stem == "fountain_pege"
                else "cylindrical"
                if stem.startswith("finial_") or stem == "basin_louterion"
                else "axis_stabilized_box",
                definition["uv_projection"],
                stem,
            )

    def test_states_items_loot_languages_and_search_tags_are_complete(self) -> None:
        languages = {
            name: load_json(DAEDALON_ASSETS / f"lang/{name}.json")
            for name in ("en_us", "de_de", "es_es")
        }
        for family in self.FAMILIES:
            family_ids = GENERATOR.family_block_ids(family)
            refs = {f"daedalon:{block_id}" for block_id in family_ids}
            for kind in ("blocks", "items"):
                self.assertEqual(
                    refs,
                    set(load_json(DAEDALON_DATA / f"tags/{kind}/{family}.json")["values"]),
                    (family, kind),
                )
            for block_id in family_ids:
                state = load_json(DAEDALON_ASSETS / f"blockstates/{block_id}.json")
                if family == "krene_fountain":
                    self.assertEqual(
                        {
                            f"facing={facing}"
                            for facing in ("north", "east", "south", "west")
                        },
                        set(state["variants"]),
                    )
                elif family == "obeliskos_monument":
                    self.assertEqual(
                        {"size=small", "size=medium", "size=large"},
                        set(state["variants"]),
                    )
                elif family in GENERATOR.FINIAL_FAMILIES:
                    self.assertEqual({"size=small", "size=large"}, set(state["variants"]))
                else:
                    self.assertEqual({""}, set(state["variants"]))
                item = load_json(DAEDALON_ASSETS / f"models/item/{block_id}.json")
                expected_display = (
                    "monument" if family == "obeliskos_monument"
                    else "krene" if family == "krene_fountain"
                    else "finial" if family in GENERATOR.FINIAL_FAMILIES
                    else "decor"
                )
                self.assertEqual(
                    f"daedalon:block/mesh/{expected_display}_display", item["parent"]
                )
                loot = load_json(DAEDALON_DATA / f"loot_tables/blocks/{block_id}.json")
                self.assertEqual(
                    f"daedalon:{block_id}",
                    loot["pools"][0]["entries"][0]["name"],
                )
                for language in languages.values():
                    self.assertIn(f"block.daedalon.{block_id}", language)

        for kind in ("blocks", "items"):
            tag_root = DAEDALON_DATA / f"tags/{kind}"
            self.assertEqual(
                {f"#daedalon:{family}" for family in self.FAMILIES if family.endswith("_finial")},
                set(load_json(tag_root / "finial.json")["values"]),
            )
            self.assertEqual(
                {
                    "#daedalon:georgian_fountain_basin",
                    "#daedalon:gothic_fountain_basin",
                    "#daedalon:greek_fountain_basin",
                    "#daedalon:krene_fountain",
                    "#daedalon:pege_fountain",
                },
                set(load_json(tag_root / "fountain.json")["values"]),
            )
            self.assertEqual(
                ["#daedalon:obeliskos_monument"],
                load_json(tag_root / "obelisk.json")["values"],
            )

    def test_registration_and_shared_runtime_transforms_are_locked(self) -> None:
        mod_blocks = (JAVA_ROOT / "registry/ModBlocks.java").read_text(encoding="utf-8")
        plugin = (
            JAVA_ROOT / "client/model/obj/ObjMeshModelLoadingPlugin.java"
        ).read_text(encoding="utf-8")
        baked = (JAVA_ROOT / "client/model/obj/ObjMeshBakedModel.java").read_text(
            encoding="utf-8"
        )
        fixed = (JAVA_ROOT / "block/FixedDecorBlock.java").read_text(encoding="utf-8")
        facing = (JAVA_ROOT / "block/FacingDecorBlock.java").read_text(encoding="utf-8")
        sized = (JAVA_ROOT / "block/SizedDecorBlock.java").read_text(encoding="utf-8")

        self.assertIn("FixedDecorBlock.Style.values()", mod_blocks)
        self.assertIn("registerKreneFountains()", mod_blocks)
        self.assertIn("registerObeliskosMonuments()", mod_blocks)
        self.assertIn("exactly 3878 decor blocks and items", mod_blocks)
        self.assertIn("new FinialBlock(decorSettings(), style)", mod_blocks)
        self.assertIn("createDetailedVariants(style.idSuffix())", plugin)
        self.assertIn('createDetailedVariants("krene_fountain")', plugin)
        self.assertIn('createDetailedVariants("obeliskos_monument")', plugin)
        self.assertIn("instanceof FacingDecorBlock", baked)
        self.assertIn("instanceof SizedDecorBlock", baked)
        self.assertIn("FacingDecorTransform.forState(state)", baked)
        self.assertIn("SizedDecorTransform.forState(state)", baked)
        self.assertIn("MODEL_REAR_SHIFT = -0.0540768F", facing)
        self.assertIn("0.0,", facing)
        definition = load_json(DAEDALON_ASSETS / "models/mesh/fountain_krene.json")
        self.assertEqual([0.0, 0.0, -0.0540768], definition["translate"])
        self.assertIn("GroundScaleTransform.forState(state)", baked)
        self.assertIn("no meaningless state properties", fixed)
        self.assertIn("Properties.HORIZONTAL_FACING", facing)
        self.assertIn("MODEL_SCALE = 2.0F", facing)
        self.assertNotIn("EnumProperty", facing)
        self.assertNotIn("FacingDecorBlock.SIZE", baked)
        self.assertIn("rotationStepsFromSouth", facing)
        self.assertIn('SMALL("small", 1.0F / 3.0F)', sized)
        self.assertIn('MEDIUM("medium", 2.0F / 3.0F)', sized)
        self.assertIn('LARGE("large", 1.0F)', sized)

        self.assertIn("isUpwardInwardSurface(centroid, geometricNormal)", baked)
        self.assertIn("INWARD_BOWL_MAX_RADIAL_DOT = -0.02F", baked)


if __name__ == "__main__":
    unittest.main()
