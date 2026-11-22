from __future__ import annotations

import unittest

from PIL import Image, ImageStat

from daedalon_test_support import DAEDALON_ASSETS
from tools.prepare_planter_earth import SIZES, normal_height


class PlanterEarthArtTests(unittest.TestCase):
    def test_literal_64_pixel_tiles_have_rough_matte_companions(self):
        albedos = []
        for width, size in SIZES.items():
            with self.subTest(width=width):
                root = DAEDALON_ASSETS / "textures/block"
                stem = f"anthophoros_earth_{width}m"
                with Image.open(root / f"{stem}.png") as albedo:
                    self.assertEqual("RGB", albedo.mode)
                    self.assertEqual((64, 64), size)
                    self.assertEqual((64, 64), albedo.size)
                    self.assertTrue(all(axis % 16 == 0 for axis in size))
                    albedos.append(albedo.tobytes())
                    luminance = ImageStat.Stat(albedo.convert("L"))
                    colours = ImageStat.Stat(albedo).mean
                    self.assertLess(luminance.mean[0], 40,
                                    "Peat should remain dark, with readable organic colour variation")
                    # Contrast is relative to brightness: dark peat should not
                    # need bright clay/mineral pixels to satisfy a variation test.
                    self.assertGreater(luminance.stddev[0] / max(1, luminance.mean[0]), .20,
                                       "Organic colour variation must remain visible in the dark tile")
                    self.assertLessEqual(colours[0]-colours[1], 18, "Avoid a dominant orange/red cast")
                    self.assertLessEqual(colours[1]-colours[2], 14, "Keep the peat palette cool and organic")
                with Image.open(root / f"{stem}_n.png") as normal:
                    self.assertEqual("RGBA", normal.mode)
                    self.assertEqual(size, normal.size)
                    r, g, b, a = normal.split()
                    for channel in (r, g):
                        low, high = channel.getextrema()
                        self.assertGreaterEqual(low, 75)
                        self.assertLessEqual(high, 180)
                    self.assertEqual((255, 255), b.getextrema(), "No baked directional AO")
                    self.assertGreaterEqual(a.getextrema()[0], 237, "Peat relief must be lower than the rocky iteration")
                    self.assertLessEqual(a.getextrema()[1], 255)
                    self.assertGreaterEqual(a.getextrema()[1]-a.getextrema()[0], 16,
                                            "The actual64x64 height field must retain rough organic detail")
                with Image.open(root / f"{stem}_s.png") as specular:
                    self.assertEqual("RGBA", specular.mode)
                    self.assertEqual(size, specular.size)
                    self.assertEqual(((0, 0), (10, 10), (64, 64), (255, 255)), specular.getextrema())
        self.assertEqual(3, len(set(albedos)), "Each width has its own ImageGen earth art")

    def test_normal_derivatives_repeat_across_the_tile_boundary(self):
        # A feature touching the far edge must affect the first normal pixel.
        # This catches clamped derivatives without relying on a particular art file.
        height_source = Image.new("RGB", (64, 64), (80,80,80))
        for y in range(64):
            height_source.putpixel((63,y), (0,0,0))
        image = normal_height(height_source)
        self.assertNotEqual(image.getpixel((0,32))[0], image.getpixel((32,32))[0])
        self.assertEqual(image.getpixel((0,32))[1], image.getpixel((32,32))[1])


if __name__ == "__main__":
    unittest.main()
