"""Prepare the approved repeated-panel Anthophoros using Blender 5.2.

Run Blender with --background --factory-startup --disable-autoexec --python
this_script -- --input source.obj --output-dir destination [--width 3].
The source stays read-only. Only plain panel margins change width; source
reliefs repeat before the explicitly approved 15,000-triangle decimation.
"""
import argparse
import hashlib
import json
import sys
from pathlib import Path
import bpy
import bmesh
from mathutils import Vector
from mathutils.bvhtree import BVHTree
from mathutils.geometry import barycentric_transform

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--input', type=Path, required=True)
parser.add_argument('--output-dir', type=Path, required=True)
parser.add_argument('--width', type=int, choices=(2, 3, 4))
args = parser.parse_args(sys.argv[sys.argv.index('--') + 1:])
assert tuple(bpy.app.version) == (5, 2, 0) and bpy.app.build_hash == b'fbe6228777e7'
source = args.input.resolve()
output = args.output_dir.resolve()
output.mkdir(parents=True, exist_ok=True)
source_hash = hashlib.sha256(source.read_bytes()).hexdigest()
bpy.ops.object.select_all(action='SELECT')
bpy.ops.object.delete(use_global=False)
bpy.ops.wm.obj_import(filepath=str(source))
original = next(o for o in bpy.context.scene.objects if o.type == 'MESH')
raw = [original.matrix_world @ v.co for v in original.data.vertices]
lo = Vector(tuple(min(p[i] for p in raw) for i in range(3)))
hi = Vector(tuple(max(p[i] for p in raw) for i in range(3)))
mid = (hi + lo) / 2
factor = 1 / (hi.z - lo.z)
positions = [Vector(((p.x-mid.x)*factor, (p.y-mid.y)*factor, (p.z-lo.z)*factor)) for p in raw]
normal_matrix = original.matrix_world.to_3x3().inverted().transposed()
normals = [(normal_matrix @ n.vector).normalized() for n in original.data.corner_normals]
triangles = [[(positions[original.data.loops[i].vertex_index], normals[i])
              for i in face.loop_indices] for face in original.data.polygons]
assert all(len(face) == 3 for face in triangles)
source_half = (hi.x-lo.x)*factor/2
depth = (hi.y-lo.y)*factor


def clip(poly, boundary, keep_greater):
    result = []
    for a,b in zip(poly, poly[1:]+poly[:1]):
        ia = (a[0].x >= boundary) if keep_greater else (a[0].x <= boundary)
        ib = (b[0].x >= boundary) if keep_greater else (b[0].x <= boundary)
        if ia:
            result.append(a)
        if ia != ib:
            t = (boundary-a[0].x)/(b[0].x-a[0].x)
            result.append((a[0].lerp(b[0], t), a[1].lerp(b[1], t).normalized()))
    return result


def section(x0, x1, center, target_center, target_width=None, core=0):
    rows = []
    outer = (x1-x0)/2
    margin_scale = ((target_width/2-core)/(outer-core)) if target_width else 1
    assert margin_scale > 0
    for face in triangles:
        if max(p.x for p,n in face) < x0 or min(p.x for p,n in face) > x1:
            continue
        poly = clip(clip(face, x0, True), x1, False)
        # Split at derivative changes so triangles never bend across a relief boundary.
        pieces = [poly]
        if target_width:
            for boundary in (center-core, center+core):
                pieces = [part for piece in pieces for part in
                          (clip(piece, boundary, False), clip(piece, boundary, True)) if len(part) >= 3]
        for piece in pieces:
            transformed = []
            for p,n in piece:
                x = p.x-center
                scale = margin_scale if abs(x) > core+1e-8 else 1
                if target_width and abs(x) > core:
                    x = (1 if x > 0 else -1)*(core+(abs(x)-core)*margin_scale)
                transformed.append((Vector((target_center+x, p.y, p.z)),
                                    Vector((n.x/scale,n.y,n.z)).normalized()))
            for i in range(1,len(transformed)-1):
                tri = [transformed[0], transformed[i], transformed[i+1]]
                if (tri[1][0]-tri[0][0]).cross(tri[2][0]-tri[0][0]).length_squared > 1e-16:
                    rows.append(tri)
    return rows


