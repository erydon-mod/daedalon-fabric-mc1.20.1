from __future__ import annotations

from collections import Counter
import json
import re
import unittest

from daedalon_test_support import DAEDALON_ASSETS
from tools.daedalon_localization import translate_languages


class LocalizationSafetyTests(unittest.TestCase):
    def test_all_five_locales_have_unique_keys_and_preserve_runtime_arguments(self):
        languages = {}
        for locale in ("en_us", "de_de", "es_es", "fr_fr", "ja_jp"):
            data = (DAEDALON_ASSETS / f"lang/{locale}.json").read_bytes()
            self.assertFalse(data.startswith(b"\xef\xbb\xbf"), locale)
            pairs = json.loads(data.decode("utf-8"), object_pairs_hook=list)
            self.assertEqual(len(pairs), len(dict(pairs)), f"duplicate key in {locale}")
            languages[locale] = dict(pairs)
        english = languages["en_us"]
        # Includes indexed Java printf arguments and Minecraft formatting codes.
        arguments = re.compile(r"%(?:\d+\$)?[-#+ 0,(]*\d*(?:\.\d+)?[a-zA-Z%]|§[0-9a-fk-or]")
        for locale, entries in languages.items():
            self.assertEqual(english.keys(), entries.keys(), locale)
            for key, value in entries.items():
                with self.subTest(locale=locale, key=key):
                    self.assertIsInstance(value, str)
                    self.assertTrue(value.strip())
                    self.assertEqual(Counter(arguments.findall(english[key])), Counter(arguments.findall(value)))
                    self.assertEqual(english[key].count("\n"), value.count("\n"))

    def test_new_untranslated_prose_is_rejected_instead_of_published_as_english(self):
        with self.assertRaisesRegex(ValueError, "Untranslated Daedalon UI entry"):
            translate_languages({"tooltip.daedalon.future_control": "New controls need translations."})


if __name__ == "__main__":
    unittest.main()
