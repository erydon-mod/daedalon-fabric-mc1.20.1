"""Rebuild approved Daedalon plinth profiles as editable architectural meshes.

Run one model as a reviewable external pilot. This tool never writes to its
source root. Architectural cores are rebuilt analytically; dense original
sculpted relief may receive bounded, measured reduction after extraction.
Stephanos defaults to the user-requested approximately16,000 editable polygon
faces; this second ornament-only reduction keeps its architectural core exact.
Source provenance and the profile measurements are recorded with the candidate, independently from any
later runtime activation. Blender 5.2 is required.
"""


from __future__ import annotations


import argparse,hashlib,json,math,os,pathlib,subprocess,sys,collections


try:
 import bpy,bmesh
 from mathutils import Vector
 from mathutils.geometry import delaunay_2d_cdt
except ImportError:
 bpy=None


BLENDER=pathlib.Path(r'C:\Program Files\Blender Foundation\Blender 5.2\blender.exe')


SOURCE={'bathron':{'uuid':'019ff635-49b7-7947-a439-55487531c93f','sha256':'89554bb41b077b37c6f58a5675c2a2a1fbb6ea5cea7f6cb28d4d63e2a18504a2'}}


BATHRON_PROFILE=[
 (0,.404394),(.00045,.405454),(.0015,.4058936555),
 (.1485,.4058936555),(.150,.40545),(.151,.404394),(.151,.38710),
 (.163,.38690),(.168,.38642),(.172,.38463),(.176,.37836),
 (.180,.37116),(.184,.36637),(.188,.36234),(.192,.35905),
 (.196,.35659),(.200,.354824),(.204,.353088),(.208,.350875),
 (.212,.34605),(.214,.33505),(.224,.33505),(.226,.33450),
 (.228,.33279),(.232,.33210),(.236,.33181),
 (.809,.29979),(.811,.30120),(.818,.30120),
 (.820,.30720),(.822,.31148),(.824,.31368),(.826,.31517),
 (.828,.31606),(.830,.31647),(.832,.31654),(.834,.31652),
 (.836,.31618),(.838,.31528),(.840,.31416),(.842,.31217),
 (.844,.30848),(.846,.30020),(.8655,.30020),(.866,.31135),
 (.870,.31370),(.874,.31631),(.878,.31949),(.882,.32316),
 (.886,.32830),(.890,.33502),(.892,.33850),(.894,.34146),
 (.896,.34323),(.898,.34400),(.901,.34430),(.9045,.34430),
 (.9055,.36000),(.908,.36260),(.912,.36380),(.916,.36470),
 (.9985,.36470),(.99955,.36425),(1,.36320)]


WIDTH=.811787311


DEPTH=.812034223


def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()


def args():
 p=argparse.ArgumentParser(description=__doc__)
 p.add_argument('--style',choices=['bathron','astragalos','kion','stephanos','triphyllon'],default='bathron')
 p.add_argument('--source-root',type=pathlib.Path,required=True,help='Folder containing the original Meshy UUID directories')
 p.add_argument('--output-dir',type=pathlib.Path,required=True)
 p.add_argument('--blender',type=pathlib.Path,default=None)
 p.add_argument('--stephanos-target-faces',type=int,default=16000,help='Editable polygon target; reduces native Stephanos ornament only, preserving its architectural core')
 p.add_argument('--approved-stephanos-input',type=pathlib.Path,default=None,help='Optional prior approved full-detail Stephanos Blender input; source originals remain hash-verified separately')
 return p.parse_args(sys.argv[sys.argv.index('--')+1:] if bpy and '--' in sys.argv else None)


def bounds(points):return {'min':[min(p[i] for p in points) for i in range(3)],'max':[max(p[i] for p in points) for i in range(3)],'size':[max(p[i] for p in points)-min(p[i] for p in points) for i in range(3)]}


def create_body():
 ratio=DEPTH/WIDTH
 corner=.0015
 vertices=[];faces=[]
 # Each corner has three analytic arc segments. The intervening long edges
 # remain true planes, with exactly the approved overall X/Y footprint.
 for z,r in BATHRON_PROFILE:
  for k in range(4):
   a0=k*math.pi/2
   sx=1 if k in (0,3) else -1
   sy=1 if k in (0,1) else -1
   for j in range(4):
    a=a0+j*math.pi/6
    vertices.append(((sx*(r-corner)+corner*math.cos(a)),(sy*(r-corner)+corner*math.sin(a))*ratio,z))
 ring=16
 for i in range(len(BATHRON_PROFILE)-1):
  for j in range(ring):
   a=i*ring+j;b=i*ring+(j+1)%ring;c=(i+1)*ring+(j+1)%ring;d=(i+1)*ring+j
   faces.append((a,b,c,d))
 faces.append(tuple(reversed(range(ring))))
 faces.append(tuple((len(BATHRON_PROFILE)-1)*ring+j for j in range(ring)))
 mesh=bpy.data.meshes.new('Bathron - measured architectural profile');mesh.from_pydata(vertices,[],faces);mesh.update()
 obj=bpy.data.objects.new('Bathron - clean editable profile',mesh);bpy.context.scene.collection.objects.link(obj);bpy.context.view_layer.objects.active=obj;obj.select_set(True)
 for face in mesh.polygons:face.use_smooth=True
 mesh.set_sharp_from_angle(angle=math.radians(42))
 modifier=obj.modifiers.new('Stone planes and moulding normals','WEIGHTED_NORMAL');modifier.keep_sharp=True;modifier.weight=30;modifier.mode='FACE_AREA_WITH_ANGLE'
 bpy.ops.object.modifier_apply(modifier=modifier.name)
 material=bpy.data.materials.new('none');material.diffuse_color=(.61,.60,.57,1);obj.data.materials.append(material)
 obj['Design']='Measured Bathron square plinth; each base/cornice moulding retained'
 obj['Height meters']=1.;obj['Medium footprint X meters']=WIDTH;obj['Medium footprint depth meters']=DEPTH
 obj['Source provenance']='Oliver-authored Meshy creation; rebuilt profile, no source modification'
 return obj


def verify(obj):
 mesh=obj.data;mesh.calc_loop_triangles();pts=[v.co.copy() for v in mesh.vertices]
 bm=bmesh.new();bm.from_mesh(mesh)
 edge_counts={'boundary':sum(e.is_boundary for e in bm.edges),'nonmanifold':sum(len(e.link_faces)>2 for e in bm.edges)}
 volume=bm.calc_volume(signed=True);bm.free()
 degenerate=sum(t.area<=1e-14 for t in mesh.loop_triangles)
 b=bounds(pts)
 render_sculpt=bool(obj.get('embedded_native_relief_render_asset',False))
 if not render_sculpt:assert edge_counts=={'boundary':0,'nonmanifold':0},edge_counts
 assert degenerate==0 and volume>0,(degenerate,volume)
 expected_width,expected_depth=FOOTPRINTS.get(obj.get('style','bathron'),(WIDTH,DEPTH))
 assert abs(b['size'][0]-expected_width)<1e-7 and abs(b['size'][1]-expected_depth)<1e-7 and abs(b['size'][2]-1)<1e-7,b
 roots=list(range(len(mesh.vertices)))
 def find(i):
  while roots[i]!=i:roots[i]=roots[roots[i]];i=roots[i]
  return i
 for e in mesh.edges:
  a,c=map(find,e.vertices);roots[c]=a
 volumes={}
 for t in mesh.loop_triangles:
  pa,pb,pc=[mesh.vertices[i].co for i in t.vertices];key=find(t.vertices[0]);volumes[key]=volumes.get(key,0)+pa.dot(pb.cross(pc))/6
 if not render_sculpt:assert all(v>1e-12 for v in volumes.values()),('Inverted or empty closed component',[v for v in volumes.values() if v<=1e-12])
 else:
  groups={}
  for i,v in enumerate(mesh.vertices):groups.setdefault(find(i),[]).append(i)
  core=[r for r,ids in groups.items() if min(mesh.vertices[i].co.z for i in ids)<1e-8 and max(mesh.vertices[i].co.z for i in ids)>1-1e-8]
  assert len(core)==1,'One sealed architectural core required'
  ids=groups[core[0]];mapping={old:new for new,old in enumerate(ids)};faces=[[mapping[i] for i in f.vertices] for f in mesh.polygons if find(f.vertices[0])==core[0]]
  cm=bpy.data.meshes.new('Temporary sealed core verification');cm.from_pydata([mesh.vertices[i].co for i in ids],[],faces);cm.update();cb=bmesh.new();cb.from_mesh(cm)
  assert all(e.is_manifold for e in cb.edges) and all(v.is_manifold for v in cb.verts),'Architectural core must be closed/manifold'
  assert cb.calc_volume(signed=True)>0,'Architectural core must have positive volume'
  cb.free();bpy.data.meshes.remove(cm)
 return {'render_asset_with_embedded_native_seams':render_sculpt,'component_count':len(volumes),'closed_components':len(volumes) if not render_sculpt else None,'minimum_component_signed_volume':min(volumes.values()) if not render_sculpt else None,'vertices':len(mesh.vertices),'polygons':len(mesh.polygons),'triangles':len(mesh.loop_triangles),'bounds_blender_z_up':b,'bounds_obj_y_up':{'min':[b['min'][0],b['min'][2],-b['max'][1]],'max':[b['max'][0],b['max'][2],-b['min'][1]],'size':[b['size'][0],b['size'][2],b['size'][1]]},'edge_counts':edge_counts,'degenerate_triangles':degenerate,'signed_volume':volume if not render_sculpt else None,'uv_layers':len(mesh.uv_layers),'custom_normals':mesh.has_custom_normals,'profile_rings':len(BATHRON_PROFILE) if obj.get('style','bathron')=='bathron' else None,'corner_arc_segments':3,'sharpen_angle_degrees':42}


def export_obj(obj,path):
 if obj.data.attributes.get('retained_obj_corner_normals') is not None:
  return _stephanos_export_preserved_obj(obj,path)
 mesh=obj.data;mesh.calc_loop_triangles();lines=[f'# {obj.get("style","bathron").title()} measured architectural rebuild; Oliver-authored source\n','# Y-up; height=1m; X/Z centered; material none; no UVs\n',f'o plinth_{obj.get("style","bathron")}\n']
 for v in mesh.vertices:lines.append(f'v {v.co.x:.9f} {v.co.z:.9f} {-v.co.y:.9f}\n')
 for n in mesh.corner_normals:lines.append(f'vn {n.vector.x:.9f} {n.vector.z:.9f} {-n.vector.y:.9f}\n')
 lines+=['usemtl none\n','s off\n']
 for t in mesh.loop_triangles:lines.append('f '+' '.join(f'{vi+1}//{li+1}' for vi,li in zip(t.vertices,t.loops))+'\n')
 path.write_text(''.join(lines),encoding='utf-8',newline='\n')


