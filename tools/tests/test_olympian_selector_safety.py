from __future__ import annotations

import unittest

from daedalon_test_support import DAEDALON_ASSETS, DAEDALON_DATA, load_json


class OlympianSelectorSafetyTests(unittest.TestCase):
    def test_every_existing_zeus_finish_has_one_item_only_selector(self) -> None:
        zeus = {path.stem for path in (DAEDALON_ASSETS / "blockstates").glob("*_zeus_statue.json")}
        expected = {name.removesuffix("_zeus_statue") + "_olympian_god_statue" for name in zeus}
        self.assertEqual(56, len(expected))
        actual = {path.stem for path in (DAEDALON_ASSETS / "models/item").glob("*_olympian_god_statue.json")}
        self.assertEqual(expected, actual)
        languages = {locale: load_json(DAEDALON_ASSETS / f"lang/{locale}.json")
                     for locale in ("en_us", "de_de", "es_es")}
        for name in sorted(expected):
            with self.subTest(item=name):
                self.assertEqual({"parent": "daedalon:item/olympian_god"},
                                 load_json(DAEDALON_ASSETS / f"models/item/{name}.json"))
                self.assertFalse((DAEDALON_ASSETS / f"blockstates/{name}.json").exists())
                self.assertFalse((DAEDALON_DATA / f"loot_tables/blocks/{name}.json").exists())
                for locale, lang in languages.items():
                    self.assertIn(f"item.daedalon.{name}", lang)
                    self.assertNotIn(f"block.daedalon.{name}", lang)
        tag = load_json(DAEDALON_DATA / "tags/items/olympian_god_statue.json")
        self.assertEqual({f"daedalon:{name}" for name in expected}, set(tag["values"]))
        self.assertIs(tag["replace"], False)
        self.assertFalse((DAEDALON_DATA / "tags/blocks/olympian_god_statue.json").exists())

    def test_creative_image_uses_the_shared_item_only_medallion(self) -> None:
        model = load_json(DAEDALON_ASSETS / "models/item/olympian_god.json")
        self.assertEqual({"parent": "daedalon:item/olympian_god_display"}, model)
        self.assertTrue((DAEDALON_ASSETS / "models/mesh/olympian_god_medallion.obj").is_file())

    def test_selector_locales_and_family_terms_do_not_depend_on_block_ids(self) -> None:
        vocabulary = load_json(DAEDALON_ASSETS / "search_vocabulary.json")
        terms = set(vocabulary["families"]["olympian_god_statue"])
        self.assertTrue({"olympian", "god", "deity", "statue", "sculpture", "cycle", "sequence"} <= terms)
        self.assertFalse({"daedalon_blocks", "daedalon_items", "zeus", "hestia"} & terms)
        for locale in ("en_us", "de_de", "es_es"):
            lang = load_json(DAEDALON_ASSETS / f"lang/{locale}.json")
            for key in ("catalogue.daedalon.design.olympian_god_statue",
                        "tooltip.daedalon.olympian_god.cycle", "tooltip.daedalon.olympian_god.next",
                        "message.daedalon.obelisk_size_blocked"):
                self.assertTrue(lang[key].strip(), (locale, key))
            self.assertIn("%s", lang["tooltip.daedalon.olympian_god.next"])

    def test_large_obelisk_helper_is_empty_and_has_no_item_or_loot(self) -> None:
        state = load_json(DAEDALON_ASSETS / "blockstates/obeliskos_part.json")
        self.assertEqual({"variants": {"": {"model": "daedalon:block/internal/obeliskos_part"}}}, state)
        self.assertEqual({"elements": []}, load_json(DAEDALON_ASSETS / "models/block/internal/obeliskos_part.json"))
        self.assertFalse((DAEDALON_ASSETS / "models/item/obeliskos_part.json").exists())
        self.assertFalse((DAEDALON_DATA / "loot_tables/blocks/obeliskos_part.json").exists())
