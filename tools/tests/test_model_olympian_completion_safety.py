from __future__ import annotations

import hashlib
import math
import re
import struct
import unittest
from pathlib import PurePosixPath

from daedalon_test_support import (
    AUTHORED_UV_STATUE_FAMILIES,
    BUST_MESHES,
    CLASSICAL_STATUE_MESHES,
    DAEDALON_ASSETS,
    DAEDALON_DATA,
    GENERATOR,
    JAVA_ROOT,
    MESH_EXPECTATIONS,
    MESH_ROOT,
    OLYMPIAN_COMPLETION_MESHES,
    REPO_ROOT,
    load_json,
    parse_obj,
    sha256,
)


OLYMPIANS_WITH_HESTIA = frozenset({
    "zeus", "hera", "poseidon", "demeter", "athena", "apollo", "artemis",
    "ares", "aphrodite", "hephaestus", "hermes", "dionysus", "hestia",
})

SOURCE_HASHES = {
    "hermes": "7522d9febbf48c9256843e35d98e7b96db5d312c16c3f9af7955049d01622b44",
    "hephaestus": "f8f995e8f48c7fe11b0de2c36fd3ab58cba3d018cdfbbd970e24e2617064cc07",
    "hestia": "a13b30bb47c988e9a6495b459b13849a5c7c659f38e37894665fe8c00b8ccf27",
    "bust_hestia": "320528575e6184f65542b68889d6c0f0fcdc06b35622b921f0b8dc4988947354",
}

# Independently frozen measured coefficients and three-block collision layers.
# The Hestia bust deliberately retains its supplied proportions and face count.
PROFILES = {
    "hermes": ((0.4564426, 0.5), (
        (-0.083277, 0.0, -0.022948, 0.996162, 0.375, 1.022948),
        (-0.041634, 0.375, 0.008534, 0.955652, 0.75, 0.994437),
        (-0.016207, 0.75, 0.280212, 0.595546, 1.125, 0.953135),
        (0.043013, 1.125, 0.184577, 0.841756, 1.5, 0.812771),
        (-0.098398, 1.5, 0.192443, 0.885284, 1.875, 0.846843),
        (-0.075711, 1.875, 0.251837, 0.95682, 2.25, 0.859112),
        (0.003748, 2.25, 0.105598, 1.018615, 2.625, 0.827453),
        (0.260052, 2.625, 0.127794, 1.098398, 3.0, 0.709634),
    )),
    "hephaestus": ((0.4657148, 0.4759253), (
        (-0.09229, 0.0, -0.083174, 1.023719, 0.375, 1.035024),
        (-0.07276, 0.375, -0.065294, 1.002737, 0.75, 1.013089),
        (-0.015558, 0.75, -0.05167, 0.76401, 1.125, 1.08642),
        (-0.034799, 1.125, -0.08642, 0.811014, 1.5, 0.796081),
        (0.034441, 1.5, 0.139077, 1.09229, 1.875, 0.814189),
        (0.000654, 1.875, 0.205263, 1.09229, 2.25, 0.885091),
        (0.006925, 2.25, 0.256306, 0.835618, 2.625, 0.876413),
        (0.140206, 2.625, 0.255092, 0.687971, 3.0, 0.755882),
    )),
    "hestia": ((0.5, 0.5365697), (
        (-0.049047, 0.0, 0.010629, 1.049047, 0.375, 1.062511),
        (-0.02516, 0.375, 0.024133, 1.025668, 0.75, 1.047093),
        (0.096333, 0.75, 0.160749, 0.790085, 1.125, 0.789178),
        (0.042495, 1.125, 0.150137, 0.81324, 1.5, 0.762606),
        (0.012781, 1.5, 0.187785, 0.832381, 1.875, 0.744078),
        (0.002128, 1.875, -0.062511, 0.848488, 2.25, 0.734229),
        (0.041914, 2.25, 0.229434, 0.837426, 2.625, 0.718168),
        (0.250656, 2.625, 0.225577, 0.665075, 3.0, 0.677889),
    )),
    "bust_hestia": ((0.4594176, 0.500026), (
        (-1.02, 0.0, -0.45261, 2.018821, 0.361222, 1.45261),
        (-1.02, 0.361222, -0.45261, 2.02, 0.722444, 1.450868),
        (-0.969004, 0.722444, -0.345129, 1.996863, 1.083666, 1.393803),
        (-0.66322, 1.083666, 0.075615, 1.681942, 1.444889, 1.366171),
        (-0.286594, 1.444889, -0.167388, 1.368119, 1.806111, 1.294037),
        (-0.247614, 1.806111, -0.236023, 1.286497, 2.167333, 1.215532),
        (-0.215718, 2.167333, -0.280964, 1.222885, 2.528555, 1.159999),
        (-0.188086, 2.528555, -0.280964, 1.086083, 2.889777, 1.065877),
    )),
}


