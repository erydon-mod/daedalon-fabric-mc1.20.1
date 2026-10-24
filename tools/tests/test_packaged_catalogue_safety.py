from __future__ import annotations

import re
import unittest

from tools import audit_daedalon_jar as audit
from daedalon_test_support import DAEDALON_ASSETS, JAVA_ROOT, MESH_ROOT, RESOURCES


class PackagedCatalogueSafetyTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        # Exercise the release auditor against real generated resources, rather
        # than duplicating its expected inventory in a synthetic fixture.
        cls.entries = {
            path.relative_to(RESOURCES).as_posix()
            for directory in (
                DAEDALON_ASSETS / "blockstates",
                DAEDALON_ASSETS / "models/item",
                RESOURCES / "data/daedalon/loot_tables/blocks",
            )
            for path in directory.glob("*.json")
        }

    def test_real_catalogue_includes_item_only_selectors_and_all_assembly_parts(self) -> None:
        actual = audit.validate_catalogue_resources(self.entries)
        self.assertEqual((4682, 4737, 4516), tuple(len(actual[key]) for key in ("blockstates", "items", "loot")))
        self.assertIn("bronze_olympian_god_statue.json", actual["items"])
        self.assertIn("olympian_god_display.json", actual["items"])
        self.assertIn("obeliskos_part.json", actual["blockstates"])

    def test_missing_selector_or_shared_parent_is_rejected(self) -> None:
        for model in ("silver_olympian_god_statue", "olympian_god", "olympian_god_display"):
            with self.subTest(model=model), self.assertRaisesRegex(AssertionError, "item-only selectors"):
                audit.validate_catalogue_resources(self.entries - {f"assets/daedalon/models/item/{model}.json"})

    def test_internal_helpers_cannot_gain_item_models(self) -> None:
        with self.assertRaisesRegex(AssertionError, "item-only selectors"):
            audit.validate_catalogue_resources(self.entries | {"assets/daedalon/models/item/obeliskos_part.json"})

    def test_item_only_selectors_cannot_gain_blockstates_or_loot(self) -> None:
        for prefix in ("assets/daedalon/blockstates", "data/daedalon/loot_tables/blocks"):
            with self.subTest(prefix=prefix), self.assertRaises(AssertionError):
                audit.validate_catalogue_resources(self.entries | {f"{prefix}/silver_olympian_god_statue.json"})

    def test_internal_allowlist_matches_the_canonical_registry(self) -> None:
        registry = (JAVA_ROOT / "registry/ModBlocks.java").read_text(encoding="utf-8")
        internal = set(re.findall(r'id\("([a-z0-9_]+_part)"\)', registry))
        self.assertEqual(audit.INTERNAL_BLOCK_IDS, internal)

    def test_item_only_medallion_is_hash_locked_without_changing_world_mesh_manifest(self) -> None:
        combined = audit.MESH_EXPECTATIONS | audit.ITEM_ONLY_MESH_EXPECTATIONS
        self.assertEqual(99, len(audit.MESH_EXPECTATIONS))
        self.assertEqual({"olympian_god_medallion"}, set(audit.ITEM_ONLY_MESH_EXPECTATIONS))
        self.assertEqual({value["obj"] for value in combined.values()}, {path.name for path in MESH_ROOT.glob("*.obj")})
        self.assertEqual({value["definition"] for value in combined.values()}, {path.name for path in MESH_ROOT.glob("*.json")})
        medallion = audit.ITEM_ONLY_MESH_EXPECTATIONS["olympian_god_medallion"]
        self.assertEqual(medallion["obj_sha256"], audit.sha256((MESH_ROOT / medallion["obj"]).read_bytes()))
        self.assertIsNone(medallion["mtl"])
        self.assertIsNone(medallion["display"])


if __name__ == "__main__":
    unittest.main()
