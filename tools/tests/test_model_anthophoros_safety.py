from __future__ import annotations

import unittest

from daedalon_test_support import (
    DAEDALON_ASSETS, DAEDALON_DATA, GENERATOR, JAVA_ROOT, MESH_ROOT,
    MESH_EXPECTATIONS, REPO_ROOT, load_json, parse_obj, sha256,
)


class AnthophorosSafetyTests(unittest.TestCase):
    def test_blender_triangle_budget_preserves_dimensions_and_reference_shading(self):
        evidence = load_json(REPO_ROOT/'docs/evidence/anthophoros-source.json')
        self.assertFalse(evidence['source_files_modified'])
        self.assertEqual('ba080eb654e9bd1997dd0c0d9b81da3f540c7b20acb7e2b2db94dd4ac87d1de4', evidence['source_sha256'])
        self.assertEqual('Blender 5.2.0 LTS', evidence['preparation']['tool'])
        self.assertEqual([['L','R'], ['L','O','R'], ['L','O','L','O','R']],
                         [m['panels'] for m in evidence['preparation']['models']])
        for filename, expected in evidence['outputs'].items():
            self.assertEqual(expected, sha256(MESH_ROOT/filename), filename)
        for width in (2,3,4):
            stem = f'anthophoros_{width}m'
            actual = parse_obj(MESH_ROOT/f'{stem}.obj')
            self.assertTrue(14900 <= actual['faces'] <= 15000)
            self.assertEqual(((-width/2, 0.0, -0.594243), (width/2, 1.0, 0.594243)), actual['bounds'])
            self.assertEqual(MESH_EXPECTATIONS[stem]['obj_sha256'], sha256(MESH_ROOT/f'{stem}.obj'))
            definition = load_json(MESH_ROOT/f'{stem}.json')
            self.assertFalse(definition['fit_to_block'])
            self.assertFalse(definition['smooth_normals'])
            self.assertEqual([1.0,1.0,1.0], definition['scale'])
            self.assertEqual([0.5,0.0,0.5], definition['translate'])
            self.assertEqual('axis_stabilized_box', definition['uv_projection'])
            self.assertEqual(4.0, definition['box_projection_span'])
        self.assertLessEqual(parse_obj(MESH_ROOT/'anthophoros_item.obj')['faces'], 2000)
        self.assertEqual(55, evidence['preparation']['geometry_shared_across_materials'])

    def test_all_finishes_names_widths_and_search(self):
        ids = GENERATOR.family_block_ids('anthophoros_planter')
        self.assertEqual(55, len(set(ids)))
        self.assertEqual(['aganite_anthophoros_planter','aganite_aged_anthophoros_planter'], ids[:2])
        self.assertIn('bronze_anthophoros_planter', ids)
        languages = [load_json(DAEDALON_ASSETS/f'lang/{lang}.json') for lang in ('en_us','de_de','es_es')]
        for block_id in ids:
            variants = load_json(DAEDALON_ASSETS/f'blockstates/{block_id}.json')['variants']
            self.assertEqual(12, len(variants))
            for width in (2,3,4):
                for facing in ('north','east','south','west'):
                    expected = f'daedalon:mesh/{block_id}' if width == 3 else f'daedalon:mesh/internal/{block_id}_{width}m'
                    self.assertEqual({'model': expected}, variants[f'width={width},facing={facing}'])
            self.assertEqual('daedalon:block/mesh/anthophoros_display', load_json(DAEDALON_ASSETS/f'models/item/{block_id}.json')['parent'])
            for language in languages:
                self.assertIn('Anthophoros', language[f'block.daedalon.{block_id}'])
        for kind in ('blocks','items'):
            self.assertEqual({f'daedalon:{i}' for i in ids}, set(load_json(DAEDALON_DATA/f'tags/{kind}/anthophoros_planter.json')['values']))
            for synonym in ('anthophoros','planter','flower_box','garden_planter'):
                self.assertEqual(['#daedalon:anthophoros_planter'], load_json(DAEDALON_DATA/f'tags/{kind}/{synonym}.json')['values'])

    def test_hollow_shell_covers_mesh_without_runtime_work(self):
        source = (JAVA_ROOT/'block/AnthophorosBlock.java').read_text()
        self.assertIn('extends BenchBlock', source)
        self.assertIn('private static final VoxelShape[][] SHAPES', source)
        for forbidden in ('BlockEntity', 'scheduledTick(', 'getTicker(', 'getStateForNeighborUpdate('):
            self.assertNotIn(forbidden, source)
        for width in (2,3,4):
            for line in (MESH_ROOT/f'anthophoros_{width}m.obj').read_text().splitlines():
                if line.startswith('v '):
                    x,y,z = map(float, line.split()[1:])
                    self.assertTrue(y <= .26 or abs(z) >= .40 or abs(x) >= width/2-.30, (width,x,y,z))


if __name__ == '__main__':
    unittest.main()