def geometry_streams(stem: str) -> tuple[list, list, list]:
    positions, normals, face_refs = [], [], []
    for line in (MESH_ROOT / f"{stem}.obj").read_text(encoding="utf-8").splitlines():
        if line.startswith("v "):
            positions.append(tuple(map(float, line.split()[1:4])))
        elif line.startswith("vn "):
            normals.append(tuple(map(float, line.split()[1:4])))
        elif line.startswith("f "):
            face_refs.append(tuple((int(token.split("/")[0]), int(token.split("/")[2]))
                                   for token in line.split()[1:]))
    return positions, normals, face_refs


def vector_digest(vectors: list) -> str:
    return hashlib.sha256(b"".join(struct.pack(">ddd", *vector) for vector in vectors)).hexdigest()


class OlympianCompletionSafetyTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.evidence = load_json(REPO_ROOT / "docs/evidence/olympian-completion-source.json")

    def test_exact_thirteen_subjects_have_both_forms_and_fifty_six_finishes(self) -> None:
        self.assertEqual(OLYMPIANS_WITH_HESTIA, set(GENERATOR.OLYMPIAN_STATUE_FAMILIES))
        self.assertEqual(OLYMPIANS_WITH_HESTIA,
                         {values[0] for values in GENERATOR.BUST_FAMILIES.values()})
        bust_java = (JAVA_ROOT / "block/BustBlock.java").read_text(encoding="utf-8")
        statue_java = (JAVA_ROOT / "block/ClassicalStatueBlock.java").read_text(encoding="utf-8")
        self.assertEqual(OLYMPIANS_WITH_HESTIA,
                         set(re.findall(r'^\s+[A-Z_]+\("([a-z_]+)",', bust_java, re.MULTILINE)))
        statue_subjects = set(re.findall(r'^\s+[A-Z_]+\("([a-z_]+)",', statue_java, re.MULTILINE)) | {"zeus"}
        self.assertTrue(OLYMPIANS_WITH_HESTIA <= statue_subjects)
        for subject in OLYMPIANS_WITH_HESTIA:
            for family, form in ((subject, "statue"), (f"bust_{subject}", "bust")):
                ids = GENERATOR.family_block_ids(family)
                self.assertEqual(56, len(ids), family)
                self.assertEqual(56, len(set(ids)), family)
                for block_id in ids:
                    self.assertTrue((DAEDALON_ASSETS / f"blockstates/{block_id}.json").is_file())
                    self.assertTrue((DAEDALON_ASSETS / f"models/item/{block_id}.json").is_file())
                for kind in ("blocks", "items"):
                    refs = load_json(DAEDALON_DATA / f"tags/{kind}/{subject}_{form}.json")["values"]
                    self.assertEqual({f"daedalon:{block_id}" for block_id in ids}, set(refs))

    def test_supplemental_meshes_preserve_historical_batch_locks(self) -> None:
        self.assertEqual(99, len(MESH_EXPECTATIONS))
        self.assertEqual(18, len(CLASSICAL_STATUE_MESHES))
        self.assertEqual(12, len(BUST_MESHES))
        self.assertEqual({"hermes", "hephaestus", "hestia", "bust_hestia"}, set(OLYMPIAN_COMPLETION_MESHES))
        self.assertFalse({"hermes", "hephaestus", "hestia"} & set(CLASSICAL_STATUE_MESHES))
        self.assertNotIn("hestia", BUST_MESHES)
        for stem, locked in OLYMPIAN_COMPLETION_MESHES.items():
            with self.subTest(stem=stem):
                expected = MESH_EXPECTATIONS[stem]
                obj_path = MESH_ROOT / expected["obj"]
                parsed = parse_obj(obj_path)
                for key in ("vertices", "uvs", "normals", "faces", "bounds", "face_sizes", "face_index_styles"):
                    self.assertEqual(expected[key], parsed[key], (stem, key))
                self.assertEqual(expected["obj_sha256"], sha256(obj_path))
                self.assertIsNone(expected["mtl"])
                self.assertNotIn(b"\r", obj_path.read_bytes())
                self.assertNotIn("mtllib ", obj_path.read_text(encoding="utf-8"))
                positions, normals, face_refs = geometry_streams(stem)
                self.assertEqual(locked["position_stream_sha256"], vector_digest(positions))
                self.assertEqual(locked["normal_stream_sha256"], vector_digest(normals))
                digest = hashlib.sha256(b"".join(struct.pack(">ii", *pair)
                                                for face in face_refs for pair in face)).hexdigest()
                self.assertEqual(locked["face_position_normal_stream_sha256"], digest)

    def test_hestia_bust_icon_uses_the_prepared_front_without_rotating_world_geometry(self) -> None:
        display_root = DAEDALON_ASSETS / "models/block/mesh"
        self.assertEqual("bust_hestia_display.json", MESH_EXPECTATIONS["bust_hestia"]["display"])
        original = load_json(display_root / "bust_display.json")
        hestia = load_json(display_root / "bust_hestia_display.json")
        original_gui = original["display"]["gui"]
        hestia_gui = hestia["display"]["gui"]
        self.assertEqual(180, (hestia_gui["rotation"][1] - original_gui["rotation"][1]) % 360)
        self.assertEqual(original_gui["rotation"][::2], hestia_gui["rotation"][::2])
        self.assertEqual({key: value for key, value in original_gui.items() if key != "rotation"},
                         {key: value for key, value in hestia_gui.items() if key != "rotation"})
        for context, transform in original["display"].items():
            if context != "gui":
                self.assertEqual(transform, hestia["display"][context])
        for family in GENERATOR.BUST_FAMILIES:
            expected_parent = "daedalon:block/mesh/" + (
                "bust_hestia_display" if family == "bust_hestia" else "bust_display")
            self.assertEqual(expected_parent, GENERATOR.display_parent(family))
            for block_id in GENERATOR.family_block_ids(family):
                self.assertEqual(expected_parent,
                                 load_json(DAEDALON_ASSETS / f"models/item/{block_id}.json")["parent"])
        mesh = self.evidence["models"]["bust_hestia"]
        self.assertEqual("north", mesh["runtime_facing"])
        self.assertEqual(mesh["runtime_obj_sha256"], sha256(MESH_ROOT / "bust_hestia.obj"))

    def test_one_shared_mesh_keeps_uv_policy_and_material_variants_separate(self) -> None:
        for stem in OLYMPIAN_COMPLETION_MESHES:
            expected = MESH_EXPECTATIONS[stem]
            definition = load_json(MESH_ROOT / expected["definition"])
            self.assertEqual(f"daedalon:models/mesh/{stem}.obj", definition["obj"])
            self.assertNotIn("mtl", definition)
            self.assertTrue(definition["fit_to_block"])
            self.assertEqual([3.0, 3.0, 3.0], definition["scale"])
            self.assertEqual([0.0, 0.0, 0.0], definition["translate"])
            self.assertTrue(definition["face_oriented_uvs"])
            self.assertTrue(definition["smooth_normals"])
            self.assertEqual(80.0, definition["smooth_angle_degrees"])
            authored = stem in AUTHORED_UV_STATUE_FAMILIES
            self.assertIs(definition["force_uv_projection"], not authored)
            self.assertIs(definition["repair_degenerate_uvs"], not authored)
            self.assertEqual("axis_stabilized_box", definition["uv_projection"])
            self.assertEqual({f"{stem}.obj"}, {path.name for path in MESH_ROOT.glob(f"*{stem}*.obj")
                                              if not path.name.startswith("bust_") or stem.startswith("bust_")})
            subject = stem.removeprefix("bust_")
            for material in (*GENERATOR.MATERIALS, "bronze", "silver"):
                self.assertFalse(any(MESH_ROOT.glob(f"{material}*{subject}*.obj")))

    def test_measured_java_profiles_and_collision_layers_match_the_normalized_meshes(self) -> None:
        for stem, (support, boxes) in PROFILES.items():
            with self.subTest(stem=stem):
                java_file = "BustBlock.java" if stem.startswith("bust_") else "ClassicalStatueBlock.java"
                java = (JAVA_ROOT / f"block/{java_file}").read_text(encoding="utf-8")
                subject = stem.removeprefix("bust_")
                match = re.search(rf'{subject.upper()}\("{subject}", "[^"]+", ([\d.]+)F, ([\d.]+)F, shape\((.*?)\)\)', java, re.DOTALL)
                self.assertIsNotNone(match, stem)
                self.assertEqual(support, tuple(map(float, match.group(1, 2))))
                values = tuple(map(float, re.findall(r"-?\d+(?:\.\d+)?", match.group(3))))
                self.assertEqual(boxes, tuple(tuple(values[index:index + 6]) for index in range(0, len(values), 6)))
                evidence = self.evidence["models"][stem]
                self.assertEqual(support, (evidence["model_support_center_x"], evidence["model_support_center_z"]))
                self.assertEqual(boxes, tuple(map(tuple, evidence["runtime_collision_boxes"])))
                self.assertEqual(0.0, evidence["model_ground_contact_y"])
                positions, _, _ = geometry_streams(stem)
                minimum = [min(point[axis] for point in positions) for axis in range(3)]
                maximum = [max(point[axis] for point in positions) for axis in range(3)]
                fit_scale = 3.0 / max(maximum[axis] - minimum[axis] for axis in range(3))
                fitted = [((x - (minimum[0] + maximum[0]) * .5) * fit_scale + .5,
                           (y - minimum[1]) * fit_scale,
                           (z - (minimum[2] + maximum[2]) * .5) * fit_scale + .5)
                          for x, y, z in positions]
                bottom = [point for point in fitted if point[1] <= .24]
                measured_support = tuple((min(point[axis] for point in bottom) + max(point[axis] for point in bottom)) * .5
                                         for axis in (0, 2))
                for expected_value, actual_value in zip(support, measured_support):
                    self.assertAlmostEqual(expected_value, actual_value, delta=1e-7)
                self.assertAlmostEqual(evidence["fitted_three_block_height"], max(point[1] for point in fitted), delta=1e-8)
                for point in fitted:
                    self.assertTrue(any(all(box[axis] - 1e-5 <= point[axis] <= box[axis + 3] + 1e-5
                                            for axis in range(3)) for box in boxes), (stem, point))

    def test_portable_provenance_locks_source_counts_and_current_runtime_coordinates(self) -> None:
        self.assertIn("Oliver-authored Meshy creations", self.evidence["provenance"])
        self.assertIn("permitted in the public Daedalon repository", self.evidence["provenance"])
        self.assertEqual(set(SOURCE_HASHES), set(self.evidence["models"]))
        serialized = (REPO_ROOT / "docs/evidence/olympian-completion-source.json").read_text(encoding="utf-8")
        self.assertNotRegex(serialized, r"[A-Za-z]:[/\\]|/Users/|/home/|\\\\[^\\]+\\")
        for stem, source_hash in SOURCE_HASHES.items():
            model = self.evidence["models"][stem]
            locked = OLYMPIAN_COMPLETION_MESHES[stem]
            source_path = PurePosixPath(model["source_file"])
            self.assertFalse(source_path.is_absolute())
            self.assertNotIn("..", source_path.parts)
            self.assertEqual(source_hash, model["source_sha256"])
            for key in ("vertices", "faces", "normals"):
                self.assertEqual(locked[key], model[f"source_{key}"])
            self.assertEqual(0, model["source_uvs"])
            self.assertEqual([3], model["source_face_sizes"])
            self.assertIs(model["source_modified"], False)
            self.assertEqual("south", model["source_facing"])
            self.assertEqual("north", model["runtime_facing"])
            self.assertIn("(x,y,z)->(-x,y,-z)", model["source_to_runtime_transform"])
            source_min, source_max = model["source_bounds"]
            rotated = ((-source_max[0], source_min[1], -source_max[2]),
                       (-source_min[0], source_max[1], -source_min[2]))
            self.assertEqual(locked["bounds"], rotated)
            self.assertEqual(locked["bounds"], tuple(map(tuple, model["runtime_bounds"])))
            self.assertEqual(8, len(model["source_collision_boxes"]))
            for source_box, runtime_box in zip(model["source_collision_boxes"], model["runtime_collision_boxes"]):
                rotated_box = (1 - source_box[3], source_box[1], 1 - source_box[5],
                               1 - source_box[0], source_box[4], 1 - source_box[2])
                for expected, actual in zip(rotated_box, runtime_box):
                    self.assertTrue(math.isclose(expected, actual, abs_tol=1e-6), (stem, rotated_box, runtime_box))
            self.assertEqual(locked["obj_sha256"], model["runtime_obj_sha256"])
            validation = model["intake_validation"]
            for key in ("source_unchanged", "vertex_rigid_rotation_exact", "normal_rigid_rotation_exact",
                        "face_records_unchanged", "lf_line_endings"):
                self.assertIs(validation[key], True, (stem, key))
            self.assertIs(validation["uv_search_changes_geometry"], False)


if __name__ == "__main__":
    unittest.main()