def make_scene(obj,output,prefix='bathron-candidate'):
 scene=bpy.context.scene;scene.unit_settings.system='METRIC';scene.unit_settings.length_unit='METERS'
 world=bpy.data.worlds.new('Neutral review world');scene.world=world;world.color=(.9,.9,.9)
 scene.render.engine='BLENDER_WORKBENCH';scene.render.resolution_x=960;scene.render.resolution_y=1100;scene.render.resolution_percentage=100;scene.render.image_settings.file_format='PNG'
 sh=scene.display.shading;sh.light='STUDIO';sh.studio_light='paint.sl';sh.color_type='SINGLE';sh.single_color=(.65,.63,.59);sh.show_shadows=True;sh.show_cavity=True;sh.cavity_type='BOTH';sh.curvature_ridge_factor=1.3;sh.curvature_valley_factor=1.05;sh.background_type='WORLD'
 camera_data=bpy.data.cameras.new('Bathron review camera');camera=bpy.data.objects.new('Bathron review camera',camera_data);scene.collection.objects.link(camera);scene.camera=camera;camera_data.type='ORTHO';camera_data.ortho_scale=1.35
 for view,offset in [('hero',(3,-5,2.8)),('front',(0,-5,0))]:
  target=Vector((0,0,.5));camera.location=target+Vector(offset);camera.rotation_euler=(target-camera.location).to_track_quat('-Z','Y').to_euler();scene.render.filepath=str(output/f'{prefix}-{view}.png');bpy.ops.render.render(write_still=True)
 # Saved editable file opens onto the review view, with the model grounded.
 target=Vector((0,0,.5));camera.location=target+Vector((3,-5,2.8));camera.rotation_euler=(target-camera.location).to_track_quat('-Z','Y').to_euler();scene.render.filepath=str(output/f'{prefix}-hero.png')


def source_context(source,width,depth):
 import numpy as np
 from mathutils.bvhtree import BVHTree
 bpy.ops.wm.open_mainfile(filepath=str(source),load_ui=False)
 obj=next(o for o in bpy.context.scene.objects if o.type=='MESH');mesh=obj.data
 a=np.empty(len(mesh.vertices)*3,dtype=np.float32);mesh.vertices.foreach_get('co',a);a=a.reshape((-1,3))
 mat=np.array(obj.matrix_world);a=a@mat[:3,:3].T+mat[:3,3];lo=a.min(axis=0);hi=a.max(axis=0)
 a[:,:2]-=(lo[:2]+hi[:2])/2;a[:,2]-=lo[2];a*=np.array([width/(hi[0]-lo[0]),depth/(hi[1]-lo[1]),1/(hi[2]-lo[2])])
 obj.matrix_world.identity();mesh.vertices.foreach_set('co',a.ravel());mesh.update();bpy.context.view_layer.update()
 bv=BVHTree.FromObject(obj,bpy.context.evaluated_depsgraph_get())
 return obj,bv


def profile_radius(bv,style,z):
 ratio=FOOTPRINTS[style][1]/FOOTPRINTS[style][0];vals=[]
 for j in range(4):
  a=j*math.pi/2;v=Vector((math.cos(a),math.sin(a),0));h,n,f,d=bv.ray_cast(v*2+Vector((0,0,z)),-v,4)
  if h:vals.append(math.hypot(h.x,h.y)/(ratio if j%2 else 1))
 return sum(vals)/len(vals)


def simplify_profile(points,tol=.00025):
 if len(points)<=2:return points
 a=Vector(points[0]);b=Vector(points[-1]);ab=b-a;den=ab.length
 ds=[abs(ab.x*(Vector(p)-a).y-ab.y*(Vector(p)-a).x)/den if den else (Vector(p)-a).length for p in points]
 at=max(range(len(ds)),key=lambda i:ds[i])
 if ds[at]<=tol:return [points[0],points[-1]]
 return simplify_profile(points[:at+1],tol)[:-1]+simplify_profile(points[at:],tol)


def measured_profiles(bv,style):
 bottom,top=BODY_BANDS[style];foot=FOOTPRINTS[style][0]/2;foot_top=FOOT_TOP[style]
 low=[(0,foot-.0015),(.0015,foot),(foot_top-.0015,foot),(foot_top,foot-.0015)]
 z0=foot_top+.001
 samples=[(z0+(bottom-z0)*i/max(1,round((bottom-z0)/.001)),0) for i in range(max(1,round((bottom-z0)/.001))+1)]
 low+=simplify_profile([(z,profile_radius(bv,style,z)) for z,_ in samples])
 count=max(1,round((.998-top)/.001));upper=simplify_profile([(top+(.998-top)*i/count,profile_radius(bv,style,top+(.998-top)*i/count)) for i in range(count+1)])
 # Keep the top perfectly flat and softly dress its upper perimeter.
 r=upper[-1][1];upper+=[(1,r-.0015)]
 return low,upper


class SolidBuilder:
 def __init__(self):self.vertices=[];self.faces=[];self.ids={};self.source_normals={}
 def vertex(self,p,normal=None):
  key=tuple(round(float(c),10) for c in p)
  if key not in self.ids:self.ids[key]=len(self.vertices);self.vertices.append(tuple(p))
  ix=self.ids[key]
  if normal is not None:self.source_normals[ix]=normal.copy()
  return ix
 def face(self,indices):
  indices=list(indices)
  if len(set(indices))>=3:self.faces.append(tuple(indices))
 def bridge(self,a,b):
  for i in range(len(a)):self.face((a[i],a[(i+1)%len(a)],b[(i+1)%len(b)],b[i]))
 def cap(self,ring,z,top):
  mid=self.vertex((0,0,z))
  for i in range(len(ring)):
   tri=(mid,ring[i],ring[(i+1)%len(ring)])
   self.face(tri if top else tuple(reversed(tri)))


def mesh_from_builder(builder,style):
 mesh=bpy.data.meshes.new(style.title()+' - reconstructed architecture and retained relief');mesh.from_pydata(builder.vertices,[],builder.faces);mesh.update()
 bm=bmesh.new();bm.from_mesh(mesh);bmesh.ops.recalc_face_normals(bm,faces=list(bm.faces))
 unseen=set(bm.faces)
 while unseen:
  seed=unseen.pop();stack=[seed];component=[seed]
  while stack:
   f=stack.pop()
   for e in f.edges:
    for linked in e.link_faces:
     if linked in unseen:unseen.remove(linked);stack.append(linked);component.append(linked)
  signed=0
  for f in component:
   vv=[v.co for v in f.verts]
   signed+=sum(vv[0].dot(vv[j].cross(vv[j+1]))/6 for j in range(1,len(vv)-1))
  if signed<0:bmesh.ops.reverse_faces(bm,faces=component)
 bm.to_mesh(mesh);bm.free();mesh.update()
 obj=bpy.data.objects.new(style.title()+' - clean editable plinth',mesh);bpy.context.scene.collection.objects.link(obj);bpy.context.view_layer.objects.active=obj;obj.select_set(True)
 for face in mesh.polygons:face.use_smooth=True
 mesh.set_sharp_from_angle(angle=math.radians(42));mod=obj.modifiers.new('Authored stone corner normals','WEIGHTED_NORMAL');mod.keep_sharp=True;mod.weight=30;mod.mode='FACE_AREA_WITH_ANGLE';bpy.ops.object.modifier_apply(modifier=mod.name)
 # Dense-source relief normals preserve leaf curvature without changing the
 # reconstructed architectural normals or adding extra geometric triangles.
 mesh=obj.data
 normals=[n.vector.copy() for n in mesh.corner_normals]
 for loop in mesh.loops:
  if loop.vertex_index in builder.source_normals:normals[loop.index]=builder.source_normals[loop.vertex_index]
 for face in mesh.polygons:
  zs=[mesh.vertices[i].co.z for i in face.vertices]
  if max(zs)-min(zs)<1e-8 and (abs(zs[0])<1e-8 or abs(zs[0]-1)<1e-8):
   face.use_smooth=False
   for li in face.loop_indices:normals[li]=Vector((0,0,1 if zs[0]>.5 else -1))
 mesh.normals_split_custom_set(normals)
 material=bpy.data.materials.new('none');material.diffuse_color=(.61,.60,.57,1);mesh.materials.append(material)
 return obj


FOOTPRINTS={'bathron':(WIDTH,DEPTH),'astragalos':(.603108658,.602949901),'kion':(.998954339,1.),'stephanos':(.831943020,.831934729),'triphyllon':(.957103561,.973928089)}


BODY_BANDS={'astragalos':(.151,.901),'kion':(.235,.776),'stephanos':(.223,.806),'triphyllon':(.203,.871)}


FOOT_TOP={'astragalos':.093,'kion':.087,'stephanos':.080,'triphyllon':.074}


SOURCE.update({'astragalos':{'uuid':'019ff635-649f-7296-a61b-7a4cf1039abe','sha256':'19c2f16926a9b25ae151f9d13186fb6850f1ffdc07f8c9e2cc762e7484ef5c7c'},'kion':{'uuid':'019ff635-2eeb-75b0-9f0a-d82912319224','sha256':'626c5313e045b18cfb5dc9e68ec917dacaa73340d4e5f2485042490265695d0b'},'stephanos':{'uuid':'019ff635-09e0-793e-87d0-f8a5996a2f7c','sha256':'2109f446a760a2f66b53dc080ccfd1e29ca667a868ff64ad72b725d9286e4435'},'triphyllon':{'uuid':'019ff657-1127-76a5-a80d-c09b9c06b8b2','sha256':'fdc7f3cebfd78338c6dd044c6a7d6b78eb53253b060360b68b766cb1ba1624fc'}})


def section_points(style,r,z):
 if style=='kion':return [Vector((r*math.cos(math.tau*j/108),r*math.sin(math.tau*j/108),z)) for j in range(108)]
 if style=='triphyllon':
  result=[];R=r/math.cos(math.pi/8);corner=.0015
  vv=[Vector((R*math.cos(math.pi/8+j*math.pi/4),R*math.sin(math.pi/8+j*math.pi/4),z)) for j in range(8)]
  for j,v in enumerate(vv):
   for other in [vv[j-1],vv[(j+1)%8]]:result.append(v+(other-v).normalized()*corner)
  return result
 result=[];corner=.0015
 for k in range(4):
  sx=1 if k in (0,3) else -1;sy=1 if k in (0,1) else -1
  for j in range(4):
   a=k*math.pi/2+j*math.pi/6;result.append(Vector((sx*(r-corner)+corner*math.cos(a),sy*(r-corner)+corner*math.sin(a),z)))
 return result


def add_core(builder,style,profile,band=None,panel=None):
 rings=[[builder.vertex(p) for p in section_points(style,r,z)] for z,r in profile]
 for i,(a,b) in enumerate(zip(rings,rings[1:])):
  isshaft=band and abs(profile[i][0]-band[0])<1e-8 and abs(profile[i+1][0]-band[1])<1e-8
  for j in range(len(a)):
   if isshaft and panel and j in (3,7,11,15):continue
   builder.face((a[j],a[(j+1)%len(a)],b[(j+1)%len(a)],b[j]))
 builder.cap(rings[0],0,False);builder.cap(rings[-1],1,True)
 if panel:
  b,t=band;r0=next(r for z,r in profile if abs(z-b)<1e-8);r1=next(r for z,r in profile if abs(z-t)<1e-8)
  def rad(z):return r0+(r1-r0)*(z-b)/(t-b)
  for face in range(4):
   a=face*math.pi/2;n=Vector((math.cos(a),math.sin(a),0));tangent=Vector((-math.sin(a),math.cos(a),0))
   def mapped(u,z,depth):return tangent*u+n*depth+Vector((0,0,z))
   outer=[builder.vertex(mapped(u,z,rad(z))) for u,z in [(-(r0-.0015),b),(r0-.0015,b),(r1-.0015,t),(-(r1-.0015),t)]]
   pz0,pz1,margin,steps=panel
   last=outer
   for inset,offset in steps:
    z0=pz0+inset;z1=pz1-inset;half0=rad(z0)-margin-inset;half1=rad(z1)-margin-inset
    ring=[builder.vertex(mapped(u,z,rad(z)+offset)) for u,z in [(-half0,z0),(half0,z0),(half1,z1),(-half1,z1)]]
    for j in range(4):builder.face((last[j],last[(j+1)%4],ring[(j+1)%4],ring[j]))
    last=ring
   builder.face(last)
 return rings


