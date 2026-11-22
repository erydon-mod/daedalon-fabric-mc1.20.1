"""Independently author Daedalon's Diota; no imported geometry or image tracing.

Run ordinary Python with --output-dir <directory>. Blender 5.2 supplies corner
normals and topology validation only. The numeric design below is the editable
source: a deeply fluted open vessel, articulated neck and lip, moulded square
plinth and little hanging bronze rings on pierced stone lugs. Kylix and Kalyx
are not inputs.
"""
from __future__ import annotations

import argparse
from collections import Counter
import hashlib
import json
import math
from pathlib import Path
import struct
import subprocess
import sys
import zlib

sys.path.insert(0, str(Path(__file__).resolve().parent))
from diota_handle_design import build_handles

try:
    import bpy
    import bmesh
except ImportError:
    bpy = None

ROOT = Path(__file__).resolve().parents[1]
BLENDER = Path(r"C:\Program Files\Blender Foundation\Blender 5.2\blender.exe")
# Compatibility envelope measured before redesign, in fitted medium-size metres.
TARGET_SIZE = (.582233804, 1., .547980138)
RUNTIME_TRIANGLE_BUDGET = 6000
PROVENANCE = (
    "New procedural geometry authored by Codex at Oliver's direction for Daedalon; "
    "not a Meshy output, imported mesh, retopology, trace or deformation."
)
# (height, radius), travelling from the underside through the exterior, around
# the open lip and down the interior to its floor. These are design parameters,
# not samples recovered from an existing mesh. The opening is genuine geometry.
EXTERIOR_PROFILE = [
    (.13,0),(.13,.163),(.136,.176),(.147,.178),(.156,.173),
    (.164,.157),(.20,.114),(.221,.094),(.239,.082),
    (.253,.078),(.272,.078),(.284,.094),(.294,.094),
    (.301,.086),(.311,.09),(.335,.13),(.352,.14),
    (.38,.151),(.415,.166),(.455,.182),(.50,.198),(.545,.212),
    (.59,.224),(.632,.233),(.67,.239),(.707,.243),(.737,.245),
    (.753,.245),(.777,.225),(.792,.204),
    (.821,.147),(.831,.146),(.843,.161),(.853,.161),
    (.86,.151),(.889,.15),(.902,.155),(.93,.184),
    (.952,.205),(.961,.207),(.972,.207),(.978,.201),
    (.982,.190),(.991,.190),(1.,.185),(1.,.168),
]
INTERIOR_PROFILE = [
    (1.,.168),(.99,.167),(.978,.181),(.964,.184),(.95,.179),
    (.916,.145),(.897,.133),(.865,.132),(.835,.126),
    (.814,.146),(.794,.181),(.77,.204),(.737,.212),
    (.66,.202),(.56,.179),(.46,.145),
    (.418,.124),(.389,.097),(.372,.061),(.367,0),
]
BODY_PROFILE = EXTERIOR_PROFILE + INTERIOR_PROFILE[1:]


def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