def make_object(name, rows):
    vertices = [p for tri in rows for p,n in tri]
    mesh = bpy.data.meshes.new(name)
    mesh.from_pydata(vertices, [], [tuple(range(i,i+3)) for i in range(0,len(vertices),3)])
    mesh.update()
    for face in mesh.polygons:
        face.use_smooth = True
    mesh.normals_split_custom_set([n for tri in rows for p,n in tri])
    obj = bpy.data.objects.new(name, mesh)
    bpy.context.collection.objects.link(obj)
    return obj


def finish_mesh(obj, width, budget, joints):
    source_points = [v.co.copy() for v in obj.data.vertices]
    source_faces = [tuple(f.vertices) for f in obj.data.polygons]
    source_normals = [[obj.data.corner_normals[i].vector.copy() for i in f.loop_indices]
                      for f in obj.data.polygons]
    surface = BVHTree.FromPolygons(source_points, source_faces, all_triangles=True)
    bpy.ops.object.select_all(action='DESELECT')
    obj.select_set(True)
    bpy.context.view_layer.objects.active = obj
    bm = bmesh.new()
    bm.from_mesh(obj.data)
    # Join the clipped triangle vertices before collapse decimation. Otherwise
    # each triangle would be an independent island and could not simplify.
    bmesh.ops.remove_doubles(bm, verts=list(bm.verts), dist=2e-6)
    seam_edges = [e for e in bm.edges if e.is_boundary and
                  any(all(abs(v.co.x-x)<1e-5 for v in e.verts) for x in joints)]
    bmesh.ops.holes_fill(bm, edges=seam_edges, sides=0)
    bmesh.ops.triangulate(bm, faces=list(bm.faces))
    bm.to_mesh(obj.data)
    bm.free()
    # One bounded collapse pass, followed only by a small budget correction.
    for _ in range(4):
        count = len(obj.data.polygons)
        if count <= budget:
            break
        modifier = obj.modifiers.new('Approved triangle budget', 'DECIMATE')
        modifier.decimate_type = 'COLLAPSE'
        modifier.ratio = (budget-2)/count
        modifier.use_collapse_triangulate = True
        bpy.ops.object.modifier_apply(modifier=modifier.name)
    assert len(obj.data.polygons) <= budget
    # Collapse may move an extremal vertex slightly. Restore exact metric bounds.
    low = Vector(tuple(min(v.co[i] for v in obj.data.vertices) for i in range(3)))
    high = Vector(tuple(max(v.co[i] for v in obj.data.vertices) for i in range(3)))
    scale = Vector((width/(high.x-low.x), depth/(high.y-low.y), 1/(high.z-low.z)))
    for vertex in obj.data.vertices:
        vertex.co = ((vertex.co.x-(high.x+low.x)/2)*scale.x,
                     (vertex.co.y-(high.y+low.y)/2)*scale.y,
                     (vertex.co.z-low.z)*scale.z)
    obj.data.update()
    # Welding/collapse changes topology, but must not replace the approved
    # crisp imported shading with an all-smooth normal field. Explicitly sample
    # the approved triangle/corner field rather than interpolating Blender's
    # packed custom-normal layer across topology-changing BMesh operations.
    corrected = []
    for polygon in obj.data.polygons:
        polygon.use_smooth = True
        # Pick the reference surface at the triangle centre, not each vertex:
        # a corner on a moulding edge can otherwise borrow the adjacent wall's
        # normal, making crisp flat panels look inflated after decimation.
        _, _, index, _ = surface.find_nearest(polygon.center)
        for loop_index in polygon.loop_indices:
            point = obj.data.vertices[obj.data.loops[loop_index].vertex_index].co
            normal = barycentric_transform(point,
                *(source_points[v] for v in source_faces[index]), *source_normals[index])
            corrected.append(normal.normalized())
    # Rebuild explicit corners: packed custom normals on the welded topology
    # must not smooth across the original hard architectural edges.
    rows = [[(obj.data.vertices[obj.data.loops[i].vertex_index].co.copy(), corrected[i])
             for i in polygon.loop_indices] for polygon in obj.data.polygons]
    replacement = make_object(obj.name + '_corners', rows)
    old_mesh = obj.data
    obj.data = replacement.data
    bpy.data.objects.remove(replacement, do_unlink=True)
    bpy.data.meshes.remove(old_mesh)
    obj.data.materials.clear()
    material = bpy.data.materials.get('none') or bpy.data.materials.new('none')
    material.diffuse_color = (.68,.64,.56,1)
    obj.data.materials.append(material)


