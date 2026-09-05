from __future__ import annotations
import unittest,sys
from daedalon_test_support import (MESH_ROOT,MESH_EXPECTATIONS,DAEDALON_ASSETS,DAEDALON_DATA,REPO_ROOT,GENERATOR,load_json,parse_obj,sha256)

class MonopterosSafetyTests(unittest.TestCase):
    def test_all_diameters_have_flat_whole_block_crowns_and_proportional_footprints(self):
        baseline=None
        for diameter,height in ((4,3),(6,4),(8,5)):
            name=f'monopteros_{diameter}m';path=MESH_ROOT/(name+'.obj');actual=parse_obj(path)
            self.assertLess(actual['faces'],15386)
            self.assertEqual(MESH_EXPECTATIONS[name]['faces'],actual['faces'])
            self.assertEqual(MESH_EXPECTATIONS[name]['obj_sha256'],sha256(path))
            lo,hi=actual['bounds'];self.assertAlmostEqual(diameter,hi[0]-lo[0],places=5)
            self.assertAlmostEqual(0,lo[1],places=6)
            self.assertAlmostEqual(height,hi[1]-lo[1],places=6)
            lines=path.read_text().splitlines()
            vertices=[tuple(float(v)/(height if i==1 else diameter) for i,v in enumerate(line.split()[1:])) for line in lines if line.startswith('v ')]
            top_faces=[face for face in lines if face.startswith('f ') and all(abs(vertices[int(v.split('/')[0])-1][1]-1)<1e-6 for v in face.split()[1:])]
            self.assertTrue(top_faces, 'Crown must have a sealed flat placement surface')
            topology=[[int(v.split('/')[0]) for v in line.split()[1:]] for line in lines if line.startswith('f ')]
            if baseline:
                self.assertEqual(baseline[1],topology)
                for a,b in zip(baseline[0],vertices):
                    for x,y in zip(a,b):self.assertAlmostEqual(x,y,delta=4e-7)
            baseline=(vertices,topology)
            definition=load_json(MESH_ROOT/(name+'.json'))
            self.assertFalse(definition['fit_to_block']);self.assertEqual([.5,0,.5],definition['translate'])
            self.assertEqual(8,definition['box_projection_span'])
        self.assertLessEqual(parse_obj(MESH_ROOT/'monopteros_item.obj')['faces'],2040)

    def test_stone_aged_and_bronze_resources(self):
        ids=GENERATOR.family_block_ids('monopteros_dome');self.assertEqual(55,len(ids))
        self.assertIn('bronze_monopteros_dome',ids);self.assertIn('aganite_aged_monopteros_dome',ids)
        for block_id in ids:
            variants=load_json(DAEDALON_ASSETS/f'blockstates/{block_id}.json')['variants']
            self.assertEqual({'diameter=4','diameter=6','diameter=8'},set(variants))
            for d in (4,6,8):
                target=f'daedalon:mesh/{block_id}' if d==6 else f'daedalon:mesh/internal/{block_id}_{d}m'
                self.assertEqual({'model':target},variants[f'diameter={d}'])
            self.assertEqual('daedalon:block/mesh/monopteros_display',load_json(DAEDALON_ASSETS/f'models/item/{block_id}.json')['parent'])
            self.assertEqual(f'daedalon:{block_id}',load_json(DAEDALON_DATA/f'loot_tables/blocks/{block_id}.json')['pools'][0]['entries'][0]['name'])
            for lang in ('en_us','de_de','es_es'):
                self.assertIn(f'block.daedalon.{block_id}',load_json(DAEDALON_ASSETS/f'lang/{lang}.json'))
        for kind in ('items','blocks'):
            for synonym in ('pavilion','pavillion','dome','roof','rotunda','cupola'):
                self.assertIn('#daedalon:monopteros_dome',load_json(DAEDALON_DATA/f'tags/{kind}/{synonym}.json')['values'])
        self.assertFalse((DAEDALON_ASSETS/'models/item/monopteros_part.json').exists())

    def test_measured_hollow_shell_and_source_evidence(self):
        sys.path.insert(0,str(REPO_ROOT/'tools'));import generate_monopteros_shape as shape
        self.assertEqual(shape.content(),shape.TARGET.read_text())
        height,rings=shape.profile();self.assertGreater(rings[0][0],.3)
        self.assertLess(rings[0][0],rings[0][1]);self.assertAlmostEqual(.625,height)
        evidence=load_json(REPO_ROOT/'docs/evidence/monopteros-source.json')
        self.assertFalse(evidence['source_files_modified']);self.assertFalse(evidence['preparation']['decimation'])
        self.assertEqual('6183f64cee6b67a59ff972bcac32c579827363d20f663f8f29fbfa5759932948',evidence['source_sha256'])

if __name__=='__main__':unittest.main()
