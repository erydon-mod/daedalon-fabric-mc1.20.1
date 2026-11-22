"""Measured clean reconstruction of Oliver's five Meshy finials.

Run with ordinary Python; the authoring pass runs in Blender 5.2 in background.
Originals are hash-checked and read only. Editable polygon and exported triangle
counts are recorded separately; the requested budget is 6,000 Blender faces.
"""
from __future__ import annotations

import argparse
import hashlib
import importlib.util
import json
import math
from pathlib import Path
import subprocess
import sys

try:
    import bpy
    import bmesh
    from mathutils import Vector
except ImportError:
    bpy = None

ROOT = Path(__file__).resolve().parents[1]
HISTORY = ROOT / "docs/evidence/finial-decor-batch-source.json"
SOURCES = {m["name"].removeprefix("finial_"): m for m in json.loads(HISTORY.read_text(encoding="utf-8"))["models"] if m["name"].startswith("finial_")}
DIMENSIONS = {
    "sphaira": (.661462481, .662851611, .605648, .10),
    "balanos": (.494869746, .494972038, .368761, .085),
    "kynara": (.521664875, .580038780, .331942, .075),
    "phlox": (.535196576, .531525611, .433910, .065),
    "strobilos": (.510813963, .513596362, .401336, .075),
}
# Measured turned profiles, with the source's random surface noise removed.
PROFILES = {
    "sphaira": [(.161,.262),(.17,.268),(.18,.273),(.195,.284),(.20,.285),(.21,.277),(.22,.265),(.24,.2495),(.26,.241),(.27,.241),(.28,.242),(.30,.255),(.31,.270),(.32,.270),(.33,.284),(.34,.303),(.36,.321),(.38,.329),(.40,.3307312405),(.42,.327),(.44,.315),(.45,.302),(.46,.280),(.466,.262)],
}


def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


class Builder:
    def __init__(self):
        self.vertices, self.faces, self.ids = [], [], {}

    def vertex(self, point):
        point = tuple(float(v) for v in point)
        key = tuple(round(v, 10) for v in point)
        if key not in self.ids:
            self.ids[key] = len(self.vertices)
            self.vertices.append(point)
        return self.ids[key]

    def face(self, indices):
        indices = list(dict.fromkeys(indices))
        if len(indices) >= 3:
            self.faces.append(tuple(indices))

    def lathe(self, profile, segments=48, ratio=1):
        rings = [[self.vertex((r*math.cos(a*2*math.pi/segments), r*math.sin(a*2*math.pi/segments)*ratio, z)) for a in range(segments)] for z,r in profile]
        for low,high in zip(rings, rings[1:]):
            for a in range(segments):
                self.face((low[a],low[(a+1)%segments],high[(a+1)%segments],high[a]))
        self.face(reversed(rings[0]))
        self.face(rings[-1])

    def square(self, profile, corner=.0015):
        rings = []
        for z,r in profile:
            ring=[]
            for quadrant in range(4):
                sx=1 if quadrant in (0,3) else -1
                sy=1 if quadrant in (0,1) else -1
                for step in range(4):
                    angle=quadrant*math.pi/2+step*math.pi/6
                    ring.append(self.vertex((sx*(r-corner)+corner*math.cos(angle),sy*(r-corner)+corner*math.sin(angle),z)))
            rings.append(ring)
        for low,high in zip(rings,rings[1:]):
            for a in range(16): self.face((low[a],low[(a+1)%16],high[(a+1)%16],high[a]))
        self.face(reversed(rings[0])); self.face(rings[-1])


def smooth_profile(points, tolerance=.00035):
    """Monotone cubic radii retain beads/cavettos without resampling Meshy noise."""
    slopes=[(b[1]-a[1])/(b[0]-a[0]) for a,b in zip(points,points[1:])]
    derivatives=[slopes[0]]
    for left,right in zip(slopes,slopes[1:]):
        derivatives.append(0 if left*right<=0 else math.copysign(min(abs((left+right)/2),3*abs(left),3*abs(right)),left))
    derivatives.append(slopes[-1])
    dense=[]
    for i,((z0,r0),(z1,r1)) in enumerate(zip(points,points[1:])):
        for step in range(6):
            t=step/6; dz=z1-z0
            r=(2*t**3-3*t*t+1)*r0+(t**3-2*t*t+t)*dz*derivatives[i]+(-2*t**3+3*t*t)*r1+(t**3-t*t)*dz*derivatives[i+1]
            dense.append((z0+t*dz,r))
    dense.append(points[-1])
    def simplify(p):
        if len(p)<3:return p
        a,b=p[0],p[-1];dx=b[0]-a[0];dy=b[1]-a[1];length=math.hypot(dx,dy)
        distances=[abs(dx*(q[1]-a[1])-dy*(q[0]-a[0]))/length for q in p]
        index=max(range(len(p)),key=lambda j:distances[j])
        if distances[index]<=tolerance:return [a,b]
        return simplify(p[:index+1])[:-1]+simplify(p[index:])
    return simplify(dense)


