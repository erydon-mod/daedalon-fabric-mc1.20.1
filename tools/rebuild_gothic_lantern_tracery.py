#!/usr/bin/env python3
"""Stage Oliver's Gothic lancet tracery in Blender 5.2; never overwrite inputs.

Run Blender --background --disable-autoexec --python-exit-code 1 --python this_file --
  --source-root <six approved aliases> --baseline-obj <approved Gothic OBJ>
  --output-dir <staging>. Review staged Blender files/renders before delivery.
The OBJ patch retains existing attribute declarations and all unaffected face
references. Only the old interior lead-tracery faces are replaced. This module's
OBJ fingerprint functions may also be imported by ordinary Python audits.
"""
from __future__ import annotations
import argparse,collections,hashlib,json,math,struct,sys
from pathlib import Path

NAMES={
 'pier':('gothic lantern - seated limestone and metal.blend','gothic lantern - clean seated.blend'),
 'wall':('gothic lantern - wall limestone and metal.blend','gothic lantern - clean wall.blend'),
 'hanging':('gothic lantern - hanging limestone and metal.blend','gothic lantern - clean hanging.blend')}

def sha(path):return hashlib.sha256(Path(path).read_bytes()).hexdigest()
def triangle_key(points):
 return tuple(sorted(tuple(f'{0.0 if abs(x)<1e-7 else x:.9f}' for x in p) for p in points))

def parse_obj(path):
 data={'v':[],'vt':[],'vn':[],'sections':[],'faces':[]};section=None;material=None
 for line in Path(path).read_text(encoding='utf-8').splitlines():
  f=line.split()
  if not f:continue
  if f[0] in ('v','vt','vn'):data[f[0]].append(line)
  elif f[0]=='o':
   section={'name':f[1],'header':[line],'faces':[]};data['sections'].append(section)
  elif f[0]=='f':
   assert section and len(f)==4
   refs=[tuple(int(i) if i else None for i in ref.split('/')) for ref in f[1:]]
   points=[tuple(map(float,data['v'][r[0]-1].split()[1:])) for r in refs]
   face={'line':line,'group':section['name'],'material':material,'refs':refs,'key':triangle_key(points)}
   section['faces'].append(face);data['faces'].append(face)
  elif section is not None and f[0] in ('g','usemtl','s'):
   section['header'].append(line)
   if f[0]=='usemtl':material=f[1]
 return data

def fingerprint_faces(data,faces):
 """Exact face references plus their original position/normal/UV declarations."""
 h=hashlib.sha256();count=0
 for face in faces:
  h.update((face['group']+'\0'+face['material']+'\n'+face['line']+'\n').encode());count+=1
  for ref in face['refs']:
   h.update((data['v'][ref[0]-1]+'\n').encode())
   if ref[1] is not None:h.update((data['vt'][ref[1]-1]+'\n').encode())
   if len(ref)>2 and ref[2] is not None:h.update((data['vn'][ref[2]-1]+'\n').encode())
 return {'triangles':count,'sha256':h.hexdigest()}

def role_fingerprints(data):
 return {name:fingerprint_faces(data,[f for f in data['faces'] if f['group']==name])
         for name in sorted({f['group'] for f in data['faces']})}