class Builder:
    """One Y-up shared mesh; weld coincident vertices within each component."""
    def __init__(self):
        self.vertices, self.faces, self.materials, self.ids = [], [], [], {}

    def vertex(self, point):
        key = tuple(round(float(v), 10) for v in point)
        if key not in self.ids:
            self.ids[key] = len(self.vertices)
            self.vertices.append(key)
        return self.ids[key]

    def face(self, points, material="none"):
        indices = tuple(dict.fromkeys(points))
        if len(indices) >= 3:
            self.faces.append(indices)
            self.materials.append(material)

    def bridge(self, low, high):
        """Stitch unequal concentric rings without T junctions or spare faces."""
        i=j=0
        while i < len(low) or j < len(high):
            left=(i+1)*len(high);right=(j+1)*len(low)
            if left==right:
                self.face((low[i%len(low)],high[j%len(high)],
                           high[(j+1)%len(high)],low[(i+1)%len(low)]))
                i+=1;j+=1
            elif left<right:
                self.face((low[i%len(low)],high[j%len(high)],low[(i+1)%len(low)]))
                i+=1
            else:
                self.face((low[i%len(low)],high[j%len(high)],high[(j+1)%len(high)]))
                j+=1

    def lathe(self, profile, segments=32, flutes=False, phase=0, interior=False):
        rings = []
        for y, radius in profile:
            ring = []
            count=24 if interior or y<.335 else 48 if flutes and .335<=y<=.753 else segments
            # The open rim's innermost ring is shared by both surfaces. Keep
            # its angular samples identical so reducing the hidden interior
            # cannot leave cracks or unmatched edges around the mouth.
            if y==1. and radius==.168:
                count=24
            for i in range(count):
                a = 2*math.pi*i/count + phase
                # Deep concave channels grow out of the lower calyx and stop
                # at a quiet shoulder band. Four samples describe each channel;
                # the deep grooves, crest positions and axial rings are kept.
                fade = math.sin(math.pi*(y-.335)/(.753-.335)) if .335<y<.753 else 0
                r = radius - (.017*fade*(.5-.5*math.cos(12*a)) if flutes else 0)
                ring.append(self.vertex((r*math.cos(a), y, r*math.sin(a))))
            rings.append(ring)
        for low, high in zip(rings, rings[1:]):
            self.bridge(low,high)

    def square_plinth(self):
        profile=[(0,.207),(.008,.22),(.028,.22),(.037,.214),
                 (.046,.198),(.088,.198),(.098,.191),(.13,.191)]
        rings=[]
        for y,r in profile:
            # Eight vertices retain flat architectural planes and small
            # chamfered corners; the body foot sits wholly on the top deck.
            c=.009
            points=[(r-c,r),(r,r-c),(r,-r+c),(r-c,-r),
                    (-r+c,-r),(-r,-r+c),(-r,r-c),(-r+c,r)]
            rings.append([self.vertex((x,y,z)) for x,z in reversed(points)])
        for low,high in zip(rings,rings[1:]):self.bridge(low,high)
        self.face(reversed(rings[0]));self.face(rings[-1])

    def palmettes(self):
        """Original paired seven-leaf shoulder reliefs, laid on the vessel.

        Each small almond is a closed solid with its back inside the stone.
        Project each vertex onto the numeric shoulder profile so leaves never
        float above a sloping shoulder. These are new motif parameters.
        """
        def radius(y):
            for (y0,r0),(y1,r1) in zip(EXTERIOR_PROFILE,EXTERIOR_PROFILE[1:]):
                if y0<=y<=y1 and y1>y0:
                    return r0+(r1-r0)*(y-y0)/(y1-y0)
            raise ValueError(("Shoulder motif outside profile",y))
        for side in (-1,1):
            for leaf in range(-3,4):
                angle=leaf*.39
                length=.050-.002*abs(leaf)
                width=.0068-.0006*abs(leaf)
                rings=[]
                for t in (0,.16,.32,.5,.68,.84,1):
                    taper=math.sin(math.pi*t)
                    cx=length*t*math.sin(angle)
                    cy=.773+length*t*math.cos(angle)
                    ring=[]
                    for j in range(4):
                        a=2*math.pi*j/4
                        cross=width*taper*math.cos(a)
                        x=cx+cross*math.cos(angle)
                        y=cy-cross*math.sin(angle)
                        z=math.sqrt(max(0,radius(y)**2-x*x))
                        relief=-.001+.003*taper+.0045*taper*math.sin(a)
                        ring.append(self.vertex((x,y,side*(z+relief))))
                    rings.append(ring)
                for low,high in zip(rings,rings[1:]):self.bridge(low,high)



def build():
    builder=Builder()
    builder.square_plinth()
    plinth_vertex_count=len(builder.vertices)
    builder.lathe(EXTERIOR_PROFILE,32,flutes=True)
    builder.lathe(INTERIOR_PROFILE,24,interior=True)
    builder.datum_vertex_limit=len(builder.vertices)
    builder.palmettes()
    builder.handle_measurements=build_handles(builder)
    low=[min(v[i] for v in builder.vertices) for i in range(3)]
    high=[max(v[i] for v in builder.vertices) for i in range(3)]
    # Design coordinates are normalized to the established envelope, never to
    # an input mesh. This preserves all existing medium/small/large placements.
    factors=[TARGET_SIZE[i]/(high[i]-low[i]) for i in range(3)]
    builder.vertices=[((v[0]-(low[0]+high[0])/2)*factors[0],(v[1]-low[1])*factors[1],(v[2]-(low[2]+high[2])/2)*factors[2]) for v in builder.vertices]
    # Preserve a genuinely square 440mm plinth despite the body's established
    # elliptical depth envelope. All modelling remains data in this script.
    for i in range(plinth_vertex_count):
        x,y,z=builder.vertices[i]
        builder.vertices[i]=(x/factors[0],y,z/factors[2])
    return builder


