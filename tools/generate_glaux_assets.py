"""Deterministic single-finish Glaux pilot from the reviewed shared authoring mesh.

No per-material geometry: bronze is the only approved Glaux finish. Normal
regeneration needs only Python's standard library; the browser is authoring-only.
"""
from __future__ import annotations
import argparse, array, base64, gzip, hashlib, json, math, sys
from collections import defaultdict
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
ASSETS=ROOT/'src/main/resources/assets/daedalon'
SOURCE=Path(__file__).with_name('glaux_mesh_source.json.gz')

def unpack(encoded, code):
    values=array.array(code);values.frombytes(base64.b64decode(encoded))
    if sys.byteorder!='little':values.byteswap()
    return values

def clip(poly, height, above):
    result=[]
    for a,b in zip(poly,poly[1:]+poly[:1]):
        ia=a[1]>=height if above else a[1]<=height
        ib=b[1]>=height if above else b[1]<=height
        if ia:result.append(a)
        if ia!=ib:
            f=(height-a[1])/(b[1]-a[1]);v=tuple(x+(y-x)*f for x,y in zip(a,b));result.append(v)
    return result

UV_ORIGIN=(-.7,-.0000005,-.65)
UV_SPAN=2.000001

def projected(face,offset):
    """One source-face window, retained through clipping and rigid part shifts."""
    a,b,c=face;u=[b[i]-a[i] for i in range(3)];v=[c[i]-a[i] for i in range(3)]
    normal=(u[1]*v[2]-u[2]*v[1],u[2]*v[0]-u[0]*v[2],u[0]*v[1]-u[1]*v[0])
    axis=max(range(3),key=lambda i:abs(normal[i]));positive=normal[axis]>=0;projection=axis*2+int(positive)
    result=[]
    for vertex in face:
        x,y,z=((vertex[i]+offset[i]-UV_ORIGIN[i])/UV_SPAN for i in range(3))
        if axis==1:uv=(x,z if positive else 1-z)
        elif axis==2:uv=(x if positive else 1-x,1-y)
        else:uv=(1-z if positive else z,1-y)
        if not all(0<=value<=1 for value in uv):raise ValueError('Authored Glaux UV outside common source window')
        result.append(vertex+uv+(projection,))
    return tuple(result)

def triangles(part,projection_offset=(0,0,0),coarse_feather=False):
    p=unpack(part['positions'],'f');n=unpack(part['normals'],'f');idx=unpack(part['indices'],'I')
    if coarse_feather:
        # The locked authoring leaf is a closed 13 x 7 front/back grid. Keep
        # both 2 mm-separated surfaces and all perimeter joins in distant LOD.
        # Generic XYZ clustering collapses that thickness into torn triangles.
        if len(p)!=182*3:raise ValueError('Unexpected authored feather grid')
        rows,columns=([0,2,4,6,8,10,12],[0,2,4,6]) if coarse_feather=='near' else ([0,4,8,12],[0,3,6]);grid=[[r*7+c for c in columns] for r in rows];faces=[]
        for r in range(len(rows)-1):
            for c in range(len(columns)-1):
                a,b,e,d=grid[r][c],grid[r][c+1],grid[r+1][c],grid[r+1][c+1]
                faces.extend([(a,e,b),(b,e,d),(a+91,b+91,e+91),(b+91,d+91,e+91)])
        for r in range(len(rows)-1):
            for c in [0,len(columns)-1]:
                a,b=grid[r][c],grid[r+1][c];faces.extend([(a,b,a+91),(b,b+91,a+91)])
        for r in [0,len(rows)-1]:
            for c in range(len(columns)-1):
                a,b=grid[r][c],grid[r][c+1];faces.extend([(a,a+91,b),(b,a+91,b+91)])
        idx=array.array('I',[i for f in faces for i in f])
    for i in range(0,len(idx),3):
        face=tuple(tuple(p[j*3:j*3+3])+tuple(n[j*3:j*3+3]) for j in idx[i:i+3])
        yield projected(face,projection_offset)

def triangulate(poly):
    for i in range(1,len(poly)-1):yield (poly[0],poly[i],poly[i+1])

