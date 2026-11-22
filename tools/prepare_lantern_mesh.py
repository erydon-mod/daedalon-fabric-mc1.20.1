#!/usr/bin/env python3
"""Prepare Oliver's lantern families without per-stone geometry duplication.

Run with locked Blender 5.2, --background --disable-autoexec --python <this>
-- --source-root <approved originals> --style georgian --output-dir <staging>.
Outputs are staged; approved
Blender originals are never overwritten by this tool. Export is Y-up/front +Z.
Use --append-flame --style <style> after base preparation to append the optional
ON-only gas-flame groups. This mode preserves every existing OBJ byte and only
adds the new cards; Blender source helpers use ordinary bottom-origin UVs while
the runtime flame export explicitly uses PNG-top V0.
"""
from __future__ import annotations
import argparse, hashlib, json, math, struct, sys
from pathlib import Path, PureWindowsPath
import bpy, bmesh
from mathutils import Vector, Matrix

ROOT=Path(__file__).resolve().parents[1]
SOURCE=None
MATERIAL_SOURCE=None
TEMPLATE=None
NAMES={
 'georgian':{'pier':'georgian lantern 2 - clean seated.blend','wall':'georgian wall lantern 2 - gas concept.blend','hanging':'georgian lantern 2 - hanging limestone and metal.blend'},
 'gothic':{'pier':'gothic lantern - seated limestone and metal.blend','wall':'gothic lantern - wall limestone and metal.blend','hanging':'gothic lantern - hanging limestone and metal.blend'},
 'byzantine':{'pier':'byzantine lantern - seated limestone.blend','wall':'byzantine lantern - wall limestone.blend','hanging':'byzantine lantern - hanging limestone and metal.blend'}}
BRONZE=ROOT/'src/main/resources/assets/daedalon/textures/block/bronze.png'
STONE_NAME='Latmion - pale limestone - native mod texture'
LEAD_NAME='Black lead - iron-like fine metalwork - native mod texture'
CANON_BURNER_SCALE=1.3/1.902
BASE=-.586
FLAME_WIDTH=.035
FLAME_HEIGHT=.075
FLAME_MARKER=b'\n# Lantern gas flame append v1\n'
FLAME_TEXTURE=ROOT/'src/main/resources/assets/daedalon/textures/block/lantern_flame.png'

def sha(path):return hashlib.sha256(Path(path).read_bytes()).hexdigest()
def public_evidence(value,key=''):
 """Keep personal execution locations in external authoring records only."""
 if isinstance(value,dict):return {k:public_evidence(v,k) for k,v in value.items()}
 if isinstance(value,list):return [public_evidence(v,key) for v in value]
 if isinstance(value,str) and (Path(value).is_absolute() or PureWindowsPath(value).is_absolute()):
  name=PureWindowsPath(value).name if PureWindowsPath(value).is_absolute() else Path(value).name
  if key=='path' and name.endswith('.obj'):return 'models/mesh/'+name
  if key=='path' and name=='bronze.png':return 'textures/block/bronze.png'
  return name
 return value
def digest(o):
 h=hashlib.sha256()
 for v in o.data.vertices:h.update(struct.pack('<3f',*v.co))
 for p in o.data.polygons:h.update(struct.pack('<'+str(len(p.vertices))+'I',*p.vertices))
 return h.hexdigest()
def bbox(points):return [[min(p[i] for p in points) for i in range(3)],[max(p[i] for p in points) for i in range(3)]]
def points(objects):return [o.matrix_world@v.co for o in objects for v in o.data.vertices]
def nozzle_anchors(base):
 vertices=[];groups={};group=None
 for line in base.decode('utf-8').splitlines():
  fields=line.split()
  if not fields:continue
  if fields[0]=='v':vertices.append(tuple(map(float,fields[1:])))
  elif fields[0]=='o':group=fields[1]
  elif fields[0]=='f' and group and group.endswith('_bronze'):
   groups.setdefault(group,set()).update(int(x.split('/')[0])-1 for x in fields[1:])
 result={}
 for mount in ('pier','wall','hanging'):
  ps=[vertices[i] for i in groups[mount+'_bronze']];top=max(p[1] for p in ps);tips=[p for p in ps if abs(p[1]-top)<1e-8]
  result[mount]=[(min(p[0] for p in tips)+max(p[0] for p in tips))/2,top,(min(p[2] for p in tips)+max(p[2] for p in tips))/2]
 return result
def flame_cards(anchor):
 vertices=[];faces=[];normals=[]
 for i in range(3):
  a=i*math.pi/3;dx=math.cos(a)*FLAME_WIDTH/2;dz=math.sin(a)*FLAME_WIDTH/2;x,y,z=anchor;k=len(vertices)
  vertices.extend([(x-dx,y,z-dz),(x+dx,y,z+dz),(x+dx,y+FLAME_HEIGHT,z+dz),(x-dx,y+FLAME_HEIGHT,z-dz)])
  faces.extend([(k,k+1,k+2,k+3),(k+3,k+2,k+1,k)])
  normals.extend([(-math.sin(a),0,math.cos(a)),(math.sin(a),0,-math.cos(a))])
 return vertices,faces,normals