def png_swatch(color):
    """Author a fresh 16px scalar material swatch, without loading any pixels."""
    def chunk(kind,data):
        return struct.pack(">I",len(data))+kind+data+struct.pack(">I",zlib.crc32(kind+data)&0xffffffff)
    raw=b"".join(b"\0"+bytes(color)*16 for _ in range(16))
    return b"\x89PNG\r\n\x1a\n"+chunk(b"IHDR",struct.pack(">IIBBBBB",16,16,8,6,0,0,0))+chunk(b"IDAT",zlib.compress(raw,9))+chunk(b"IEND",b"")


def authored_object(builder):
    mesh=bpy.data.meshes.new("Diota independently authored surfaces")
    mesh.from_pydata([(x,-z,y) for x,y,z in builder.vertices],[],builder.faces)
    mesh.update()
    obj=bpy.data.objects.new("Diota - original Daedalon design",mesh)
    bpy.context.collection.objects.link(obj)
    for name,color in [("none",(.64,.62,.57,1)),("bronze_handles",(.55,.30,.09,1))]:
        material=bpy.data.materials.new(name);material.diffuse_color=color;mesh.materials.append(material)
    for polygon,material in zip(mesh.polygons,builder.materials):
        polygon.material_index=material=="bronze_handles"
        # The plinth has eight rings of eight vertices. Its planes and narrow
        # corner chamfers must stay flat instead of swelling like soft clay.
        polygon.use_smooth=not all(v<64 for v in polygon.vertices)
    mesh.set_sharp_from_angle(angle=math.radians(65))
    # Orient every closed component outward; mirror handles invert winding.
    bm=bmesh.new();bm.from_mesh(mesh)
    bmesh.ops.recalc_face_normals(bm,faces=list(bm.faces));bm.to_mesh(mesh);bm.free()
    # Crisp concentric fillets give the neck, rim and stem their moulded
    # section. Keep the flute/shoulder transition smooth at every angle.
    datums=(.284,.294,.843,.853,.86,.889,.961,.972,.982,.991,1.)
    sharp=mesh.attributes.get("sharp_edge")
    if sharp:
        for edge in mesh.edges:
            if max(edge.vertices)>=builder.datum_vertex_limit:
                continue
            a,b=(mesh.vertices[v].co for v in edge.vertices)
            if abs(a.z-b.z)<1e-7 and any(abs(a.z-y)<1e-7 for y in datums):
                sharp.data[edge.index].value=True
    # set_sharp_from_angle enables smooth faces. Restore planar architectural
    # surfaces AFTER that operation and after bmesh, so the exported normals
    # stay genuinely flat instead of rounding the plinth corners.
    for polygon in mesh.polygons:
        if all(v<64 for v in polygon.vertices):
            polygon.use_smooth=False
        elif max(polygon.vertices)<builder.datum_vertex_limit:
            heights=[mesh.vertices[v].co.z for v in polygon.vertices]
            if max(heights)-min(heights)<1e-7:
                polygon.use_smooth=False
    mesh.update();obj["Creation provenance"]=PROVENANCE
    return obj


def inspect(obj):
    mesh=obj.data;mesh.calc_loop_triangles()
    points=[(v.co.x,v.co.z,-v.co.y) for v in mesh.vertices]
    low=[min(p[i] for p in points) for i in range(3)]
    high=[max(p[i] for p in points) for i in range(3)]
    bm=bmesh.new();bm.from_mesh(mesh)
    edges={"boundary":sum(e.is_boundary for e in bm.edges),"nonmanifold":sum(len(e.link_faces)>2 for e in bm.edges)}
    volume=bm.calc_volume(signed=True);bm.free()
    degenerate=sum(t.area<1e-13 for t in mesh.loop_triangles)
    assert edges=={"boundary":0,"nonmanifold":0},edges
    assert volume>0 and degenerate==0,(volume,degenerate)
    assert len(mesh.polygons)<=6000,len(mesh.polygons)
    assert len(mesh.loop_triangles)<=RUNTIME_TRIANGLE_BUDGET,len(mesh.loop_triangles)
    counts=Counter(mesh.materials[mesh.polygons[t.polygon_index].material_index].name for t in mesh.loop_triangles)
    return {"vertices":len(mesh.vertices),"normals":len(mesh.loops),"authored_polygons":len(mesh.polygons),"runtime_triangles":len(mesh.loop_triangles),"material_triangles":dict(counts),"edge_counts":edges,"degenerate_triangles":degenerate,"signed_volume_m3":volume,
            "design_envelope_m":list(TARGET_SIZE),
            "bounds_obj_y_up":{"min":low,"max":high,"size":[high[i]-low[i] for i in range(3)]}}