def orient_closed_feather(vertices,faces):
    """Restore outward winding and smooth normals on each closed bronze leaf.

    The approved web pilot hid inconsistent side winding with DoubleSide. The
    chunk-baked row is culled normally, so repair the leaf once at generation.
    No extra backfaces or render-thread work are required.
    """
    points={};ids=[]
    for v in vertices:
        key=tuple(round(c,7) for c in v[:3]);ids.append(points.setdefault(key,len(points)))
    adjacency=defaultdict(list)
    for i,f in enumerate(faces):
        for a,b in zip(f,f[1:]+f[:1]):
            a,b=ids[a],ids[b];adjacency[tuple(sorted((a,b)))].append((i,a<b))
    if any(len(edge)!=2 for edge in adjacency.values()):raise ValueError('Authored Glaux feather is not closed')
    links=defaultdict(list)
    for (a,forward),(b,other) in adjacency.values():
        links[a].append((b,forward==other));links[b].append((a,forward==other))
    flipped={}
    for start in range(len(faces)):
        if start in flipped:continue
        flipped[start]=False;pending=[start]
        while pending:
            a=pending.pop()
            for b,different in links[a]:
                target=flipped[a]^different
                if b in flipped:
                    if flipped[b]!=target:raise ValueError('Non-orientable Glaux feather')
                else:flipped[b]=target;pending.append(b)
    faces=[(f[0],f[2],f[1]) if flipped[i] else f for i,f in enumerate(faces)]
    volume=0
    for f in faces:
        a,b,c=(vertices[i] for i in f)
        volume+=a[0]*(b[1]*c[2]-b[2]*c[1])+a[1]*(b[2]*c[0]-b[0]*c[2])+a[2]*(b[0]*c[1]-b[1]*c[0])
    if volume<0:faces=[(a,c,b) for a,b,c in faces]
    normals=defaultdict(lambda:[0.,0.,0.])
    for f in faces:
        a,b,c=(vertices[i] for i in f);u=[b[i]-a[i] for i in range(3)];v=[c[i]-a[i] for i in range(3)]
        cross=(u[1]*v[2]-u[2]*v[1],u[2]*v[0]-u[0]*v[2],u[0]*v[1]-u[1]*v[0])
        for i in f:
            for j in range(3):normals[ids[i]][j]+=cross[j]
    result=[]
    for i,v in enumerate(vertices):
        n=normals[ids[i]];length=math.sqrt(sum(c*c for c in n))
        if length<1e-20:raise ValueError('Degenerate Glaux feather normal')
        result.append(v[:3]+tuple(c/length for c in n)+v[6:])
    return result,faces

def simplify(groups,cell,refinements=None,pass_index=0):
    """Cluster positions independently of UV/normal seams.

    A source point has one geometric result even when its corner attributes
    differ. Averaging XYZ per projection/normal bucket tears a closed sculpture
    into thousands of tiny holes. Attribute vertices may split; XYZ never does.
    """
    out={};refinements={} if refinements is None else refinements;refine=set()
    for name,faces in groups.items():
        geometry={};position_sums=[];attributes={};sums=[];mapped=[];originals=[];point_keys=[]
        for face in faces:
            row=[];geometric_row=[]
            for v in face:
                protected_beak=name=='head' and cell<.035 and -.025<v[0]<.18 and -.025<v[1]<.27 and v[2]>.365
                spacing=1e-7 if 'feather_' in name else min(cell,.006) if protected_beak else cell
                seam=name=='body' and abs(v[1]-1)<1e-7
                base_key=(name,protected_beak,seam)+tuple(round(c*1e7) if seam else round(c/spacing) for c in v[:3])
                level=refinements.get(base_key,0)
                point_key=base_key+tuple(round(c*1e7) if seam else round(c/(spacing/(2**level))) for c in v[:3])
                point=geometry.get(point_key)
                if point is None:point=len(position_sums);geometry[point_key]=point;position_sums.append([0.0,0.0,0.0,0]);point_keys.append(base_key)
                for j in range(3):position_sums[point][j]+=v[j]
                position_sums[point][3]+=1
                key=(point,round(v[8]),tuple(1 if c>=0 else -1 for c in v[3:6]))
                index=attributes.get(key)
                if index is None:index=len(sums);attributes[key]=index;sums.append([point,0.0,0.0,0.0,0.0,0.0,0])
                for j in range(5):sums[index][j+1]+=v[j+3]
                sums[index][6]+=1;row.append(index);geometric_row.append(point)
            if len(set(geometric_row))==3:mapped.append(tuple(row));originals.append(face)
        positions=[tuple(c/v[3] for c in v[:3]) for v in position_sums]
        vertices=[]
        for v in sums:
            normal=[c/v[6] for c in v[1:4]];length=math.sqrt(sum(c*c for c in normal)) or 1
            vertices.append(positions[v[0]]+tuple(c/length for c in normal)+tuple(c/v[6] for c in v[4:6]))
        valid=[];seen=set()
        for f,original in zip(mapped,originals):
            # UV islands may describe the same geometric face: keep only one.
            face_key=tuple(sums[i][0] for i in f)
            if face_key in seen:continue
            a,b,c=(vertices[j] for j in f);u=[b[j]-a[j] for j in range(3)];v=[c[j]-a[j] for j in range(3)]
            area=sum(x*x for x in (u[1]*v[2]-u[2]*v[1],u[2]*v[0]-u[0]*v[2],u[0]*v[1]-u[1]*v[0]))
            if area<(1e-28 if name=='body' and any(abs(v[1]-1)<1e-7 for v in original) else 1e-17):continue
            if 'feather_' not in name and cell<.035 and pass_index<4:
                oa,ob,oc=original;ou=[ob[j]-oa[j] for j in range(3)];ov=[oc[j]-oa[j] for j in range(3)]
                before=(ou[1]*ov[2]-ou[2]*ov[1],ou[2]*ov[0]-ou[0]*ov[2],ou[0]*ov[1]-ou[1]*ov[0])
                after=(u[1]*v[2]-u[2]*v[1],u[2]*v[0]-u[0]*v[2],u[0]*v[1]-u[1]*v[0])
                if sum(x*y for x,y in zip(before,after))<0:refine.update(point_keys[i] for i in face_key)
            seen.add(face_key);valid.append(f)
        used=sorted({i for f in valid for i in f});remap={old:i for i,old in enumerate(used)}
        value=([vertices[i] for i in used],[tuple(remap[i] for i in f) for f in valid])
        out[name]=orient_closed_feather(*value) if 'feather_' in name and valid else value
    if refine:
        for key in refine:refinements[key]=refinements.get(key,0)+1
        return simplify(groups,cell,refinements,pass_index+1)
    return out

