from __future__ import annotations

import math
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


STYLES = ("georgian", "greek")


class GeorgianGreekFountainCollectionSafetyTests(unittest.TestCase):
    def test_basin_assets_states_languages_tags_and_no_drops_are_complete(self) -> None:
        registered_ids = all_block_ids()
        languages = {
            name: load_json(DAEDALON_ASSETS / f"lang/{name}.json")
            for name in ("en_us", "de_de", "es_es")
        }
        expected_variants = {
            f"size={size},waterlogged={waterlogged}"
            for size in ("small", "medium", "large")
            for waterlogged in ("false", "true")
        }

        for style in STYLES:
            family = f"{style}_fountain_basin"
            with self.subTest(style=style):
                ids = GENERATOR.family_block_ids(family)
                expected_ids = {
                    f"{material}_{'aged_' if aged else ''}{family}"
                    for material in GENERATOR.MATERIALS
                    for aged in (False, True)
                }
                self.assertEqual(54, len(ids))
                self.assertEqual(expected_ids, set(ids))
                self.assertTrue(expected_ids.issubset(registered_ids))
                self.assertIn(family, GENERATOR.NO_DROP_FAMILIES)

                for block_id in ids:
                    state = load_json(
                        DAEDALON_ASSETS / f"blockstates/{block_id}.json"
                    )
                    self.assertEqual(expected_variants, set(state["variants"]))
                    self.assertEqual(
                        "daedalon:block/mesh/fountain_basin_display",
                        load_json(
                            DAEDALON_ASSETS / f"models/item/{block_id}.json"
                        )["parent"],
                    )
                    self.assertFalse(
                        (DAEDALON_DATA / f"loot_tables/blocks/{block_id}.json").exists()
                    )
                    for language in languages.values():
                        self.assertIn(f"block.daedalon.{block_id}", language)

                references = {f"daedalon:{block_id}" for block_id in ids}
                family_reference = f"#daedalon:{family}"
                for kind in ("blocks", "items"):
                    tag_root = DAEDALON_DATA / f"tags/{kind}"
                    self.assertEqual(
                        references,
                        set(load_json(tag_root / f"{family}.json")["values"]),
                    )
                    for relation in (style, "fountain", "water_feature", "basin"):
                        self.assertIn(
                            family_reference,
                            load_json(tag_root / f"{relation}.json")["values"],
                        )

        registry = (JAVA_ROOT / "registry/ModBlocks.java").read_text(
            encoding="utf-8"
        )
        self.assertIn(
            "new FountainBasinBlock(decorSettings().dropsNothing(), style)",
            registry,
        )

    def test_bowls_remain_internal_models_without_block_or_item_ids(self) -> None:
        registered_ids = all_block_ids()
        languages = {
            name: load_json(DAEDALON_ASSETS / f"lang/{name}.json")
            for name in ("en_us", "de_de", "es_es")
        }

        for style in STYLES:
            bowl_family = f"{style}_fountain_bowl"
            self.assertNotIn(bowl_family, GENERATOR.ALL_FAMILIES)
            for material in GENERATOR.MATERIALS:
                for block_id in (
                    f"{material}_{bowl_family}",
                    f"{material}_aged_{bowl_family}",
                ):
                    self.assertNotIn(block_id, registered_ids)
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
                self.assertFalse(
                    (
                        DAEDALON_DATA
                        / f"tags/{kind}/{bowl_family}.json"
                    ).exists()
                )

        registry = (JAVA_ROOT / "registry/ModBlocks.java").read_text(
            encoding="utf-8"
        )
        self.assertNotIn("georgian_fountain_bowl", registry)
        self.assertNotIn("greek_fountain_bowl", registry)

    def test_supplied_meshes_definitions_and_evidence_are_hash_locked(self) -> None:
        for style in STYLES:
            for kind, scale in (("basin", 5.0), ("bowl", 2.0)):
                mesh = f"fountain_{style}_{kind}"
                expected = MESH_EXPECTATIONS[mesh]
                evidence = load_json(
                    REPO_ROOT / f"docs/evidence/{style}-fountain-{kind}-source.json"
                )
                source = evidence["source"]
                runtime = evidence["runtime"]
                with self.subTest(style=style, kind=kind):
                    supplied_style = ({"georgian": "greek", "greek": "georgian"}[style]
                                      if kind == "bowl" else style)
                    self.assertEqual(f"fountain_{supplied_style}_{kind}_BE.obj", source["file"])
                    self.assertNotIn(":\\", source["file"])
                    self.assertTrue(source["provenance"])
                    self.assertEqual(
                        "direct byte-for-byte integration",
                        evidence["preparation"]["tool"],
                    )
                    self.assertEqual(
                        "preserve exact supplied geometry",
                        evidence["preparation"]["face_policy"],
                    )
                    self.assertTrue(evidence["preparation"]["authored_uvs_retained"])
                    self.assertTrue(
                        evidence["preparation"]["authored_normals_retained"]
                    )
                    self.assertEqual(expected["obj_sha256"], source["sha256"])
                    self.assertEqual(source["sha256"], runtime["obj_sha256"])
                    self.assertEqual(expected["mtl_sha256"], runtime["mtl_sha256"])
                    self.assertEqual(source["triangles"], runtime["faces"])
                    self.assertEqual(
                        source["triangles"],
                        evidence["preparation"]["target_faces"],
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
                    self.assertEqual(0, actual["uvs"])
                    self.assertGreater(actual["normals"], 0)
                    self.assertEqual({"v//vn"}, actual["face_index_styles"])
                    self.assertEqual(expected["obj_sha256"], sha256(obj_path))
                    self.assertEqual(
                        expected["mtl_sha256"],
                        sha256(MESH_ROOT / expected["mtl"]),
                    )

                    definition = load_json(MESH_ROOT / expected["definition"])
                    self.assertEqual(
                        f"daedalon:models/mesh/{mesh}.obj", definition["obj"]
                    )
                    self.assertTrue(definition["fit_to_block"])
                    self.assertEqual([scale, scale, scale], definition["scale"])
                    self.assertFalse(definition["smooth_normals"])
                    self.assertTrue(definition["force_uv_projection"])
                    self.assertEqual(
                        "axis_stabilized_box", definition["uv_projection"]
                    )
                    self.assertTrue(definition["face_oriented_uvs"])
                    self.assertTrue(definition["repair_degenerate_uvs"])

    def test_georgian_basin_normalises_only_the_planar_inner_floor_normals(self) -> None:
        definition = load_json(MESH_ROOT / "fountain_georgian_basin.json")
        self.assertTrue(definition["repair_floor_normals"])
        for mesh in (
            "fountain_gothic_basin",
            "fountain_gothic_bowl",
            "fountain_georgian_bowl",
            "fountain_greek_basin",
            "fountain_greek_bowl",
        ):
            self.assertFalse(
                load_json(MESH_ROOT / f"{mesh}.json").get(
                    "repair_floor_normals", False
                ),
                mesh,
            )

        vertices: list[tuple[float, float, float]] = []
        normals: list[tuple[float, float, float]] = []
        faces: list[list[tuple[int, int]]] = []
        for line in (MESH_ROOT / "fountain_georgian_basin.obj").read_text(
            encoding="utf-8"
        ).splitlines():
            if line.startswith("v "):
                vertices.append(tuple(map(float, line.split()[1:4])))
            elif line.startswith("vn "):
                normals.append(tuple(map(float, line.split()[1:4])))
            elif line.startswith("f "):
                faces.append(
                    [
                        (int(token.split("/")[0]) - 1, int(token.split("/")[2]) - 1)
                        for token in line.split()[1:]
                    ]
                )

        minimum = tuple(min(vertex[axis] for vertex in vertices) for axis in range(3))
        maximum = tuple(max(vertex[axis] for vertex in vertices) for axis in range(3))
        span = max(maximum[axis] - minimum[axis] for axis in range(3))
        center_x = (minimum[0] + maximum[0]) * 0.5
        center_z = (minimum[2] + maximum[2]) * 0.5
        floor_faces = repaired_corners = affected_faces = 0

        for face in faces:
            first, second, third = (vertices[face[index][0]] for index in range(3))
            edge_ab = tuple(second[axis] - first[axis] for axis in range(3))
            edge_ac = tuple(third[axis] - first[axis] for axis in range(3))
            geometric = (
                edge_ab[1] * edge_ac[2] - edge_ab[2] * edge_ac[1],
                edge_ab[2] * edge_ac[0] - edge_ab[0] * edge_ac[2],
                edge_ab[0] * edge_ac[1] - edge_ab[1] * edge_ac[0],
            )
            length = math.sqrt(sum(component * component for component in geometric))
            geometric = tuple(component / length for component in geometric)
            centroid = tuple(
                sum(vertices[vertex_index][axis] for vertex_index, _ in face)
                / len(face)
                for axis in range(3)
            )
            normalized_y = (centroid[1] - minimum[1]) / span
            normalized_x = (centroid[0] - center_x) / span
            normalized_z = (centroid[2] - center_z) / span
            if not (
                geometric[1] >= 0.95
                and 0.06 < normalized_y < 0.09
                and normalized_x * normalized_x + normalized_z * normalized_z
                < 0.42 * 0.42
            ):
                continue

            floor_faces += 1
            face_repaired = False
            for _, normal_index in face:
                imported = normals[normal_index]
                normal_length = math.sqrt(
                    sum(component * component for component in imported)
                )
                alignment = sum(
                    imported[axis] / normal_length * geometric[axis]
                    for axis in range(3)
                )
                if alignment < 0.8:
                    repaired_corners += 1
                    face_repaired = True
            affected_faces += int(face_repaired)

        self.assertEqual(186, floor_faces)
        self.assertEqual(0, repaired_corners)
        self.assertEqual(0, affected_faces)

        baked = (JAVA_ROOT / "client/model/obj/ObjMeshBakedModel.java").read_text(
            encoding="utf-8"
        )
        self.assertIn("definition.repairFloorNormals()", baked)
        self.assertIn("isInteriorFloorFace", baked)
        self.assertIn("if (repairFloorNormals)", baked)
        self.assertIn("normal = new ObjMeshData.Vec3(0.0F, 1.0F, 0.0F)", baked)
        self.assertNotIn("normalDot(transformed, geometricNormal) < 0.8F", baked)

    def test_measured_water_surfaces_stay_inside_each_mesh(self) -> None:
        for style in STYLES:
            evidence = load_json(
                REPO_ROOT / f"docs/evidence/{style}-fountain-basin-source.json"
            )
            small = evidence["placement"]["sizes"]["small"]
            profile = load_json(DAEDALON_DATA / f"fountain_water/{style}.json")
            with self.subTest(style=style, kind="basin"):
                self.assertGreaterEqual(profile["minimum_clearance"], 0.001)
                self.assertLessEqual(profile["maximum_clearance"], 0.005)
                self.assertAlmostEqual(profile["water_surface_y"] * 3, small["water_surface_y"])
                self.assertAlmostEqual(max(abs(c) for p in profile["points"] for c in p) * 3,
                                       small["water_half_width"])

        georgian = load_json(
            REPO_ROOT / "docs/evidence/georgian-fountain-bowl-source.json"
        )["placement"]["sizes"]["small"]
        self.assertLess(georgian["connection_rise"], georgian["water_floor_y"])
        greek = load_json(
            REPO_ROOT / "docs/evidence/greek-fountain-bowl-source.json"
        )["placement"]["sizes"]["small"]
        self.assertEqual(greek["connection_rise"], greek["water_floor_y"])
        for style, bowl in (("georgian", georgian), ("greek", greek)):
            profile = load_json(DAEDALON_DATA / f"fountain_water/{style}_bowl.json")
            self.assertAlmostEqual(profile["water_surface_y"], bowl["water_surface_y"])
            self.assertGreaterEqual(profile["minimum_clearance"], 0.001)
            self.assertLessEqual(profile["maximum_clearance"], 0.005)

    def test_basin_style_selects_the_matching_internal_bowl_model(self) -> None:
        basin = (JAVA_ROOT / "block/FountainBasinBlock.java").read_text(
            encoding="utf-8"
        )
        layout = (JAVA_ROOT / "block/FountainAssemblyLayout.java").read_text(
            encoding="utf-8"
        )
        loader = (
            JAVA_ROOT / "client/model/obj/ObjMeshModelLoadingPlugin.java"
        ).read_text(encoding="utf-8")
        baked = (JAVA_ROOT / "client/model/obj/ObjMeshBakedModel.java").read_text(
            encoding="utf-8"
        )

        for style, bowl_style in (("georgian", "greek"), ("greek", "georgian")):
            match = re.search(
                rf"{style.upper()}\(\s*\"{style}_fountain_basin\".*?"
                rf"FountainBowlModel\.Style\.{bowl_style.upper()}",
                basin,
                re.DOTALL,
            )
            self.assertIsNotNone(match, style)

        self.assertIn(
            "FountainBowlModel.Style bowlStyle = basinStyle.bowlStyle();",
            layout,
        )
        self.assertIn("new PlacedBowl(bowlStyle, size", layout)
        self.assertIn("FountainBowlModel.Style.values()", loader)
        self.assertIn("createInternalFountainBowlVariants(style)", loader)
        self.assertIn("style.basinIdSuffix()", loader)
        self.assertIn("bowl.style()", baked)
        self.assertIn("fountainBowlModelId(", baked)


if __name__ == "__main__":
    unittest.main()