def rounded_square_path(distance,r,c=.006):
 line=2*(r-c);arc=math.pi*c/2;unit=line+arc;total=4*unit;distance%=total;k=int(distance/unit);q=distance-k*unit
 if q<arc:
  a=k*math.pi/2+q/c;cx=(1 if k in (0,3) else -1)*(r-c);cy=(1 if k in (0,1) else -1)*(r-c)
  p=Vector((cx+c*math.cos(a),cy+c*math.sin(a),0));n=Vector((math.cos(a),math.sin(a),0));return p,Vector((-math.sin(a),math.cos(a),0)),n,total
 # Straight path after each corner, following the counter-clockwise perimeter.
 s=(q-arc)/line
 if k==0:p=Vector((r-c-line*s,r,0));n=Vector((0,1,0));t=Vector((-1,0,0))
 elif k==1:p=Vector((-r,r-c-line*s,0));n=Vector((-1,0,0));t=Vector((0,-1,0))
 elif k==2:p=Vector((-r+c+line*s,-r,0));n=Vector((0,-1,0));t=Vector((1,0,0))
 else:p=Vector((r,-r+c+line*s,0));n=Vector((1,0,0));t=Vector((0,1,0))
 return p,t,n,total


def build_astragalos(builder):
 f=FOOTPRINTS['astragalos'][0]/2
 profile=[(0,f-.0015),(.0015,f),(.091,f),(.093,f-.0015),(.095,.2898),(.103,.2907),(.109,.2897),(.115,.2861),(.121,.2741),(.127,.2678),(.133,.2637),(.139,.2609),(.145,.2592),(.149,.2499),(.164,.2485),(.199,.2491),(.205,.2489),(.211,.2470),(.217,.24085),(.851,.21235),(.853,.2183),(.859,.2173),(.860,.2157),(.889,.2150),(.895,.2156),(.901,.2157),(.907,.2228),(.913,.2315),(.919,.2369),(.925,.2395),(.931,.2435),(.937,.2506),(.943,.2601),(.949,.2670),(.955,.2671),(.959,.2765),(.963,.2783),(.9985,.2783),(1,.2768)]
 steps=[(0,0),(.002,.0025),(.004,.0025),(.006,0),(.008,0),(.010,.0017),(.012,.0017),(.015,-.0048),(.017,-.0048)]
 add_core(builder,'astragalos',profile,band=(.217,.851),panel=(.277,.795,.060,steps))
 for z,r,count in [(.181,.2485,128),(.874,.2157,112)]:
  total=rounded_square_path(0,r)[3]
  for j in range(count):
   p,t,n,_=rounded_square_path((j+.5)*total/count,r);p.z=z;add_bead_relief(builder,p,t,n,.0074,.0144,.0137)
 return {'bead_collar_count':2,'bead_centres_y':[.181,.874],'beads_per_collar':[128,112],'framed_panel_count':4,'shaft_y_interval':[.217,.851],'bead_geometry':'Closed rounded arc18 flat-backed barrel relief','method':'Measured clean square profile, explicit recessed panels and 240 individually modeled stone beads'},profile






def create_extended(source,style):
 global _RELIEF_BVH,_RELIEF_SOURCE_OBJ,_ORIGINAL_ARRAYS
 width,depth=FOOTPRINTS[style];sourceobj,bv=source_context(source,width,depth);_RELIEF_BVH=bv;_RELIEF_SOURCE_OBJ=sourceobj;_ORIGINAL_ARRAYS=None
 if style=='astragalos':builder=SolidBuilder();retention,profile=build_astragalos(builder)
 else:
  low,upper=measured_profiles(bv,style);builder=SolidBuilder()
  if style=='kion':retention,profile=build_kion(builder,low,upper)
  elif style=='stephanos':retention,profile=build_stephanos(builder,low,upper)
  else:retention,profile=build_triphyllon(builder,low,upper)
 # Match the exact existing medium footprint, including its slight X/Z ratio.
 ratio=depth/width;builder.vertices=[(x,y*ratio,z) for x,y,z in builder.vertices]
 builder.source_normals={i:Vector((n.x,n.y/ratio,n.z)).normalized() for i,n in builder.source_normals.items()}
 bpy.ops.wm.read_factory_settings(use_empty=True);obj=mesh_from_builder(builder,style)
 obj['Design']='Faithful measured architectural and decorative reconstruction; editable component geometry'
 return obj,retention,profile,[]


















def add_bead_relief(builder, center, tangent, normal, rt, rn, rz,
                    variant="arc18", embed=.0007):
    """Add one sealed bead: current14, barrel16 or rounded arc18 triangles.

    rt/rn/rz are metric half-width, outward depth and half-height. The two new
    variants retain the same visible rim and depth as the existing bead.
    All profiles have outward winding and a flat reverse embedded in the collar.
    """
    center = Vector(center) - Vector(normal) * embed
    tangent, normal, up = Vector(tangent), Vector(normal), Vector((0, 0, 1))
    assert tangent.cross(up).dot(normal) > .999

    def vertex(x, y, z):
        point = center + tangent * (rt*x) + normal * (rn*y) + up * (rz*z)
        analytic = (tangent*(x/rt) + normal*(y/rn) + up*(z/rz)).normalized()
        return builder.vertex(point, analytic)

    rim = [vertex(math.cos(j*math.tau/8), 0, math.sin(j*math.tau/8)) for j in range(8)]
    if variant == "current14":
        peak = vertex(0, 1, 0)
        for j in range(8):
            builder.face((rim[j], rim[(j+1)%8], peak))
    elif variant in ("barrel16", "arc18"):
        offset = .45 if variant == "barrel16" else .55
        forward = math.sqrt(1-offset*offset)
        upper = vertex(0, forward, offset)
        lower = vertex(0, forward, -offset)
        for j in range(4):
            builder.face((rim[j], rim[j+1], upper))
        for j in range(4, 8):
            builder.face((rim[j], rim[(j+1)%8], lower))
        if variant == "barrel16":
            builder.face((rim[0], upper, lower))
            builder.face((rim[4], lower, upper))
        else:
            middle = vertex(0, 1, 0)
            builder.face((rim[0], upper, middle))
            builder.face((rim[0], middle, lower))
            builder.face((rim[4], lower, middle))
            builder.face((rim[4], middle, upper))
    else:
        raise ValueError(variant)
    builder.face(tuple(reversed(rim)))






KION_RADIUS = .34835
KION_DEPTH = .026
KION_CAP_RADIUS = .028
KION_LOWER_SPRING = .285
KION_UPPER_SPRING = .715
KION_FLUTES = 18
KION_CAP_QUADRANT_SEGMENTS = 4


def _kion_clean_face(builder, indices):
    clean = []
    for ix in indices:
        if not clean or clean[-1] != ix:
            clean.append(ix)
    if len(clean) > 1 and clean[0] == clean[-1]:
        clean.pop()
    if len(set(clean)) >= 3:
        assert len(set(clean)) == len(clean), clean
        builder.face(clean)


def _kion_bridge_angles(builder, a, ta, b, tb):
    """Join compact, independently sampled rings without a T junction."""
    i = j = 0
    na, nb = len(a), len(b)
    while i < na or j < nb:
        ai = ta[i + 1] if i + 1 < na else (math.tau if i < na else math.tau + 1)
        bj = tb[j + 1] if j + 1 < nb else (math.tau if j < nb else math.tau + 1)
        if abs(ai - bj) < 1e-9:
            _kion_clean_face(builder, (a[i % na], a[(i + 1) % na], b[(j + 1) % nb], b[j % nb]))
            i += 1
            j += 1
        elif ai < bj:
            _kion_clean_face(builder, (a[i % na], a[(i + 1) % na], b[j % nb]))
            i += 1
        else:
            _kion_clean_face(builder, (a[i % na], b[(j + 1) % nb], b[j % nb]))
            j += 1


def _kion_point_and_normal(theta, z):
    """C1 cosine-bell capsule recess, with exact analytic derivatives."""
    pitch = math.tau / KION_FLUTES
    phase = (theta + pitch / 2) % pitch - pitch / 2
    u = KION_RADIUS * phase
    w = z - KION_LOWER_SPRING if z < KION_LOWER_SPRING else (z - KION_UPPER_SPRING if z > KION_UPPER_SPRING else 0.)
    q = math.hypot(u, w) / KION_CAP_RADIUS
    rho = KION_RADIUS
    rho_theta = rho_z = 0.
    if q < 1:
        rho -= KION_DEPTH * .5 * (1 + math.cos(math.pi * q))
        if q > 1e-10:
            factor = KION_DEPTH * math.pi * .5 * math.sin(math.pi * q) / (KION_CAP_RADIUS * KION_CAP_RADIUS * q)
            rho_theta = factor * KION_RADIUS * u
            rho_z = factor * w
    c, s = math.cos(theta), math.sin(theta)
    point = Vector((rho * c, rho * s, z))
    normal = Vector((c + rho_theta / rho * s, s - rho_theta / rho * c, -rho_z)).normalized()
    return point, normal