def write_obj(path,groups,offset=(0,0,0)):
    lines=['# Glaux: Oliver-supplied Meshy owl, reviewed rigid feather rig.','usemtl bronze'];count=0;bounds=[float('inf')]*3+[float('-inf')]*3;face_count=0
    for name,(vertices,faces) in groups.items():
        if not faces:continue
        lines.append('o '+name)
        for v in vertices:
            xyz=[v[i]+offset[i] for i in range(3)]
            for i,x in enumerate(xyz):bounds[i]=min(bounds[i],x);bounds[i+3]=max(bounds[i+3],x)
            lines.append('v '+' '.join(f'{x:.7f}' for x in xyz))
        lines.extend('vn '+' '.join(f'{x:.7f}' for x in v[3:6]) for v in vertices)
        # OBJ V runs upward; the shared loader's flip_v restores the same atlas V.
        lines.extend(f'vt {v[6]:.8f} {1-v[7]:.8f}' for v in vertices)
        for face in faces:
            indices=[i+count+1 for i in face];lines.append('f '+' '.join(f'{i}/{i}/{i}' for i in indices));face_count+=1
        count+=len(vertices)
    path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(('\n'.join(lines)+'\n').encode('utf8'))
    return {'vertices':count,'triangles':face_count,'bounds':bounds,'sha256':hashlib.sha256(path.read_bytes()).hexdigest(),'bytes':path.stat().st_size,'objects':[name for name,(_,faces) in groups.items() if faces]}

def write_json(path,value):
    path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes((json.dumps(value,ensure_ascii=False,indent=2)+'\n').encode('utf8'))