def append_flame_groups(path):
 """Append only: every approved pre-flame byte/vertex/face remains unchanged."""
 original=path.read_bytes();base=original.split(FLAME_MARKER,1)[0];anchors=nozzle_anchors(base)
 nv=sum(line.startswith(b'v ') for line in base.splitlines());nt=sum(line.startswith(b'vt ') for line in base.splitlines());nn=sum(line.startswith(b'vn ') for line in base.splitlines())
 lines=[];carddata={};metadata={}
 for mount,anchor in anchors.items():
  vertices,faces,normals=flame_cards(anchor);carddata[mount]=(vertices,faces,normals)
  lines.extend('v '+' '.join(f'{x:.9f}' for x in p) for p in vertices)
  metadata[mount]={'flame_anchor':anchor,'flame_bounds':bbox(vertices),'flame_triangles':12,'flame_authoring_polygons':6,'flame_width_m':FLAME_WIDTH,'flame_height_m':FLAME_HEIGHT,'flame_group':mount+'_flame','flame_uv_policy':'Runtime bottom V1 / top V0; Blender bottom V0 / top V1'}
 # Minecraft sprite V0 is its PNG top; invert only the newly authored flame.
 lines.extend(['vt 0.0000000 1.0000000','vt 1.0000000 1.0000000','vt 1.0000000 0.0000000','vt 0.0000000 0.0000000'])
 normals=carddata['pier'][2];lines.extend('vn '+' '.join(f'{x:.9f}' for x in p) for p in normals)
 for mi,(mount,(vertices,faces,normals)) in enumerate(carddata.items()):
  lines.extend([f'o {mount}_flame',f'g {mount}_flame','usemtl flame','s off'])
  for fi,face in enumerate(faces):
   for ids in ((0,1,2),(0,2,3)):
    refs=[f'{nv+mi*12+face[i]+1}/{nt+face[i]%4+1}/{nn+fi+1}' for i in ids];lines.append('f '+' '.join(refs))
 append=FLAME_MARKER+('\n'.join(lines)+'\n').encode('utf-8');path.write_bytes(base+append)
 assert path.read_bytes()[:len(base)]==base
 report={'path':str(path),'sha256':sha(path),'body_prefix_bytes':len(base),'body_prefix_sha256':hashlib.sha256(base).hexdigest(),'body_prefix_byte_identical':True,
  'vertices':nv+36,'normals':nn+6,'uvs':nt+4,'flame_triangles':36,'mounts':metadata,'validation':validate_obj(path)}
 return report
def record_flame_evidence(family,report):
 previous=family['export'];expected=previous.get('body_prefix_sha256',previous['sha256'])
 assert report['body_prefix_sha256']==expected,'Existing approved body prefix changed'
 groups=dict(previous['groups']);groups.update({m+'_flame':12 for m in report['mounts']})
 previous.update({k:v for k,v in report.items() if k!='mounts'});previous['groups']=groups
 for mount in family['mounts']:mount.update(report['mounts'][mount['mount']])
 family['flame_texture']={'path':'textures/block/lantern_flame.png','sha256':sha(FLAME_TEXTURE),'width':64,'height':128,'rgba':True}
 family['flame_policy']='Three crossed, double-sided cards; material flame; runtime top V0/bottom V1; show only when lit. Existing body geometry unchanged.'
 return family
def blender_flame_material():
 mat=bpy.data.materials.get('flame') or bpy.data.materials.new('flame');mat.use_nodes=True;nodes=mat.node_tree.nodes;nodes.clear()
 tex=nodes.new('ShaderNodeTexImage');tex.image=bpy.data.images.load(str(FLAME_TEXTURE),check_existing=True);tex.image.pack();tex.extension='CLIP'
 transparent=nodes.new('ShaderNodeBsdfTransparent');emission=nodes.new('ShaderNodeEmission');emission.inputs['Strength'].default_value=2.5;mix=nodes.new('ShaderNodeMixShader');output=nodes.new('ShaderNodeOutputMaterial')
 links=mat.node_tree.links;links.new(tex.outputs['Color'],emission.inputs['Color']);links.new(tex.outputs['Alpha'],mix.inputs[0]);links.new(transparent.outputs[0],mix.inputs[1]);links.new(emission.outputs[0],mix.inputs[2]);links.new(mix.outputs[0],output.inputs['Surface']);mat.diffuse_color=(.95,.75,.22,.5)
 return mat
def add_blender_flame(model,scene,style,mount,anchor,wallrear=0,clean=False):
 old=[o for o in model.all_objects if o and o.type=='MESH' and o.get('lantern_component')=='flame']
 assert not old,'Flame already exists; use approved flame-free source for this operation'
 vertices,faces,_=flame_cards(anchor)
 inverse=lambda p:(p[0],-p[2],p[1]) if style=='byzantine' and mount!='wall' else (p[2]+wallrear,p[0],p[1])
 me=bpy.data.meshes.new('Three double-sided translucent gas-flame cards');me.from_pydata([inverse(p) for p in vertices],[],faces);me.update()
 col=bpy.data.collections.new('FLAME - gas jet - hide for OFF');model.children.link(col);o=bpy.data.objects.new(mount+'_flame',me);col.objects.link(o);o['lantern_component']='flame';o['material_role']='Separate translucent/emissive gas flame, ON only'
 uv=me.uv_layers.new(name='Flame standard Blender UV')
 for poly in me.polygons:
  for li in poly.loop_indices:uv.data[li].uv=((0,0),(1,0),(1,1),(0,1))[me.loops[li].vertex_index%4]
 scene['Gas flame ON']=True;scene.id_properties_ui('Gas flame ON').update(description='Turn off to hide the gas flame. The burner and full lantern body remain unchanged.')
 for prop in ('hide_render','hide_viewport'):
  driver=o.driver_add(prop).driver;driver.type='SCRIPTED';driver.expression='not flame_on';var=driver.variables.new();var.name='flame_on';var.type='SINGLE_PROP';var.targets[0].id_type='SCENE';var.targets[0].id=scene;var.targets[0].data_path='["Gas flame ON"]'
 if clean:o.display_type='WIRE';col.hide_render=True
 else:me.materials.append(blender_flame_material())
 scene.update_tag();scene.frame_set(scene.frame_current);bpy.context.view_layer.update()
 return o
