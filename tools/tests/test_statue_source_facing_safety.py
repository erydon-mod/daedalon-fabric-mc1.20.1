from __future__ import annotations

import hashlib
import unittest

from daedalon_test_support import GENERATOR, MESH_ROOT, REPO_ROOT, load_json, sha256


class StatueSourceFacingSafetyTests(unittest.TestCase):
    def test_reviewed_front_vectors_cover_every_unchanged_statue_and_bust_mesh(self) -> None:
        evidence = load_json(REPO_ROOT / "docs/evidence/statue-source-facing.json")
        expected = {"spartan", *GENERATOR.CANONICAL_STATUE_FAMILIES, *GENERATOR.BUST_FAMILIES}
        self.assertEqual(36, len(expected))
        self.assertEqual(expected, set(evidence["models"]))
        north = {"hermes", "hephaestus", "hestia", "bust_hestia"}
        self.assertEqual(north, set(evidence["normalized_north_sources"]))
        self.assertEqual([0, 0, -1], evidence["coordinate_frame"]["north"])
        self.assertEqual([0, 0, 1], evidence["coordinate_frame"]["south"])
        for family, model in evidence["models"].items():
            with self.subTest(family=family):
                self.assertEqual("north" if family in north else "south", model["source_front"])
                self.assertEqual(model["sha256"], sha256(MESH_ROOT / model["obj"]),
                                 "Facing correction must retain each reviewed mesh, normals and UVs")

    def test_published_spartan_profile_and_public_api_remain_identical(self) -> None:
        evidence = load_json(REPO_ROOT / "docs/evidence/statue-source-facing.json")["models"]["spartan"]
        self.assertEqual("south", evidence["source_front"])
        self.assertEqual("east", evidence["published_profile_facing"])
        self.assertEqual(90, evidence["retained_physical_front_clockwise_offset_degrees"])
        source = (REPO_ROOT / evidence["legacy_profile_source"]).read_text(encoding="utf-8")
        self.assertEqual(evidence["legacy_profile_source_lf_sha256"], hashlib.sha256(source.encode()).hexdigest(),
                         "Published Spartan source, collision fixture and public helper must remain unchanged")


if __name__ == "__main__":
    unittest.main()