def patch_obj(baseline,destination,old_tris,new_tris,frame_tris):
 data=parse_obj(baseline);before=role_fingerprints(data)
 removals={m:collections.Counter(triangle_key([v[0] for v in tri]) for tri in old_tris[m]) for m in NAMES}
 frame_keys={m:collections.Counter(triangle_key([v[0] for v in tri]) for tri in frame_tris[m]) for m in NAMES}
 retained={};removed={m:0 for m in NAMES}
 for mount in NAMES:assert not(set(removals[mount]) & set(frame_keys[mount])),'Tracery overlaps retained frame triangles'
 for section in data['sections']:
  if not section['name'].endswith('_lead'):continue
  mount=section['name'].split('_')[0];keep=[]
  for face in section['faces']:
   if removals[mount][face['key']]:removals[mount][face['key']]-=1;removed[mount]+=1
   else:keep.append(face)
  assert not +removals[mount],('Unmatched original tracery triangles',mount,list((+removals[mount]).items())[:5])
  assert collections.Counter(f['key'] for f in keep)==frame_keys[mount],('Retained outer frame mismatch',mount)
  retained[mount]=fingerprint_faces(data,keep);section['faces']=keep
 positions={};normals={};new_faces={}
 for mount,tris in new_tris.items():
  lines=[]
  for tri in tris:
   refs=[]
   for p,n,_ in tri:
    pk=tuple(round(x,9) for x in p);nk=tuple(round(x,9) for x in n)
    vi=positions.setdefault(pk,len(data['v'])+len(positions)+1)
    ni=normals.setdefault(nk,len(data['vn'])+len(normals)+1)
    refs.append(f'{vi}//{ni}')
   lines.append('f '+' '.join(refs))
  new_faces[mount]=lines
 # Move existing declarations ahead of faces without changing any array order,
 # then append new attribute entries. Every retained face reference remains exact.
 lines=['# Oliver Meshy Gothic lantern; approved paired pointed lancets and circular quatrefoil.']
 lines+=data['v']+['v '+' '.join(f'{x:.9f}' for x in p) for p in positions]
 lines+=data['vt']+data['vn']+['vn '+' '.join(f'{x:.9f}' for x in n) for n in normals]
 for section in data['sections']:
  lines+=section['header']+[face['line'] for face in section['faces']]
  if section['name'].endswith('_lead'):lines+=new_faces[section['name'].split('_')[0]]
 destination.write_text('\n'.join(lines)+'\n',encoding='utf-8',newline='\n')
 result=parse_obj(destination);after=role_fingerprints(result)
 for name,fp in before.items():
  if not name.endswith('_lead'):assert after[name]==fp,('Unchanged role differs',name)
 for mount in NAMES:
  keep=[f for f in result['faces'] if f['group']==mount+'_lead' and f['refs'][0][0]<=len(data['v'])]
  assert fingerprint_faces(result,keep)==retained[mount]
 return {'before_sha256':sha(baseline),'sha256':sha(destination),'vertices':len(result['v']),
  'normals':len(result['vn']),'uvs':len(result['vt']),'groups':{k:v['triangles'] for k,v in after.items()},
  'before_role_fingerprints':before,'after_role_fingerprints':after,'retained_outer_frame_fingerprints':retained,
  'removed_old_tracery_triangles':removed,'added_tracery_triangles':{m:len(v) for m,v in new_tris.items()},
  'non_lead_roles_exact':True,'outer_frames_exact':True,
  'attribute_policy':'Original declarations and unaffected face references retained; new lead positions/normals appended.'}

def mesh_digest(o):
 h=hashlib.sha256();me=o.data
 for v in me.vertices:h.update(struct.pack('<3f',*v.co))
 for p in me.polygons:
  h.update(struct.pack('<'+str(len(p.vertices))+'I',*p.vertices));h.update(struct.pack('<I?',p.material_index,p.use_smooth))
 for n in me.corner_normals:h.update(struct.pack('<3f',*n.vector))
 for uv in me.uv_layers:
  for v in uv.data:h.update(struct.pack('<2f',*v.uv))
 for row in o.matrix_world:h.update(struct.pack('<4f',*row))
 return h.hexdigest()

def is_suspension(o):return o.name.startswith(('half_chain','full_chain','canopy'))

def component_counts(model):
 meshes=[o for o in model.all_objects if o.type=='MESH']
 result={}
 for role,objects in [('body',[o for o in meshes if o.get('lantern_component')!='flame' and not is_suspension(o)]),
                      ('rig',[o for o in meshes if is_suspension(o)]),
                      ('flame',[o for o in meshes if o.get('lantern_component')=='flame'])]:
  result[role]={'polygons':sum(len(o.data.polygons) for o in objects),'triangles':sum(len(p.vertices)-2 for o in objects for p in o.data.polygons)}
  if role=='flame':result[role]['mesh_objects']=len(objects)
 return result

def native_records(objects,mount,rear):
 from mathutils import Vector
 out=[]
 for o in sorted(objects,key=lambda x:x.name):
  me=o.data;me.calc_loop_triangles();normal_matrix=o.matrix_world.to_3x3().inverted().transposed()
  for tri in me.loop_triangles:
   row=[]
   for li in tri.loops:
    p=o.matrix_world@me.vertices[me.loops[li].vertex_index].co
    n=(normal_matrix@me.corner_normals[li].vector).normalized()
    q=Vector((p.y,p.z,p.x-rear if mount=='wall' else p.x));nn=Vector((n.y,n.z,n.x))
    for axis in range(3):
     if abs(q[axis])<1e-7:q[axis]=0
    if abs(q.y-1)<1e-7:q.y=1
    row.append((tuple(q),tuple(nn),None))
   out.append(row)
 return out