def get_materials():
 missing=[n for n in (STONE_NAME,LEAD_NAME) if not bpy.data.materials.get(n)]
 if missing:
  with bpy.data.libraries.load(str(MATERIAL_SOURCE),link=False) as (src,dst):dst.materials=missing
 stone=bpy.data.materials[STONE_NAME];lead=bpy.data.materials[LEAD_NAME]
 bronze=bpy.data.materials.get('Bronze - Daedalon native metal finish') or bpy.data.materials.new('Bronze - Daedalon native metal finish')
 bronze.use_nodes=True;bs=bronze.node_tree.nodes.get('Principled BSDF');bs.inputs['Metallic'].default_value=1;bs.inputs['Roughness'].default_value=.16
 tex=bronze.node_tree.nodes.new('ShaderNodeTexImage');tex.image=bpy.data.images.load(str(BRONZE),check_existing=True);tex.image.pack();bronze.node_tree.links.new(tex.outputs['Color'],bs.inputs['Base Color']);bronze.diffuse_color=(.7835,.3515,.0123,1)
 glass=bpy.data.materials.get('Glazing glass - clear inset panes') or bpy.data.materials.new('Glazing glass - clear inset panes')
 glass.use_nodes=True;bs=glass.node_tree.nodes.get('Principled BSDF');bs.inputs['Base Color'].default_value=(.93,.97,.98,1);bs.inputs['Transmission Weight'].default_value=1;bs.inputs['Roughness'].default_value=.055;bs.inputs['IOR'].default_value=1.45;glass.diffuse_color=(.72,.88,.93,.18)
 return {'stone':stone,'lead':lead,'bronze':bronze,'glass':glass}
def assign(o,role,mats):
 o['lantern_component']=role;o.data.materials.clear();o.data.materials.append(mats[role]);o.color=mats[role].diffuse_color
 for f in o.data.polygons:f.material_index=0
def contour(style,off=0):
 if style=='georgian':
  h=.135+off;return [(-h,-.557-off),(h,-.557-off),(h,-.15)]+[(h*math.cos(math.pi*i/24),-.15+h*math.sin(math.pi*i/24)) for i in range(1,25)]
 if style=='byzantine':
  h=.122+off;return [(-h,-.322-off),(h,-.322-off)]+[(h*math.cos(math.pi*i/28),.025+h*math.sin(math.pi*i/28)) for i in range(29)]
 h=.114+off;ht=.182+off;r=(h*h+ht*ht)/(2*h);cx=h-r;angle=math.atan2(ht,-cx)
 side=[(cx+r*math.cos(angle*i/18),.016+r*math.sin(angle*i/18)) for i in range(19)]
 return [(-h,-.421-off),(h,-.421-off)]+side+[(-u,z) for u,z in reversed(side[:-1])]
def obj_from_bm(name,bm,col):
 bmesh.ops.recalc_face_normals(bm,faces=list(bm.faces));me=bpy.data.meshes.new(name);bm.to_mesh(me);bm.free();me.update();o=bpy.data.objects.new(name,me);col.objects.link(o);return o
def prism(name,outline,n,t,depths,col,uv=False):
 bm=bmesh.new();loops=[]
 for d in depths:loops.append([bm.verts.new(n*d+t*u+Vector((0,0,z))) for u,z in outline])
 bm.faces.new(list(reversed(loops[0])));bm.faces.new(loops[1])
 for i in range(len(outline)):
  j=(i+1)%len(outline);bm.faces.new((loops[0][i],loops[0][j],loops[1][j],loops[1][i]))
 o=obj_from_bm(name,bm,col)
 if uv:
  layer=o.data.uv_layers.new(name='Planar glazing UV');umin=min(u for u,z in outline);umax=max(u for u,z in outline);zmin=min(z for u,z in outline);zmax=max(z for u,z in outline)
  for p in o.data.polygons:
   for j,li in enumerate(p.loop_indices):
    co=o.data.vertices[o.data.loops[li].vertex_index].co
    layer.data[li].uv=((co.dot(t)-umin)/(umax-umin),(co.z-zmin)/(zmax-zmin)) if p.index<2 else ((0,0),(1,0),(1,1),(0,1))[j]
 return o
def new_glazing(style,mount,col,mats):
 r={'georgian':.44,'gothic':.318,'byzantine':.420}[style];ap=r*math.cos(math.pi/6)
 angles=(range(0,360,60) if mount!='wall' else (-60,0,60)) if style=='georgian' else ((30,90,150,210,270,330) if mount!='wall' else (30,90,270,330))
 result=[]
 for i,deg in enumerate(angles,1):
  a=math.radians(deg);n=Vector((math.cos(a),math.sin(a),0));t=Vector((-math.sin(a),math.cos(a),0))
  pane=prism(f'GL{i:02d} Inset clear glazing pane',contour(style,.002),n,t,(ap-.012,ap-.009),col,True);assign(pane,'glass',mats);result.append(pane)
  if style=='byzantine':
   bm=bmesh.new();loops=[]
   for off,d in [(-.003,.001),(-.002,.004),(.001,.005),(.004,.003),(.004,-.002),(-.003,-.002)]:loops.append([bm.verts.new(n*(ap+d)+t*u+Vector((0,0,z))) for u,z in contour(style,off)])
   for aa,bb in zip(loops,loops[1:]+loops[:1]):
    for j in range(len(aa)):k=(j+1)%len(aa);bm.faces.new((aa[j],aa[k],bb[k],bb[j]))
   o=obj_from_bm(f'L{i:02d} Lead glazing border inside stone archivolt',bm,col)
   for f in o.data.polygons:f.use_smooth=True
   assign(o,'lead',mats);result.append(o)
 return result
def burner(col,mats,floor,raise_by=0,existing=None):
 if existing is None:
  with bpy.data.libraries.load(str(TEMPLATE),link=False) as (src,dst):dst.objects=[n for n in src.objects if n.startswith('G0')]
  existing=dst.objects
 assert len(existing)==8
 for o in existing:
  if o.name not in col.objects:col.objects.link(o)
 bpy.context.view_layer.update()
 for o in existing:
  world=o.matrix_world.copy();o.data.transform(world);o.matrix_world.identity()
  for v in o.data.vertices:v.co=Vector((v.co.x*CANON_BURNER_SCALE,v.co.y*CANON_BURNER_SCALE,(v.co.z-BASE)*CANON_BURNER_SCALE+floor+raise_by))
  assign(o,'bronze',mats)
 bpy.context.view_layer.update()
 for o in existing:
  if o.name.startswith(('G04','G05')):
   bb=bbox(points([o]));assert floor+raise_by+.03<bb[0][2]<bb[1][2]<floor+raise_by+.07,(o.name,bb,floor,raise_by)
 if raise_by>1e-7:
  bm=bmesh.new();bmesh.ops.create_cone(bm,cap_ends=True,cap_tris=False,segments=24,radius1=.010936,radius2=.010936,depth=raise_by)
  for v in bm.verts:v.co.z+=floor+raise_by/2
  o=obj_from_bm('G09 Plain bronze supply riser to raised burner',bm,col);assign(o,'bronze',mats)
 return .2254*CANON_BURNER_SCALE
