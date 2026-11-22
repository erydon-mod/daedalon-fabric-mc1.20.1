"""Portable guards for the bounded, standalone settings artwork and font licence."""
from __future__ import annotations

import hashlib
import json
from pathlib import Path
import struct
import unittest

ROOT = Path(__file__).resolve().parents[2]


class ClientSettingsStyleTest(unittest.TestCase):
    def test_shared_assets_keep_the_reviewed_bytes_and_dimensions(self):
        contract = json.loads((ROOT / "config/client-settings-style-assets.json").read_text(encoding="utf-8"))
        self.assertEqual(8, len(contract["assets"]))
        for asset in contract["assets"]:
            path = (ROOT / asset["path"]).resolve()
            self.assertTrue(path.is_relative_to(ROOT))
            data = path.read_bytes()
            self.assertEqual(asset["bytes"], len(data), asset["path"])
            self.assertEqual(asset["sha256"], hashlib.sha256(data).hexdigest(), asset["path"])
            if path.suffix == ".png":
                self.assertEqual(b"\x89PNG\r\n\x1a\n", data[:8])
                self.assertEqual(tuple(asset["size"]), struct.unpack(">II", data[16:24]))
            elif path.name.endswith(".png.mcmeta"):
                self.assertEqual({"blur": True, "clamp": True}, json.loads(data)["texture"])

    def test_font_is_standalone_and_keeps_the_full_copyright_and_licence_notice(self):
        provider = json.loads((ROOT / "src/main/resources/assets/daedalon/font/cinzel.json").read_text(encoding="utf-8"))
        fonts = [entry for entry in provider["providers"] if entry["type"] == "ttf"]
        self.assertEqual(["daedalon:cinzel.ttf"], [entry["file"] for entry in fonts])
        contract = json.loads((ROOT / "config/client-settings-style-assets.json").read_text(encoding="utf-8"))
        license_file = ROOT / contract["font_license"]["path"]
        self.assertTrue(license_file.resolve().is_relative_to((ROOT / "src/main/resources").resolve()),
                        "The full font copyright and licence notice must ship in the mod")
        data = license_file.read_bytes()
        self.assertEqual(contract["font_license"]["sha256"], hashlib.sha256(data).hexdigest())
        notice = data.decode("utf-8")
        self.assertIn("SIL OPEN FONT LICENSE", notice.upper())
        self.assertIn("Cinzel", notice)
        self.assertIn("src/main/resources/assets/daedalon/font/OFL-1.1.txt", (ROOT / "THIRD_PARTY_NOTICES.md").read_text(encoding="utf-8"))


if __name__ == "__main__":
    unittest.main()