def make_tracery(model,lead,scale,offset,depth):
 import bpy,bmesh
 from mathutils import Vector
 def create(name,bm):
  bmesh.ops.recalc_face_normals(bm,faces=list(bm.faces))
  for f in bm.faces:f.smooth=True
  me=bpy.data.meshes.new(name);bm.to_mesh(me);bm.free();me.update()
  ob=bpy.data.objects.new(name,me);model.objects.link(ob)
  if lead:me.materials.append(lead)
  return ob
 def tube(name,points,radius,closed=False,sides=6):
  ps=[Vector((u*scale,depth,z*scale+offset)) for u,z in points];bm=bmesh.new();loops=[];plane=Vector((0,1,0))
  for i,p in enumerate(ps):
   prev=ps[(i-1)%len(ps)] if closed or i else p;nxt=ps[(i+1)%len(ps)] if closed or i<len(ps)-1 else p
   direction=(nxt-prev).normalized();other=direction.cross(plane).normalized()
   loops.append([bm.verts.new(p+radius*scale*(plane*math.cos(j*math.tau/sides)+other*math.sin(j*math.tau/sides))) for j in range(sides)])
  pairs=list(zip(loops,loops[1:]))
  if closed:pairs.append((loops[-1],loops[0]))
  else:bm.faces.new(list(reversed(loops[0])));bm.faces.new(loops[-1])
  for a,b in pairs:
   for j in range(sides):bm.faces.new((a[j],a[(j+1)%sides],b[(j+1)%sides],b[j]))
  return create(name,bm)
 def arch(centre,half=.057,spring=-.022,height=.100,steps=14):
  r=(half*half+height*height)/(2*half);cx=half-r;theta=math.atan2(height,-cx)
  right=[(cx+r*math.cos(theta*i/steps),spring+r*math.sin(theta*i/steps)) for i in range(steps+1)]
  return [(centre-u,z) for u,z in right]+[(centre+u,z) for u,z in reversed(right[:-1])]
 def quatrefoil(centre=.118,c=.018,r=.022,steps=16):
  q=c/2+math.sqrt(r*r-c*c/2)/math.sqrt(2);start=math.atan2(q-c,-q);end=math.atan2(q-c,q);out=[]
  for lobe in range(4):
   rot=-lobe*math.pi/2
   for j in range(steps):
    a=start+(end-start)*j/steps;u=r*math.cos(a);z=c+r*math.sin(a)
    out.append((u*math.cos(rot)-z*math.sin(rot),centre+u*math.sin(rot)+z*math.cos(rot)))
  return out
 pieces=[tube('New central mullion',[(0,-.428),(0,.081)],.0042),tube('New left lancet',arch(-.057),.0040),
  tube('New right lancet',arch(.057),.0040),tube('New circular quatrefoil',quatrefoil(),.0038,True),
  tube('New supported head',[(0,.155),(0,.203)],.0040)]
 base=pieces[0]
 for piece in pieces[1:]:
  bpy.context.view_layer.objects.active=base;base.select_set(True)
  mod=base.modifiers.new('Welded lead junction','BOOLEAN');mod.operation='UNION';mod.solver='EXACT';mod.object=piece
  bpy.ops.object.modifier_apply(modifier=mod.name);bpy.data.objects.remove(piece,do_unlink=True);base.select_set(False)
 bm=bmesh.new();bm.from_mesh(base.data);bmesh.ops.remove_doubles(bm,verts=list(bm.verts),dist=1e-7)
 bmesh.ops.dissolve_degenerate(bm,edges=list(bm.edges),dist=1e-8);bmesh.ops.recalc_face_normals(bm,faces=list(bm.faces))
 for f in bm.faces:f.smooth=True
 for e in bm.edges:
  if e.is_manifold and e.calc_face_angle()>.85:e.smooth=False
 bm.to_mesh(base.data);bm.free();base.data.update()
 template=base.data.copy();bpy.data.objects.remove(base,do_unlink=True);return template

