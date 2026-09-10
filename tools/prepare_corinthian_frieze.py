"""Prepare the Corinthian pilot in Blender; never modify the supplied OBJ.

Run Blender in background/factory-startup/disable-autoexec mode with this script,
then -- --input <source.obj> --output-dir <directory> [--preview-dir <directory>].
Named OBJ objects are immutable render components, shared across all finishes.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import math
import sys
from collections import defaultdict
from pathlib import Path

import bpy
from mathutils import Vector

WIDTH = 3.0
DEPTH = 0.226145
CUT = 0.83


def clip(triangles, distance):
    """Clip triangles, interpolating the supplied corner normals at intersections."""
    result = []
    for triangle in triangles:
        polygon = []
        previous = triangle[-1]
        dp = distance(previous[0])
        for current in triangle:
            dc = distance(current[0])
            if (dc >= -1e-9) != (dp >= -1e-9):
                t = dp / (dp - dc)
                p = previous[0].lerp(current[0], t)
                n = previous[1].lerp(current[1], t).normalized()
                polygon.append((p, n))
            if dc >= -1e-9:
                polygon.append(current)
            previous, dp = current, dc
        for i in range(1, len(polygon)-1):
            tri = [polygon[0], polygon[i], polygon[i+1]]
            if (tri[1][0]-tri[0][0]).cross(tri[2][0]-tri[0][0]).length > 1e-10:
                result.append(tri)
    return result


def move(triangles, fn, normal_fn=lambda v: v):
    return [[(fn(p.copy()), normal_fn(n.copy()).normalized()) for p,n in tri] for tri in triangles]


def face(a,b,c,d=None):
    vertices = list(map(Vector, (a,b,c) if d is None else (a,b,c,d)))
    normal = (vertices[1]-vertices[0]).cross(vertices[2]-vertices[0]).normalized()
    corners = [(v,normal) for v in vertices]
    return [corners[:3]] if d is None else [corners[:3], [corners[0],corners[2],corners[3]]]


def cut_closure(triangles, distance, position, outward):
    """Close the actual cut silhouette, without an oversized border or fascia.

    Every height interval uses its intersecting front edge, preserving abrupt
    moulding steps. Closures lie on the cut plane and add no width or projection.
    The relief is a solid backed by z=0; the same construction closes mitres.
    """
    segments=[]
    for tri in triangles:
        points=[p for p,n in tri if abs(distance(p)) < 2e-6]
        if len(points)==2 and abs(points[0].y-points[1].y)>1e-8:
            # Match the exported source grid before interpolating: the top
            # surface includes near-horizontal edges only micrometres high.
            edge=[(round(p.y,7),round(p.z,7)) for p in points]
            if edge[0][0]!=edge[1][0]: segments.append(edge)
    ys=sorted({p[0] for edge in segments for p in edge})
    result=[]
    for y0,y1 in zip(ys,ys[1:]):
        ym=(y0+y1)/2
        active=[(a,b) for a,b in segments if min(a[0],b[0])<=ym<=max(a[0],b[0])]
        if not active: continue
        def depth(edge,y):
            a,b=edge
            return a[1]+(b[1]-a[1])*(y-a[0])/(b[0]-a[0])
        front=max(active,key=lambda edge:depth(edge,ym))
        z0,z1=max(0,depth(front,y0)),max(0,depth(front,y1))
        for tri in face(position(y0,0),position(y0,z0),position(y1,z1),position(y1,0)):
            if (tri[1][0]-tri[0][0]).cross(tri[2][0]-tri[0][0]).length < 1e-10: continue
            if tri[0][1].dot(Vector(outward)) < 0: tri=list(reversed(tri))
            result.append([(p,Vector(outward).normalized()) for p,n in tri])
    if not result: raise ValueError('Empty cut closure')
    return result


def smooth_relief_normals(triangles, width, angle_degrees=30):
    """Angle-weighted corner normals before component duplication; no vertex moves.

    Periodic endpoints share normals. Sharp edges and separately added cut caps
    stay crisp, without averaging overlapping corner variants at runtime.
    """
    incident=defaultdict(list)
    def key(p):
        x=0 if abs(p.x-width)<1e-6 else p.x
        return (round(x,6),round(p.y,6),round(p.z,6))
    geometric=[]
    for tri in triangles:
        normal=(tri[1][0]-tri[0][0]).cross(tri[2][0]-tri[0][0]).normalized()
        geometric.append(normal)
        for i,(p,_) in enumerate(tri):
            a=(tri[(i-1)%3][0]-p).normalized(); b=(tri[(i+1)%3][0]-p).normalized()
            weight=math.acos(max(-1,min(1,a.dot(b))))
            incident[key(p)].append((normal,weight))
    threshold=math.cos(math.radians(angle_degrees))
    def corner(p,face_normal):
        averaged=sum((n*w for n,w in incident[key(p)] if n.dot(face_normal)>=threshold),Vector())
        # Float32 angles can round to zero on very thin loft triangles.
        return averaged.normalized() if averaged.length_squared>1e-20 else face_normal.copy()
    return [[(p,corner(p,face_normal))
             for p,_ in tri] for tri,face_normal in zip(triangles,geometric)]


def weld_repeat(triangles, margin=0.025, width=WIDTH, profile_tolerance=0,
                height_bounds=(0.0,1.0), close_horizontal=True):
    """Loft only the last 25mm at each repeat end to an identical shared profile.

    Cross-section vertices from both cuts define the sampling, retaining the
    moulding steps. No terminal face or end decoration is added to a straight join.
    """
    def section(x):
        segments=[]
        for tri in triangles:
            points=[]
            for (a,_),(b,_) in zip(tri,tri[1:]+tri[:1]):
                if (a.x <= x < b.x) or (b.x <= x < a.x):
                    points.append(a.lerp(b,(x-a.x)/(b.x-a.x)))
            if len(points)==2 and abs(points[0].y-points[1].y)>1e-10:
                segments.append(points)
        return segments
    left,right=section(margin),section(width-margin)
    bottom,top=height_bounds
    ys=sorted({round(bottom,7),round(top,7),*[round(p.y,7) for pair in left+right for p in pair]})
    def front(segments,y):
        values=[a.z+(b.z-a.z)*(y-a.y)/(b.y-a.y) for a,b in segments
                if min(a.y,b.y)-2e-7 <= y <= max(a.y,b.y)+2e-7]
        return max(values) if values else 0.0
    lp=[front(left,y) for y in ys]; rp=[front(right,y) for y in ys]
    if profile_tolerance:
        # Reduce only redundant loft samples, never the source carving.
        keep={0,len(ys)-1}; pending=[(0,len(ys)-1)]
        while pending:
            a,b=pending.pop()
            worst,index=0,None
            for j in range(a+1,b):
                t=(ys[j]-ys[a])/(ys[b]-ys[a])
                error=max(abs(profile[j]-(profile[a]+t*(profile[b]-profile[a]))) for profile in (lp,rp))
                if error>worst:worst,index=error,j
            if worst>profile_tolerance:
                keep.add(index); pending.extend(((a,index),(index,b)))
        indices=sorted(keep)
        ys,lp,rp=([values[i] for i in indices] for values in (ys,lp,rp))
    shared=[(a+b)/2 for a,b in zip(lp,rp)]
    result=clip(clip(triangles,lambda p:p.x-margin),lambda p:width-margin-p.x)
    for i in range(len(ys)-1):
        y0,y1=ys[i:i+2]
        result += face((0,y0,shared[i]),(margin,y0,lp[i]),(margin,y1,lp[i+1]),(0,y1,shared[i+1]))
        result += face((width-margin,y0,rp[i]),(width,y0,shared[i]),(width,y1,shared[i+1]),(width-margin,y1,rp[i+1]))
        for x0,x1 in ((0,margin),(width-margin,width)):
            if not profile_tolerance:
                result += face((x1,y0,0),(x0,y0,0),(x0,y1,0),(x1,y1,0))
    if profile_tolerance:
        for x0,x1 in ((0,margin),(width-margin,width)):
            result += face((x1,bottom,0),(x0,bottom,0),(x0,top,0),(x1,top,0))
    if close_horizontal:
        for x0,x1,p0,p1 in ((0,margin,shared,lp),(width-margin,width,rp,shared)):
            result += face((x0,bottom,0),(x1,bottom,0),(x1,bottom,p1[0]),(x0,bottom,p0[0]))
            result += face((x0,top,p0[-1]),(x1,top,p1[-1]),(x1,top,0),(x0,top,0))
    return result


def build_components(triangles, sections=3):
    groups={}
    for i in range(sections):
        part=clip(clip(triangles,lambda p:p.x-i),lambda p:i+1-p.x)
        groups[f'straight_{i}']=move(part,lambda p:Vector((p.x-i,p.y,p.z)))
        groups[f'cap_left_{i}']=cut_closure(groups[f'straight_{i}'],lambda p:p.x,lambda y,z:(0,y,z),(-1,0,0))
        groups[f'cap_right_{i}']=cut_closure(groups[f'straight_{i}'],lambda p:p.x-1,lambda y,z:(1,y,z),(1,0,0))
    east=lambda p:Vector((p.z,p.y,1-p.x))
    east_n=lambda n:Vector((n.z,n.y,-n.x))
    west=lambda p:Vector((1-p.z,p.y,p.x))
    west_n=lambda n:Vector((-n.z,n.y,n.x))
    shapes={
        'inner_left':(lambda p:p.x-p.z,lambda p:p.z-p.x,east,east_n),
        'inner_right':(lambda p:1-p.x-p.z,lambda p:p.x+p.z-1,west,west_n),
        'outer_left':(lambda p:p.z-p.x,lambda p:p.x-p.z,east,east_n),
        'outer_right':(lambda p:p.x+p.z-1,lambda p:1-p.x-p.z,west,west_n),
    }
    for shape,(primary,secondary,rotate,rotate_n) in shapes.items():
        plane=(lambda y,z:(z,y,z)) if shape.endswith('left') else (lambda y,z:(1-z,y,z))
        normal=Vector((-1,0,1)) if shape.endswith('left') else Vector((1,0,1))
        if shape.startswith('outer'): normal=-normal
        for i in range(sections):
            part=clip(groups[f'straight_{i}'],primary)
            closure=cut_closure(part,primary,plane,normal)
            groups[f'{shape}_primary_{i}']=part+closure
            # Rotate a locally closed cut, so relief depth still means local z.
            local=clip(groups[f'straight_{i}'],lambda p:secondary(rotate(p)))
            x_at_depth=(lambda y,z:(1-z,y,z)) if shape.endswith('left') else (lambda y,z:(z,y,z))
            local_normal=Vector((1,0,1)) if shape.endswith('left') else Vector((-1,0,1))
            if shape.startswith('outer'): local_normal=-local_normal
            closure=cut_closure(local,lambda p:secondary(rotate(p)),x_at_depth,local_normal)
            groups[f'{shape}_secondary_{i}']=move(local+closure,rotate,rotate_n)
    for i in range(sections):
        groups[f'cap_east_low_{i}']=move(groups[f'cap_left_{i}'],east,east_n)
        groups[f'cap_east_high_{i}']=move(groups[f'cap_right_{i}'],east,east_n)
        groups[f'cap_west_low_{i}']=move(groups[f'cap_left_{i}'],west,west_n)
        groups[f'cap_west_high_{i}']=move(groups[f'cap_right_{i}'],west,west_n)
    return groups


def export(path, groups, title="Corinthian Frieze"):
    # Deduplicate exact rounded positions/normals, preserving authored hard edges.
    positions, normals, records = {}, {}, []
    for name, triangles in groups.items():
        refs=[]
        for triangle in triangles:
            row=[]
            for p,n in triangle:
                pk=tuple(round(v,7) for v in p); nk=tuple(round(v,7) for v in n)
                row.append((positions.setdefault(pk,len(positions)+1), normals.setdefault(nk,len(normals)+1)))
            refs.append(row)
        records.append((name,refs))
    lines=[f'# Prepared {title}; Y up, front +Z.',f'mtllib {path.with_suffix(".mtl").name}']
    lines += ['v '+' '.join(f'{v:.7f}' for v in p) for p in positions]
    lines += ['vn '+' '.join(f'{v:.7f}' for v in n) for n in normals]
    for name,refs in records:
        lines += [f'o {name}','usemtl none','s 1']
        lines += ['f '+' '.join(f'{v}//{n}' for v,n in row) for row in refs]
    path.write_text('\n'.join(lines)+'\n',encoding='utf-8', newline="\n")


def preview(groups, directory, prefix="corinthian"):
    bpy.context.preferences.filepaths.save_version=0
    bpy.ops.object.select_all(action='SELECT'); bpy.ops.object.delete(use_global=False)
    scene=bpy.context.scene
    scene.render.engine='BLENDER_WORKBENCH'
    scene.render.resolution_x=1800; scene.render.resolution_y=1000; scene.render.resolution_percentage=100
    scene.display.shading.light='STUDIO'; scene.display.shading.show_cavity=True
    scene.display.shading.cavity_type='BOTH'; scene.display.shading.color_type='SINGLE'
    scene.display.shading.single_color=(0.68,0.64,0.56)
    scene.world.color=(0.055,0.055,0.055)
    sections=sum(name.startswith("straight_") for name in groups)
    objects=[]
    def add(name, offset=(0,0,0), rotation=0):
        base,index=name.rsplit("_",1)
        resolved=base+"_"+str(int(index)%sections)
        tris=groups[resolved]; verts=[(p.x,-p.z,p.y) for tri in tris for p,n in tri]
        mesh=bpy.data.meshes.new(name); mesh.from_pydata(verts,[],[(i,i+1,i+2) for i in range(0,len(verts),3)])
        mesh.update()
        normals=[(n.x,-n.z,n.y) for tri in tris for p,n in tri]
        for poly in mesh.polygons: poly.use_smooth=True
        mesh.normals_split_custom_set(normals)
        obj=bpy.data.objects.new(name,mesh); scene.collection.objects.link(obj)
        obj.location=(offset[0],-offset[2],offset[1]); obj.rotation_euler.z=rotation
        objects.append(obj)
        return obj
    camdata=bpy.data.cameras.new('Preview'); camdata.type='ORTHO'; camdata.ortho_scale=7.1
    cam=bpy.data.objects.new('Preview',camdata); scene.collection.objects.link(cam); scene.camera=cam
    def render(name,loc,target,scale):
        scene.render.resolution_y=540 if 'straight' in name else (760 if 'individual' in name else 1000)
        camdata.ortho_scale=scale; cam.location=loc
        cam.rotation_euler=(Vector(target)-cam.location).to_track_quat('-Z','Y').to_euler()
        scene.render.filepath=str(directory/name); bpy.ops.render.render(write_still=True)
    for i in range(6): add(f'straight_{i%sections}',(i,0,0))
    add('cap_left_0'); add(f'cap_right_{5%sections}',(5,0,0))
    render(f'{prefix}-straight.png',(3,-9,2.5),(3,0,.5),7)
    for obj in objects: bpy.data.objects.remove(obj,do_unlink=True)
    objects.clear()
    for i in range(sections): add(f'straight_{i}',(i,0,0))
    add('cap_left_0'); add(f'cap_right_{sections-1}',(sections-1,0,0))
    render(f'{prefix}-complete-repeat.png',(sections/2,-6,.5),(sections/2,0,.5),max(2.5,sections+.4))
    for obj in objects: bpy.data.objects.remove(obj,do_unlink=True)
    objects.clear()
    for row,shape in enumerate(('inner_left','inner_right','outer_left','outer_right')):
        offset=((row%2)*2.0,0,(row//2)*2.0)
        add(f'{shape}_primary_1',offset); add(f'{shape}_secondary_1',offset)
    render(f'{prefix}-corners.png',(5,-6,6),(1.5,-1.5,.5),7.0)
    for obj in objects: bpy.data.objects.remove(obj,do_unlink=True)
    objects.clear()
    for i in range(sections):
        add(f'straight_{i}',(i*1.25,0,0)); add(f'cap_left_{i}',(i*1.25,0,0)); add(f'cap_right_{i}',(i*1.25,0,0))
    center=((sections-1)*1.25+1)/2
    render(f'{prefix}-individual.png',(center,-6,2.0),(center,0,.5),max(2,sections*1.4))
    bpy.ops.wm.save_as_mainfile(filepath=str(directory/f'{prefix}-frieze-preview.blend'))
    for obj in objects: bpy.data.objects.remove(obj,do_unlink=True)
    objects.clear()
    add('straight_1'); add('cap_left_1'); add('cap_right_1')
    render(f'{prefix}-end-detail.png',(-1.4,-2.4,1.05),(.2,-.08,.5),1.35)
    for obj in objects: bpy.data.objects.remove(obj,do_unlink=True)
    objects.clear()
    previous=sections-1
    add('outer_left_primary_0'); add(f'outer_left_secondary_{previous}')
    add(f'straight_{previous}',(-1,0,0)); add(f'cap_left_{previous}',(-1,0,0))
    groups['preview_east_0']=move(groups['straight_0'],lambda p:Vector((p.z,p.y,1-p.x)),lambda n:Vector((n.z,n.y,-n.x)))
    add('preview_east_0',(0,0,-1)); add('cap_east_high_0',(0,0,-1))
    render(f'{prefix}-outer-run.png',(3,-3,1.4),(-.35,.35,.5),2.4)
    del groups['preview_east_0']


def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--input',type=Path,required=True)
    parser.add_argument('--output-dir',type=Path,required=True)
    parser.add_argument('--preview-dir',type=Path)
    parser.add_argument('--evidence',type=Path,help='Evidence JSON destination; defaults to the output directory')
    args=parser.parse_args(sys.argv[sys.argv.index('--')+1:])
    if bpy.app.version != (5,2,0): raise RuntimeError('Use Blender 5.2.0')
    source_hash=hashlib.sha256(args.input.read_bytes()).hexdigest()
    verts=[]; normals=[]; triangles=[]
    for line in args.input.read_text().splitlines():
        p=line.split()
        if not p: continue
        if p[0]=='v': verts.append(Vector(tuple(map(float,p[1:4]))))
        elif p[0]=='vn': normals.append(Vector(tuple(map(float,p[1:4]))))
        elif p[0]=='f':
            refs=[v.split('/') for v in p[1:]]
            triangles.append([(verts[int(r[0])-1].copy(),normals[int(r[2])-1].copy()) for r in refs])
    # Remove the authored side returns. Keep the supplied relief and its normals.
    triangles=clip(clip(triangles,lambda p:p.x+CUT),lambda p:CUT-p.x)
    sx=WIDTH/(2*CUT); sy=1/.533164; sz=3/1.901874
    triangles=move(triangles,lambda p:Vector(((p.x+CUT)*sx,(p.y+.267069)*sy,(p.z+.072168)*sz)),
                   lambda n:Vector((n.x/sx,n.y/sy,n.z/sz)))
    triangles=weld_repeat(triangles)
    triangles=smooth_relief_normals(triangles,WIDTH)
    groups=build_components(triangles)
    out=args.output_dir; out.mkdir(parents=True,exist_ok=True)
    export(out/'corinthian_frieze.obj',groups)
    (out/'corinthian_frieze.mtl').write_text('newmtl none\nKd 1.0 1.0 1.0\n',encoding='utf-8', newline="\n")
    definition={'obj':'daedalon:models/mesh/corinthian_frieze.obj','mtl':'daedalon:models/mesh/corinthian_frieze.mtl',
                'flip_v':True,'repair_degenerate_uvs':True,'force_uv_projection':True,'uv_projection':'axis_stabilized_box',
                'box_projection_span':6.0,'face_oriented_uvs':True,'smooth_normals':False,'fit_to_block':False,
                'scale':[1.,1.,1.],'translate':[0.,0.,0.],
                'particle':'daedalon:block/statue_spartan_promachos_aganite',
                'materials':{'none':'daedalon:block/statue_spartan_promachos_aganite'}}
    (out/'corinthian_frieze.json').write_text(json.dumps(definition,indent=2)+'\n',encoding='utf-8', newline="\n")
    evidence={'source_name':args.input.name,'source_sha256':source_hash,'source_files_modified':False,
              'provenance':'Oliver supplied his own Meshy creation; approved for the public Daedalon repository.',
              'preparation':{'tool':'Blender 5.2.0 LTS','height_m':1,'repeat_width_m':3,'decimated':False,
                             'side_return_cut_source_x':[-CUT,CUT],'end_closure':'exact section silhouette; zero added width',
                             'shared_repeat_profile_blend_m':0.025,
                             'normal_smoothing_angle_degrees':30,'smoothing_changes_geometry':False,
                             'depth_envelope_m':DEPTH,'components':{k:len(v) for k,v in groups.items()}},
              'outputs':{p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in out.glob('corinthian_frieze.*')}}
    evidence_path=args.evidence or out/'corinthian-frieze-source.json'
    evidence_path.parent.mkdir(parents=True,exist_ok=True)
    evidence_path.write_text(json.dumps(evidence,indent=2)+'\n',encoding='utf-8', newline="\n")
    if args.preview_dir:
        args.preview_dir.mkdir(parents=True,exist_ok=True); preview(groups,args.preview_dir)
    assert hashlib.sha256(args.input.read_bytes()).hexdigest()==source_hash
    print(json.dumps(evidence))


if __name__=='__main__': main()
