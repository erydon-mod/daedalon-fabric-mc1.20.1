from __future__ import annotations

from collections import Counter
import json
from pathlib import Path
import re
import unittest

from daedalon_test_support import (
    DAEDALON_ASSETS, DAEDALON_DATA, GENERATOR, REPO_ROOT, all_block_ids, load_json,
)


class OracleCatalogueIntegrationTests(unittest.TestCase):
    def test_glaux_is_a_single_bronze_character_not_a_material_family(self):
        data = load_json(REPO_ROOT / "config/glaux-catalogue.json")
        self.assertEqual(("glaux",), GENERATOR.ADDITIONAL_BLOCK_IDS)
        self.assertEqual(1, all_block_ids().count("glaux"))
        self.assertEqual("bronze", data["material"])
        self.assertEqual(2, data["height_metres"])
        self.assertFalse(any("glaux" in name and name != "glaux" for name in all_block_ids()))
        for kind in ("blocks", "items"):
            root = DAEDALON_DATA / "tags" / kind
            self.assertIn("daedalon:glaux", load_json(root / "bronze.json")["values"])
            self.assertNotIn("daedalon:glaux", load_json(root / "silver.json")["values"])
            self.assertNotIn("daedalon:glaux", load_json(root / "stone.json")["values"])
            self.assertNotIn("daedalon:glaux", load_json(root / "aged.json")["values"])

    def test_generated_language_entries_have_explicit_matching_arguments(self):
        data = load_json(REPO_ROOT / "config/glaux-catalogue.json")
        english = data["translations"]["en_us"]
        arguments = re.compile(r"%(?:\d+\$)?[a-zA-Z%]")
        for locale, entries in data["translations"].items():
            self.assertEqual(english.keys(), entries.keys(), locale)
            for key, value in entries.items():
                self.assertTrue(value.strip(), (locale, key))
                self.assertEqual(Counter(arguments.findall(english[key])),
                                 Counter(arguments.findall(value)), (locale, key))
            if locale in ("en_us", "de_de", "es_es"):
                actual = load_json(DAEDALON_ASSETS / "lang" / f"{locale}.json")
                for key, value in entries.items():
                    self.assertEqual(value, actual[key], (locale, key))

    def test_character_is_discoverable_without_fictitious_material_variants(self):
        vocabulary = load_json(DAEDALON_ASSETS / "search_vocabulary.json")
        self.assertTrue({"glaux", "owl", "oracle", "bronze"} <=
                        set(vocabulary["families"]["glaux"]))
        self.assertNotIn("glaux", GENERATOR.ALL_FAMILIES)

    def test_glaux_entrypoints_and_input_mixins_are_side_separated(self):
        metadata = load_json(REPO_ROOT / "src/main/resources/fabric.mod.json")
        self.assertIn("com.oliver.daedalon.glaux.GlauxMod", metadata["entrypoints"]["main"])
        self.assertIn("com.oliver.daedalon.client.glaux.GlauxClient", metadata["entrypoints"]["client"])
        self.assertIn({"config": "daedalon-glaux.mixins.json", "environment": "client"}, metadata["mixins"])


if __name__ == "__main__":
    unittest.main()
