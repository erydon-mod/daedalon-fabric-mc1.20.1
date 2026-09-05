from __future__ import annotations

import importlib.util
import math
import unittest

from daedalon_test_support import (
    DAEDALON_ASSETS, DAEDALON_DATA, GENERATOR, JAVA_ROOT, MESH_ROOT,
    MESH_EXPECTATIONS, REPO_ROOT, load_json, parse_obj, sha256,
)


class ExedraSafetyTests(unittest.TestCase):
    def test_widths_preserve_topology_height_depth_and_ground(self):
        vertices, topology = [], []
        for width in (2, 3, 4):
            path = MESH_ROOT / f'exedra_{width}m.obj'
            actual = parse_obj(path)
            self.assertEqual(7423, actual['vertices'])
            self.assertEqual(14842, actual['faces'])
            self.assertEqual(((-width / 2, 0.0, -0.5145), (width / 2, 1.0, 0.5145)), actual['bounds'])
            self.assertEqual(MESH_EXPECTATIONS[f'exedra_{width}m']['obj_sha256'], sha256(path))
            lines = path.read_text(encoding='utf-8').splitlines()
            vertices.append([tuple(map(float, line.split()[1:])) for line in lines if line.startswith('v ')])
            topology.append([[int(v.split('/')[0]) for v in line.split()[1:]] for line in lines if line.startswith('f ')])
            for line in lines:
                if line.startswith('vn '):
                    # Blender's OBJ normals are rounded to four decimal places.
                    self.assertAlmostEqual(1.0, math.sqrt(sum(float(v)**2 for v in line.split()[1:])), delta=1.0e-4)
            definition = load_json(MESH_ROOT / f'exedra_{width}m.json')
            self.assertFalse(definition['fit_to_block'])
            self.assertEqual([1.0, 1.0, 1.0], definition['scale'])
            self.assertEqual([0.5, 0.0, 0.5], definition['translate'])
            self.assertEqual('axis_stabilized_box', definition['uv_projection'])
            self.assertEqual(4.0, definition['box_projection_span'])
            self.assertTrue(definition['force_uv_projection'])
        self.assertEqual(topology[0], topology[1])
        self.assertEqual(topology[1], topology[2])
        for narrow, middle, wide in zip(*vertices):
            self.assertEqual(narrow[1:], middle[1:])
            self.assertEqual(middle[1:], wide[1:])
            self.assertAlmostEqual(narrow[0] / 2, middle[0] / 3, places=6)
            self.assertAlmostEqual(wide[0] / 4, middle[0] / 3, places=6)

    def test_materials_states_items_drops_names_and_search_are_complete(self):
        ids = GENERATOR.family_block_ids('exedra')
        self.assertEqual(54, len(ids))
        self.assertEqual('aganite_exedra', ids[0])
        self.assertEqual('aganite_aged_exedra', ids[1])
        languages = [load_json(DAEDALON_ASSETS / f'lang/{lang}.json') for lang in ('en_us','de_de','es_es')]
        for block_id in ids:
            variants = load_json(DAEDALON_ASSETS / f'blockstates/{block_id}.json')['variants']
            self.assertEqual(12, len(variants))
            for width in (2,3,4):
                for facing in ('north','east','south','west'):
                    expected = f'daedalon:mesh/{block_id}' if width == 3 else f'daedalon:mesh/internal/{block_id}_{width}m'
                    self.assertEqual({'model': expected}, variants[f'width={width},facing={facing}'])
            self.assertEqual('daedalon:block/mesh/exedra_display', load_json(DAEDALON_ASSETS / f'models/item/{block_id}.json')['parent'])
            loot = load_json(DAEDALON_DATA / f'loot_tables/blocks/{block_id}.json')
            self.assertEqual(f'daedalon:{block_id}', loot['pools'][0]['entries'][0]['name'])
            for language in languages:
                self.assertIn('Exedra', language[f'block.daedalon.{block_id}'])
        for kind in ('blocks','items'):
            self.assertEqual({f'daedalon:{block_id}' for block_id in ids}, set(load_json(DAEDALON_DATA / f'tags/{kind}/exedra.json')['values']))
            for synonym in ('bench','seat','seating','furniture','curved_bench'):
                self.assertEqual(['#daedalon:exedra'], load_json(DAEDALON_DATA / f'tags/{kind}/{synonym}.json')['values'])

    def test_curved_collision_profile_is_reproducible_and_keeps_front_open(self):
        path = REPO_ROOT / 'tools/generate_exedra_shape.py'
        spec = importlib.util.spec_from_file_location('exedra_shape',path)
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        self.assertEqual(module.content(), (JAVA_ROOT / 'block/ExedraShape.java').read_text(encoding='utf-8'))
        boxes = module.boxes()
        self.assertEqual(36, len(boxes))
        self.assertFalse(any(x0 <= 0 <= x1 and z0 <= 0.4 <= z1 for x0,y0,z0,x1,y1,z1 in boxes))

    def test_prepared_widths_use_shared_geometry_without_tick_or_controller(self):
        block = (JAVA_ROOT / 'block/ExedraBlock.java').read_text(encoding='utf-8')
        plugin = (JAVA_ROOT / 'client/model/obj/ObjMeshModelLoadingPlugin.java').read_text(encoding='utf-8')
        self.assertIn('IntProperty.of("width", 2, 4)', block)
        self.assertIn('DEFAULT_WIDTH = 3', block)
        self.assertIn('builder.add(WIDTH, FACING)', block)
        for forbidden in ('BlockWithEntity','BlockEntityProvider','scheduledTick','randomTick','getTicker'):
            self.assertNotIn(forbidden, block)
        self.assertIn('createExedraVariants(width)', plugin)
        self.assertIn('width == ExedraBlock.DEFAULT_WIDTH', plugin)
        evidence = load_json(REPO_ROOT / 'docs/evidence/exedra-source.json')
        self.assertFalse(evidence['source_files_modified'])
        self.assertFalse(evidence['preparation']['decimation'])


if __name__ == '__main__':
    unittest.main()
