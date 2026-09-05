from __future__ import annotations

import sys
import unittest

from daedalon_test_support import (
    DAEDALON_ASSETS, DAEDALON_DATA, GENERATOR, JAVA_ROOT, MESH_ROOT,
    MESH_EXPECTATIONS, REPO_ROOT, load_json, parse_obj, sha256,
)


class HedraSafetyTests(unittest.TestCase):
    def test_widths_preserve_height_depth_topology_and_rigid_supports(self):
        vertices, topology = [], []
        for width in (2, 3, 4):
            path = MESH_ROOT / f'hedra_{width}m.obj'
            actual = parse_obj(path)
            self.assertEqual((6508, 13012), (actual['vertices'], actual['faces']))
            self.assertEqual(((-width/2, 0.0, -0.215441), (width/2, 0.5, 0.215441)), actual['bounds'])
            self.assertEqual(MESH_EXPECTATIONS[f'hedra_{width}m']['obj_sha256'], sha256(path))
            lines = path.read_text().splitlines()
            vertices.append([tuple(map(float, line.split()[1:])) for line in lines if line.startswith('v ')])
            topology.append([[int(v.split('/')[0]) for v in line.split()[1:]] for line in lines if line.startswith('f ')])
            definition = load_json(MESH_ROOT / f'hedra_{width}m.json')
            self.assertFalse(definition['fit_to_block'])
            self.assertEqual([1.0, 1.0, 1.0], definition['scale'])
            self.assertEqual([0.5, 0.0, 0.5], definition['translate'])
            self.assertEqual('axis_stabilized_box', definition['uv_projection'])
            self.assertEqual(4.0, definition['box_projection_span'])
        self.assertEqual(topology[0], topology[1])
        self.assertEqual(topology[1], topology[2])
        for narrow, middle, wide in zip(*vertices):
            self.assertEqual(narrow[1:], middle[1:])
            self.assertEqual(middle[1:], wide[1:])
            if narrow[1] < 0.35:
                # Every support vertex translates by exactly half the extra
                # seat length: no stretched carvings or widening feet.
                expected = 0.5 if narrow[0] > 0 else -0.5
                # Six-decimal OBJ export can round the translated endpoints
                # in opposite directions by one micrometre.
                self.assertAlmostEqual(expected, middle[0] - narrow[0], delta=2e-6)
                self.assertAlmostEqual(expected, wide[0] - middle[0], delta=2e-6)

    def test_all_materials_states_drops_names_and_shared_search_tags(self):
        ids = GENERATOR.family_block_ids('hedra')
        self.assertEqual(54, len(ids))
        self.assertEqual(['aganite_hedra', 'aganite_aged_hedra'], ids[:2])
        languages = [load_json(DAEDALON_ASSETS / f'lang/{lang}.json') for lang in ('en_us','de_de','es_es')]
        for block_id in ids:
            variants = load_json(DAEDALON_ASSETS / f'blockstates/{block_id}.json')['variants']
            self.assertEqual(12, len(variants))
            for width in (2, 3, 4):
                for facing in ('north','east','south','west'):
                    expected = f'daedalon:mesh/{block_id}' if width == 3 else f'daedalon:mesh/internal/{block_id}_{width}m'
                    self.assertEqual({'model': expected}, variants[f'width={width},facing={facing}'])
            self.assertEqual('daedalon:block/mesh/hedra_display', load_json(DAEDALON_ASSETS / f'models/item/{block_id}.json')['parent'])
            self.assertEqual(f'daedalon:{block_id}', load_json(DAEDALON_DATA / f'loot_tables/blocks/{block_id}.json')['pools'][0]['entries'][0]['name'])
            for language in languages:
                self.assertIn('Hedra', language[f'block.daedalon.{block_id}'])
        for kind in ('blocks', 'items'):
            self.assertEqual({f'daedalon:{block_id}' for block_id in ids}, set(load_json(DAEDALON_DATA / f'tags/{kind}/hedra.json')['values']))
            for synonym in ('bench','seat','seating','furniture'):
                self.assertEqual(['#daedalon:exedra', '#daedalon:hedra'], load_json(DAEDALON_DATA / f'tags/{kind}/{synonym}.json')['values'])

    def test_measured_collision_contains_mesh_and_keeps_space_beneath_seat(self):
        sys.path.insert(0, str(REPO_ROOT/'tools'))
        import generate_hedra_shape as shape
        self.assertEqual(shape.content(), (JAVA_ROOT/'block/HedraShape.java').read_text())
        for width in (2, 3, 4):
            boxes = shape.boxes(width)
            self.assertEqual(5, len(boxes))
            self.assertFalse(any(x0 <= 0 <= x1 and y0 <= 0.2 <= y1 and z0 <= 0 <= z1 for x0,y0,z0,x1,y1,z1 in boxes))
            for line in (MESH_ROOT/f'hedra_{width}m.obj').read_text().splitlines():
                if line.startswith('v '):
                    point = tuple(map(float, line.split()[1:]))
                    self.assertTrue(any(all(box[i]-1e-7 <= point[i] <= box[i+3]+1e-7 for i in range(3)) for box in boxes), point)

    def test_item_budget_preserves_full_world_models_and_approved_source(self):
        actual = parse_obj(MESH_ROOT/'hedra_item.obj')
        self.assertEqual(2000, actual['faces'])
        evidence = load_json(REPO_ROOT/'docs/evidence/hedra-source.json')
        self.assertFalse(evidence['source_files_modified'])
        self.assertFalse(evidence['preparation']['decimation'])
        self.assertEqual(0.5, evidence['preparation']['height_meters'])
        self.assertEqual(54, evidence['preparation']['item_geometry_shared_across_materials'])


if __name__ == '__main__':
    unittest.main()
