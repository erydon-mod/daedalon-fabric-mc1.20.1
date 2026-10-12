from __future__ import annotations

import unittest

from PIL import Image

from daedalon_test_support import (
    DAEDALON_ASSETS, DAEDALON_DATA, GENERATOR, all_block_ids, load_json, sha256,
)


class MetalDecorSafetyTests(unittest.TestCase):
    def test_every_requested_family_has_both_metals_and_complete_resources(self) -> None:
        families = (
            "spartan", *GENERATOR.CANONICAL_STATUE_FAMILIES,
            *GENERATOR.URN_FAMILIES, *GENERATOR.BUST_FAMILIES,
            *GENERATOR.FINIAL_FAMILIES, *GENERATOR.CORBEL_FAMILIES,
            *GENERATOR.PLINTH_FAMILIES,
        )
        self.assertEqual(58, len(families))
        self.assertEqual(set(families), set(GENERATOR.METAL_DECOR_FAMILIES))
        languages = {
            locale: load_json(DAEDALON_ASSETS / f"lang/{locale}.json")
            for locale in ("en_us", "de_de", "es_es")
        }
        metal_ids = set()
        for family in families:
            ids = GENERATOR.family_block_ids(family)
            self.assertEqual(56, len(ids), family)
            stone = load_json(DAEDALON_ASSETS / f"blockstates/{ids[0]}.json")["variants"]
            for metal in ("bronze", "silver"):
                block_id = GENERATOR.metal_block_id(family, metal)
                metal_ids.add(block_id)
                self.assertIn(block_id, ids)
                self.assertNotIn("aged", block_id)
                variants = load_json(DAEDALON_ASSETS / f"blockstates/{block_id}.json")["variants"]
                self.assertEqual(set(stone), set(variants), block_id)
                self.assertEqual({f"daedalon:mesh/{block_id}"}, {v["model"] for v in variants.values()})
                self.assertEqual(
                    load_json(DAEDALON_ASSETS / f"models/item/{ids[0]}.json"),
                    load_json(DAEDALON_ASSETS / f"models/item/{block_id}.json"),
                )
                loot = load_json(DAEDALON_DATA / f"loot_tables/blocks/{block_id}.json")
                self.assertEqual(f"daedalon:{block_id}", loot["pools"][0]["entries"][0]["name"])
                for locale, entries in languages.items():
                    label = entries[f"block.daedalon.{block_id}"]
                    expected = {"en_us": "Silver", "de_de": "Silber", "es_es": "Plata"}[locale] if metal == "silver" else {"en_us": "Bronze", "de_de": "Bronze", "es_es": "Bronce"}[locale]
                    self.assertTrue(label.startswith(expected + " "), (locale, block_id, label))
        self.assertEqual(116, len(metal_ids))
        self.assertTrue(metal_ids <= set(all_block_ids()))

    def test_metal_tags_exclude_stone_and_fountain_plinths(self) -> None:
        for kind in ("blocks", "items"):
            tags = DAEDALON_DATA / f"tags/{kind}"
            bronze = set(load_json(tags / "bronze.json")["values"])
            silver = set(load_json(tags / "silver.json")["values"])
            self.assertEqual(61, len(bronze))
            self.assertEqual(58, len(silver))
            self.assertEqual(bronze | silver, set(load_json(DAEDALON_DATA / f"../material/tags/{kind}/metal.json")["values"]))
            self.assertTrue((bronze | silver).isdisjoint(load_json(DAEDALON_DATA / f"../material/tags/{kind}/stone.json")["values"]))
            fountain_plinths = set(load_json(tags / "fountain_plinth.json")["values"])
            self.assertEqual(270, len(fountain_plinths))
            self.assertTrue((bronze | silver).isdisjoint(fountain_plinths))
            for synonym in ("fountain", "water_feature"):
                self.assertIn("#daedalon:fountain_plinth", load_json(tags / f"{synonym}.json")["values"])
                self.assertNotIn("#daedalon:plinth", load_json(tags / f"{synonym}.json")["values"])
        for family in GENERATOR.ALL_FAMILIES:
            if family not in GENERATOR.METAL_DECOR_FAMILIES:
                self.assertFalse(any(block_id.startswith("silver_") for block_id in GENERATOR.family_block_ids(family)), family)

    def test_native_metals_match_the_erydon_gloss_sources(self) -> None:
        # Exact bytes copied from ERYDON; tests remain standalone in clean checkouts.
        expected = {
            "bronze.png": ("2d032e11f5d62ed4bb391f29b51c6f0d44d926eebf3e02f17bddd53d22c6c89e", (229, 160, 29, 255)),
            "bronze_s.png": ("ec7ab9035d3184e2119dc1c90c6df4b5b0930844bca744042e236ce8f692bdb8", (255, 255, 0, 255)),
            "silver.png": ("45558c4ddc67c752d825c811af1427b801778f75c57529a299ca1023e666e8f4", (206, 206, 206, 255)),
            "silver_s.png": ("c14c09fd53aa64d6a47323f79a760f7f97eec337f1da155b87a6acc480568cfe", (255, 255, 0, 255)),
        }
        for name, (digest, pixel) in expected.items():
            path = DAEDALON_ASSETS / f"textures/block/{name}"
            self.assertEqual(digest, sha256(path), name)
            with Image.open(path) as image:
                self.assertEqual((16, 16), image.size)
                self.assertEqual({pixel}, set(image.convert("RGBA").getdata()), name)


if __name__ == "__main__":
    unittest.main()