def load_helper(name):
    spec=importlib.util.spec_from_file_location(name,ROOT/"tools"/(name+".py"))
    module=importlib.util.module_from_spec(spec);sys.modules[name]=module;spec.loader.exec_module(module)
    return module


def build(style, source_path=None):
    builder=Builder();width,depth,foot,height=DIMENSIONS[style]
    if style=="sphaira":
        centre=.67315838;radius=1-centre
        profile=[(centre-radius*math.cos(i*math.pi/24),radius*math.sin(i*math.pi/24)) for i in range(25)]
        builder.lathe(profile,48,depth/width)
        features={"sphere_centre_y":centre,"sphere_radius":radius,"sphere_longitudes":48,"sphere_latitude_bands":24,"flat_face_indices":[]}
    else:
        if style=="phlox": features=load_helper("finial_flame_profile").build_flame(builder,z0=.22,z1=1,max_radius=width/2)
        elif style=="balanos":
            if source_path is None: raise ValueError("Balanos needs its original carved cup source")
            features=load_helper("finial_balanos_relief").build_acorn_and_cup(builder,source_path,z0=.298,z1=1,max_radius=width/2)
            bpy.ops.wm.read_factory_settings(use_empty=True)
        elif style=="strobilos": features=load_helper("finial_strobilos_profile").build_strobilos(builder,z0=.272,z1=1,max_radius=max(width,depth)/2)
        else: features=load_helper("finial_ornament_profiles").build_ornament(style,builder,z0=.272,z1=1,max_radius=max(width,depth)/2)
        # Clean ornament bounds keep the existing save/collision footprint. The
        # separately constructed base remains centred and keeps its exact foot width.
        lower=[min(v[i] for v in builder.vertices) for i in range(3)]
        upper=[max(v[i] for v in builder.vertices) for i in range(3)]
        builder.vertices=[((v[0]-(lower[0]+upper[0])/2)*width/(upper[0]-lower[0]),(v[1]-(lower[1]+upper[1])/2)*depth/(upper[1]-lower[1]),v[2]) for v in builder.vertices]
        builder.ids={tuple(round(v,10) for v in p):i for i,p in enumerate(builder.vertices)}
    ornament_vertex_count=len(builder.vertices)
    if style=="sphaira":
        builder.square([(0,foot/2-.0015),(.0015,foot/2),(.0985,foot/2),(.10,foot/2-.0015),(.102, .282),(.115,.282),(.117,.266),(.161,.266),(.164,.264)])
        profile=smooth_profile(PROFILES[style])
        builder.lathe(profile,40,depth/width)
        base_features={"foot_width":foot,"square_foot_height":height,"turned_profile":profile,
                       "profile_landmarks":[{"label":"square_foot","y":height/2,"width":foot,"depth":foot},
                                            {"label":"turned_neck","y":.27,"width":.482,"depth":.482*depth/width}]}
    else:
        base_features=load_helper("finial_base_profiles").build_base(builder,style,width,depth,foot,height)
    mesh=bpy.data.meshes.new("Measured finial surfaces");mesh.from_pydata(builder.vertices,[],builder.faces);mesh.update()
    obj=bpy.data.objects.new("Finial "+style+" - clean reconstruction",mesh);bpy.context.collection.objects.link(obj)
    bpy.context.view_layer.objects.active=obj;obj.select_set(True)
    flat=set(features.pop("flat_face_indices",[]))
    dressed_creases={tuple(sorted(pair)) for pair in features.pop("sharp_edge_pairs",[])}
    smooth_front_edges={tuple(sorted(pair)) for pair in features.pop("smooth_front_edge_pairs",[])}
    for polygon in mesh.polygons:polygon.use_smooth=polygon.index not in flat
    mesh.set_sharp_from_angle(angle=math.radians(40))
    if style=="kynara":
        # The angle operation can enable smoothing. Preserve the intentionally
        # flat cut walls so the leaf fronts retain a clear dressed edge.
        for index in flat:mesh.polygons[index].use_smooth=False
        sharp=mesh.attributes.get("sharp_edge") or mesh.attributes.new("sharp_edge","BOOLEAN","EDGE")
        for edge in mesh.edges:
            if tuple(sorted(edge.vertices)) in smooth_front_edges:sharp.data[edge.index].value=False
            if tuple(sorted(edge.vertices)) in dressed_creases:sharp.data[edge.index].value=True
        features["dressed_leaf_crease_edges"]=len(dressed_creases)
        features["smooth_carved_front_edges"]=len(smooth_front_edges)
    elif style=="strobilos":
        # Shallow lower diamonds miss the generic 40-degree split. Keep their
        # authored centre ridge readable without faceting the horizontal joins.
        sharp = mesh.attributes.get("sharp_edge") or mesh.attributes.new("sharp_edge", "BOOLEAN", "EDGE")
        for edge in mesh.edges:
            if tuple(sorted(edge.vertices)) in dressed_creases:
                sharp.data[edge.index].value = True
        features["lower_meridional_ridge_edges"] = len(dressed_creases)
    if style=="phlox":
        # Its six spiral valleys are continuous curved stone. Automatic angle
        # thresholds otherwise trace a stair-step crease across the regular grid.
        sharp=mesh.attributes.get("sharp_edge")
        if sharp:
            for edge in mesh.edges:
                if all(index<ornament_vertex_count for index in edge.vertices):
                    sharp.data[edge.index].value=False
    material=bpy.data.materials.new("none");material.diffuse_color=(.66,.64,.59,1);mesh.materials.append(material)
    obj["Source provenance"]="Oliver-authored Meshy creation; measured clean reconstruction"
    obj["Editable face budget"]=6000;obj["style"]=style
    features.update(base_features)
    features["flat_ornament_faces"]=len(flat)
    return obj,features