def audit(o):
 import bmesh
 bm=bmesh.new();bm.from_mesh(o.data)
 result={'name':o.name,'role':'lead','vertices':len(bm.verts),'polygons':len(bm.faces),'triangles':sum(len(f.verts)-2 for f in bm.faces),
  'boundary':sum(e.is_boundary for e in bm.edges),'nonmanifold':sum(not e.is_manifold for e in bm.edges),'degenerate':sum(f.calc_area()<1e-12 for f in bm.faces)}
 remaining=set(bm.verts);components=0
 while remaining:
  stack=[remaining.pop()];components+=1
  while stack:
   v=stack.pop()
   for e in v.link_edges:
    other=e.other_vert(v)
    if other in remaining:remaining.remove(other);stack.append(other)
 result['components']=components;bm.free()
 assert not(result['boundary'] or result['nonmanifold'] or result['degenerate']) and components==1,result
 return result

def stage_native(source,destination,mount,finish,out,render,wall_rear):
 import bpy
 from mathutils import Vector,Matrix
 source_hash=sha(source);bpy.ops.wm.open_mainfile(filepath=str(source),load_ui=False);scene=bpy.context.scene
 model=next(c for c in bpy.data.collections if 'game components' in c.name)
 old=[o for o in model.all_objects if o.type=='MESH' and o.name.startswith('03 ')]
 frames=[o for o in model.all_objects if o.type=='MESH' and o.name.startswith('02 Layered pointed arch moulding ')]
 assert len(old)==len(frames)*5 and len(frames)==(4 if mount=='wall' else 6),'Source must be the approved old-tracery baseline'
 preserved={o.name:mesh_digest(o) for o in bpy.data.objects if o.type=='MESH' and o not in old}
 materials=list(old[0].data.materials);lead=materials[0] if materials else None;color=old[0].color[:]
 ref=next(o for o in frames if o.name.endswith(' 2'));ps=[ref.matrix_world@v.co for v in ref.data.vertices]
 scale=(max(p.z for p in ps)-min(p.z for p in ps))/.665;offset=min(p.z for p in ps)+.444*scale;depth=min(p.y for p in ps)+.002*scale
 rear=wall_rear if mount=='wall' else 0
 old_tris=native_records(old,mount,rear);frame_tris=native_records(frames,mount,rear)
 for o in old:bpy.data.objects.remove(o,do_unlink=True)
 template=make_tracery(model,lead,scale,offset,depth);new=[]
 for frame in frames:
  i=int(frame.name.rsplit(' ',1)[1]);degree=30+(i-1)*60;me=template.copy();me.transform(Matrix.Rotation(math.radians(degree-90),4,'Z'))
  ob=bpy.data.objects.new(f'03 Gothic twin lancets and quatrefoil {i}',me);model.objects.link(ob)
  ob['lantern_component']='lead';ob['material_role']='Paired pointed lancets, central mullion and supported circular quatrefoil';ob.color=color;new.append(ob)
 rows=[audit(o) for o in new];new_tris=native_records(new,mount,rear)
 assert all(mesh_digest(bpy.data.objects[name])==h for name,h in preserved.items())
 geometry_after={o.name:mesh_digest(o) for o in model.all_objects if o.type=='MESH' and o.get('lantern_component')!='flame' and not is_suspension(o)}
 counts=component_counts(model)
 original_preview=Path(scene.render.filepath).name
 scene['Tracery design']='Paired pointed lancets, continuous mullion and supported four-circular-lobed quatrefoil in black lead'
 scene['Gas flame ON']=True;scene.update_tag();scene.frame_set(scene.frame_current);bpy.context.view_layer.update()
 if scene.render.engine=='CYCLES':
  scene.cycles.samples=48;scene.cycles.use_denoising=True
  try:
   prefs=bpy.context.preferences.addons['cycles'].preferences;prefs.compute_device_type='OPTIX';prefs.get_devices()
   for device in prefs.devices:device.use=device.type=='OPTIX'
   scene.cycles.device='GPU'
  except Exception:scene.cycles.device='CPU'
 if render:
  saved_path=scene.render.filepath;scene.render.filepath=str(out/original_preview);bpy.ops.render.render(write_still=True);scene.render.filepath=saved_path
  if finish=='material':
   camera=scene.camera;loc=camera.location.copy();rot=camera.rotation_euler.copy();ortho=camera.data.ortho_scale;res=(scene.render.resolution_x,scene.render.resolution_y)
   n=Vector((math.cos(math.radians(330)),math.sin(math.radians(330)),0));target=n*depth+Vector((0,0,.545))
   camera.location=target+n*4;camera.rotation_euler=(target-camera.location).to_track_quat('-Z','Y').to_euler();camera.data.ortho_scale=.265
   scene.render.resolution_x=scene.render.resolution_y=1100;scene.render.filepath=str(out/f'gothic-{mount}-new-tracery-detail.png');bpy.ops.render.render(write_still=True)
   camera.location=loc;camera.rotation_euler=rot;camera.data.ortho_scale=ortho;scene.render.resolution_x,scene.render.resolution_y=res;scene.render.filepath=saved_path
 bpy.context.preferences.filepaths.save_version=0;bpy.ops.wm.save_as_mainfile(filepath=str(destination),compress=True,check_existing=False)
 assert sha(source)==source_hash
 return {'mount':mount,'finish':finish,'file':source.name,'source_sha256':source_hash,'delivery_sha256':sha(destination),'preview':original_preview,
  'retained_native_mesh_fingerprints':preserved,'retained_meshes_exact':True,'new_components':rows,'counts':counts,
  'body_mesh_digests':geometry_after,'bar_diameters_m':{'mullion':.0084*scale,'lancets':.0080*scale,'quatrefoil':.0076*scale}},old_tris,new_tris,frame_tris