def check(o):
 bm=bmesh.new();bm.from_mesh(o.data);r={'name':o.name,'role':o.get('lantern_component'),'vertices':len(bm.verts),'polygons':len(bm.faces),'triangles':sum(len(f.verts)-2 for f in bm.faces),'boundary':sum(e.is_boundary for e in bm.edges),'nonmanifold':sum(not e.is_manifold for e in bm.edges),'degenerate':sum(f.calc_area()<1e-12 for f in bm.faces)};bm.free()
 assert not(r['boundary'] or r['nonmanifold'] or r['degenerate']),r
 return r
def runtime(p,style,mount,wallrear):
 q=Vector((p.x,p.z,-p.y)) if style=='byzantine' and mount!='wall' else Vector((p.y,p.z,p.x-(wallrear if mount=='wall' else 0)))
 for i in range(3):
  if abs(q[i])<1e-7:q[i]=0
 if abs(q.y-1)<1e-7:q.y=1
 return q
def runtime_normal(n,style,mount):
 return Vector((n.x,n.z,-n.y)) if style=='byzantine' and mount!='wall' else Vector((n.y,n.z,n.x))
def double_area_squared(points):
 a,b,c=points;u=[b[i]-a[i] for i in range(3)];v=[c[i]-a[i] for i in range(3)]
 return sum(x*x for x in (u[1]*v[2]-u[2]*v[1],u[2]*v[0]-u[0]*v[2],u[0]*v[1]-u[1]*v[0]))
def robust_polygon_triangles(me,poly):
 """Retessellate collinear cap ears without removing an authored boundary vertex."""
 drop=max(range(3),key=lambda i:abs(poly.normal[i]));axes=[i for i in range(3) if i!=drop]
 loops=list(poly.loop_indices);co={i:tuple(float(me.vertices[me.loops[i].vertex_index].co[j]) for j in axes) for i in loops}
 def cross(a,b,c):return (b[0]-a[0])*(c[1]-a[1])-(b[1]-a[1])*(c[0]-a[0])
 area=sum(co[a][0]*co[b][1]-co[b][0]*co[a][1] for a,b in zip(loops,loops[1:]+loops[:1]));sign=1 if area>0 else -1
 result=[]
 while len(loops)>3:
  candidates=[]
  for i,b in enumerate(loops):
   a,c=loops[i-1],loops[(i+1)%len(loops)];weight=sign*cross(co[a],co[b],co[c])
   if weight<=1e-16:continue
   if any(sign*cross(co[a],co[b],co[k])>=-1e-16 and sign*cross(co[b],co[c],co[k])>=-1e-16 and sign*cross(co[c],co[a],co[k])>=-1e-16 for k in loops if k not in (a,b,c)):continue
   remaining=loops[:i]+loops[i+1:]
   if len(remaining)==3 and abs(cross(*(co[k] for k in remaining)))<=1e-16:continue
   candidates.append((weight,i,(a,b,c)))
  assert candidates,('Cannot triangulate complete authored cap',poly.index,len(loops))
  _,i,tri=max(candidates);result.append(tri);del loops[i]
 result.append(tuple(loops));assert len(result)==len(poly.vertices)-2
 return result
def records(col,style,mount,wallrear):
 out={}
 for o in sorted((o for o in col.all_objects if o.type=='MESH'),key=lambda o:o.name):
  role=o['lantern_component']
  if role not in ('stone','lead','bronze','glass'):continue
  group=mount+'_'+role;me=o.data;me.calc_loop_triangles();uv=me.uv_layers.active
  bypoly={}
  for tri in me.loop_triangles:bypoly.setdefault(tri.polygon_index,[]).append(tuple(tri.loops))
  for pi,tris in bypoly.items():
   if any(double_area_squared([tuple(me.vertices[me.loops[i].vertex_index].co) for i in tri])<1e-28 for tri in tris):bypoly[pi]=robust_polygon_triangles(me,me.polygons[pi])
  for loops in (tri for tris in bypoly.values() for tri in tris):
   row=[]
   for li in loops:
    p=runtime(o.matrix_world@me.vertices[me.loops[li].vertex_index].co,style,mount,wallrear)
    n=runtime_normal((o.matrix_world.to_3x3().inverted().transposed()@me.corner_normals[li].vector).normalized(),style,mount)
    row.append((tuple(p),tuple(n),tuple(uv.data[li].uv) if role=='glass' and uv else None))
   out.setdefault(group,[]).append(row)
 return out

def validate_obj(path):
 """Validate the actual LF export, including rounded geometry and mixed UVs."""
 data=path.read_bytes();assert b'\r' not in data
 vs=[];ns=[];uvs=[];groups={};name=None;material=None;zero=[];normal_min=1.
 for line in data.decode('utf-8').splitlines():
  fields=line.split()
  if not fields:continue
  if fields[0]=='v':vs.append(Vector(tuple(map(float,fields[1:]))))
  elif fields[0]=='vn':
   n=Vector(tuple(map(float,fields[1:])));assert abs(n.length-1)<2e-5;ns.append(n)
  elif fields[0]=='vt':
   uv=tuple(map(float,fields[1:]));assert all(-1e-7<=x<=1+1e-7 for x in uv);uvs.append(uv)
  elif fields[0]=='o':name=fields[1];groups[name]=0
  elif fields[0]=='usemtl':material=fields[1];assert material in ('stone','lead','bronze','glass','flame')
  elif fields[0]=='f':
   assert name and material and len(fields)==4;refs=[r.split('/') for r in fields[1:]];p=[vs[int(r[0])-1] for r in refs];n=[ns[int(r[2])-1] for r in refs]
   if double_area_squared([tuple(x) for x in p])<1e-28:zero.append((name,groups[name]))
   if material in ('glass','flame'):assert all(r[1] and 0<int(r[1])<=len(uvs) for r in refs)
   groups[name]+=1
 assert not zero,('Degenerate rounded OBJ triangles',zero[:12])
 assert all(math.isfinite(x) for p in vs for x in p)
 return {'lf_text':True,'finite_unit_normals':True,'triangulated_no_degenerates':True,'glass_uvs_0_to_1':True,'groups':groups}