def build_kion(builder, low, upper):
    """Compatible with tools.rebuild_plinth_meshes.build_kion."""
    foot = .998954339 / 2
    r = KION_RADIUS
    low = [(0,foot-.002),(.002,foot),(.084,foot),(.087,.464),(.109,.4657),
           (.123,.4585),(.134,.432),(.140,.387),(.158,.381),(.165,.381),
           (.171,.410),(.183,.4163),(.194,.4142),(.205,.4054),(.211,.380),
           (.229,.385),(.233,r),(.250,r)]
    upper = [(.776,r),(.782,.367),(.796,.3756),(.811,.372),(.823,.355),
             (.832,.364),(.855,.367),(.874,.375),(.886,.395),(.906,.4195),
             (.913,.414),(.919,.409),(.926,.428),(.938,.438),(.985,.4435),
             (.998,.4395),(1,.438)]
    profile_angles = [math.tau * j / 48 for j in range(48)]

    def profile_ring(z, rho):
        return [builder.vertex((rho * math.cos(a), rho * math.sin(a), z)) for a in profile_angles]

    low_rings = [profile_ring(z, rho) for z, rho in low]
    upper_rings = [profile_ring(z, rho) for z, rho in upper]
    for rings in (low_rings, upper_rings):
        for a, b in zip(rings, rings[1:]):
            builder.bridge(a, b)

    # Exterior rows follow both exact lips and the land midway between
    # flutes. Corresponding columns track the curved lips instead of crossing
    # them with a fixed radial shaft grid.
    zs = [KION_LOWER_SPRING - KION_CAP_RADIUS * math.cos(math.pi * j / (2 * KION_CAP_QUADRANT_SEGMENTS))
          for j in range(KION_CAP_QUADRANT_SEGMENTS + 1)]
    zs += [KION_UPPER_SPRING + KION_CAP_RADIUS * math.sin(math.pi * j / (2 * KION_CAP_QUADRANT_SEGMENTS))
           for j in range(KION_CAP_QUADRANT_SEGMENTS + 1)]
    pitch = math.tau / KION_FLUTES
    rings = []
    angular_rows = []
    half_widths = []
    for row_index, z in enumerate(zs):
        w = max(KION_LOWER_SPRING - z, 0., z - KION_UPPER_SPRING)
        half = 0. if row_index in (0, len(zs) - 1) else math.sqrt(max(0., KION_CAP_RADIUS**2 - w**2))
        offsets = [-pitch / 2, -half / KION_RADIUS, half / KION_RADIUS]
        angles = [k * pitch + offset for k in range(KION_FLUTES) for offset in offsets]
        ring = []
        for theta in angles:
            p, normal = _kion_point_and_normal(theta, z)
            ring.append(builder.vertex(p, normal))
        rings.append(ring)
        angular_rows.append(angles)
        half_widths.append(half)

    for a, b in zip(rings, rings[1:]):
        for j in range(len(a)):
            if j % 3 != 1:
                _kion_clean_face(builder, (a[j], a[(j + 1) % len(a)], b[(j + 1) % len(b)], b[j]))

    # The recessed surface uses nested capsule contours, so every inner cap
    # is a true concentric semicircle. This keeps the bowl curvature coherent
    # without oversampling a rectangular grid or smoothing an angular tip.
    half_segments = 2 * KION_CAP_QUADRANT_SEGMENTS
    for k in range(KION_FLUTES):
        contours = []
        for fraction in (1., 2/3, 1/3, 0.):
            radius = KION_CAP_RADIUS * fraction
            contour = []
            for spring, start_angle in ((KION_LOWER_SPRING, math.pi), (KION_UPPER_SPRING, 0.)):
                for j in range(half_segments + 1):
                    alpha = start_angle + math.pi * j / half_segments
                    u = radius * math.cos(alpha)
                    z = spring + radius * math.sin(alpha)
                    theta = k * pitch + u / KION_RADIUS
                    p, normal = _kion_point_and_normal(theta, z)
                    contour.append(builder.vertex(p, normal))
            contours.append(contour)
        for a, b in zip(contours, contours[1:]):
            for j in range(len(a)):
                _kion_clean_face(builder, (a[j], a[(j + 1) % len(a)], b[(j + 1) % len(b)], b[j]))

    def compact_tip(ring, angles):
        compact = {}
        for ix, a in zip(ring, angles):
            theta = a % math.tau
            if theta > math.tau - 1e-10 or theta < 1e-10:
                theta = 0.
            compact[ix] = theta
        ordered = sorted(compact.items(), key=lambda pair: pair[1])
        return [p[0] for p in ordered], [p[1] for p in ordered]

    first, first_angles = compact_tip(rings[0], angular_rows[0])
    last, last_angles = compact_tip(rings[-1], angular_rows[-1])
    _kion_bridge_angles(builder, low_rings[-1], profile_angles, first, first_angles)
    _kion_bridge_angles(builder, last, last_angles, upper_rings[0], profile_angles)
    builder.cap(low_rings[0], 0, False)
    builder.cap(upper_rings[-1], 1, True)

    return {
        'flute_count': KION_FLUTES,
        'flute_y_interval': [KION_LOWER_SPRING - KION_CAP_RADIUS, KION_UPPER_SPRING + KION_CAP_RADIUS],
        'shaft_y_interval': [.233, .776],
        'shaft_radius': KION_RADIUS,
        'flute_depth': KION_DEPTH,
        'flute_width': 2 * KION_CAP_RADIUS,
        'flute_width_unit': 'metres along shaft circumference',
        'cap_quadrant_segments': KION_CAP_QUADRANT_SEGMENTS,
        'recess_capsule_contour_radii': [KION_CAP_RADIUS * q for q in (1., 2/3, 1/3, 0.)],
        'cap_boundary_max_chord_error_metres': KION_CAP_RADIUS * (1 - math.cos(math.pi / (4 * KION_CAP_QUADRANT_SEGMENTS))),
        'cap_rows': [{'z':z, 'half_width':half, 'unique_vertices':len(set(row))}
                     for z,half,row in zip(zs,half_widths,rings)],
        'method': 'Feature-aligned capsule topology: exact semicircular lips, concentric semicircular recess contours, merged tips, six flute interior intervals, analytic surface normals; unchanged architectural profiles'
    }, low + [(.5, r)] + upper


def seal_native_relief(bm,normal,source_plane):
 """Repair cut/source defects inside the support with simple closed caps."""
 bmesh.ops.remove_doubles(bm,verts=list(bm.verts),dist=1e-7)
 bmesh.ops.dissolve_degenerate(bm,dist=1e-8,edges=list(bm.edges))
 for _ in range(10):
  over=[e for e in bm.edges if len(e.link_faces)>2]
  if not over:break
  bad=set()
  for e in over:
   fs=list(e.link_faces);back=[f for f in fs if max(v.co.dot(normal) for v in f.verts)<source_plane+.0004]
   ranked=sorted(back if back else fs,key=lambda f:f.calc_area())
   bad.update(ranked[:len(fs)-2])
  bmesh.ops.delete(bm,geom=list(bad),context='FACES_ONLY')
 wires=[e for e in bm.edges if not e.link_faces]
 if wires:bmesh.ops.delete(bm,geom=wires,context='EDGES')
 # Boundary graphs have even degree. Decompose touching boundaries into
 # simple cycles; every cap gets its own centre, so no edge is shared four ways.
 unused=set(e for e in bm.edges if e.is_boundary)
 while unused:
  first=next(iter(unused));path=[first.verts[0]];edgepath=[];current=first.verts[0];local={current:0};previous=None
  while True:
   choices=[e for e in current.link_edges if e in unused and e is not previous]
   if not choices:raise RuntimeError('Source relief has an open cut chain')
   edge=choices[0];nxt=edge.other_vert(current);edgepath.append(edge)
   if nxt in local:
    start=local[nxt];cycle=path[start:];used=edgepath[start:]
    for e in used:unused.remove(e)
    center=sum((v.co for v in cycle),Vector())/len(cycle)-normal*.000001
    cv=bm.verts.new(center)
    for a,b in zip(cycle,cycle[1:]+cycle[:1]):bm.faces.new((a,b,cv))
    break
   path.append(nxt);local[nxt]=len(path)-1;previous=edge;current=nxt
 bmesh.ops.recalc_face_normals(bm,faces=list(bm.faces))
 assert not any(e.is_boundary or len(e.link_faces)>2 for e in bm.edges),'Unsealed native ornament'


