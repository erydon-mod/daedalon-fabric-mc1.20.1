"""Prepare one shared square Gothic panel without simplifying the supplied carving.

Blender --background --factory-startup --disable-autoexec --python <script>
-- --input <source.obj> --output-dir <mesh directory> --evidence <json>
[--preview-dir <directory>]. The source is never modified.
"""
import argparse,hashlib,json,math,sys
from pathlib import Path
import bpy
from mathutils import Vector
sys.path.insert(0,str(Path(__file__).resolve().parent))
from prepare_corinthian_frieze import export,smooth_relief_normals

def main():
    parser=argparse.ArgumentParser()
    for flag in ('input','output-dir','evidence'):parser.add_argument('--'+flag,type=Path,required=True)
    parser.add_argument('--preview-dir',type=Path)
    args=parser.parse_args(sys.argv[sys.argv.index('--')+1:])
    if bpy.app.version!=(5,2,0):raise RuntimeError('Use Blender 5.2.0')
    digest=hashlib.sha256(args.input.read_bytes()).hexdigest()
    vertices=[];normals=[];faces=[]
    for line in args.input.read_text().splitlines():
        p=line.split()
        if not p:continue
        if p[0]=='v':vertices.append(Vector(tuple(map(float,p[1:4]))))
        elif p[0]=='vn':normals.append(Vector(tuple(map(float,p[1:4]))))
        elif p[0]=='f':faces.append([v.split('/') for v in p[1:]])
    bounds=[(min(v[i] for v in vertices),max(v[i] for v in vertices)) for i in range(3)]
    scale=1/max(bounds[0][1]-bounds[0][0],bounds[1][1]-bounds[1][0])
    transformed=[]
    for p in vertices:
        q=Vector(tuple((p[i]-bounds[i][0])*scale for i in range(3)))
        for axis in (0,1):
            if q[axis]<.001:q[axis]=0
            elif q[axis]>.999:q[axis]=1
        if q.z<.0001:q.z=0
        transformed.append(q)
    triangles=[]
    for face in faces:
        for i in range(1,len(face)-1):
            refs=[face[0],face[i],face[i+1]]
            triangles.append([(transformed[int(r[0])-1].copy(),normals[int(r[2])-1].copy()) for r in refs])
    triangles=smooth_relief_normals(triangles,width=2,angle_degrees=30)
    out=args.output_dir;out.mkdir(parents=True,exist_ok=True)
    export(out/'gothic_panel.obj',{'panel':triangles},'Gothic Wall and Ceiling Panel')
    (out/'gothic_panel.mtl').write_text('newmtl none\nKd 1.0 1.0 1.0\n', encoding="utf-8", newline="\n")
    definition={'obj':'daedalon:models/mesh/gothic_panel.obj','mtl':'daedalon:models/mesh/gothic_panel.mtl',
                'flip_v':True,'repair_degenerate_uvs':True,'force_uv_projection':True,'uv_projection':'axis_stabilized_box',
                'box_projection_span':6.0,'face_oriented_uvs':True,'smooth_normals':False,'fit_to_block':False,
                'scale':[1.,1.,1.],'translate':[0.,0.,0.],
                'particle':'daedalon:block/statue_spartan_promachos_aganite',
                'materials':{'none':'daedalon:block/statue_spartan_promachos_aganite'}}
    (out/'gothic_panel.json').write_text(json.dumps(definition,indent=2)+'\n', encoding="utf-8", newline="\n")
    evidence={'source_name':args.input.name,'source_sha256':digest,'source_files_modified':False,
              'provenance':'Oliver supplied his own Meshy creation; approved for the public Daedalon repository.',
              'source':{'vertices':len(vertices),'triangles':len(triangles),'bounds':bounds,'front':'+Z','up':'+Y'},
              'preparation':{'tool':'Blender 5.2.0 LTS','uniform_scale':scale,'decimated':False,
                             'square_outer_frame_snap_m':.001,'back_planarity_band_m':.0001,
                             'normal_smoothing_angle_degrees':30,'depth_m':max(v.z for v in transformed),
                             'base_size_m':[1,1],'runtime_sizes_m':[1,2,3],'mounts':['wall','ceiling'],
                             'triangles':len(triangles),'shared_geometry':True},
              'outputs':{p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in out.glob('gothic_panel.*')}}
    args.evidence.parent.mkdir(parents=True,exist_ok=True);args.evidence.write_text(json.dumps(evidence,indent=2)+'\n', encoding="utf-8", newline="\n")
    if args.preview_dir:preview(triangles,args.preview_dir)
    assert hashlib.sha256(args.input.read_bytes()).hexdigest()==digest
    print(json.dumps(evidence))

def preview(triangles,directory):
    directory.mkdir(parents=True,exist_ok=True)
    bpy.context.preferences.filepaths.save_version=0
    bpy.ops.object.select_all(action='SELECT');bpy.ops.object.delete(use_global=False)
    verts=[(p.x,-p.z,p.y) for tri in triangles for p,n in tri]
    ns=[(n.x,-n.z,n.y) for tri in triangles for p,n in tri]
    mesh=bpy.data.meshes.new('Shared Gothic panel');mesh.from_pydata(verts,[],[(i,i+1,i+2) for i in range(0,len(verts),3)]);mesh.update()
    for poly in mesh.polygons:poly.use_smooth=True
    mesh.normals_split_custom_set(ns)
    scene=bpy.context.scene;scene.render.engine='BLENDER_WORKBENCH';scene.render.resolution_x=1700;scene.render.resolution_y=900;scene.render.resolution_percentage=100
    scene.display.shading.light='STUDIO';scene.display.shading.show_cavity=True;scene.display.shading.cavity_type='BOTH';scene.display.shading.color_type='SINGLE';scene.display.shading.single_color=(.68,.64,.56);scene.world.color=(.055,.055,.055)
    camera=bpy.data.cameras.new('Preview');camera.type='ORTHO';camera.ortho_scale=7.8
    cam=bpy.data.objects.new('Preview',camera);scene.collection.objects.link(cam);scene.camera=cam
    objects=[]
    for size,x in ((1,0),(2,1.3),(3,3.6)):
        obj=bpy.data.objects.new(f'{size}m Gothic panel',mesh);scene.collection.objects.link(obj);obj.location=(x,0,0);obj.scale=(size,size,size);objects.append(obj)
    for mount,loc,target in [('wall',(3.3,-10,3.6),(3.3,0,1.5)),('ceiling',(3.3,-6,-5),(3.3,-1.5,1))]:
        if mount=='ceiling':
            for obj in objects:obj.rotation_euler.x=math.pi/2;obj.location.z=1
        cam.location=loc;cam.rotation_euler=(Vector(target)-cam.location).to_track_quat('-Z','Y').to_euler()
        scene.render.filepath=str(directory/f'gothic-{mount}-panel-sizes.png');bpy.ops.render.render(write_still=True)
    scene.unit_settings.system='METRIC'
    bpy.ops.wm.save_as_mainfile(filepath=str(directory/'gothic-panel-preview.blend'))

if __name__=='__main__':main()