def export(obj,path):
    mesh=obj.data;mesh.calc_loop_triangles()
    lines=["# Original Daedalon Diota; procedural design at Oliver's direction\n", "# Source: tools/author_classical_vessels.py; Y-up; runtime cylindrical UVs\n", "mtllib urn_diota.mtl\n", "o urn_diota\n"]
    lines += [f"v {v.co.x:.9f} {v.co.z:.9f} {-v.co.y:.9f}\n" for v in mesh.vertices]
    lines += [f"vn {n.vector.x:.9f} {n.vector.z:.9f} {-n.vector.y:.9f}\n" for n in mesh.corner_normals]
    lines.append("s off\n");current=None
    for triangle in mesh.loop_triangles:
        material=mesh.materials[mesh.polygons[triangle.polygon_index].material_index].name
        if material!=current:lines.append("usemtl "+material+"\n");current=material
        lines.append("f "+" ".join(f"{v+1}//{loop+1}" for v,loop in zip(triangle.vertices,triangle.loops))+"\n")
    path.write_text("".join(lines),encoding="utf-8",newline="\n")


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output-dir",type=Path,required=True)
    parser.add_argument("--save-blend",action="store_true")
    argv=sys.argv[sys.argv.index("--")+1:] if bpy and "--" in sys.argv else sys.argv[1:]
    args=parser.parse_args(argv)
    if bpy is None:
        return subprocess.run([str(BLENDER),"--background","--factory-startup","--disable-autoexec","--python-exit-code","1","--python",str(Path(__file__).resolve()),"--",*argv],check=False).returncode
    assert bpy.app.version==(5,2,0) and bpy.app.build_hash.decode()=="fbe6228777e7"
    bpy.ops.wm.read_factory_settings(use_empty=True)
    output=args.output_dir.resolve();output.mkdir(parents=True,exist_ok=True)
    builder=build();obj=authored_object(builder);report=inspect(obj)
    objpath=output/"urn_diota.obj";export(obj,objpath)
    mtl=output/"urn_diota.mtl"
    mtl.write_text("# Original Daedalon Diota material assignments\nnewmtl none\n\nnewmtl bronze_handles\nKd 0.898039 0.627451 0.113725\nmap_Kd ../../textures/block/urn_diota_bronze_handles.png\n",encoding="utf-8",newline="\n")
    for name,color in [("urn_diota_bronze_handles.png",(229,160,29,255)),("urn_diota_bronze_handles_s.png",(255,255,0,255))]:
        (output/name).write_bytes(png_swatch(color))
    report.update({"family":"diota","creation_provenance":PROVENANCE,"tool":"tools/author_classical_vessels.py","tool_sha256":sha(__file__),"blender_version":bpy.app.version_string,"blender_build_hash":bpy.app.build_hash.decode(),"runtime_obj_sha256":sha(objpath),"mtl_sha256":sha(mtl),"texture_sha256":{p.name:sha(p) for p in output.glob("urn_diota*.png")},"source_geometry_loaded":False,"source_facing":"paired palmettes face +Z/-Z; pierced lugs stand on +X/-X; rings hang outward; runtime Y-up; ground Y=0; centred support pivot X=Z=0"})
    report["handle_design"]=builder.handle_measurements
    report["design_source_sha256"]={name:sha(Path(__file__).with_name(name)) for name in ("author_classical_vessels.py","diota_handle_design.py")}
    (output/"urn_diota-report.json").write_text(json.dumps(report,indent=2)+"\n",encoding="utf-8")
    if args.save_blend:
        bpy.context.preferences.filepaths.save_version=0
        bpy.ops.wm.save_as_mainfile(filepath=str(output/"diota-original-design.blend"))
    print("DIOTA_AUTHORING_OK",json.dumps(report))
    return 0


if __name__=="__main__":
    raise SystemExit(main())