def inspect(obj):
    mesh=obj.data;mesh.calc_loop_triangles()
    vertices=[v.co for v in mesh.vertices]
    lower=[min(v[i] for v in vertices) for i in range(3)];upper=[max(v[i] for v in vertices) for i in range(3)]
    bm=bmesh.new();bm.from_mesh(mesh)
    edges={"boundary":sum(e.is_boundary for e in bm.edges),"nonmanifold":sum(len(e.link_faces)>2 for e in bm.edges)}
    volume=bm.calc_volume(signed=True);bm.free()
    degenerates=sum(t.area<=1e-14 for t in mesh.loop_triangles)
    assert len(mesh.polygons)<=6000,("Face budget",len(mesh.polygons))
    assert edges=={"boundary":0,"nonmanifold":0},edges
    assert degenerates==0 and volume>0,(degenerates,volume)
    assert abs(lower[2])<1e-7 and abs(upper[2]-1)<1e-7,(lower,upper)
    return {"vertices":len(vertices),"normals":len(mesh.loops),"authored_polygons":len(mesh.polygons),"runtime_triangles":len(mesh.loop_triangles),"edge_counts":edges,"degenerate_triangles":degenerates,"signed_volume":volume,"bounds_obj_y_up":{"min":[lower[0],lower[2],-upper[1]],"max":[upper[0],upper[2],-lower[1]],"size":[upper[0]-lower[0],upper[2]-lower[2],upper[1]-lower[1]]}}


def export(obj,path):
    mesh=obj.data;mesh.calc_loop_triangles()
    lines=["# Measured clean finial reconstruction; Oliver-authored source\n","# Y-up; material none; authored corner normals; no UVs\n","o finial_"+obj["style"]+"\n"]
    lines += [f"v {v.co.x:.9f} {v.co.z:.9f} {-v.co.y:.9f}\n" for v in mesh.vertices]
    lines += [f"vn {n.vector.x:.9f} {n.vector.z:.9f} {-n.vector.y:.9f}\n" for n in mesh.corner_normals]
    lines += ["usemtl none\n","s off\n"]
    lines += ["f "+" ".join(f"{v+1}//{loop+1}" for v,loop in zip(t.vertices,t.loops))+"\n" for t in mesh.loop_triangles]
    path.write_text("".join(lines),encoding="utf-8",newline="\n")


