"""Glaux UI translations shared with the existing catalogue generator."""
from __future__ import annotations

import json
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


def register_optional_locales(metadata: dict) -> None:
    """Extend the optional catalogue translator without changing its source."""
    if not (ROOT / "tools/daedalon_localization.py").is_file():
        return
    try:
        from tools import daedalon_localization as localization
    except ModuleNotFoundError:
        import daedalon_localization as localization
    translations = metadata["translations"]
    if not all(locale in translations for locale in ("en_us", "fr_fr", "ja_jp")):
        raise ValueError("Glaux needs explicit English, French and Japanese translations")
    english = translations["en_us"]
    for key, value in english.items():
        pair = tuple(translations[locale][key] for locale in ("fr_fr", "ja_jp"))
        if key.startswith("search.daedalon.tag."):
            localization.SEARCH_WORDS[key.removeprefix("search.daedalon.tag.")] = pair
        else:
            localization.PHRASES[value] = pair


def load_metadata() -> dict:
    metadata = json.loads((ROOT / "config/glaux-catalogue.json").read_text(encoding="utf-8"))
    register_optional_locales(metadata)
    return metadata