def generate():
    source=json.loads(gzip.decompress(SOURCE.read_bytes()));body=defaultdict(list);moving=defaultdict(list)
    fixed_feather=0
    middle=defaultdict(list)
    for part in source['parts']:
        name=part['name'];is_body=name=='body';is_feather=len(unpack(part['positions'],'f'))==182*3
        if is_body and is_feather:name=f'under_feather_{fixed_feather:02d}';fixed_feather+=1
        offset=(0,0,0) if is_body else tuple(source['pivot'])
        (body if is_body else moving)[name].extend(triangles(part,offset,coarse_feather='near' if is_feather and not is_body else False))
        if not is_body:middle[name].extend(triangles(part,offset,coarse_feather=is_feather))
    lower=defaultdict(list);upper=defaultdict(list)
    for name,faces in body.items():
        for face in faces:
            lower[name].extend(triangulate(clip(list(face),1,False)))
            upper[name].extend(triangulate(clip(list(face),1,True)))
    outputs={}
    specs=[('glaux_body_lower',lower,.020,(.5,0,.5)),('glaux_body_upper',upper,.014,(.5,-1,.5)),('glaux_head_near',moving,.023,(0,0,0)),('glaux_head_mid',middle,.050,(0,0,0)),('glaux_head_far',{'head':[face for faces in moving.values() for face in faces]},.13,(0,0,0))]
    item=defaultdict(list);item['body'].extend(face for faces in body.values() for face in faces);px,py,pz=source['pivot']
    for faces in moving.values():
        item['head'].extend(tuple((v[0]+px,v[1]+py,v[2]+pz,*v[3:]) for v in face) for face in faces)
    specs.append(('glaux_item',item,.070,(.5,0,.5)))
    for stem,groups,cell,offset in specs:
        result=simplify(groups,cell);obj=ASSETS/'models/mesh'/f'{stem}.obj';outputs[stem]=write_obj(obj,result,offset)
        definition={'obj':f'daedalon:models/mesh/{stem}.obj','flip_v':True,'repair_degenerate_uvs':False,'force_uv_projection':False,'uv_projection':'axis_stabilized_box','box_projection_span':UV_SPAN,'face_oriented_uvs':True,'smooth_normals':False,'fit_to_block':False,'scale':[1,1,1],'translate':[0,0,0],'particle':'daedalon:block/bronze','materials':{'bronze':'daedalon:block/bronze'}}
        write_json(ASSETS/'models/mesh'/f'{stem}.json',definition)
    # Lightweight marker models are replaced by the dedicated shared-OBJ plugin.
    for stem in ['glaux_body_lower','glaux_body_upper']:
        write_json(ASSETS/'models/block/glaux'/f'{stem}.json',{'parent':'daedalon:block/mesh/glaux_display','textures':{'particle':'daedalon:block/bronze'}})
    write_json(ASSETS/'models/item/glaux.json',{'parent':'daedalon:block/mesh/glaux_display'})
    variants={f'facing={facing},half={half}':{'model':f'daedalon:block/glaux/glaux_body_{half}'} for facing in ['north','east','south','west'] for half in ['lower','upper']}
    write_json(ASSETS/'blockstates/glaux.json',{'variants':variants})
    display={'parent':'minecraft:block/block','textures':{'particle':'daedalon:block/bronze'},'display':{'gui':{'rotation':[20,195,0],'translation':[0,-5,0],'scale':[.43,.43,.43]},'ground':{'translation':[0,2,0],'scale':[.22,.22,.22]},'fixed':{'rotation':[0,180,0],'translation':[0,-2,0],'scale':[.4,.4,.4]},'thirdperson_righthand':{'rotation':[75,225,0],'translation':[0,1,0],'scale':[.25,.25,.25]},'thirdperson_lefthand':{'rotation':[75,45,0],'translation':[0,1,0],'scale':[.25,.25,.25]},'firstperson_righthand':{'rotation':[0,225,0],'translation':[0,-2,0],'scale':[.35,.35,.35]},'firstperson_lefthand':{'rotation':[0,45,0],'translation':[0,-2,0],'scale':[.35,.35,.35]}}}
    write_json(ASSETS/'models/block/mesh/glaux_display.json',display)
    write_json(ROOT/'src/main/resources/data/daedalon/loot_tables/blocks/glaux.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':'daedalon:glaux','conditions':[{'condition':'minecraft:block_state_property','block':'daedalon:glaux','properties':{'half':'lower'}}]}],'conditions':[{'condition':'minecraft:survives_explosion'}]}]})
    runtime={k:source[k] for k in ['pivot','hinges','profiles','profileBottom','profileTop','profileAngles']}
    write_json(ASSETS/'glaux/rig.json',runtime)
    evidence={'schema':1,'source':source['provenance'],'authoringDataSha256':hashlib.sha256(SOURCE.read_bytes()).hexdigest(),'finish':'bronze only','heightMetres':2,'sourceRig':source['rig'],'outputs':outputs,'uvLayout':{'origin':UV_ORIGIN,'span':UV_SPAN,'policy':'Authored once in whole-owl rest space before clipping, LOD and local pivot translation; projection seam identities retained'},'runtimePolicy':'Static body split across lower/upper chunk cells; cached moving head LOD; no runtime parsing per frame','ctm':'canonical daedalon:block/bronze sprite, no Continuity dependency; in-game native and active Collection-pack appearance remains a required check'}
    write_json(ROOT/'docs/evidence/glaux-source.json',evidence)
    print(json.dumps({name:{'triangles':value['triangles'],'bytes':value['bytes']} for name,value in outputs.items()},indent=2))
    return evidence

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--check',action='store_true');args=parser.parse_args()
    if args.check:
        evidence=json.loads((ROOT/'docs/evidence/glaux-source.json').read_text())
        for stem,record in evidence['outputs'].items():
            path=ASSETS/'models/mesh'/f'{stem}.obj'
            assert hashlib.sha256(path.read_bytes()).hexdigest()==record['sha256'],stem
        assert hashlib.sha256(SOURCE.read_bytes()).hexdigest()==evidence['authoringDataSha256']
        print('Glaux source and generated mesh hashes verified')
    else:generate()
