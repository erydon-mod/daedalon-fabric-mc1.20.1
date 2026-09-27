import math,unittest
from daedalon_test_support import DAEDALON_ASSETS,DAEDALON_DATA,GENERATOR,MESH_ROOT,MESH_EXPECTATIONS,REPO_ROOT,load_json,parse_obj,sha256

class GothicPanelSafetyTests(unittest.TestCase):
    def test_complete_source_and_one_shared_mesh_are_preserved(self):
        evidence=load_json(REPO_ROOT/'docs/evidence/gothic-panel-source.json')
        self.assertEqual('bc625eb51c7c491a3e5119ccdee0cc28a1f11f51b648c5ce81f2e64ee5884b5c',evidence['source_sha256'])
        self.assertFalse(evidence['source_files_modified'])
        self.assertFalse(evidence['preparation']['decimated'])
        actual=parse_obj(MESH_ROOT/'gothic_panel.obj')
        self.assertEqual(15510,actual['faces'])
        self.assertEqual((0,0,0),actual['bounds'][0])
        self.assertEqual((1,1),actual['bounds'][1][:2])
        self.assertLess(actual['bounds'][1][2],.091)
        self.assertEqual([1,2,3],evidence['preparation']['runtime_sizes_m'])
        for name,digest in evidence['outputs'].items():self.assertEqual(digest,sha256(MESH_ROOT/name))
        self.assertEqual(MESH_EXPECTATIONS['gothic_panel']['obj_sha256'],sha256(MESH_ROOT/'gothic_panel.obj'))
        definition=load_json(MESH_ROOT/'gothic_panel.json')
        self.assertFalse(definition['fit_to_block']);self.assertFalse(definition['smooth_normals'])
        self.assertEqual(6,definition['box_projection_span'])

    def test_wall_and_ceiling_materials_names_search_and_drops(self):
        all_ids=[]
        for family in ('gothic_wall_panel',):
            ids=GENERATOR.family_block_ids(family);all_ids+=ids
            self.assertEqual(54,len(ids))
            for block_id in ids:
                state=load_json(DAEDALON_ASSETS/f'blockstates/{block_id}.json')
                self.assertEqual({'variants':{'':{'model':f'daedalon:mesh/{block_id}'}}},state)
                item=load_json(DAEDALON_ASSETS/f'models/item/{block_id}.json')
                self.assertEqual('daedalon:block/mesh/gothic_panel_display',item['parent'])
                self.assertTrue((DAEDALON_DATA/f'loot_tables/blocks/{block_id}.json').exists())
                for lang in ('en_us','de_de','es_es'):
                    self.assertIn(f'block.daedalon.{block_id}',load_json(DAEDALON_ASSETS/f'lang/{lang}.json'))
            for kind in ('blocks','items'):
                for term in ('panel','gothic','tracery','ceiling','coffer','coffered_ceiling'):
                    self.assertIn('#daedalon:'+family,load_json(DAEDALON_DATA/f'tags/{kind}/{term}.json')['values'])
        self.assertEqual(54,len(set(all_ids)))
        self.assertFalse(list((DAEDALON_ASSETS/"blockstates").glob("*_gothic_coffered_ceiling.json")))

    def test_families_share_the_same_mesh_definition(self):
        loader=(REPO_ROOT/'src/main/java/com/oliver/daedalon/client/model/obj/ObjMeshModelLoadingPlugin.java').read_text()
        self.assertIn('id("models/mesh/gothic_panel.json")',loader)
        self.assertEqual(1,loader.count('id("models/mesh/gothic_panel.json")'))

if __name__=='__main__':unittest.main()
