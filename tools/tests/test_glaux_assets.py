"""Glaux resource and performance contracts independent of the global catalogue."""
import hashlib,json,math,sys,unittest
from collections import Counter,defaultdict
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'tools'))
import generate_glaux_assets as generator

class GlauxAssetsTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.manifest=json.loads((ROOT/'docs/evidence/glaux-source.json').read_text(encoding='utf8'))
    def test_source_provenance_and_only_bronze(self):
        self.assertEqual('664b4644c643206956e3f4bfe4a53bf70a28a1714e46fb8b4b0a55662ab713c1',self.manifest['source']['sourceSha256'])
        self.assertEqual('bronze only',self.manifest['finish']);self.assertEqual(2,self.manifest['heightMetres'])
        self.assertEqual(hashlib.sha256(generator.SOURCE.read_bytes()).hexdigest(),self.manifest['authoringDataSha256'])
    def test_runtime_lod_budgets(self):
        outputs=self.manifest['outputs']
        self.assertLess(outputs['glaux_head_near']['triangles'],20000)
        self.assertLess(outputs['glaux_head_mid']['triangles'],3500)
        self.assertLess(outputs['glaux_head_far']['triangles'],600)
        self.assertLess(outputs['glaux_item']['triangles'],7000)
        self.assertLess(outputs['glaux_head_far']['triangles'],outputs['glaux_head_mid']['triangles'])
    def test_static_body_split_does_not_cross_vertical_chunk_cell(self):
        for name in ['glaux_body_lower','glaux_body_upper']:
            bounds=self.manifest['outputs'][name]['bounds'];self.assertGreaterEqual(bounds[1],-1e-6);self.assertLessEqual(bounds[4],1.000001)
    def test_exact_mesh_outputs_and_shared_bronze_sprite(self):
        for stem,record in self.manifest['outputs'].items():
            obj=generator.ASSETS/'models/mesh'/f'{stem}.obj'
            self.assertNotIn(b'\r',obj.read_bytes(),f'{stem} must match its LF Git/JAR blob bytes')
            self.assertEqual(record['sha256'],hashlib.sha256(obj.read_bytes()).hexdigest())
            definition=json.loads(obj.with_suffix('.json').read_text())
            self.assertEqual({'bronze':'daedalon:block/bronze'},definition['materials']);self.assertFalse(definition['fit_to_block'])
            self.assertFalse(definition['force_uv_projection']);self.assertFalse(definition['repair_degenerate_uvs'])
    def test_one_item_eight_facing_half_states_and_one_drop(self):
        variants=json.loads((generator.ASSETS/'blockstates/glaux.json').read_text())['variants'];self.assertEqual(8,len(variants))
        loot=json.loads((ROOT/'src/main/resources/data/daedalon/loot_tables/blocks/glaux.json').read_text())
        entry=loot['pools'][0]['entries'][0];self.assertEqual('daedalon:glaux',entry['name']);self.assertEqual({'half':'lower'},entry['conditions'][0]['properties'])
    def test_rig_parts_and_neutral_beak_are_finite(self):
        rig=json.loads((generator.ASSETS/'glaux/rig.json').read_text());self.assertEqual(29,len(rig['hinges']))
        for hinge in rig['hinges']:
            self.assertTrue(all(math.isfinite(v) for v in hinge['root']+hinge['axis']))
        near=self.manifest['outputs']['glaux_head_near'];self.assertAlmostEqual(.6,near['bounds'][4],delta=.012)
    def test_authored_uvs_stay_inside_one_atlas_sprite(self):
        for stem in self.manifest['outputs']:
            for line in (generator.ASSETS/'models/mesh'/f'{stem}.obj').read_text().splitlines():
                if line.startswith('vt '):self.assertTrue(all(0<=float(value)<=1 for value in line.split()[1:3]))
    def test_projection_and_normal_seams_do_not_tear_shared_positions(self):
        def corner(p,projection):return (*p,1 if projection==1 else -1,0,0,.4,.6,projection)
        a=(0,0,0);d=(.01,0,0)
        faces=[tuple(corner(p,k) for p in points) for k,points in [
            (1,[a,(.25,0,0),(0,.25,0)]),
            (2,[a,(0,0,.25),(.25,.25,.25)]),
            (1,[d,(-.25,0,0),(0,-.25,0)])]]
        vertices,_=generator.simplify({'sample':faces},.1)['sample']
        shared={tuple(round(c,8) for c in v[:3]) for v in vertices if sum(c*c for c in v[:3])<.01}
        self.assertEqual(1,len(shared),'UV and normal corners must retain identical welded XYZ')
    def test_neck_leaves_are_closed_outward_shells_at_both_lods(self):
        for stem,expected in [('glaux_body_upper',27),('glaux_head_near',29),('glaux_head_mid',29)]:
            points=[];groups=defaultdict(list);name=''
            for line in (generator.ASSETS/'models/mesh'/f'{stem}.obj').read_text().splitlines():
                if line.startswith('v '):points.append(tuple(map(float,line.split()[1:4])))
                elif line.startswith('o '):name=line[2:]
                elif line.startswith('f ') and 'feather_' in name:
                    groups[name].append(tuple(points[int(v.split('/')[0])-1] for v in line.split()[1:]))
            self.assertEqual(expected,len(groups),stem)
            for name,faces in groups.items():
                directed=Counter((a,b) for f in faces for a,b in zip(f,f[1:]+f[:1]))
                self.assertTrue(all(count==1 and directed[(b,a)]==1 for (a,b),count in directed.items()),(stem,name,'open or inward leaf edge'))
                volume=sum(a[0]*(b[1]*c[2]-b[2]*c[1])+a[1]*(b[2]*c[0]-b[0]*c[2])+a[2]*(b[0]*c[1]-b[1]*c[0]) for a,b,c in faces)/6
                self.assertGreater(volume,1e-9,(stem,name,'leaf thickness was collapsed'))
    def test_body_seam_keeps_same_phase_through_all_four_facings(self):
        def seam(stem,y):
            lines=(generator.ASSETS/'models/mesh'/f'{stem}.obj').read_text().splitlines();p=[list(map(float,l.split()[1:4])) for l in lines if l.startswith('v ')];uv=[list(map(float,l.split()[1:3])) for l in lines if l.startswith('vt ')]
            return[(v[0],v[2],*uv[i]) for i,v in enumerate(p) if abs(v[1]-y)<1e-7]
        lower=seam('glaux_body_lower',1);upper=seam('glaux_body_upper',0);self.assertTrue(lower)
        for turns in range(4):
            def rotated(values):
                out=set()
                for x,z,u,v in values:
                    for _ in range(turns):x,z=z,1-x
                    out.add((round(x,7),round(z,7),round(u,8),round(v,8)))
                return out
            self.assertEqual(rotated(lower),rotated(upper),turns)

if __name__=='__main__':unittest.main()