def export_obj(obj):
    bpy.ops.object.select_all(action='DESELECT')
    obj.select_set(True)
    bpy.context.view_layer.objects.active = obj
    bpy.ops.wm.obj_export(filepath=str(output/f'{obj.name}.obj'), check_existing=False,
                          forward_axis='NEGATIVE_Z', up_axis='Y', export_selected_objects=True,
                          export_uv=False, export_normals=True, export_materials=True,
                          export_triangulated_mesh=True)
    definition = {
        'obj': f'daedalon:models/mesh/{obj.name}.obj',
        'mtl': f'daedalon:models/mesh/{obj.name}.mtl',
        'flip_v': True, 'repair_degenerate_uvs': True,
        'force_uv_projection': True, 'uv_projection': 'axis_stabilized_box',
        'box_projection_span': 4.0, 'face_oriented_uvs': True,
        'smooth_normals': False, 'fit_to_block': False,
        'scale': [1.0, 1.0, 1.0], 'translate': [0.5, 0.0, 0.5],
        'particle': 'daedalon:block/statue_spartan_promachos_aganite',
        'materials': {'none': 'daedalon:block/statue_spartan_promachos_aganite'},
    }
    (output/f'{obj.name}.json').write_text(json.dumps(definition, indent=2)+'\n', encoding='utf-8', newline="\n")


original.hide_render = True
original.hide_set(True)
prepared = []
metrics = []
item_triangles = None
for width, sequence in [(2, ['L','R']), (3, ['L','O','R']), (4, ['L','O','L','O','R'])]:
    if args.width and width != args.width:
        continue
    cap = .30
    cell = (width-2*cap)/len(sequence)
    rows = section(-source_half, -source_half+cap, -source_half+cap/2, -width/2+cap/2)
    rows += section(source_half-cap, source_half, source_half-cap/2, width/2-cap/2)
    for i, motif in enumerate(sequence):
        center, band, core = {'L':(-.662,.70,.292), 'R':(.662,.70,.292), 'O':(0,.59,.22)}[motif]
        target_center = -width/2+cap+(i+.5)*cell
        rows += section(center-band/2, center+band/2, center, target_center, cell, core)
    obj = make_object(f'anthophoros_{width}m', rows)
    joints = [-width/2+cap+i*cell for i in range(len(sequence)+1)]
    finish_mesh(obj, width, 15000, joints)
    prepared.append(obj)
    export_obj(obj)
    metric = {'width_m':width, 'height_m':1, 'depth_m':round(depth,6), 'panels':sequence,
              'triangles_before_cleanup':len(rows), 'triangles':len(obj.data.polygons), 'protected_garland_width_m':.584,
              'protected_rosette_width_m':.44}
    actual = [max(v.co[i] for v in obj.data.vertices)-min(v.co[i] for v in obj.data.vertices) for i in range(3)]
    assert abs(actual[0]-width)<1e-5 and abs(actual[1]-depth)<1e-5 and abs(actual[2]-1)<1e-5
    metrics.append(metric)
    if width == 3:
        item = obj.copy()
        item.data = obj.data.copy()
        item.name = 'anthophoros_item'
        bpy.context.collection.objects.link(item)
        finish_mesh(item, width, 2000, [])
        export_obj(item)
        item_triangles = len(item.data.polygons)
        item.hide_render = True
        item.hide_set(True)