def preview(obj,output):
    scene=bpy.context.scene;scene.unit_settings.system="METRIC"
    scene.render.engine="BLENDER_WORKBENCH";scene.render.resolution_x=800;scene.render.resolution_y=1000;scene.render.resolution_percentage=100
    scene.render.image_settings.file_format="PNG"
    shading=scene.display.shading;shading.light="STUDIO";shading.studio_light="paint.sl";shading.color_type="SINGLE";shading.single_color=(.68,.66,.61)
    shading.show_shadows=True;shading.show_cavity=True;shading.cavity_type="BOTH";shading.curvature_ridge_factor=1.1;shading.curvature_valley_factor=1.1
    shading.background_type="WORLD";scene.world=bpy.data.worlds.new("Neutral review world");scene.world.color=(.85,.85,.85)
    data=bpy.data.cameras.new("Finial review camera");camera=bpy.data.objects.new("Finial review camera",data);scene.collection.objects.link(camera);scene.camera=camera;data.type="ORTHO";data.ortho_scale=1.25
    for view,offset in [("hero",(3,-5,2.5)),("front",(0,-5,0))]:
        target=Vector((0,0,.5));camera.location=target+Vector(offset);camera.rotation_euler=(target-camera.location).to_track_quat("-Z","Y").to_euler()
        scene.render.filepath=str(output/f"finial_{obj['style']}-{view}.png");bpy.ops.render.render(write_still=True)
    target=Vector((0,0,.5));camera.location=target+Vector((3,-5,2.5));camera.rotation_euler=(target-camera.location).to_track_quat("-Z","Y").to_euler()


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source-root",type=Path,required=True);parser.add_argument("--output-dir",type=Path,required=True)
    parser.add_argument("--style",choices=list(DIMENSIONS),required=True);parser.add_argument("--blender",type=Path)
    args=parser.parse_args(sys.argv[sys.argv.index("--")+1:] if bpy and "--" in sys.argv else None)
    if bpy is None:
        executable=args.blender or Path(r"C:\Program Files\Blender Foundation\Blender 5.2\blender.exe")
        return subprocess.run([str(executable),"--background","--factory-startup","--python-exit-code","1","--python",str(Path(__file__).resolve()),"--","--source-root",str(args.source_root),"--output-dir",str(args.output_dir),"--style",args.style],check=False).returncode
    assert bpy.app.version==(5,2,0) and bpy.app.build_hash.decode()=="fbe6228777e7"
    source=SOURCES[args.style];source_path=args.source_root/Path(source["source_blend"]).parent.name/"Meshy_AI_model.blend"
    assert sha(source_path)==source["source_sha256"],"Source identity changed"
    output=args.output_dir.resolve();output.mkdir(parents=True,exist_ok=True)
    assert source_path.resolve().parent not in output.parents and output!=source_path.resolve().parent
    bpy.ops.wm.open_mainfile(filepath=str(source_path),load_ui=False)
    source_object=next(o for o in bpy.context.scene.objects if o.type=="MESH")
    source_polygons=len(source_object.data.polygons)
    bpy.ops.wm.read_factory_settings(use_empty=True)
    obj,features=build(args.style,source_path);report=inspect(obj)
    objpath=output/f"finial_{args.style}.obj";export(obj,objpath);preview(obj,output)
    bpy.context.preferences.filepaths.save_version=0
    blend=output/f"finial_{args.style} - clean architectural model.blend";bpy.ops.wm.save_as_mainfile(filepath=str(blend))
    assert sha(source_path)==source["source_sha256"],"Source changed during reconstruction"
    report.update({"style":args.style,"source_uuid":source_path.parent.name,"source_sha256":sha(source_path),"source_polygons":source_polygons,"source_files_modified":False,"runtime_obj_sha256":sha(objpath),"blend_sha256":sha(blend),"feature_retention":features})
    (output/f"finial_{args.style}-report.json").write_text(json.dumps(report,indent=2)+"\n",encoding="utf-8")
    print("FINIAL_REBUILD_OK",json.dumps({k:report[k] for k in ("style","authored_polygons","runtime_triangles","runtime_obj_sha256")}))
    return 0


if __name__=="__main__":
    raise SystemExit(main())
