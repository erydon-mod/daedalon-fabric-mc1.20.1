import unittest
from pathlib import Path
from tempfile import TemporaryDirectory
from unittest.mock import patch

from PIL import Image

from daedalon_test_support import DAEDALON_ASSETS, DAEDALON_DATA, GENERATOR, load_json, sha256


class ArchitecturalFinishesSafetyTests(unittest.TestCase):
    def test_every_frieze_and_panel_has_complete_white_black_bronze_and_silver_finishes(self):
        self.assertEqual(9, len(GENERATOR.ARCHITECTURAL_FINISH_FAMILIES))
        self.assertEqual(("white", "black", "bronze", "silver"), GENERATOR.ARCHITECTURAL_FINISHES)
        finishes = set()
        languages = {locale: load_json(DAEDALON_ASSETS / f"lang/{locale}.json")
                     for locale in (path.stem for path in (DAEDALON_ASSETS / "lang").glob("*.json"))}
        for family in GENERATOR.ARCHITECTURAL_FINISH_FAMILIES:
            ids = GENERATOR.family_block_ids(family)
            self.assertEqual(58, len(ids), family)
            self.assertEqual(GENERATOR.ARCHITECTURAL_FINISHES, GENERATOR.architectural_finishes(family))
            stone_item = load_json(DAEDALON_ASSETS / f"models/item/{ids[0]}.json")
            for finish in GENERATOR.architectural_finishes(family):
                block_id = GENERATOR.architectural_finish_block_id(family, finish)
                finishes.add(block_id)
                self.assertIn(block_id, ids)
                self.assertEqual({"variants": {"": {"model": f"daedalon:mesh/{block_id}"}}},
                                 load_json(DAEDALON_ASSETS / f"blockstates/{block_id}.json"))
                self.assertEqual(stone_item, load_json(DAEDALON_ASSETS / f"models/item/{block_id}.json"))
                loot = load_json(DAEDALON_DATA / f"loot_tables/blocks/{block_id}.json")
                self.assertEqual(f"daedalon:{block_id}", loot["pools"][0]["entries"][0]["name"])
                for entries in languages.values():
                    self.assertIn(f"block.daedalon.{block_id}", entries)
                for kind in ("blocks", "items"):
                    ref = f"daedalon:{block_id}"
                    self.assertIn(ref, load_json(DAEDALON_DATA / f"tags/{kind}/{finish}.json")["values"])
                    metal = load_json(DAEDALON_DATA / f"../material/tags/{kind}/metal.json")["values"]
                    self.assertEqual(finish in ("bronze", "silver"), ref in metal)
        self.assertEqual(36, len(finishes))

    def test_all_five_panels_share_white_black_model_routes_without_material_texture_copies(self):
        self.assertEqual({"gothic_wall_panel", "corinthian_wall_panel", "byzantine_wall_panel",
                          "georgian_wall_panel", "renaissance_wall_panel"}, set(GENERATOR.PANEL_FAMILIES))
        metal = {kind: set(load_json(DAEDALON_DATA / f"../material/tags/{kind}/metal.json")["values"])
                 for kind in ("blocks", "items")}
        stone = {kind: set(load_json(DAEDALON_DATA / f"../material/tags/{kind}/stone.json")["values"])
                 for kind in ("blocks", "items")}
        for family, (mesh, *_labels) in GENERATOR.PANEL_FAMILIES.items():
            with self.subTest(family=family):
                white_item = load_json(DAEDALON_ASSETS / f"models/item/white_{family}.json")
                for finish in ("white", "black"):
                    block_id = f"{finish}_{family}"
                    ref = "daedalon:" + block_id
                    # The empty variant routes every size, facing and mount to
                    # the shared PanelBlock model rather than separate assets.
                    state = load_json(DAEDALON_ASSETS / f"blockstates/{block_id}.json")
                    self.assertEqual({"variants": {"": {"model": "daedalon:mesh/" + block_id}}}, state)
                    self.assertEqual(white_item, load_json(DAEDALON_ASSETS / f"models/item/{block_id}.json"))
                    for kind in ("blocks", "items"):
                        self.assertIn(ref, stone[kind])
                        self.assertNotIn(ref, metal[kind])
                    self.assertFalse(list((DAEDALON_ASSETS / "textures/block").glob(block_id + "*.png")),
                                     "Plain panels must reuse the existing shared Black/White texture")
                    self.assertFalse(list((DAEDALON_ASSETS / "models/mesh").glob(block_id + ".*")),
                                     "A material option must not duplicate the panel geometry")
                self.assertTrue((DAEDALON_ASSETS / f"models/mesh/{mesh}.json").is_file())
                self.assertTrue((DAEDALON_ASSETS / f"models/mesh/{mesh}.obj").is_file())

    def test_plain_native_palette_reuses_the_existing_bronze_and_silver(self):
        expected = {
            "black.png": ("789cc3d7c8416b40a4f20155ece071c362f85d610e71b32b328bfc12b4cf2ead", (0, 0, 0, 255)),
            "white.png": ("8cce60052e08828a3bafc5a74665510101e25ccfd1544950762385a0c94ce9dc", (255, 255, 255, 255)),
            "bronze.png": ("2d032e11f5d62ed4bb391f29b51c6f0d44d926eebf3e02f17bddd53d22c6c89e", (229, 160, 29, 255)),
            "silver.png": ("45558c4ddc67c752d825c811af1427b801778f75c57529a299ca1023e666e8f4", (206, 206, 206, 255)),
            "silver_s.png": ("c14c09fd53aa64d6a47323f79a760f7f97eec337f1da155b87a6acc480568cfe", (255, 255, 0, 255)),
        }
        for name, (digest, color) in expected.items():
            path = DAEDALON_ASSETS / f"textures/block/{name}"
            self.assertEqual(digest, sha256(path), name)
            with Image.open(path) as image:
                self.assertEqual((16, 16), image.size)
                rgba = image.convert("RGBA")
                self.assertEqual({color}, {rgba.getpixel((x, y)) for y in range(16) for x in range(16)}, name)
        for finish in ("white", "black"):
            for suffix in ("_s", "_n"):
                self.assertFalse((DAEDALON_ASSETS / f"textures/block/{finish}{suffix}.png").exists())

    def test_only_unpublished_architectural_gold_assets_are_removed(self):
        with TemporaryDirectory() as temporary:
            root = Path(temporary)
            resources = root / "src/main/resources"
            targets = []
            for family in GENERATOR.LEGACY_UNPUBLISHED_ARCHITECTURAL_GOLD_FAMILIES:
                targets.extend((
                    resources / f"assets/daedalon/blockstates/gold_{family}.json",
                    resources / f"assets/daedalon/models/item/gold_{family}.json",
                    resources / f"data/daedalon/loot_tables/blocks/gold_{family}.json",
                ))
            targets.extend(resources / f"assets/daedalon/textures/block/{name}" for name in ("gold.png", "gold_s.png"))
            targets.extend(resources / f"data/daedalon/tags/{kind}/gold.json" for kind in ("blocks", "items"))
            preserved = [resources / f"assets/daedalon/textures/block/{name}" for name in ("white.png", "bronze.png", "silver.png")]
            preserved.append(resources / "assets/daedalon/blockstates/gold_unrelated_family.json")
            preserved.append(resources / "assets/daedalon/blockstates/gold_corinthian_wall_panel.json")
            for path in targets + preserved:
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text("fixture", encoding="utf-8")
            changed = []
            with patch.object(GENERATOR, "ROOT", root), patch.object(GENERATOR, "RESOURCES", resources):
                with self.assertRaisesRegex(ValueError, "Obsolete unpublished architectural gold"):
                    GENERATOR.remove_obsolete_unpublished_architectural_gold_assets(True, changed)
                self.assertTrue(all(path.exists() for path in targets + preserved))
                GENERATOR.remove_obsolete_unpublished_architectural_gold_assets(False, changed)
            self.assertEqual(19, len(changed))
            self.assertEqual(set(targets), set(changed))
            self.assertFalse(any(path.exists() for path in targets))
            self.assertTrue(all(path.exists() for path in preserved))


if __name__ == "__main__":
    unittest.main()