scene = bpy.context.scene
scene.unit_settings.system = 'METRIC'
scene.render.engine = 'BLENDER_WORKBENCH'
scene.render.resolution_x = 1400
scene.render.resolution_y = 1200
scene.render.resolution_percentage = 100
scene.render.image_settings.file_format = 'PNG'
scene.display.shading.light = 'STUDIO'
scene.display.shading.color_type = 'MATERIAL'
scene.display.shading.show_shadows = True
scene.display.shading.show_cavity = True
scene.display.shading.cavity_type = 'BOTH'
scene.display.shading.background_type = 'WORLD'
scene.world.color = (.09,.09,.09)
stone = bpy.data.materials.new('Stone inspection material')
stone.diffuse_color = (.68,.64,.56,1)
label_material = bpy.data.materials.new('Label')
label_material.diffuse_color = (.9,.9,.9,1)
camera = bpy.data.objects.new('Comparison camera', bpy.data.cameras.new('Camera'))
scene.collection.objects.link(camera)
scene.camera = camera
camera.data.type = 'ORTHO'
camera.data.ortho_scale = 6.2
for obj in prepared:
    obj.data.materials.append(stone)

labels = []
for index, obj in enumerate(prepared):
    obj.location.z = (2-index)*1.65
    label = bpy.data.curves.new('Width label', type='FONT')
    label.body = obj.name.removeprefix('anthophoros_').replace('m', ' m')
    label.size = .15
    label.align_x = 'LEFT'
    text = bpy.data.objects.new(label.body, label)
    scene.collection.objects.link(text)
    text.data.materials.append(label_material)
    text.location = (-2.55,-.65,obj.location.z+.5)
    text.rotation_euler = (1.57079632679,0,0)
    labels.append(text)

center = Vector((-.15,0,2.1))
for name, direction in [('front',(0,-10,0)), ('angled',(3,-10,5.5))]:
    camera.location = center + Vector(direction)
    camera.rotation_euler = (center-camera.location).to_track_quat('-Z','Y').to_euler()
    scene.render.filepath = str(output/f'anthophoros_sizes_{name}.png')
    bpy.ops.render.render(write_still=True)
bpy.context.preferences.filepaths.save_version = 0
bpy.ops.wm.save_as_mainfile(filepath=str(output/'anthophoros_sizes.blend'))
assert hashlib.sha256(source.read_bytes()).hexdigest() == source_hash
(output/'anthophoros_sizes.json').write_text(json.dumps({'source_file':source.name,
    'source_sha256':source_hash,'source_modified':False,'models':metrics},indent=2)+'\n', encoding='utf-8', newline="\n")
if not args.width:
    evidence = {
        'source_file': source.name, 'source_sha256': source_hash,
        'source_files_modified': False,
        'ownership': 'User-created Meshy model; ownership confirmed for Daedalon use.',
        'preparation': {
            'tool': 'Blender 5.2.0 LTS', 'build': bpy.app.build_hash.decode(),
            'script': 'tools/prepare_anthophoros_mesh.py',
            'decimation': 'Blender Collapse; at most 15000 triangles per placed model',
            'normals': 'Reference triangle-centre corner normals retained after decimation; no runtime smoothing override',
            'height_meters': 1.0, 'depth_meters': round(depth, 6),
            'widths_meters': [2, 3, 4], 'models': metrics,
            'item_triangles': item_triangles, 'geometry_shared_across_materials': 55,
            'runtime': 'Static width/facing states, cached hollow five-box shell; no controller or ticks',
        },
        'outputs': {name: hashlib.sha256((output/name).read_bytes()).hexdigest()
                    for stem in ('anthophoros_2m', 'anthophoros_3m', 'anthophoros_4m', 'anthophoros_item')
                    for name in (stem+'.obj', stem+'.mtl', stem+'.json')},
    }
    (output/'anthophoros-source.json').write_text(json.dumps(evidence, indent=2)+'\n', encoding='utf-8', newline="\n")
print(json.dumps(metrics))