def original_relief(builder,normal,tangent,umin,umax,zmin,zmax,source_plane,target_plane,label):
 """Crop the native sculpt, retaining real overlapping/undercut leaf surfaces.

 The original sculpted surface is retained, then redundant ornament triangles
 are reduced only within a measured surface-error bound. The clean analytic
 chassis remains unchanged; cut boundaries close behind the stone face.
 """
 import numpy as np
 global _ORIGINAL_ARRAYS
 normal=Vector(normal);tangent=Vector(tangent)
 if _ORIGINAL_ARRAYS is None:
  source=_RELIEF_SOURCE_OBJ.data;co=np.empty(len(source.vertices)*3,dtype=np.float32);source.vertices.foreach_get('co',co);co=co.reshape((-1,3));tri=np.empty(len(source.polygons)*3,dtype=np.int32);source.polygons.foreach_get('vertices',tri);tri=tri.reshape((-1,3));_ORIGINAL_ARRAYS=(co,tri)
 co,tri=_ORIGINAL_ARRAYS;u=co@np.array(tangent);d=co@np.array(normal);zz=co[:,2];cut=source_plane+.00035;rear=source_plane-.0008
 mask=(u[tri].max(axis=1)>=umin)&(u[tri].min(axis=1)<=umax)&(zz[tri].max(axis=1)>=zmin)&(zz[tri].min(axis=1)<=zmax)&(d[tri].max(axis=1)>=cut)
 selected=tri[mask];ids,inverse=np.unique(selected.ravel(),return_inverse=True);verts=co[ids];faces=inverse.reshape((-1,3));temporary=bpy.data.meshes.new('Native relief extraction');temporary.from_pydata(verts.tolist(),[],faces.tolist());temporary.update();bm=bmesh.new();bm.from_mesh(temporary);bpy.data.meshes.remove(temporary)
 for point,direction in [(tangent*umin,tangent),(-tangent*umax,-tangent),(Vector((0,0,zmin)),Vector((0,0,1))),(Vector((0,0,zmax)),Vector((0,0,-1))),(normal*cut,normal)]:
  # The upper U plane is represented by its negative normal, with the same
  # physical point on that plane.
  if direction.dot(tangent)<-.9:point=tangent*umax
  bmesh.ops.bisect_plane(bm,geom=list(bm.verts)+list(bm.edges)+list(bm.faces),dist=1e-7,plane_co=point,plane_no=direction,clear_inner=True,clear_outer=False)
 # Close the original crop behind the rebuilt stone face before reducing
 # its redundant dense source triangles. The analytic chassis is never touched.
 boundary=[e for e in bm.edges if e.is_boundary];back={}
 for e in boundary:
  for v in e.verts:
   if v not in back:back[v]=bm.verts.new(v.co+normal*(rear-v.co.dot(normal)))
 for e in boundary:
  aa,bb=e.verts
  try:bm.faces.new((aa,bb,back[bb],back[aa]))
  except ValueError:pass
 remaining=[e for e in bm.edges if e.is_boundary]
 if remaining:bmesh.ops.holes_fill(bm,edges=remaining,sides=0)
 bmesh.ops.recalc_face_normals(bm,faces=list(bm.faces))
 from mathutils.bvhtree import BVHTree
 reference=BVHTree.FromBMesh(bm);visible=[v.co.copy() for v in bm.verts if v.co.dot(normal)>source_plane+.001]
 visible=visible[::max(1,len(visible)//2500)]
 dense=bpy.data.meshes.new('Retained native sculpt');bm.to_mesh(dense);bm.free()
 ratio=.025;max_error=0;reduced=None
 while True:
  obj=bpy.data.objects.new('Temporary bounded native relief',dense.copy());bpy.context.scene.collection.objects.link(obj);bpy.context.view_layer.objects.active=obj;obj.select_set(True)
  mod=obj.modifiers.new('Bounded sculpt-only triangle reduction','DECIMATE');mod.decimate_type='COLLAPSE';mod.ratio=ratio;mod.use_collapse_triangulate=True
  bpy.ops.object.modifier_apply(modifier=mod.name)
  candidate=BVHTree.FromObject(obj,bpy.context.evaluated_depsgraph_get());errors=[candidate.find_nearest(v)[3] for v in visible];max_error=max(errors,default=0)
  reduced=obj.data.copy();bpy.data.objects.remove(obj,do_unlink=True)
  if max_error<=.00125 or ratio>=1:break
  bpy.data.meshes.remove(reduced);ratio*=1.6
 bm=bmesh.new();bm.from_mesh(reduced);bpy.data.meshes.remove(reduced);bpy.data.meshes.remove(dense)
 seal_native_relief(bm,normal,source_plane)
 for _ in range(2):
  bmesh.ops.triangulate(bm,faces=list(bm.faces),quad_method='BEAUTY',ngon_method='BEAUTY')
  bmesh.ops.dissolve_degenerate(bm,dist=1e-7,edges=list(bm.edges))
  if any(e.is_boundary or len(e.link_faces)>2 for e in bm.edges):seal_native_relief(bm,normal,source_plane)
 final_surface=BVHTree.FromBMesh(bm)
 max_error=max((final_surface.find_nearest(v)[3] for v in visible),default=0)
 reverse_error=max((reference.find_nearest(v.co)[3] for v in list(bm.verts)[::max(1,len(bm.verts)//2500)] if v.co.dot(normal)>source_plane+.001),default=0)
 bmesh.ops.recalc_face_normals(bm,faces=list(bm.faces));bm.verts.index_update();indices={v:len(builder.vertices)+i for i,v in enumerate(bm.verts)};shift=normal*(target_plane-source_plane)
 for v in bm.verts:builder.vertices.append(tuple(v.co+shift))
 for f in bm.faces:builder.faces.append(tuple(indices[v] for v in f.verts))
 assert max_error<=.00125 and reverse_error<=.00125,(max_error,reverse_error)
 result={'label':label,'selected_source_triangles':int(len(selected)),'retained_vertices':len(bm.verts),'retained_polygons':len(bm.faces),'sculpt_reduction_ratio':ratio,'sampled_source_to_candidate_max_error_m':max_error,'sampled_visible_source_points':len(visible),'error_limit_m':.00125,'sampled_candidate_to_source_max_error_m':reverse_error,'closed_boundaries':not any(e.is_boundary for e in bm.edges),'source_plane':source_plane,'target_plane':target_plane}
 print('NATIVE_RELIEF_EXTRACTED',json.dumps(result),flush=True);bm.free();return result


def build_stephanos(builder,low,upper):
 low=simplify_profile(low,.00085);upper=simplify_profile(upper,.00085);low[-1]=(.223,.3192);upper[0]=(.806,.3192);profile=low+upper
 steps=[(0,0),(.003,.002),(.006,.002),(.010,-.004),(.015,-.004),(.019,-.001),(.022,-.001),(.028,-.0235),(.032,-.0235)]
 add_core(builder,'stephanos',profile,band=(.223,.806),panel=(.283,.755,.096,steps));records=[]
 for face in [1,3]:
  a=face*math.pi/2;n=Vector((math.cos(a),math.sin(a),0));tangent=Vector((-math.sin(a),math.cos(a),0));records.append(original_relief(builder,n,tangent,-.183,.183,.340,.714,.2957,.2957,'Original overlapping laurel wreath and ribbon'))
 for face in range(4):
  a=face*math.pi/2;n=Vector((math.cos(a),math.sin(a),0));tangent=Vector((-math.sin(a),math.cos(a),0))
  for sign in [-1,1]:
   probe=n*2+tangent*(sign*.24)+Vector((0,0,.5));hit,_,_,_=_RELIEF_BVH.ray_cast(probe,-n,4);plane=hit.dot(n)
   records.append(original_relief(builder,n,tangent,min(sign*.235,sign*.326),max(sign*.235,sign*.326),.223,.805,plane,.3192,'Original acanthus, long corner pilaster and base flare'))
 return {'laurel_wreath_count':2,'wreath_faces':'Opposing front/back faces','corner_foliage_applications':8,'framed_panel_count':4,'shaft_y_interval':[.223,.806],'method':'Clean measured architecture with the actual native sculpted wreath, overlapping leaves, ribbons, acanthus curls, long corner pilasters and base flares; original relief surfaces cropped and closed behind the stone face','native_source_relief':records},profile


"""Source-faithful staging-only Triphyllon architectural reconstruction."""


def _triphyllon_simplify(points, tolerance=.0009):
    if len(points) <= 2:
        return points
    a,b = Vector(points[0]),Vector(points[-1])
    ab = b-a
    distances = [abs(ab.x*(Vector(p)-a).y-ab.y*(Vector(p)-a).x)/ab.length if ab.length else (Vector(p)-a).length for p in points]
    at = max(range(len(distances)),key=lambda i:distances[i])
    if distances[at] <= tolerance:
        return [points[0],points[-1]]
    return _triphyllon_simplify(points[:at+1],tolerance)[:-1]+_triphyllon_simplify(points[at:],tolerance)


def _triphyllon_bezier(a,b,c,d,steps):
    a,b,c,d = map(Vector,(a,b,c,d))
    return [tuple(a*(1-t)**3+b*3*(1-t)**2*t+c*3*(1-t)*t*t+d*t**3)
            for t in [j/steps for j in range(steps+1)]]


def _triphyllon_lancet(half,bottom,spring,apex,segments=12):
    rise=apex-spring
    centre=(rise*rise-half*half)/(2*half)
    radius=half+centre
    end=math.acos(-centre/radius)
    left=[(-half,bottom),(-half,spring)]
    for j in range(1,segments+1):
        angle=math.pi-(math.pi-end)*j/segments
        left.append((centre+radius*math.cos(angle),spring+radius*math.sin(angle)))
    left[-1]=(0,apex)
    return left+[( -u,z) for u,z in reversed(left[:-1])]


def _triphyllon_trefoiled_opening():
    """An open-neck trefoil head, continuously joined to its arched jambs."""
    left=[(-.0645,.221),(-.0645,.588)]
    low_cusp=(-.010,.637)
    left += _triphyllon_bezier(left[-1],(-.0645,.612),(-.035,.628),low_cusp,6)[1:]
    left.append((-.010,.6479474411))
    # Major 240-degree circle arc gives the deep cusp at either end of the
    # side lobe. It is not an isolated closed clover pasted on the panel.
    for j in range(1,17):
        angle=-math.pi/3-j*(4*math.pi/3)/16
        left.append((-.021+.022*math.cos(angle),.667+.022*math.sin(angle)))
    left[-1]=(-.010,.6860525589)
    left += _triphyllon_bezier(left[-1],(-.025,.701),(-.026,.721),(0,.737),8)[1:]
    return left+[(-u,z) for u,z in reversed(left[:-1])]


def _triphyllon_outward_normals(path):
    result=[]
    for i,p in enumerate(path):
        prev=(Vector(p)-Vector(path[i-1])).normalized()
        nxt=(Vector(path[(i+1)%len(path)])-Vector(p)).normalized()
        n0,n1=Vector((-prev.y,prev.x)),Vector((-nxt.y,nxt.x))
        average=n0+n1
        den=average.dot(n1)
        miter=average/den if abs(den)>1e-8 else n1
        if miter.length>3.5:
            miter=miter.normalized()*3.5
        result.append(miter)
    return result


def _triphyllon_annulus(builder,a,aid,b,bid):
    """Constrained triangulation between resolved noncrossing contours."""
    def area(path):
        return abs(sum(x[0]*y[1]-x[1]*y[0] for x,y in zip(path,path[1:]+path[:1])))
    na,nb=len(a),len(b)
    points=[Vector(p) for p in a+b]
    edges=[(i,(i+1)%na) for i in range(na)]
    edges += [(na+i,na+(i+1)%nb) for i in range(nb)]
    outer=list(range(na)) if area(a)>area(b) else list(range(na,na+nb))
    inner=b if area(a)>area(b) else a
    coords,_,faces,orig,_,_=delaunay_2d_cdt(points,edges,[outer],1,1e-8,True)
    assert all(orig), 'Moulding contours must have no intersections'
    original_ids=aid+bid
    ids=[original_ids[old[0]] for old in orig]
    for face in faces:
        centre=sum((coords[i] for i in face),Vector((0,0)))/len(face)
        if not _triphyllon_inside(centre,inner):
            builder.face([ids[i] for i in face])


def _triphyllon_moulding(builder,n,tangent,paths,depths,profile_offsets,embed=.322):
    """Closed moulded stone with resolved cusp joins and planar backing."""
    assert len(paths)==len(depths)
    rings=[]
    for row,(path,depth) in enumerate(zip(paths,depths)):
        slope=0.
        if 0<row<len(paths)-1 and (depth-depths[row-1])*(depths[row+1]-depth)>0:
            slope=(depths[row+1]-depths[row-1])/(profile_offsets[row+1]-profile_offsets[row-1])
        outwards=_triphyllon_outward_normals(path)
        rings.append([builder.vertex(n*depth+tangent*u+Vector((0,0,z)),
                                     (n-(tangent*direction.x+Vector((0,0,direction.y)))*slope).normalized())
                      for (u,z),direction in zip(path,outwards)])
    for i in range(len(rings)-1):
        _triphyllon_annulus(builder,paths[i],rings[i],paths[i+1],rings[i+1])
    backs=[]
    for path in (paths[0],paths[-1]):
        backs.append([builder.vertex(n*embed+tangent*u+Vector((0,0,z))) for u,z in path])
    for i in (0,1):
        ring=rings[0 if i==0 else -1]
        back=backs[i]
        for j in range(len(ring)):
            k=(j+1)%len(ring)
            builder.face((ring[j],back[j],back[k],ring[k]))
    _triphyllon_annulus(builder,paths[0],backs[0],paths[-1],backs[-1])


def _triphyllon_inside(point,path):
    x,y=point
    inside=False
    for a,b in zip(path,path[1:]+path[:1]):
        if (a[1]>y)!=(b[1]>y) and x<(b[0]-a[0])*(y-a[1])/(b[1]-a[1])+a[0]:
            inside=not inside
    return inside


def _triphyllon_recessed_side(builder,n,tangent,apothem,opening,outer_ids):
    half=apothem*math.tan(math.pi/8)
    outer=[(-half,.203),(half,.203),(half,.871),(-half,.871)]
    points=[Vector(p) for p in outer+opening]
    edges=[(i,(i+1)%4) for i in range(4)]
    edges += [(4+i,4+(i+1)%len(opening)) for i in range(len(opening))]
    coords,ee,faces,orig,_,_=delaunay_2d_cdt(points,edges,[list(range(4))],1,1e-8,True)
    top=[builder.vertex(n*apothem+tangent*u+Vector((0,0,z))) for u,z in opening]
    original_ids=outer_ids+top
    assert all(old for old in orig), [(tuple(p),old) for p,old in zip(coords,orig) if not old]
    ids=[original_ids[old[0]] for old in orig]
    for face in faces:
        centre=sum((coords[i] for i in face),Vector((0,0)))/len(face)
        if not _triphyllon_inside(centre,opening):
            builder.face([ids[i] for i in face])
    inset=[builder.vertex(n*.3218+tangent*u+Vector((0,0,z)),n) for u,z in opening]
    for j in range(len(top)):
        k=(j+1)%len(top)
        builder.face((top[j],top[k],inset[k],inset[j]))
    poly=[Vector(p) for p in opening]
    _,_,inner_faces,inner_orig,_,_=delaunay_2d_cdt(poly,[],[list(range(len(poly)))],1,1e-8,True)
    assert all(inner_orig)
    for triangle in inner_faces:
        builder.face([inset[inner_orig[i][0]] for i in triangle])


def _triphyllon_lathe(builder,centre,n,tangent,profile,sides=12,shaft_normals=False):
    component_ids={}
    def vertex(p,normal):
        key=tuple(round(float(c),10) for c in p)
        if key not in component_ids:
            if key in builder.ids:
                # Separate closed capitals can touch at an embedded tangent
                # point. Keep their indices distinct without moving stone.
                ix=len(builder.vertices)
                builder.vertices.append(tuple(p))
                if normal is not None:builder.source_normals[ix]=normal.copy()
            else:
                ix=builder.vertex(p,normal)
            component_ids[key]=ix
        return component_ids[key]
    rings=[]
    for row,(z,radius) in enumerate(profile):
        ring=[]
        for j in range(sides):
            angle=math.tau*j/sides
            radial=n*math.cos(angle)+tangent*math.sin(angle)
            p=centre+radial*radius+Vector((0,0,z))
            ring.append(vertex(p,radial if shaft_normals and row in (0,1) else None))
        rings.append(ring)
    for a,b in zip(rings,rings[1:]):
        builder.bridge(a,b)
    builder.face(reversed(rings[0]))
    builder.face(rings[-1])


def _triphyllon_cluster_foot(builder,n,tangent):
    # One broad shared foot, as in the source, rather than three tiny feet.
    profile=[(.203,.045,.032),(.211,.046,.032),(.220,.040,.025),
             (.229,.033,.017),(.235,.039,.023),(.241,.042,.025),
             (.247,.039,.022),(.253,.036,.017)]
    rings=[]
    for z,width,depth in profile:
        rings.append([builder.vertex(n*(.3675+depth*math.cos(math.tau*j/16))+
                                     tangent*(width*math.sin(math.tau*j/16))+Vector((0,0,z)))
                      for j in range(16)])
    for a,b in zip(rings,rings[1:]):
        builder.bridge(a,b)
    builder.face(reversed(rings[0]));builder.face(rings[-1])


def build_triphyllon(builder,low,upper):
    low=_triphyllon_simplify(low);upper=_triphyllon_simplify(upper)
    apothem=.3235
    low[-1]=(.203,apothem);upper[0]=(.871,apothem)
    profile=low+upper
    rings=[]
    for z,radius in profile:
        ring=[builder.vertex((radius/math.cos(math.pi/8)*math.cos(math.pi/8+j*math.pi/4),
                              radius/math.cos(math.pi/8)*math.sin(math.pi/8+j*math.pi/4),z)) for j in range(8)]
        rings.append(ring)
    for i,(a,b) in enumerate(zip(rings,rings[1:])):
        if abs(profile[i][0]-.203)<1e-8 and abs(profile[i+1][0]-.871)<1e-8:
            continue
        builder.bridge(a,b)
    builder.cap(rings[0],0,False);builder.cap(rings[-1],1,True)
    shaft_low=rings[len(low)-1]
    shaft_high=rings[len(low)]

    inner_paths=TRIPHYLLON_RESOLVED_TREFOIL_CONTOURS
    opening=inner_paths[0]
    arch_specs=[(.114,.212,.595,.818),(.110,.216,.595,.810),
                (.099,.220,.595,.795),(.088,.222,.595,.781),(.081,.225,.595,.772)]
    arch_depths=[.324,.345,.334,.344,.324]
    fine_specs=[];fine_depths=[]
    for i in range(len(arch_specs)-1):
        fine_specs.extend((arch_specs[i],tuple((a+b)/2 for a,b in zip(arch_specs[i],arch_specs[i+1]))))
        fine_depths.extend((arch_depths[i],(arch_depths[i]+arch_depths[i+1])/2))
    fine_specs.append(arch_specs[-1]);fine_depths.append(arch_depths[-1])
    arch_specs=fine_specs;arch_depths=fine_depths
    arch_paths=[_triphyllon_lancet(*spec,segments=12) for spec in arch_specs]
    # The three separate stone bowls are tall, straight-sided flares with
    # short annulets and flat abaci; they do not become a stack of balls.
    column_profile=[(.250,.0125),(.788,.0125),(.790,.014),(.794,.019),
                    (.800,.019),(.804,.014),(.809,.013),(.815,.014),
                    (.826,.0205),(.830,.023),(.834,.024),(.841,.024),
                    (.844,.0125),(.871,.0125)]
    for face in range(8):
        phi=face*math.pi/4
        n=Vector((math.cos(phi),math.sin(phi),0));tangent=Vector((-n.y,n.x,0))
        outer_ids=[shaft_low[(face-1)%8],shaft_low[face],shaft_high[face],shaft_high[(face-1)%8]]
        _triphyllon_recessed_side(builder,n,tangent,apothem,opening,outer_ids)
        _triphyllon_moulding(builder,n,tangent,arch_paths,arch_depths,[s[0] for s in arch_specs])
        _triphyllon_moulding(builder,n,tangent,inner_paths,[.324,.334,.341,.334,.324],[0.,.0025,.0065,.0105,.013])
        phi=math.pi/8+face*math.pi/4
        n=Vector((math.cos(phi),math.sin(phi),0));tangent=Vector((-n.y,n.x,0))
        _triphyllon_cluster_foot(builder,n,tangent)
        for offset,radial in ((-.023,.3595),(0.,.368),(+.023,.3595)):
            _triphyllon_lathe(builder,n*radial+tangent*offset,n,tangent,column_profile,sides=12,shaft_normals=True)
    return {'pointed_panel_count':8,'trefoil_count':8,'colonnette_count':8,
            'rounded_shafts_per_cluster':3,'shaft_diameter_m':.025,
            'shaft_cluster_width_m':.071,'cluster_capital_width_m':.094,
            'shaft_y_interval':[.250,.788],'capital_y_interval':[.788,.871],
            'trefoil_opening_y_interval':[.6479474411,.737],
            'trefoil_integration':'Continuous open-neck cusped head joined to the panel jambs; no separate clover loop',
            'method':'Source-measured broad triple shafts, shared rounded feet and three cup capitals; smooth true circular pointed arches, layered stone mouldings and continuous deeply cusped trefoil recesses'},profile


TRIPHYLLON_RESOLVED_TREFOIL_CONTOURS = [[(-0.0645, 0.221), (-0.0645, 0.588), (-0.062199074074, 0.599337962963), (-0.055925925926, 0.60937037037),
  (-0.046625, 0.618125), (-0.035240740741, 0.62562962963), (-0.022717592593, 0.631912037037), (-0.01, 0.637),
  (-0.01, 0.6479474411), (-0.015305981008, 0.645749631822), (-0.021, 0.645),
  (-0.026694018992, 0.645749631822), (-0.032, 0.647947441117), (-0.036556349186, 0.651443650814),
  (-0.040052558883, 0.656), (-0.042250368178, 0.661305981008), (-0.043, 0.667),
  (-0.042250368178, 0.672694018992), (-0.040052558883, 0.678), (-0.036556349186, 0.682556349186),
  (-0.032, 0.686052558883), (-0.026694018992, 0.688250368178), (-0.021, 0.689),
  (-0.015305981008, 0.688250368178), (-0.01, 0.6860525589), (-0.014943359375, 0.691877007232),
  (-0.018421875, 0.698069048286), (-0.020283203125, 0.704522597388), (-0.020375, 0.711131569863),
  (-0.018544921875, 0.717789881036), (-0.014640625, 0.724391446233), (-0.008509765625, 0.730830180779),
  (0.0, 0.737), (0.008509765625, 0.730830180779), (0.014640625, 0.724391446233),
  (0.018544921875, 0.717789881036), (0.020375, 0.711131569863), (0.020283203125, 0.704522597388),
  (0.018421875, 0.698069048286), (0.014943359375, 0.691877007232), (0.01, 0.6860525589),
  (0.015305981008, 0.688250368178), (0.021, 0.689), (0.026694018992, 0.688250368178), (0.032, 0.686052558883),
  (0.036556349186, 0.682556349186), (0.040052558883, 0.678), (0.042250368178, 0.672694018992), (0.043, 0.667),
  (0.042250368178, 0.661305981008), (0.040052558883, 0.656), (0.036556349186, 0.651443650814),
  (0.032, 0.647947441117), (0.026694018992, 0.645749631822), (0.021, 0.645), (0.015305981008, 0.645749631822),
  (0.01, 0.6479474411), (0.01, 0.637), (0.022717592593, 0.631912037037), (0.035240740741, 0.62562962963),
  (0.046625, 0.618125), (0.055925925926, 0.60937037037), (0.062199074074, 0.599337962963), (0.0645, 0.588),
  (0.0645, 0.221)],
 [(-0.067, 0.2185), (-0.067, 0.588251115531), (-0.064559016054, 0.600279223544),
  (-0.057874493249, 0.610969526805), (-0.048181252775, 0.620093428117), (-0.036493567986, 0.627798079212),
  (-0.023744234811, 0.634193955411), (-0.0125, 0.638692467991), (-0.0125, 0.644205926704),
  (-0.01465335005, 0.643313979916), (-0.021, 0.642478427599), (-0.027346649953, 0.643313979916),
  (-0.033260786201, 0.64576369536), (-0.03833937013, 0.64966062987), (-0.04223630464, 0.654739213799),
  (-0.044686020084, 0.660653350047), (-0.045521572401, 0.667), (-0.044686020084, 0.673346649953),
  (-0.04223630464, 0.679260786201), (-0.03833937013, 0.68433937013), (-0.033260786201, 0.68823630464),
  (-0.027346649953, 0.690686020084), (-0.021, 0.691521572401), (-0.017340512008, 0.691039791667),
  (-0.020743570747, 0.69709751332), (-0.02277830061, 0.704152276476), (-0.022879689391, 0.711451824138),
  (-0.020866928165, 0.718774785042), (-0.01664750618, 0.725909178307), (-0.01016248761, 0.732719856965),
  (0.0, 0.74008794607), (0.01016248761, 0.732719856965), (0.01664750618, 0.725909178307),
  (0.020866928165, 0.718774785042), (0.022879689391, 0.711451824138), (0.02277830061, 0.704152276476),
  (0.020743570747, 0.69709751332), (0.017340512008, 0.691039791667), (0.021, 0.691521572401),
  (0.027346649953, 0.690686020084), (0.033260786201, 0.68823630464), (0.03833937013, 0.68433937013),
  (0.04223630464, 0.679260786201), (0.044686020084, 0.673346649953), (0.045521572401, 0.667),
  (0.044686020084, 0.660653350047), (0.04223630464, 0.654739213799), (0.03833937013, 0.64966062987),
  (0.033260786201, 0.64576369536), (0.027346649953, 0.643313979916), (0.021, 0.642478427599),
  (0.01465335005, 0.643313979916), (0.0125, 0.644205926704), (0.0125, 0.638692467991),
  (0.023744234811, 0.634193955411), (0.036493567986, 0.627798079212), (0.048181252775, 0.620093428117),
  (0.057874493249, 0.610969526805), (0.064559016054, 0.600279223544), (0.067, 0.588251115531),
  (0.067, 0.2185)],
 [(-0.071, 0.2145), (-0.071, 0.58865290038), (-0.068334923223, 0.601785240473),
  (-0.060992200967, 0.6135281771), (-0.050671257216, 0.623242913105), (-0.038498091579, 0.631267598544),
  (-0.025386862361, 0.63784502481), (-0.023174388509, 0.638730175434), (-0.028390859491, 0.639416936868),
  (-0.035278044122, 0.642269702148), (-0.041192203641, 0.646807796359), (-0.045730297852, 0.652721955878),
  (-0.048583063132, 0.659609140509), (-0.049556088244, 0.667), (-0.048583063132, 0.674390859491),
  (-0.045730297852, 0.681278044122), (-0.041192203641, 0.687192203641), (-0.035278044122, 0.691730297852),
  (-0.028390859491, 0.694583063132), (-0.024226943774, 0.695131253036), (-0.024458283943, 0.695543057375),
  (-0.026770456586, 0.703559763018), (-0.026887192417, 0.711964230978), (-0.024582138229, 0.720350631453),
  (-0.019858516069, 0.728337549625), (-0.012806842786, 0.735743338862), (0.0, 0.745028659782),
  (0.012806842786, 0.735743338862), (0.019858516069, 0.728337549625), (0.024582138229, 0.720350631453),
  (0.026887192417, 0.711964230978), (0.026770456586, 0.703559763018), (0.024458283943, 0.695543057375),
  (0.024226943774, 0.695131253036), (0.028390859491, 0.694583063132), (0.035278044122, 0.691730297852),
  (0.041192203641, 0.687192203641), (0.045730297852, 0.681278044122), (0.048583063132, 0.674390859491),
  (0.049556088244, 0.667), (0.048583063132, 0.659609140509), (0.045730297852, 0.652721955878),
  (0.041192203641, 0.646807796359), (0.035278044122, 0.642269702148), (0.028390859491, 0.639416936868),
  (0.023174388509, 0.638730175434), (0.025386862361, 0.63784502481), (0.038498091579, 0.631267598544),
  (0.050671257216, 0.623242913105), (0.060992200967, 0.6135281771), (0.068334923223, 0.601785240473),
  (0.071, 0.58865290038), (0.071, 0.2145)],
 [(-0.075, 0.2105), (-0.075, 0.589054685229), (-0.072110830392, 0.603291257402),
  (-0.064109908685, 0.616086827394), (-0.053161261656, 0.626392398092), (-0.040502615172, 0.634737117876),
  (-0.034642545766, 0.637676901309), (-0.037295302043, 0.638775708937), (-0.044045037152, 0.643954962848),
  (-0.049224291063, 0.650704697957), (-0.052480106181, 0.658564930971), (-0.053590604086, 0.667),
  (-0.052480106181, 0.675435069029), (-0.049224291063, 0.683295302043), (-0.044045037152, 0.690045037152),
  (-0.037295302043, 0.695224291063), (-0.029464873184, 0.698467760896), (-0.030762612562, 0.70296724956),
  (-0.030894695443, 0.712476637819), (-0.028297348293, 0.721926477864), (-0.023069525957, 0.730765920943),
  (-0.015451197961, 0.73876682076), (0.0, 0.749969373494), (0.015451197961, 0.73876682076),
  (0.023069525957, 0.730765920943), (0.028297348293, 0.721926477864), (0.030894695443, 0.712476637819),
  (0.030762612562, 0.70296724956), (0.029464873184, 0.698467760896), (0.037295302043, 0.695224291063),
  (0.044045037152, 0.690045037152), (0.049224291063, 0.683295302043), (0.052480106181, 0.675435069029),
  (0.053590604086, 0.667), (0.052480106181, 0.658564930971), (0.049224291063, 0.650704697957),
  (0.044045037152, 0.643954962848), (0.037295302043, 0.638775708937), (0.034642545766, 0.637676901309),
  (0.040502615172, 0.634737117876), (0.053161261656, 0.626392398092), (0.064109908685, 0.616086827394),
  (0.072110830392, 0.603291257402), (0.075, 0.589054685229), (0.075, 0.2105)],
 [(-0.0775, 0.208), (-0.0775, 0.589305800759), (-0.074470772372, 0.604232517983),
  (-0.066058476008, 0.617685983829), (-0.054717514431, 0.628360826209), (-0.041755442418, 0.636905567459),
  (-0.040068001576, 0.637752095083), (-0.045828058097, 0.642171941903), (-0.05140803682, 0.649443911756),
  (-0.054915758087, 0.65791230001), (-0.056112176488, 0.667), (-0.054915758087, 0.67608769999),
  (-0.05140803682, 0.684556088244), (-0.045828058097, 0.691828058097), (-0.038556088244, 0.69740803682),
  (-0.032486276786, 0.699922235047), (-0.033257710047, 0.702596928649), (-0.033399384834, 0.712796892094),
  (-0.030619354583, 0.722911381871), (-0.025076407137, 0.732283653016), (-0.017103919946, 0.740656496945),
  (0.0, 0.753057319564), (0.017103919946, 0.740656496945), (0.025076407137, 0.732283653016),
  (0.030619354583, 0.722911381871), (0.033399384834, 0.712796892094), (0.033257710047, 0.702596928649),
  (0.032486276786, 0.699922235047), (0.038556088244, 0.69740803682), (0.045828058097, 0.691828058097),
  (0.05140803682, 0.684556088244), (0.054915758087, 0.67608769999), (0.056112176488, 0.667),
  (0.054915758087, 0.65791230001), (0.05140803682, 0.649443911756), (0.045828058097, 0.642171941903),
  (0.040068001576, 0.637752095083), (0.041755442418, 0.636905567459), (0.054717514431, 0.628360826209),
  (0.066058476008, 0.617685983829), (0.074470772372, 0.604232517983), (0.0775, 0.589305800759),
  (0.0775, 0.208)]]


def _stephanos_remove_faulty_rear_triangles(obj):
    """Rendered-asset repair: delete only invalid hidden ears, preserve the rest."""
    import bpy,hashlib,json
    source=obj.data;source.calc_loop_triangles()
    positions=[tuple(v.co) for v in source.vertices]
    normal_values=[tuple(n.vector) for n in source.corner_normals]
    kept=[];removed=[];original_edges=collections.defaultdict(list)
    def cross_squared(points):
        a,b,c=[tuple(round(x,9) for x in p) for p in points]
        u=tuple(y-x for x,y in zip(a,b));v=tuple(y-x for x,y in zip(a,c))
        cross=(u[1]*v[2]-u[2]*v[1],u[2]*v[0]-u[0]*v[2],u[0]*v[1]-u[1]*v[0])
        return sum(x*x for x in cross)
    for ti,t in enumerate(source.loop_triangles):
        for a,b in zip(t.vertices,list(t.vertices)[1:]+list(t.vertices)[:1]):
            original_edges[tuple(sorted((a,b)))].append(ti)
        points=[positions[i] for i in t.vertices]
        norm=[normal_values[i] for i in t.loops]
        record=(tuple(t.vertices),tuple(norm),ti)
        if cross_squared(points)<=1e-24:
            assert _stephanos_rear_plane(points) is not None, ('Invalid triangle outside retained rear planes',points)
            removed.append({'source_triangle_index':ti,'source_polygon_index':t.polygon_index,
                            'source_vertex_indices':list(t.vertices),'positions':points,
                            'squared_cross_product_after_9_decimal_export':cross_squared(points)})
        else:kept.append(record)
    assert removed
    used=sorted({v for face,_,_ in kept for v in face});mapping={old:new for new,old in enumerate(used)}
    vertices=[positions[i] for i in used]
    faces=[tuple(mapping[i] for i in face) for face,_,_ in kept]
    mesh=bpy.data.meshes.new('Stephanos - original visible sculpt; invalid hidden cap ears removed')
    mesh.from_pydata(vertices,[],faces);mesh.update()
    for polygon in mesh.polygons:polygon.use_smooth=True
    normals=[n for _,row,_ in kept for n in row]
    mesh.normals_split_custom_set(normals)
    for material in source.materials:mesh.materials.append(material)
    attr=mesh.attributes.new('retained_obj_corner_normals','FLOAT_VECTOR','CORNER')
    attr.data.foreach_set('vector',[v for normal in normals for v in normal])
    provenance=mesh.attributes.new('retained_source_triangle_index','INT','FACE')
    provenance.data.foreach_set('value',[ti for _,_,ti in kept])
    obj.data=mesh
    fingerprint=hashlib.sha256();obj_fingerprint=hashlib.sha256()
    for face,row,_ in kept:
        fingerprint.update(json.dumps([[positions[v],n] for v,n in zip(face,row)],separators=(',',':')).encode('ascii'))
        values=[]
        for v,n in zip(face,row):
            p=positions[v]
            values.append([[round(p[0],9),round(p[2],9),round(-p[1],9)],
                           [round(n[0],9),round(n[2],9),round(-n[1],9)]])
        obj_fingerprint.update(json.dumps(values,separators=(',',':')).encode('ascii'))
    return {'method':'Remove only invalid zero/tiny-area hidden rear cap triangles; retain all other source triangles and exact corner normals',
            'removed_hidden_triangles':removed,'removed_count':len(removed),'retained_source_triangles':len(kept),
            'source_to_output_vertex_indices':{str(old):new for old,new in mapping.items()},
            'all_retained_positions_and_normals_exact':True,'retained_triangle_attribute_sha256':fingerprint.hexdigest(),
            'retained_obj_triangle_attribute_sha256':obj_fingerprint.hexdigest(),
            'preexisting_source_tessellation_edges_over_two':[{'source_vertex_indices':list(edge),'source_triangle_indices':faces,'valence':len(faces)} for edge,faces in original_edges.items() if len(faces)>2],
            'hidden_relief_may_be_open':True,'architectural_core_unchanged':True}


def reduce_stephanos(obj,target_faces=16000):
 """Reduce only native ornament; preserve the measured core and its normals."""
 from mathutils.bvhtree import BVHTree
 mesh=obj.data;mesh.calc_loop_triangles()
 if len(mesh.polygons)<=target_faces:return {'applied':False,'polygons':len(mesh.polygons)}
 original_positions=[tuple(v.co) for v in mesh.vertices]
 original_normals=[tuple(v.vector) for v in mesh.attributes['retained_obj_corner_normals'].data]
 roots=list(range(len(mesh.vertices)))
 def find(i):
  while roots[i]!=i:roots[i]=roots[roots[i]];i=roots[i]
  return i
 for e in mesh.edges:
  a,b=map(find,e.vertices);roots[b]=a
 groups=collections.defaultdict(list)
 for i,v in enumerate(mesh.vertices):groups[find(i)].append(i)
 core=next(set(ids) for ids in groups.values() if min(mesh.vertices[i].co.z for i in ids)<1e-8 and max(mesh.vertices[i].co.z for i in ids)>1-1e-8)
 core_faces=[t for t in mesh.loop_triangles if t.vertices[0] in core]
 relief_faces=[t for t in mesh.loop_triangles if t.vertices[0] not in core]
 assert target_faces>len(core_faces),'Target must leave room for the unchanged architectural core'
 report={'original_polygons':len(mesh.polygons),'original_triangles':len(mesh.loop_triangles),'target_editable_faces':target_faces,'preserved_core_triangles':len(core_faces),'preserved_core_vertices':len(core)}
 print('STEPHANOS_REDUCING',json.dumps(report),flush=True)
 relief_ids=sorted({v for t in relief_faces for v in t.vertices});remap={v:i for i,v in enumerate(relief_ids)}
 rv=[original_positions[i] for i in relief_ids];rf=[tuple(remap[i] for i in t.vertices) for t in relief_faces]
 rn=[[Vector(original_normals[i]) for i in t.loops] for t in relief_faces]
 reference=BVHTree.FromPolygons(rv,rf,all_triangles=True)
 dense=bpy.data.meshes.new('Source relief only');dense.from_pydata(rv,[],rf);dense.update()
 def reduced(ratio):
  r=bpy.data.objects.new('Sculpt-only reduction',dense.copy());bpy.context.scene.collection.objects.link(r)
  bpy.ops.object.select_all(action='DESELECT');r.select_set(True);bpy.context.view_layer.objects.active=r
  mod=r.modifiers.new('Requested reduction to around16k editable faces','DECIMATE');mod.decimate_type='COLLAPSE';mod.ratio=ratio;mod.use_collapse_triangulate=True
  bpy.ops.object.modifier_apply(modifier=mod.name)
  m=r.data.copy();bpy.data.objects.remove(r,do_unlink=True);m.calc_loop_triangles();return m
 ratio=(target_faces-len(core_faces))/len(relief_faces);candidate=reduced(ratio)
 report['sculpt_only_ratio']=ratio
 positions=[];faces=[];normals=[]
 core_ids=sorted(core);cm={i:j for j,i in enumerate(core_ids)}
 positions.extend(original_positions[i] for i in core_ids)
 for t in core_faces:
  faces.append(tuple(cm[i] for i in t.vertices));normals.extend(original_normals[i] for i in t.loops)
 offset=len(positions);positions.extend(tuple(v.co) for v in candidate.vertices)
 def sampled_normal(p,geometric):
  hit,_,idx,dist=reference.find_nearest(p)
  if hit is None:return tuple(geometric)
  a,b,c=[Vector(rv[i]) for i in rf[idx]];v0=b-a;v1=c-a;v2=hit-a
  d00=v0.dot(v0);d01=v0.dot(v1);d11=v1.dot(v1);d20=v2.dot(v0);d21=v2.dot(v1);den=d00*d11-d01*d01
  if abs(den)<1e-24:return tuple(rn[idx][0])
  v=(d11*d20-d01*d21)/den;w=(d00*d21-d01*d20)/den;u=1-v-w
  n=rn[idx][0]*u+rn[idx][1]*v+rn[idx][2]*w
  return tuple(n.normalized()) if n.length_squared>1e-16 else tuple(geometric)
 for t in candidate.loop_triangles:
  p=[candidate.vertices[i].co for i in t.vertices]
  if (p[1]-p[0]).cross(p[2]-p[0]).length_squared<=1e-24:continue
  faces.append(tuple(offset+i for i in t.vertices))
  centroid=sum(p,Vector())/3
  for v in p:normals.append(sampled_normal(v*.999+centroid*.001,t.normal))
 result=bpy.data.meshes.new('Stephanos - requested16k face sculpt with unchanged architectural core');result.from_pydata(positions,[],faces);result.update()
 for p in result.polygons:p.use_smooth=True
 result.normals_split_custom_set(normals);attr=result.attributes.new('retained_obj_corner_normals','FLOAT_VECTOR','CORNER');attr.data.foreach_set('vector',[x for n in normals for x in n])
 for mat in mesh.materials:result.materials.append(mat)
 oldmesh=mesh;obj.data=result;obj['Reduction request']='Requested editable-face reduction preserving exact analytic core';obj['style']='stephanos';obj['embedded_native_relief_render_asset']=True
 report.update(verify(obj));report['core_geometry_and_corner_normals_exact']=all(tuple(result.vertices[i].co)==original_positions[old] for i,old in enumerate(core_ids)) and [tuple(n) for n in normals[:len(core_faces)*3]]==[original_normals[i] for t in core_faces for i in t.loops]
 report['core_digest']=hashlib.sha256(json.dumps([[original_positions[i] for i in t.vertices]+[original_normals[i] for i in t.loops] for t in core_faces],separators=(',',':')).encode()).hexdigest()
 newref=BVHTree.FromPolygons([tuple(v.co) for v in candidate.vertices],[tuple(t.vertices) for t in candidate.loop_triangles],all_triangles=True)
 source_errors=[newref.find_nearest(Vector(p))[3] for p in rv[::max(1,len(rv)//3000)]]
 candidate_errors=[reference.find_nearest(v.co)[3] for v in list(candidate.vertices)[::max(1,len(candidate.vertices)//3000)]]
 report['sampled_surface_error_m']={'source_to_candidate_max':max(source_errors),'candidate_to_source_max':max(candidate_errors)}
 obj['requested_reduction_target_faces']=target_faces
 report['applied']=True
 return report


def _stephanos_export_preserved_obj(obj,path):
    """Write original raw corner normal vectors without Blender repacking."""
    mesh=obj.data;mesh.calc_loop_triangles();attr=mesh.attributes['retained_obj_corner_normals']
    reduced=bool(obj.get('requested_reduction_target_faces',0))
    lines=[('# Stephanos requested editable-face reduction; exact architectural core and native sculpt retained\n' if reduced else '# Stephanos original sculpt with only invalid embedded rear cap ears removed\n'),
           ('# Y-up; height=1m; material none; no UVs; ornament reduced with transferred corner normals\n' if reduced else '# Y-up; height=1m; material none; no UVs; valid exposed geometry retained exactly\n'),'o plinth_stephanos\n']
    for v in mesh.vertices:lines.append(f'v {v.co.x:.9f} {v.co.z:.9f} {-v.co.y:.9f}\n')
    for value in attr.data:
        n=value.vector;lines.append(f'vn {n.x:.9f} {n.z:.9f} {-n.y:.9f}\n')
    lines+=['usemtl none\n','s off\n']
    for t in mesh.loop_triangles:lines.append('f '+' '.join(f'{v+1}//{li+1}' for v,li in zip(t.vertices,t.loops))+'\n')
    path.write_text(''.join(lines),encoding='utf-8',newline='\n')


STEPHANOS_SUPPORT_RATIO=.831934729/.831943020


STEPHANOS_BACK_PLANES=[(0,s*.3184) for s in (-1,1)]


STEPHANOS_BACK_PLANES += [(1,s*r*STEPHANOS_SUPPORT_RATIO) for r in (.3184,.2949) for s in (-1,1)]


def _stephanos_rear_plane(points):
    for axis,value in STEPHANOS_BACK_PLANES:
        tolerance=.0008*(STEPHANOS_SUPPORT_RATIO if axis==1 else 1.)
        sign=math.copysign(1.,value)
        if sum(abs(p[axis]-value)<2e-6 for p in points)>=2 and all(sign*(p[axis]-value)<=tolerance+2e-6 for p in points):
            return axis,value
    return None


def main():
 a=args()
 if bpy is None:
  blender=a.blender or pathlib.Path(os.environ.get('DAEDALON_BLENDER',str(BLENDER)))
  command=[str(blender),'--background','--factory-startup','--disable-autoexec','--python-exit-code','1','-t','4','--python',str(pathlib.Path(__file__).resolve()),'--','--style',a.style,'--source-root',str(a.source_root),'--output-dir',str(a.output_dir)]
  command+=['--stephanos-target-faces',str(a.stephanos_target_faces)]
  if a.approved_stephanos_input:command+=['--approved-stephanos-input',str(a.approved_stephanos_input)]
  return subprocess.run(command,check=False).returncode
 assert bpy.app.version==(5,2,0) and bpy.app.build_hash.decode('ascii')=='fbe6228777e7','Locked Blender 5.2.0 build fbe6228777e7 is required'
 source=a.source_root.resolve()/SOURCE[a.style]['uuid']/'Meshy_AI_model.blend'
 assert source.is_file() and sha(source)==SOURCE[a.style]['sha256'],'Source path/hash mismatch'
 output=a.output_dir.resolve();output.mkdir(parents=True,exist_ok=True)
 assert output!=source.parent and source.parent not in output.parents,'Output must be separate from source'
 bpy.ops.wm.read_factory_settings(use_empty=True)
 retention={};low=[];upper=[]
 if a.approved_stephanos_input:
  assert a.style=='stephanos','Approved-input option is specific to Stephanos'
  approved=a.approved_stephanos_input.resolve();bpy.ops.wm.open_mainfile(filepath=str(approved),load_ui=False)
  obj=next(o for o in bpy.context.scene.objects if o.type=='MESH')
  retention['approved_reduction_input']={'basename':approved.name,'sha256':sha(approved)}
  retention['method']='User-requested editable polygon reduction of the approved native sculpt only; exact sealed architectural core and its normals retained'
  for existing in list(bpy.context.scene.objects):
   if existing.type!='MESH':bpy.data.objects.remove(existing,do_unlink=True)
 elif a.style=='bathron':obj=create_body()
 else:obj,retention,low,upper=create_extended(source,a.style)
 obj['style']=a.style
 if a.style=='stephanos':
  if not a.approved_stephanos_input:
   cleanup=_stephanos_remove_faulty_rear_triangles(obj);retention['rendered_sculpt_cleanup']=cleanup
  obj['embedded_native_relief_render_asset']=True
  if a.stephanos_target_faces>0:retention['requested_polygon_reduction']=reduce_stephanos(obj,a.stephanos_target_faces)
 report=verify(obj);objpath=output/f'plinth_{a.style}-candidate.obj';export_obj(obj,objpath);make_scene(obj,output,prefix=f'{a.style}-candidate')
 blendpath=output/f'{a.style.title()} - clean architectural candidate.blend';bpy.context.preferences.filepaths.save_version=0;bpy.ops.wm.save_as_mainfile(filepath=str(blendpath))
 report.update({'style':a.style,'source':{'basename':source.name,'uuid':SOURCE[a.style]['uuid'],'sha256':sha(source),'provenance':'Oliver-authored Meshy creation'},'geometry_method':retention.get('method','Measured editable architectural profile rings; no collapse decimation'),'profile':BATHRON_PROFILE if a.style=='bathron' else {'lower':low,'upper':upper},'feature_retention':retention,'candidate_obj':{'name':objpath.name,'sha256':sha(objpath)},'candidate_blend':{'name':blendpath.name,'sha256':sha(blendpath)},'source_unmodified':sha(source)==SOURCE[a.style]['sha256']})
 (output/f'{a.style}-candidate-report.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
 print('PLINTH_CANDIDATE_READY',json.dumps({k:report[k] for k in ['vertices','polygons','triangles','bounds_obj_y_up','edge_counts','degenerate_triangles','custom_normals']}),flush=True)
 return 0


if __name__=='__main__':raise SystemExit(main())