def export(path,groups):
 positions={};normals={};uvs={};rows=[]
 for name,tris in sorted(groups.items()):
  faces=[]
  for tri in tris:
   refs=[]
   for p,n,uv in tri:
    pk=tuple(round(x,9) for x in p);nk=tuple(round(x,9) for x in n);vi=positions.setdefault(pk,len(positions)+1);ni=normals.setdefault(nk,len(normals)+1)
    ti=uvs.setdefault(tuple(round(x,7) for x in uv),len(uvs)+1) if uv is not None else None
    refs.append(f'{vi}/{ti}/{ni}' if ti else f'{vi}//{ni}')
   faces.append('f '+' '.join(refs))
  material='lead' if name.startswith(('half_chain','full_chain','canopy')) else name.rsplit('_',1)[1]
  rows.extend([f'o {name}',f'g {name}',f'usemtl {material}','s 1',*faces])
 lines=['# Oliver Meshy lantern, clean authored components. Y-up, front +Z. No MTL required.']
 lines+=['v '+' '.join(f'{x:.9f}' for x in p) for p in positions];lines+=['vt '+' '.join(f'{x:.7f}' for x in uv) for uv in uvs];lines+=['vn '+' '.join(f'{x:.9f}' for x in n) for n in normals];lines+=rows
 path.write_text('\n'.join(lines)+'\n',encoding='utf-8',newline='\n')
 return {'path':str(path),'sha256':sha(path),'vertices':len(positions),'normals':len(normals),'uvs':len(uvs),'groups':{k:len(v) for k,v in groups.items()},'validation':validate_obj(path)}
def pilot_preview(scene,col,path,style='georgian',mount='pier'):
 scene.render.engine='CYCLES';scene.cycles.samples=32;scene.cycles.use_denoising=True
 try:
  prefs=bpy.context.preferences.addons['cycles'].preferences;prefs.compute_device_type='OPTIX';prefs.get_devices()
  for d in prefs.devices:d.use=d.type=='OPTIX'
  scene.cycles.device='GPU'
 except Exception:scene.cycles.device='CPU'
 scene.render.resolution_x=1050;scene.render.resolution_y=1400;scene.render.resolution_percentage=100;scene.render.image_settings.file_format='PNG'
 scene.world=bpy.data.worlds.new('Lantern pilot ambient');scene.world.use_nodes=True;scene.world.node_tree.nodes['Background'].inputs['Color'].default_value=(.65,.70,.78,1);scene.world.node_tree.nodes['Background'].inputs['Strength'].default_value=.4
 studio=bpy.data.collections.new('Pilot studio - presentation only');scene.collection.children.link(studio)
 for name,pos,power,size in [('Left',(2,-3,3),180,2.5),('Right',(2,3,1.5),140,2),('Rim',(-2,1,2),220,1.8)]:
  d=bpy.data.lights.new(name,'AREA');d.energy=power;d.shape='DISK';d.size=size;o=bpy.data.objects.new(name,d);studio.objects.link(o);o.location=pos;o.rotation_euler=(Vector((0,0,.5))-o.location).to_track_quat('-Z','Y').to_euler()
 height=max(p.z for p in points([o for o in col.all_objects if o.type=='MESH']));target=Vector((0,0,height/2))
 xy={'georgian':(3,-4),'gothic':(4,-2),'byzantine':(1,-5)}[style]
 if style=='byzantine' and mount=='wall':xy=(5,-1)
 cd=bpy.data.cameras.new('Pilot camera');cam=bpy.data.objects.new(cd.name,cd);studio.objects.link(cam);scene.camera=cam;cd.type='ORTHO';cd.ortho_scale=1.40 if height<1.1 else 1.90;cam.location=(*xy,height/2+1.1);cam.rotation_euler=(target-cam.location).to_track_quat('-Z','Y').to_euler();scene.render.filepath=str(path);bpy.ops.render.render(write_still=True)
 return studio
