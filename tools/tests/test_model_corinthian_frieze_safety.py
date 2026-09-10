from __future__ import annotations

import unittest
import math
from collections import defaultdict

from daedalon_test_support import (
    DAEDALON_ASSETS, DAEDALON_DATA, GENERATOR, MESH_ROOT, MESH_EXPECTATIONS,
    REPO_ROOT, load_json, parse_obj, sha256,
)


class FriezeSafetyChecks:
    sections=3
    max_faces=8500
    decimated=False

    def test_imported_normals_soften_facets_but_keep_cut_caps_flat(self):
        normals=[]; vertices=[]; name=''; softened=0
        for line in (MESH_ROOT/(self.family+'.obj')).read_text().splitlines():
            p=line.split()
            if not p:continue
            if p[0]=='v':vertices.append(tuple(map(float,p[1:4])))
            elif p[0]=='vn':normals.append(tuple(map(float,p[1:4])))
            elif p[0]=='o':name=p[1]
            elif p[0]=='f' and name.startswith(('straight_','cap_left_','cap_right_')):
                refs=[r.split('//') for r in p[1:]]
                points=[vertices[int(r[0])-1] for r in refs]
                ns=[normals[int(r[1])-1] for r in refs]
                if name.startswith('cap_'):
                    expected=(-1,0,0) if name.startswith('cap_left_') else (1,0,0)
                    for n in ns:self.assertEqual(expected,n)
                    continue
                a=[q-r for q,r in zip(points[1],points[0])]; b=[q-r for q,r in zip(points[2],points[0])]
                cross=(a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0])
                area=math.sqrt(sum(v*v for v in cross))
                if area<1e-7:continue
                face=[v/area for v in cross]
                for n in ns:
                    self.assertAlmostEqual(1,sum(v*v for v in n),places=5)
                    dot=sum(v*w for v,w in zip(n,face))
                    self.assertGreater(dot,.84,'Smoothing crossed a sharp edge')
                    softened+=dot<.999
        self.assertGreater(softened,500,'Imported normals still look flat')
    @classmethod
    def setUpClass(cls):
        cls.vertices=[]; cls.parts=defaultdict(list); group=''
        for line in (MESH_ROOT/(cls.family+'.obj')).read_text().splitlines():
            p=line.split()
            if not p: continue
            if p[0]=='v': cls.vertices.append(tuple(map(float,p[1:4])))
            elif p[0]=='o': group=p[1]
            elif p[0]=='f': cls.parts[group].append([cls.vertices[int(v.split('/')[0])-1] for v in p[1:]])

    def test_provenance_hashes_and_all_shared_components(self):
        evidence=load_json(REPO_ROOT/f'docs/evidence/{self.evidence_file}')
        self.assertEqual(self.source_digest,evidence['source_sha256'])
        self.assertFalse(evidence['source_files_modified'])
        self.assertEqual(self.decimated,evidence['preparation']['decimated'])
        for file,digest in evidence['outputs'].items(): self.assertEqual(digest,sha256(MESH_ROOT/file))
        self.assertEqual(MESH_EXPECTATIONS[self.family]['obj_sha256'],sha256(MESH_ROOT/(self.family+'.obj')))
        self.assertEqual(evidence['preparation']['components'],{k:len(v) for k,v in self.parts.items()})
        for name,triangles in self.parts.items():
            for triangle in triangles:
                for x,y,z in triangle:
                    self.assertTrue(-1e-6<=x<=1+1e-6 and -1e-6<=y<=1+1e-6 and -1e-6<=z<=1+1e-6,name)
        for phase in range(self.sections):
            self.assertTrue(self.min_faces<len(self.parts[f'straight_{phase}'])<self.max_faces)

    def test_all_three_straight_joins_share_boundary_profiles(self):
        def edges(part,x):
            result=set()
            for tri in self.parts[part]:
                boundary=[p for p in tri if abs(p[0]-x)<1e-6]
                if len(boundary)==2:
                    result.add(tuple(sorted((round(p[1],6),round(p[2],6)) for p in boundary)))
            return result
        for a,b in ((i,(i+1)%self.sections) for i in range(self.sections)):
            first,second=edges(f'straight_{a}',1),edges(f'straight_{b}',0)
            # Blender float32 clipping can discard sub-0.01mm slivers; compare
            # geometric coverage rather than requiring identical tessellation.
            def distance(p,edge):
                q,r=edge; dx,dy=r[0]-q[0],r[1]-q[1]
                length=dx*dx+dy*dy
                t=max(0,min(1,((p[0]-q[0])*dx+(p[1]-q[1])*dy)/length)) if length else 0
                return ((p[0]-q[0]-t*dx)**2+(p[1]-q[1]-t*dy)**2)**.5
            for source,target in ((first,second),(second,first)):
                self.assertTrue(target)
                for edge in source:
                    for p in (edge[0],edge[1],tuple((u+v)/2 for u,v in zip(*edge))):
                        self.assertLess(min(distance(p,other) for other in target),1e-5,f'Open repeat seam {a}->{b}')

    def test_neutral_ends_and_corner_parts_fit_cached_collision(self):
        for name,tris in self.parts.items():
            for tri in tris:
                for x,y,z in tri:
                    if name.startswith(('straight_', 'cap_left_', 'cap_right_')):
                        self.assertLessEqual(z,self.depth+1e-6)
                    elif name.startswith('inner_left'):
                        self.assertTrue(z<=self.depth+1e-6 or x<=self.depth+1e-6,name)
                    elif name.startswith('inner_right'):
                        self.assertTrue(z<=self.depth+1e-6 or x>=1-self.depth-1e-6,name)
                    elif name.startswith('outer_left'):
                        self.assertTrue(z<=self.depth+1e-6 and x<=self.depth+1e-6,name)
                    elif name.startswith('outer_right'):
                        self.assertTrue(z<=self.depth+1e-6 and x>=1-self.depth-1e-6,name)

    def test_end_closures_follow_each_actual_cut_without_a_projecting_border(self):
        for phase in range(self.sections):
            for side,x in (('left',0),('right',1)):
                segments=[]
                for tri in self.parts[f'straight_{phase}']:
                    points=[p for p in tri if abs(p[0]-x)<2e-6]
                    if len(points)==2 and abs(points[0][1]-points[1][1])>1e-8:
                        segments.append(points)
                caps=self.parts[f'cap_{side}_{phase}']
                self.assertTrue(caps)
                for tri in caps:
                    for p in tri: self.assertAlmostEqual(x,p[0],places=6)
                    # Sample the filled cap's front at each strip midpoint and
                    # compare against the actual model cross-section there.
                    y=(min(p[1] for p in tri)+max(p[1] for p in tri))/2
                    depths=[a[2]+(b[2]-a[2])*(y-a[1])/(b[1]-a[1]) for a,b in segments
                            if min(a[1],b[1])-1e-7<=y<=max(a[1],b[1])+1e-7]
                    cap_depths=[]
                    for a,b in zip(tri,tri[1:]+tri[:1]):
                        if min(a[1],b[1])<=y<=max(a[1],b[1]) and abs(a[1]-b[1])>1e-8:
                            cap_depths.append(a[2]+(b[2]-a[2])*(y-a[1])/(b[1]-a[1]))
                    if depths and cap_depths:
                        self.assertLessEqual(max(cap_depths),max(depths)+2e-5,f'Projecting cap: {phase} {side}')

    def test_materials_languages_search_and_one_block_item(self):
        ids=GENERATOR.family_block_ids(self.family)
        self.assertEqual(54,len(set(ids)))
        self.assertIn('aganite_aged_'+self.family,ids)
        for lang in ('en_us','de_de','es_es'):
            values=load_json(DAEDALON_ASSETS/f'lang/{lang}.json')
            for block_id in ids: self.assertIn(f'block.daedalon.{block_id}',values)
        for block_id in ids:
            self.assertEqual({'variants':{'':{'model':f'daedalon:mesh/{block_id}'}}},load_json(DAEDALON_ASSETS/f'blockstates/{block_id}.json'))
            self.assertTrue((DAEDALON_DATA/f'loot_tables/blocks/{block_id}.json').exists())
        for kind in ('blocks','items'):
            for term in ('frieze','entablature','wall_relief',*self.search_terms):
                self.assertIn('#daedalon:'+self.family,load_json(DAEDALON_DATA/f'tags/{kind}/{term}.json')['values'])
        definition=load_json(MESH_ROOT/(self.family+'.json'))
        self.assertEqual(6.0,definition['box_projection_span'])
        self.assertFalse(definition['smooth_normals'])


class CorinthianFriezeSafetyTests(FriezeSafetyChecks,unittest.TestCase):
    family='corinthian_frieze'
    evidence_file='corinthian-frieze-source.json'
    source_digest='26be4f12d27741bee6eecd67471c6d8f9c0249395e83113e31a3607cdbeb9916'
    depth=.23
    min_faces=4500
    search_terms=('acanthus',)


if __name__=='__main__': unittest.main()
