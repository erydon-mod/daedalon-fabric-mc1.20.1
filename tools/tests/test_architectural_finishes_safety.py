import unittest

from PIL import Image

from daedalon_test_support import DAEDALON_ASSETS, DAEDALON_DATA, GENERATOR, load_json, sha256


class ArchitecturalFinishesSafetyTests(unittest.TestCase):
    def test_every_carved_family_has_three_complete_plain_finishes(self):
        self.assertEqual(5, len(GENERATOR.ARCHITECTURAL_FINISH_FAMILIES))
        finishes = set()
        languages = {locale: load_json(DAEDALON_ASSETS / f"lang/{locale}.json")
                     for locale in ("en_us", "de_de", "es_es")}
        for family in GENERATOR.ARCHITECTURAL_FINISH_FAMILIES:
            ids = GENERATOR.family_block_ids(family)
            self.assertEqual(57, len(ids), family)
            stone_item = load_json(DAEDALON_ASSETS / f"models/item/{ids[0]}.json")
            for finish in GENERATOR.ARCHITECTURAL_FINISHES:
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
                    self.assertEqual(finish != "white", ref in metal)
        self.assertEqual(15, len(finishes))

    def test_plain_native_palette_and_authored_gold_metal_mask(self):
        expected = {
            "white.png": ("8cce60052e08828a3bafc5a74665510101e25ccfd1544950762385a0c94ce9dc", (255, 255, 255, 255)),
            "gold.png": ("a7c17c1b42a93eb7d841c515085b211c42b32793d318f9e70589eec292b07948", (255, 220, 96, 255)),
            "gold_s.png": ("ec7ab9035d3184e2119dc1c90c6df4b5b0930844bca744042e236ce8f692bdb8", (255, 255, 0, 255)),
        }
        for name, (digest, color) in expected.items():
            path = DAEDALON_ASSETS / f"textures/block/{name}"
            self.assertEqual(digest, sha256(path), name)
            with Image.open(path) as image:
                self.assertEqual((16, 16), image.size)
                rgba = image.convert("RGBA")
                self.assertEqual({color}, {rgba.getpixel((x, y)) for y in range(16) for x in range(16)}, name)
        self.assertFalse((DAEDALON_ASSETS / "textures/block/white_s.png").exists())


if __name__ == "__main__":
    unittest.main()