def prepare(style,mount,out,preview):
 source=SOURCE/NAMES[style][mount];sourcehash=sha(source);bpy.ops.wm.open_mainfile(filepath=str(source),load_ui=False)
 if bpy.context.scene.get('game_body_height_m'):
  raise RuntimeError('Source is already prepared; use --source-root with the retained approved baseline')
 old=next(c for c in bpy.data.collections if 'clean editable' in c.name.lower());objects=[o for o in old.all_objects if o.type=='MESH' and not o.name.startswith(('G0','H0'))]
 source_digests={o.name:digest(o) for o in objects}
 modifier_records={o.name:[{'name':m.name,'type':m.type,'viewport':m.show_viewport,'render':m.show_render,**({'ratio':m.ratio,'reduces_geometry':m.ratio<1.} if m.type=='DECIMATE' else {})} for m in o.modifiers] for o in objects if o.modifiers}
 for o in objects:
  if o.modifiers:
   if any(m.type=='DECIMATE' and m.show_render and m.ratio<1. for m in o.modifiers):raise RuntimeError('Source contains active decimation; supply the full approved original geometry')
   for m in o.modifiers:m.show_viewport=m.show_render
   bpy.context.view_layer.update();deps=bpy.context.evaluated_depsgraph_get();me=bpy.data.meshes.new_from_object(o.evaluated_get(deps),depsgraph=deps);o.data=me;o.modifiers.clear()
 original_bounds=bbox(points(objects));lo,hi=original_bounds;scale=1/(hi[2]-lo[2])
 chassis=next(o for o in objects if o.name.startswith('01 ') and 'trim' not in o.name.lower());hit,floor,_,_=chassis.ray_cast(Vector((.08,0,-.25)),Vector((0,0,-1)),distance=.6);assert hit
 floorz=floor.z;model=bpy.data.collections.new(f'{style.upper()} {mount.upper()} - game components');scene=bpy.data.scenes.new(f'{style.title()} {mount} - GAME MODEL');scene.collection.children.link(model);bpy.context.window.scene=scene
 for o in objects:model.objects.link(o)
 mats=get_materials()
 for o in objects:
  role='lead' if (style=='georgian' and 'glazing trim' in o.name.lower()) or (style=='gothic' and o.name.startswith(('02 ','03 '))) else 'stone';assign(o,role,mats)
 new_glazing(style,mount,model,mats)
 for o in model.objects:
  o.data.transform(o.matrix_world);o.matrix_world.identity()
  o.data.transform(Matrix.Scale(scale,4)@Matrix.Translation(Vector((0,0,-lo[2]))));o.data.update()
 normalized_floor=(floorz-lo[2])*scale
 window_base={'georgian':-.557,'gothic':-.421,'byzantine':-.322}[style];window_top={'georgian':-.015,'gothic':.198,'byzantine':.147}[style]
 targettip=(window_base+.35*(window_top-window_base)-lo[2])*scale
 raise_by=max(0,targettip-normalized_floor-.2254*CANON_BURNER_SCALE) if style=='byzantine' else 0
 existing=[o for o in bpy.data.objects if o.name.startswith('G0')] if source.resolve()==TEMPLATE.resolve() else None
 if existing is None:
  for o in list(bpy.data.objects):
   if o.name.startswith('G0'):bpy.data.objects.remove(o,do_unlink=True)
 burner(model,mats,normalized_floor,raise_by,existing)
 refs={n.object for m in mats.values() for n in m.node_tree.nodes if n.type=='TEX_COORD' and n.object}
 for o in refs:
  if o.name not in model.objects:model.objects.link(o)
 keep=set(model.all_objects)
 for s in list(bpy.data.scenes):
  if s!=scene:bpy.data.scenes.remove(s)
 for o in list(bpy.data.objects):
  if o not in keep:bpy.data.objects.remove(o,do_unlink=True)
 for c in list(bpy.data.collections):
  if c!=model:bpy.data.collections.remove(c)
 checks=[check(o) for o in model.objects if o.type=='MESH'];wallrear=lo[0]*scale if mount=='wall' else 0
 groups=records(model,style,mount,wallrear)
 b=bbox([Vector(p) for tris in groups.values() for tri in tris for p,n,uv in tri]);assert abs(b[0][1])<1e-5 and abs(b[1][1]-1)<1e-5
 suspension_source=out/'lantern-suspension-authoring.blend'
 if mount=='hanging' and suspension_source.is_file():
  with bpy.data.libraries.load(str(suspension_source),link=False) as (src,dst):dst.collections=[n for n in src.collections if n in ('half_chain','canopy')]
  for c in dst.collections:
   model.children.link(c)
   for o in c.all_objects:
    if o.type=='MESH':
     o.location.z+=1.5 if c.name.startswith('canopy') else 1.;assign(o,'lead',mats);o['lantern_component']='suspension'
  bpy.context.view_layer.update()
 scene.unit_settings.system='METRIC';scene.unit_settings.scale_length=1
 scene['provenance']="Oliver's own Meshy creation, approved clean authored lantern family."
 scene['game_body_height_m']=1.;scene['game_floor_y']=normalized_floor;scene['burner_height_m']=.2254*CANON_BURNER_SCALE
 scene['canonical_front']='+Z';scene['source_front']='-Y' if style=='byzantine' and mount!='wall' else '+X'
 for im in bpy.data.images:
  if im.source=='FILE' and im.has_data and not im.packed_file:im.pack()
 studio=pilot_preview(scene,model,out/f'{style}-{mount}-game-preview.png',style,mount) if preview else None
 bpy.data.orphans_purge(do_recursive=True)
 bpy.context.preferences.filepaths.save_version=0;blend=out/f'{style}-{mount}-game-ready.blend';bpy.ops.wm.save_as_mainfile(filepath=str(blend),compress=True,check_existing=False)
 assert sha(source)==sourcehash
 # Read the saved full-detail authoring mesh so exported packed corner normals
 # reproduce the exact result of a later authoring-file replay.
 bpy.ops.wm.open_mainfile(filepath=str(blend),load_ui=False);scene=bpy.context.scene;model=next(c for c in bpy.data.collections if 'game components' in c.name)
 groups=records(model,style,mount,wallrear)
 b=bbox([Vector(p) for tris in groups.values() for tri in tris for p,n,uv in tri]);assert abs(b[0][1])<1e-5 and abs(b[1][1]-1)<1e-5
 result={'style':style,'mount':mount,'source':str(source),'source_sha256':sourcehash,'source_original_bounds':original_bounds,'source_mesh_digests':source_digests,'source_modifiers_baked':modifier_records,'blender_file':str(blend),'blender_sha256':sha(blend),
  'body_bounds':b,'body_height_m':1.,'floor_y':normalized_floor,'normalization_scale':scale,'source_front':scene['source_front'],'runtime_front':'+Z','wall_rear_z':0 if mount=='wall' else None,
  'burner_height_m':.2254*CANON_BURNER_SCALE,'burner_raise_m':raise_by,'burner_base_y':normalized_floor+raise_by,'glass_panes':sum(o.get('lantern_component')=='glass' for o in model.objects),
  'objects':checks,'authoring_polygons':sum(r['polygons'] for r in checks),'authoring_triangles':sum(r['triangles'] for r in checks),'triangles':sum(len(v) for v in groups.values()),'runtime_geometry_policy':'Full approved authoring geometry retained; no runtime reduction','sources_unchanged':True,'provenance':scene['provenance']}
 (out/f'{style}-{mount}-game-report.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8',newline='\n')
 return groups,result

