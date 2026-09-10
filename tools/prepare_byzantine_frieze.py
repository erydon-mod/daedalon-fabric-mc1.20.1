"""Prepare the two-block Byzantine repeat using the approved frieze components.

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

# Main-panel repeat boundaries pass through the large outer pillar centres.
CUT_LEFT, CUT_RIGHT = -0.737, 0.737
SOURCE_BACK_Z = -0.111647
PLAIN_BANDS = ((-0.539316,-0.468),(-0.332,-0.255),(0.105,0.189),(0.250,0.289),(0.461,0.537217))
# Each carving band keeps uniform XYZ proportions, using its own natural repeat.
# name, bottom, top, source left/right, cycles across the complete two metres
CARVED_BANDS = (('rope',-.468,-.405,-.041,.044,24),
                ('braid',-.405,-.332,-.070,.076,14),
                ('main_panel',-.255,.105,CUT_LEFT,CUT_RIGHT,1),
                ('dentils',.189,.250,-.040,.050,22),
                ('foliage',.289,.461,-.170,.178,6))


def bridge_horizontal(lower, upper, y):
    """Join differing tier profiles with only their exposed horizontal ledge."""
    from prepare_corinthian_frieze import face
    def edges(tris):
        result=[]
        for tri in tris:
            points=[p for p,n in tri if abs(p.y-y)<2e-6]
            if len(points)==2 and abs(points[0].x-points[1].x)>1e-8:
                result.append(points)
        return result
    a,b=edges(lower),edges(upper)
    xs=sorted({0.,2.,*[round(p.x,7) for edge in a+b for p in edge]})
    result=[]
    for x0,x1 in zip(xs,xs[1:]):
        if x1-x0<1e-7: continue
        # Select each segment at its midpoint, preserving vertical profile steps.
        mid=(x0+x1)/2
        def segment(edges):
            active=[(u,v) for u,v in edges if min(u.x,v.x)<=mid<=max(u.x,v.x)]
            if not active:return (0.,0.)
            u,v=max(active,key=lambda edge:edge[0].z+(edge[1].z-edge[0].z)*(mid-edge[0].x)/(edge[1].x-edge[0].x))
            return tuple(u.z+(v.z-u.z)*(x-u.x)/(v.x-u.x) for x in (x0,x1))
        z0,z1=segment(a); w0,w1=segment(b)
        cuts=[(x0,z0,w0),(x1,z1,w1)]
        if (z0-w0)*(z1-w1)<0:
            t=(z0-w0)/((z0-w0)-(z1-w1))
            cuts.insert(1,(x0+t*(x1-x0),z0+t*(z1-z0),w0+t*(w1-w0)))
        for (x,z,w),(xx,zz,ww) in zip(cuts,cuts[1:]):
            for tri in face((x,y,z),(xx,y,zz),(xx,y,ww),(x,y,w)):
                if (tri[1][0]-tri[0][0]).cross(tri[2][0]-tri[0][0]).length<1e-10:continue
                desired=1 if z+zz>w+ww else -1
                if tri[0][1].y*desired<0:tri=list(reversed(tri))
                result.append([(p,Vector((0,desired,0))) for p,n in tri])
    return result


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
    source_triangles=triangles
    carved_height=sum((high-low)*2/cycles/(right-left) for name,low,high,left,right,cycles in CARVED_BANDS)
    plain_scale=(1-carved_height)/sum(high-low for low,high in PLAIN_BANDS)
    assert .3 < plain_scale < .6
    bands=[(name,low,high,left,right,cycles,True) for name,low,high,left,right,cycles in CARVED_BANDS]
    bands += [('moulding',low,high,CUT_LEFT,CUT_RIGHT,1,False) for low,high in PLAIN_BANDS]
    bands.sort(key=lambda b:b[1])
    tiers=[]; measurements=[]; y=0.
    for name,low,high,left,right,cycles,carved in bands:
        scale=2/cycles/(right-left)
        sy=scale if carved else plain_scale
        # Use the neighbouring carving's depth for plain horizontal mouldings.
        sz=scale if carved else 1.
        height=(high-low)*sy; next_y=y+height
        tier=clip(clip(source_triangles,lambda p:p.y-low),lambda p:high-p.y)
        tier=clip(clip(tier,lambda p:p.x-left),lambda p:right-p.x)
        def fit(p):return Vector(((p.x-left)*scale,y+(p.y-low)*sy,(p.z-SOURCE_BACK_Z)*sz))
        tier=move(tier,fit)
        for tri in tier:
            for p,n in tri:
                if abs(p.y-y)<2e-6:p.y=y
                if abs(p.y-next_y)<2e-6:p.y=next_y
        if carved and name!='main_panel':
            # Simplify small repeated trim before joining; retain the main relief.
            coords={}; faces=[]
            for tri in tier:
                faces.append([coords.setdefault(tuple(round(v,7) for v in p),len(coords)) for p,n in tri])
            mesh=bpy.data.meshes.new(name); mesh.from_pydata(list(coords),[],faces); mesh.update()
            obj=bpy.data.objects.new(name,mesh); bpy.context.collection.objects.link(obj)
            modifier=obj.modifiers.new('Small trim detail budget','DECIMATE')
            modifier.ratio=.5; modifier.use_collapse_triangulate=True
            evaluated=obj.evaluated_get(bpy.context.evaluated_depsgraph_get()); reduced=evaluated.to_mesh()
            tier=[[(reduced.vertices[i].co.copy(),poly.normal.copy()) for i in poly.vertices] for poly in reduced.polygons]
            evaluated.to_mesh_clear(); bpy.data.objects.remove(obj,do_unlink=True); bpy.data.meshes.remove(mesh)
        width=2/cycles
        tier=weld_repeat(tier,margin=min(.008,width*.04),width=width,profile_tolerance=.0001,
                         height_bounds=(y,next_y),close_horizontal=False)
        repeated=[tri for i in range(cycles) for tri in move(tier,lambda p:Vector((p.x+i*width,p.y,p.z)))]
        tiers.append(repeated)
        measurements.append({'name':name,'source_y':[low,high],'source_x':[left,right],
                             'cycles':cycles,'uniform_scale':scale if carved else None,
                             'height_m':height,'output_y':[y,next_y],'triangles':len(repeated)})
        y=next_y
    assert abs(y-1)<1e-6
    triangles=[tri for tier in tiers for tri in tier]
    for index in range(len(tiers)+1):
        lower=tiers[index-1] if index else []
        upper=tiers[index] if index<len(tiers) else []
        height=measurements[index]['output_y'][0] if index<len(tiers) else 1.
        triangles+=bridge_horizontal(lower,upper,height)
    triangles=smooth_relief_normals(triangles,2)
    groups=build_components(triangles,sections=2)
    depth=max(p.z for tri in triangles for p,n in tri)
    out=args.output_dir; out.mkdir(parents=True,exist_ok=True)
    export(out/'byzantine_frieze.obj',groups,'Byzantine Frieze')
    (out/'byzantine_frieze.mtl').write_text('newmtl none\nKd 1.0 1.0 1.0\n', encoding="utf-8", newline="\n")
    definition={'obj':'daedalon:models/mesh/byzantine_frieze.obj','mtl':'daedalon:models/mesh/byzantine_frieze.mtl',
                'flip_v':True,'repair_degenerate_uvs':True,'force_uv_projection':True,'uv_projection':'axis_stabilized_box',
                'box_projection_span':6.0,'face_oriented_uvs':True,'smooth_normals':False,'fit_to_block':False,
                'scale':[1.,1.,1.],'translate':[0.,0.,0.],
                'particle':'daedalon:block/statue_spartan_promachos_aganite',
                'materials':{'none':'daedalon:block/statue_spartan_promachos_aganite'}}
    (out/'byzantine_frieze.json').write_text(json.dumps(definition,indent=2)+'\n', encoding="utf-8", newline="\n")
    evidence={'source_name':args.input.name,'source_sha256':digest,'source_files_modified':False,
              'provenance':'Oliver supplied his own Meshy creation; approved for the public Daedalon repository.',
              'source':{'vertices':len(vertices),'triangles':source_faces,'bounds':bounds,'front':'+Z','up':'+Y'},
              'preparation':{'tool':'Blender 5.2.0 LTS','height_m':1,'repeat_width_m':2,'decimated':True,'small_trim_decimation_ratio':.5,'main_panel_decimated':False,
                             'repeat_cut_source_x':[CUT_LEFT,CUT_RIGHT],
                             'motifs_per_repeat':['half large pillar','palm','small pillar','interlace','cross medallion','interlace','small pillar','palm','half large pillar'],
                             'source_repeat_width':CUT_RIGHT-CUT_LEFT,
                             'ornament_stretched':False,'carving_bands':measurements,
                             'plain_fascia_source_y':PLAIN_BANDS,
                             'plain_moulding_vertical_scale':plain_scale,
                             'depth_envelope_m':0.235,'measured_depth_m':depth,
                             'loft_profile_tolerance_m':0.0001,

                             'normal_smoothing_angle_degrees':30,'smoothing_changes_geometry':False,
                             'end_closure':'exact section silhouette; zero added width',
                             'components':{k:len(v) for k,v in groups.items()}},
              'outputs':{p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in out.glob('byzantine_frieze.*')}}
    args.evidence.parent.mkdir(parents=True,exist_ok=True)
    args.evidence.write_text(json.dumps(evidence,indent=2)+'\n', encoding="utf-8", newline="\n")
    if args.preview_dir:
        args.preview_dir.mkdir(parents=True,exist_ok=True)
        preview(groups,args.preview_dir,'byzantine')
    assert hashlib.sha256(args.input.read_bytes()).hexdigest()==digest
    print(json.dumps(evidence))


if __name__=='__main__': main()
