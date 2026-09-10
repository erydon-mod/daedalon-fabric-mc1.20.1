"""Check actual plinth mesh extents against the material repeat layout."""
import json
import unittest

from daedalon_test_support import MESH_ROOT
from test_model_uv_scale_safety import transformed_largest_span


class PlinthUvDensityTests(unittest.TestCase):
    def test_one_material_tile_per_block_at_every_size(self):
        definitions = sorted(MESH_ROOT.glob('plinth_*.json'))
        self.assertEqual(5, len(definitions))
        for path in definitions:
            definition = json.loads(path.read_text())
            baked_span = transformed_largest_span(definition, path)
            projection_span = max(baked_span, definition.get('box_projection_span', 0))
            self.assertEqual('axis_stabilized_box', definition['uv_projection'])
            for scale in (0.25, 0.5, 1.0):
                world_span = baked_span * scale
                # Detailed surfaces contain six one-block tiles on each source axis.
                material_tiles = baked_span / projection_span * scale * 6
                self.assertAlmostEqual(world_span, material_tiles, places=6,
                                       msg=f'{path.stem}, scale={scale}: material density must match block dimensions')
                # Cover both UV handednesses, size centring and all world phases.
                for source_uv in (0, baked_span / projection_span, 1 - baked_span / projection_span, 1):
                    uv = 0.5 + (source_uv - 0.5) * scale
                    for phase in range(6):
                        self.assertTrue(0 <= uv * 6 / 12 + phase / 12 <= 1)
                    self.assertTrue(0 <= uv <= 1)


if __name__ == '__main__':
    unittest.main()
