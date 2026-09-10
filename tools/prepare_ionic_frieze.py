"""Prepare the two-motif Ionic repeat using the approved frieze components.

Blender --background --factory-startup --disable-autoexec --python <this file>
-- --input <source.obj> --output-dir <mesh directory> --evidence <evidence.json>
[--preview-dir <directory>]. The supplied source is never modified.
"""
import argparse
import hashlib
import json
import sys
from pathlib import Path

import bpy
from mathutils import Vector

sys.path.insert(0,str(Path(__file__).resolve().parent))
from prepare_corinthian_frieze import clip, move, weld_repeat, build_components, export, preview, smooth_relief_normals

# Matching low-relief gaps after the two occurrences of the mounted group.
# This retains both distinct scenes (chariot + mounted group) and both palms.
CUT_LEFT, CUT_RIGHT = -0.357, 0.724
SOURCE_MIN_Y, SOURCE_HEIGHT = -0.258187, 0.514064
SOURCE_BACK_Z = -0.08758


def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--input',type=Path,required=True)
    parser.add_argument('--output-dir',type=Path,required=True)
    parser.add_argument('--evidence',type=Path,required=True)
    parser.add_argument('--preview-dir',type=Path)
    args=parser.parse_args(sys.argv[sys.argv.index('--')+1:])
    if bpy.app.version != (5,2,0): raise RuntimeError('Use Blender 5.2.0')
    digest=hashlib.sha256(args.input.read_bytes()).hexdigest()
    vertices=[]; normals=[]; triangles=[]
    for line in args.input.read_text().splitlines():
        p=line.split()
        if not p: continue
        if p[0]=='v': vertices.append(Vector(tuple(map(float,p[1:4]))))
        elif p[0]=='vn': normals.append(Vector(tuple(map(float,p[1:4]))))
        elif p[0]=='f':
            refs=[v.split('/') for v in p[1:]]
            triangles.append([(vertices[int(r[0])-1].copy(),normals[int(r[2])-1].copy()) for r in refs])
    source_faces=len(triangles)
    bounds=[(min(p[i] for p in vertices),max(p[i] for p in vertices)) for i in range(3)]
    triangles=clip(clip(triangles,lambda p:p.x-CUT_LEFT),lambda p:CUT_RIGHT-p.x)
    sx=3/(CUT_RIGHT-CUT_LEFT); sy=sz=1/SOURCE_HEIGHT
    triangles=move(triangles,lambda p:Vector(((p.x-CUT_LEFT)*sx,(p.y-SOURCE_MIN_Y)*sy,(p.z-SOURCE_BACK_Z)*sz)),
                   lambda n:Vector((n.x/sx,n.y/sy,n.z/sz)))
    # Remove sub-millimetre noise from the intended horizontal boundary planes.
    for tri in triangles:
        for p,n in tri:
            if p.y<0.001: p.y=0
            elif p.y>0.999: p.y=1
    triangles=weld_repeat(triangles)
    triangles=smooth_relief_normals(triangles,3)
    groups=build_components(triangles)
    out=args.output_dir; out.mkdir(parents=True,exist_ok=True)
    export(out/'ionic_frieze.obj',groups,'Ionic Frieze')
    (out/'ionic_frieze.mtl').write_text('newmtl none\nKd 1.0 1.0 1.0\n', encoding="utf-8", newline="\n")
    definition={'obj':'daedalon:models/mesh/ionic_frieze.obj','mtl':'daedalon:models/mesh/ionic_frieze.mtl',
                'flip_v':True,'repair_degenerate_uvs':True,'force_uv_projection':True,'uv_projection':'axis_stabilized_box',
                'box_projection_span':6.0,'face_oriented_uvs':True,'smooth_normals':False,'fit_to_block':False,
                'scale':[1.,1.,1.],'translate':[0.,0.,0.],
                'particle':'daedalon:block/statue_spartan_promachos_aganite',
                'materials':{'none':'daedalon:block/statue_spartan_promachos_aganite'}}
    (out/'ionic_frieze.json').write_text(json.dumps(definition,indent=2)+'\n', encoding="utf-8", newline="\n")
    evidence={'source_name':args.input.name,'source_sha256':digest,'source_files_modified':False,
              'provenance':'Oliver supplied his own Meshy creation; approved for the public Daedalon repository.',
              'source':{'vertices':len(vertices),'triangles':source_faces,'bounds':bounds,'front':'+Z','up':'+Y'},
              'preparation':{'tool':'Blender 5.2.0 LTS','height_m':1,'repeat_width_m':3,'decimated':False,
                             'repeat_cut_source_x':[CUT_LEFT,CUT_RIGHT],
                             'motifs_per_repeat':['palm','chariot group','palm','mounted group'],
                             'source_repeat_width':CUT_RIGHT-CUT_LEFT,
                             'depth_scale':'same as height; retains authored section proportions',
                             'depth_envelope_m':0.34,'shared_repeat_profile_blend_m':0.025,
                             'top_bottom_planarity_band_m':0.001,
                             'normal_smoothing_angle_degrees':30,'smoothing_changes_geometry':False,
                             'end_closure':'exact section silhouette; zero added width',
                             'components':{k:len(v) for k,v in groups.items()}},
              'outputs':{p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in out.glob('ionic_frieze.*')}}
    args.evidence.parent.mkdir(parents=True,exist_ok=True)
    args.evidence.write_text(json.dumps(evidence,indent=2)+'\n', encoding="utf-8", newline="\n")
    if args.preview_dir:
        args.preview_dir.mkdir(parents=True,exist_ok=True)
        preview(groups,args.preview_dir,'ionic')
    assert hashlib.sha256(args.input.read_bytes()).hexdigest()==digest
    print(json.dumps(evidence))


if __name__=='__main__': main()
