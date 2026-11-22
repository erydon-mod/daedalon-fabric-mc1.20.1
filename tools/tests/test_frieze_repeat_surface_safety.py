from __future__ import annotations

import sys
import tempfile
import unittest
import io
import json
from pathlib import Path
from zipfile import ZipFile

from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from audit_frieze_repeat_surfaces import NATIVE_ROOT, audit_ctm_tiles, audit_pack, audit_root, surface_proof


class FriezeRepeatSurfaceSafetyTests(unittest.TestCase):
    def test_native_albedo_and_every_present_pbr_map_preserve_all_channels(self):
        proof = audit_root(NATIVE_ROOT)
        self.assertEqual(58, proof["role_counts"]["albedo"])
        self.assertGreater(proof["role_counts"]["normal"], 0)
        self.assertGreater(proof["role_counts"]["specular"], 0)
        solids = [record for record in proof["surfaces"] if record["pixel_equivalence"] == "uniform"]
        self.assertTrue(solids)
        self.assertTrue(all(record["dimensions"] == [16, 16] for record in solids))

    def test_proof_rejects_different_rgb_even_when_both_halves_have_identical_alpha(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "repeat.png"
            image = Image.new("RGBA", (4, 2), (128, 128, 255, 0))
            image.putpixel((2, 0), (127, 128, 255, 0))
            image.save(path)
            with self.assertRaisesRegex(ValueError, "would change pixels"):
                surface_proof(path)

    def test_proof_rejects_a_changed_normal_or_specular_alpha_channel(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "repeat_n.png"
            image = Image.new("RGBA", (4, 2), (128, 128, 255, 255))
            image.putpixel((2, 0), (128, 128, 255, 254))
            image.save(path)
            with self.assertRaisesRegex(ValueError, "would change pixels"):
                surface_proof(path)

    def test_pack_aliases_and_native_fallback_are_both_checked(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            payload = io.BytesIO()
            Image.new("RGBA", (4, 2), (128, 128, 255, 171)).save(payload, format="PNG")
            archive_path = root / "collection.zip"
            for finish in ("white", "black", "bronze", "silver"):
                Image.new("RGBA", (2, 2), (206, 206, 206, 255)).save(root / (finish + ".png"))
            with ZipFile(archive_path, "w") as archive:
                archive.writestr("assets/daedalon/texture_aliases/v1.json", json.dumps({"aliases": [{
                    "path": "textures/block/statue_spartan_promachos_probe_n.png",
                    "target": "assets/daedalon/texture_blobs/probe.png"}]}))
                archive.writestr("assets/daedalon/texture_blobs/probe.png", payload.getvalue())
            proof = audit_pack(archive_path, root)
            self.assertEqual({"albedo": 4, "normal": 1, "specular": 0}, proof["role_counts"])
            self.assertEqual(1, sum(row["provider"] == "pack_alias" for row in proof["surfaces"]))
            self.assertEqual(4, sum(row["provider"] == "native_fallback" for row in proof["surfaces"]))

    def test_ctm_proof_checks_tile_order_rgba_and_resolution(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            sheet_root, ctm_root = root / "sheets", root / "ctm"
            sheet_root.mkdir()
            material_root = ctm_root / "probe"
            material_root.mkdir(parents=True)
            sheet = Image.new("RGBA", (24, 12))
            for index in range(36):
                tile = Image.new("RGBA", (2, 2), (index, index + 1, 200, 255 - index))
                tile.save(material_root / f"{index}.png")
                for period in (0, 12):
                    sheet.paste(tile, (index % 6 * 2 + period, index // 6 * 2))
            sheet.save(sheet_root / "statue_spartan_promachos_probe.png")
            self.assertEqual(36, audit_ctm_tiles(sheet_root, ctm_root)["tile_comparisons"])
            changed = Image.open(material_root / "17.png").convert("RGBA")
            changed.putpixel((1, 1), (17, 18, 200, 0))
            changed.save(material_root / "17.png")
            with self.assertRaisesRegex(ValueError, "differs from ERYDON"):
                audit_ctm_tiles(sheet_root, ctm_root)


if __name__ == "__main__":
    unittest.main()
