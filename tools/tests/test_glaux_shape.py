"""Keep the cached collision silhouette in step with the authored Glaux mesh."""
import sys
import unittest
from pathlib import Path

TOOLS = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(TOOLS))
import generate_glaux_shape as generator


class GlauxShapeSafetyTest(unittest.TestCase):
    def test_cached_silhouette_matches_authored_source(self):
        expected = generator.render(generator.measure())
        self.assertEqual(expected, generator.TARGET.read_text(encoding='utf-8'))


if __name__ == '__main__':
    unittest.main()