def prepare_suspension(out,meshout,evidence_path):
 """Closed periodic links; exact anchors, with intentional seam-ring overhang."""
 import numpy as np
 bpy.ops.wm.read_factory_settings(use_empty=True);scene=bpy.context.scene;scene.name='Shared lantern suspension - component authoring'
 mats=get_materials();lead=mats['lead'];groups={};reports={};unit=1/1.902
 def make(name,bm,col):
  o=obj_from_bm(name,bm,col)
  for f in o.data.polygons:f.use_smooth=True
  assign(o,'lead',mats);return o
 def sweep(name,path,section,col,plane):
  path=[Vector(p) for p in path];normal=Vector((0,1,0)) if plane=='XZ' else Vector((1,0,0));bm=bmesh.new();loops=[]
  for i,p in enumerate(path):
   tangent=(path[(i+1)%len(path)]-path[(i-1)%len(path)]).normalized();across=tangent.cross(normal).normalized()
   loops.append([bm.verts.new(p+across*x+normal*y) for x,y in section])
  for a,b in zip(loops,loops[1:]+loops[:1]):
   for j in range(len(section)):k=(j+1)%len(section);bm.faces.new((a[j],a[k],b[k],b[j]))
  return make(name,bm,col)
 def ring(name,r,z,wire,col,plane):
  path=[(r*math.cos(i*math.tau/32),0,z+r*math.sin(i*math.tau/32)) if plane=='XZ' else (0,r*math.cos(i*math.tau/32),z+r*math.sin(i*math.tau/32)) for i in range(32)]
  o=sweep(name,path,[(wire*math.cos(i*math.tau/8),wire*math.sin(i*math.tau/8)) for i in range(8)],col,plane)
  return o,path,wire
 def rectangle(name,z,mult,col):
  w,h,c=.029*unit*mult,.066*unit*mult,.009*unit*mult;path=[]
  for x,y,start in [(w-c,h-c,0),(-w+c,h-c,90),(-w+c,-h+c,180),(w-c,-h+c,270)]:
   for j in range(7):
    a=math.radians(start+j*15);path.append((x+c*math.cos(a),0,z+y+c*math.sin(a)))
  hw,hd,b=.005*unit*mult,.004*unit*mult,.0015*unit*mult;section=[]
  for x,y,start in [(hw-b,hd-b,0),(-hw+b,hd-b,90),(-hw+b,-hd+b,180),(hw-b,-hd+b,270)]:
   for j in range(4):
    a=math.radians(start+j*30);section.append((x+b*math.cos(a),y+b*math.sin(a)))
  o=sweep(name,path,section,col,'XZ');return o,path,math.hypot(hw,hd)
 def radial_part(name,profile,col,n=48):
  bm=bmesh.new();loops=[]
  for r,z in profile:loops.append([bm.verts.new((r*math.cos(i*math.tau/n),r*math.sin(i*math.tau/n),z)) for i in range(n)])
  bm.faces.new(list(reversed(loops[0])));bm.faces.new(loops[-1])
  for a,b in zip(loops,loops[1:]):
   for j in range(n):k=(j+1)%n;bm.faces.new((a[j],a[k],b[k],b[j]))
  return make(name,bm,col)
 def clearance(paths):
  def dense(path):
   pts=[]
   for p,q in zip(path,path[1:]+path[:1]):
    p,q=np.asarray(p),np.asarray(q);n=max(1,math.ceil(float(np.linalg.norm(q-p))/.00035));pts.extend(p+(q-p)*t/n for t in range(n))
   return np.asarray(pts)
  distances=[]
  for a,b in zip(paths,paths[1:]):
   pa,pb=dense(a[1]),dense(b[1]);d=float(np.sqrt(((pa[:,None,:]-pb[None,:,:])**2).sum(axis=2)).min())-a[2]-b[2];distances.append(d)
  return min(distances) if distances else None
 for mult,suffix in ((1.,''),(.5,'_small')):
  for typ,span,count in (('half_chain',.5,6 if mult==1 else 13),('full_chain',1.,13 if mult==1 else 26)):
   name=typ+suffix;col=bpy.data.collections.new(name);scene.collection.children.link(col);paths=[]
   if typ=='half_chain':
    eye_r=.038*unit*mult;eye_wire=.007*unit*mult;eye_center=eye_r+eye_wire
    radial_part(name+' - embedded apex fixing',[(.006*unit*mult,-.015*unit*mult),(.007*unit*mult,-.004*unit*mult),(.007*unit*mult,.004*unit*mult),(.005*unit*mult,.009*unit*mult)],col,24)
    paths.append(ring(name+' - direct apex eye',eye_r,eye_center,eye_wire,col,'XZ'))
    start=eye_center+.044*unit*mult;period=(span-start)/count
    paths.append(ring(name+' - initial round join',.022*unit*mult,start,.0055*unit*mult,col,'YZ'))
   else:start=0.;period=span/count
   for i in range(count):
    paths.append(rectangle(name+f' - rectangular link {i+1:02d}',start+(i+.5)*period,mult,col))
    paths.append(ring(name+f' - round join {i+1:02d}',.022*unit*mult,start+(i+1)*period,.0055*unit*mult,col,'YZ'))
   gap=clearance(paths);assert gap>.00030*mult,(name,gap)
   checks=[check(o) for o in col.objects];g=records(col,'byzantine','pier',0);groups[name]=g['pier_lead']
   bounds=bbox([Vector(p) for tri in groups[name] for p,n,uv in tri]);reports[name]={'logical_anchor_span_m':span,'geometric_bounds':bounds,'rectangular_links':count,'round_join_rings':count+(1 if typ=='half_chain' else 0),'direct_eye':typ=='half_chain','stock_scale':mult,'minimum_stock_clearance_m':gap,'end_seam_ring_owned':True,'triangles':sum(r['triangles'] for r in checks)}
  name='canopy'+suffix;col=bpy.data.collections.new(name);scene.collection.children.link(col)
  profile=[(.022,1.768),(.023,1.782),(.037,1.790),(.055,1.798),(.083,1.814),(.107,1.827),(.123,1.832),(.129,1.839),(.129,1.849),(.134,1.852),(.134,1.862),(.129,1.866)]
  radial_part(name,[(r*unit*mult,(z-1.866)*unit*mult) for r,z in profile],col);checks=[check(o) for o in col.objects];groups[name]=records(col,'byzantine','pier',0)['pier_lead'];bounds=bbox([Vector(p) for tri in groups[name] for p,n,uv in tri]);assert abs(bounds[1][1])<1e-8
  reports[name]={'ceiling_contact_y':0.,'geometric_bounds':bounds,'stock_scale':mult,'triangles':sum(r['triangles'] for r in checks)}
 refs={n.object for n in lead.node_tree.nodes if n.type=='TEX_COORD' and n.object}
 for o in refs:
  if not o.users_collection:scene.collection.objects.link(o)
 scene['anchor_contract']='Exact 0.5/1m anchors. Lower unit owns complete end round join; upper unit begins perpendicular rectangular link. Geometry intentionally overhangs logical anchors.'
 bpy.context.preferences.filepaths.save_version=0;blend=out/'lantern-suspension-authoring.blend';bpy.ops.wm.save_as_mainfile(filepath=str(blend),compress=True,check_existing=False)
 bpy.ops.wm.open_mainfile(filepath=str(blend),load_ui=False)
 groups={name:records(bpy.data.collections[name],'byzantine','pier',0)['pier_lead'] for name in groups}
 exported=export(meshout/'lantern_suspension.obj',groups)
 result={'export':exported,'components':reports,'material':'lead','units':'metres','coordinate_contract':'Y-up; canopy contactY0; half-chain anchors0/.5; full-chain anchors0/1','blender_source':str(blend),'blender_sha256':sha(blend)}
 (out/'lantern-suspension-evidence.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8',newline='\n')
 if evidence_path:
  evidence=json.loads(evidence_path.read_text()) if evidence_path.exists() else {'schema':1,'families':{}};evidence['suspension']=result;evidence_path.write_text(json.dumps(public_evidence(evidence),indent=2)+'\n',encoding='utf-8',newline='\n')
 print('LANTERN_SUSPENSION_READY',json.dumps(result),flush=True)