def main():
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--source-root',type=Path,required=True)
 parser.add_argument('--baseline-obj',type=Path,required=True);parser.add_argument('--output-dir',type=Path,required=True)
 parser.add_argument('--source-evidence',type=Path,default=Path(__file__).resolve().parents[1]/'docs/evidence/lantern-source.json')
 parser.add_argument('--no-render',action='store_true');parser.add_argument('--patch-only',action='store_true')
 args=parser.parse_args(sys.argv[sys.argv.index('--')+1:] if '--' in sys.argv else sys.argv[1:])
 out=args.output_dir.resolve();out.mkdir(parents=True,exist_ok=True);old={};new={};frames={};files=[]
 profile=next(m for m in json.loads(args.source_evidence.read_text())['families']['gothic']['mounts'] if m['mount']=='wall')
 # Match the original double-precision cut offset, not the slightly rounded saved cap.
 wall_rear=profile['source_original_bounds'][0][0]*profile['normalization_scale']
 if args.patch_only:
  import bpy
  staged=json.loads((out/'native-tracery-staging.json').read_text())['files']
  for mount,names in NAMES.items():
   source=args.source_root/names[0];destination=out/names[0]
   row=next(x for x in staged if x['mount']==mount and x['finish']=='material')
   assert sha(source)==row['source_sha256'] and sha(destination)==row['delivery_sha256']
   bpy.ops.wm.open_mainfile(filepath=str(source),load_ui=False)
   model=next(c for c in bpy.data.collections if 'game components' in c.name)
   rear=wall_rear if mount=='wall' else 0
   old[mount]=native_records([o for o in model.all_objects if o.type=='MESH' and o.name.startswith('03 ')],mount,rear)
   frames[mount]=native_records([o for o in model.all_objects if o.type=='MESH' and o.name.startswith('02 Layered pointed arch moulding ')],mount,rear)
   bpy.ops.wm.open_mainfile(filepath=str(destination),load_ui=False)
   model=next(c for c in bpy.data.collections if 'game components' in c.name)
   new[mount]=native_records([o for o in model.all_objects if o.type=='MESH' and o.name.startswith('03 ')],mount,rear)
  report=patch_obj(args.baseline_obj,out/'lantern_gothic.obj',old,new,frames)
  (out/'gothic-tracery-obj-patch.json').write_text(json.dumps(report,indent=2)+'\n')
  print('GOTHIC_TRACERY_PATCHED',json.dumps({'obj_sha256':report['sha256'],'groups':report['groups']}),flush=True)
  return
 for mount,names in NAMES.items():
  for finish,name in zip(('material','clean'),names):
   record,a,b,c=stage_native(args.source_root/name,out/name,mount,finish,out,not args.no_render,wall_rear);files.append(record)
   if finish=='material':old[mount]=a;new[mount]=b;frames[mount]=c
   (out/'native-tracery-staging.json').write_text(json.dumps({'files':files},indent=2)+'\n');print('TRACERY_ALIAS_READY',mount,finish,flush=True)
 report=patch_obj(args.baseline_obj,out/'lantern_gothic.obj',old,new,frames)
 (out/'gothic-tracery-obj-patch.json').write_text(json.dumps(report,indent=2)+'\n')
 print('GOTHIC_TRACERY_STAGED',json.dumps({'obj_sha256':report['sha256'],'groups':report['groups']}),flush=True)

if __name__=='__main__':main()
