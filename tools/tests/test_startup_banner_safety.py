from __future__ import annotations

import unittest

from daedalon_test_support import JAVA_ROOT, RESOURCES


class StartupBannerSafetyTests(unittest.TestCase):
    def test_daedalon_banner_is_packaged_and_logged_before_registration(self) -> None:
        initializer = (JAVA_ROOT / "Daedalon.java").read_text(encoding="utf-8")
        banner_path = RESOURCES / "daedalon_text_logo.txt"
        self.assertTrue(banner_path.is_file())
        lines = banner_path.read_text(encoding="utf-8").splitlines()
        self.assertEqual(7, len(lines))
        self.assertTrue(all(line.strip() for line in lines))
        self.assertIn('getResourceAsStream("/daedalon_text_logo.txt")', initializer)
        self.assertIn("STARTUP_TEXT_LOGO.split(\"\\\\R\", -1)", initializer)
        self.assertLess(
            initializer.index("logStartupTextLogo();"),
            initializer.index("ModBlocks.register();"),
        )


if __name__ == "__main__":
    unittest.main()