def main():
 global SOURCE,TEMPLATE,MATERIAL_SOURCE
 parser=argparse.ArgumentParser();parser.add_argument('--style',choices=tuple(NAMES));parser.add_argument('--mounts',nargs='+',choices=('pier','wall','hanging'),default=['pier','wall','hanging']);parser.add_argument('--output-dir',type=Path,required=True);parser.add_argument('--source-root',type=Path,required=True);parser.add_argument('--burner-template',type=Path);parser.add_argument('--preview',action='store_true');parser.add_argument('--evidence',type=Path);parser.add_argument('--suspension-only',action='store_true');parser.add_argument('--mesh-output-dir',type=Path);parser.add_argument('--append-flame',action='store_true')
 args=parser.parse_args(sys.argv[sys.argv.index('--')+1:] if '--' in sys.argv else [])
 SOURCE=args.source_root.resolve();TEMPLATE=args.burner_template.resolve() if args.burner_template else SOURCE/NAMES['georgian']['pier'];MATERIAL_SOURCE=SOURCE/NAMES['gothic']['pier']
 assert tuple(bpy.app.version)==(5,2,0) and bpy.app.build_hash.decode()=='fbe6228777e7','Use locked Blender 5.2.0'
 out=args.output_dir.resolve();out.mkdir(parents=True,exist_ok=True);meshout=args.mesh_output_dir.resolve() if args.mesh_output_dir else out;meshout.mkdir(parents=True,exist_ok=True);groups={};reports=[]
 if args.append_flame:
  if not args.style or not args.evidence:parser.error('--append-flame requires --style and --evidence')
  evidence=json.loads(args.evidence.read_text());report=append_flame_groups(meshout/f'lantern_{args.style}.obj');record_flame_evidence(evidence['families'][args.style],report)
  args.evidence.write_text(json.dumps(public_evidence(evidence),indent=2)+'\n',encoding='utf-8',newline='\n');(out/f'{args.style}-flame-append.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8',newline='\n')
  print('LANTERN_FLAME_APPENDED',json.dumps(report),flush=True);return
 if args.suspension_only:prepare_suspension(out,meshout,args.evidence);return
 if not args.style:parser.error('--style is required unless --suspension-only is selected')
 for mount in args.mounts:
  g,r=prepare(args.style,mount,out,args.preview);groups.update(g);reports.append(r)
 exported=export(meshout/f'lantern_{args.style}.obj',groups)
 evidence={'schema':1,'provenance':"Oliver's own supplied Meshy creations and approved clean authored derivatives",'blender_version':bpy.app.version_string,'blender_build':bpy.app.build_hash.decode(),'coordinate_contract':'Y-up, front+Z; body height1m excluding suspension; pier/hanging centredXZ; wall rearZ0/front+Z','canonical_burner_height_m':.2254*CANON_BURNER_SCALE,'mounts':reports,'export':exported,'native_bronze':{'path':str(BRONZE),'sha256':sha(BRONZE)}}
 (out/f'{args.style}-export-evidence.json').write_text(json.dumps(evidence,indent=2)+'\n',encoding='utf-8',newline='\n')
 if args.evidence:
  previous=json.loads(args.evidence.read_text()) if args.evidence.exists() else {'schema':1,'families':{}}
  previous.setdefault('families',{})[args.style]=evidence;args.evidence.parent.mkdir(parents=True,exist_ok=True);args.evidence.write_text(json.dumps(public_evidence(previous),indent=2)+'\n',encoding='utf-8',newline='\n')
 print('LANTERN_GAME_PREPARATION',json.dumps({'style':args.style,'mounts':len(reports),'export':exported,'reports':[str(out/f'{args.style}-{m}-game-report.json') for m in args.mounts]}),flush=True)
if __name__=='__main__':main()
