# Lantern surface textures

Placed opaque lantern surfaces use one texture tile per world metre. Projection
runs after the mount, size, facing and support offset have positioned the
geometry. Stone courses therefore keep the same scale and world phase across
the Georgian, Gothic and Byzantine bodies. Body frames, burners, half/full
chains and ceiling canopies all use this path, including standalone chain cells.
Clear panes and flame cards retain
their complete authored sprite UVs at either physical size.

`LanternSurfaceEmitter` handles placed surfaces; `LanternWorldUv` supplies the
projection and clipping maths. The largest component of the placed triangle's
geometric normal chooses its projection axis, with stable ties at rounded
corners. Shallow roofs, undersides and mouldings use the top/bottom projection;
steeper surfaces use a side projection. Smooth vertex normals and a triangle's
centroid do not choose its texture axis.

## World coordinates and density

Let `X`, `Y` and `Z` be the final vertex coordinates in the world. The continuous
texture coordinates match ERYDON's native 6×6 repeat mapping:

| Projection face | S | T |
| --- | --- | --- |
| Up | X | Z |
| Down | X | −Z |
| North | −X | 1−Y |
| South | X | 1−Y |
| West | Z | 1−Y |
| East | −Z | 1−Y |

One unit of S or T is one world metre and one texture tile. The 0.75m Small body
covers proportionately less of the pattern than the 1.25m Large body, with the
same texture density. Rotating or moving the body does not rotate or restart the world pattern.
Axis-aligned faces retain the resource pack's one-tile-per-metre scale exactly.
On angled faces, the dominant-axis choice bounds projection stretch to about
1.733 times the surface distance. The former rule forced wall projection onto
every slope more than 0.81 degrees from horizontal, which severely stretched
shallow undersides and roof mouldings.

## Repeat sheets and atlas seams

The shared stone sprite contains 12 columns and 6 rows. Its second six-column
half is an exact duplicate of the first. The actual sheet sizes are:

| Resources | Sheet pixels | Pixels per tile |
| --- | ---: | ---: |
| Native | 192×96 | 16 |
| Collection 32x | 384×192 | 32 |
| Collection 64x | 768×384 | 64 |

For a repeat-sheet fragment, choose common origins in multiples of six and map
`u=(S−uOrigin)/12`, `v=(T−vOrigin)/6`. The spare horizontal copy accommodates a
lantern face crossing the six-column repeat boundary. The vertical direction
has no spare copy, so a polygon crossing `T=6k` is clipped there before each
fragment receives its origin. The atlas guard may move an entire primitive to
the identical horizontal copy to improve its outside-edge clearance.

Plain lead, bronze and silver sprites contain one tile. Their polygons are
clipped at crossed integer S and T planes, then mapped with
`u=S−floor(cellS)` and `v=T−floor(cellT)`. Vertices are never wrapped separately:
that would stretch a triangle across the whole sprite at a seam. Clipping keeps
the surface footprint and winding, interpolates unit normals and discards
zero-area fragments.

Finite metal sprites then receive one affine half-source-texel inset before
atlas baking. This keeps bilinear filtering inside the selected sprite, including
the shallow roof mouldings near a Large body's one-metre UV boundary. Inclusive
0..1 vertex bounds alone do not protect that filter footprint. The stone repeat
mapping and full authored glass/flame images do not receive this inset.

The final material pass bakes these UVs directly into the sprite and skips the
older OBJ phase remapping for already mapped world surfaces. Other model
families keep their established phase path. The emitter shares its four-entry
scratch arrays across the source faces in one emission pass; only crossing
polygons enter the allocating clipping path. World/support queries happen
outside that per-face loop.

Shared suspension meshes bake their own lead sprite before adopting the parent
lantern's lead material index. Standalone chain cells keep their own material
index. This keeps shader metadata consistent without changing the hardware's
texture or projection.

## Astragalos corner triangles

The Astragalos base is geometrically sound. In the former centroid-based axis
selection, OBJ triangles 34 and 35 came from the same planar rounded-corner
quad and had the same geometric normal, but their centroids selected North and
East texture bases. The diagonal therefore became visible on detailed finishes.
The audit reproduced this on 60 of 288 base quad pairs; the broad base planes
did not have conflicting geometry or normals.

Astragalos now overrides only its shared `none` material with
`"material_uv_projections": {"none": "box"}`. The geometric normal chooses its
basis; its OBJ, authored normals and existing scale remain unchanged. Organic
families retain their axial projection.

## Verification boundary

[Portable evidence](evidence/lantern-world-uv.json) records the actual native and
Collection sheets, exact Aganite source-tile comparisons and the Astragalos
reproducer. `LanternWorldUvTest` covers all six faces, negative and distant world
positions, seam clipping, area preservation, normal interpolation, both sizes,
three mounts and four horizontal facings. Native, Collection-pack and shader
appearance still require separate in-game visual acceptance; source and unit
checks do not provide that proof.

`LanternSurfaceProjectionTest` additionally replays the shipped opaque body and
suspension triangles through the production projector at both current sizes and
four horizontal facings. It checks the selected normal component and actual
world S/T area, including the shallow surfaces that exposed the former rule.
