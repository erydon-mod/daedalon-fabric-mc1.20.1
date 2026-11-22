# Glaux model and render implementation

Glaux is one bronze, two-metre ornament. Its two internal block halves share
one facing and one lower block entity. No material or size property is exposed.
Placement, upper-cell obstruction, ordinary two-half removal and drop behaviour
use the vanilla tall-block path; the world stores no dialogue or player data.

The original owl is Oliver's supplied Meshy creation. Its SHA-256 is
`664b4644c643206956e3f4bfe4a53bf70a28a1714e46fb8b4b0a55662ab713c1`.
The approved feather-overlap authoring recipe was imported with the original
unquantized positions matched to the preview's Morton-ordered vertex topology.
`tools/glaux_mesh_source.json.gz` is the current shared authoring data, not a
rollback copy. `tools/generate_glaux_assets.py` regenerates only the six Glaux
mesh/definition pairs and its own resource files; it never writes finial assets.

## Rendering and movement

- The static lower and upper body meshes stop at their own one-block vertical
  cell boundaries. Slight horizontal overhang uses the established section
  bounds helper during chunk baking.
- The complete beak, face and head turn rigidly about the measured neck pivot.
  The 29 moving neck feathers have individual rigid hinges; the 27 fixed
  feathers are included in the static body. No animated vertex stretching or
  per-frame mesh parsing, triangulation or geometry generation is used.
- Moving head buffers are cached once per resource reload, with near, middle
  and far levels of detail. Raw parsed OBJ source data is released after baking.
- All head parts use one shared bronze-atlas render layer and buffer. Cached
  primitive float vertices are transformed directly without allocating a vector
  per vertex. The body is never resubmitted by the block-entity renderer.
- The cosmetic disapproval gesture is a finite head/neck-feather shiver. The
  chunk-baked body remains planted; this intentionally avoids two chunk rebuilds
  per gesture or continuous full-body block-entity rendering.
- Each block entity caches its complete head bounds. The renderer checks the
  current world frustum before emitting vertices, despite the head extending
  outside the lower block's unit cube. Far visible owls retain a small head.
- A recent-visible-pose map limits tracking work. Nearby line-of-sight tests are
  staggered across ticks; no world scan or animation networking occurs.
- Still preferences survive pose-cache expiration within the current world.
  Still and reduced-motion modes suppress acknowledgement, nod and shiver too.
  The preference is cleared on removal/world change and is not saved server-side.

Ordinary head tracking is acceleration-limited and follows the shortest wrapped
angle. Crossing the rear `+180/-180` boundary does not trigger a full return
turn. The default tracking target uses the measured neck pivot, transformed
with the placed facing, rather than the block centre.

## UV and material coverage

Every Glaux face has authored UVs from one whole-owl rest-space origin and span,
before clipping the two static body cells, shifting the head pivot or making
LODs. Projection seam identities retain their UV and normal attributes, while
all corners of a geometric cluster share exactly the same XYZ. Close-range
body/head cells receive up to four offline refinements where a collapse would
fold a source triangle. This replaces the previous per-attribute XYZ averaging,
which opened cracks at projection and normal boundaries.

Neck leaves retain separate front/back shells and closed perimeter joins. Their
winding is oriented outwards and their normals rebuilt once during generation;
the web pilot used double-sided material to hide inconsistent edge winding.
Moving leaves use closed 6-by-3 and 3-by-2 sampling grids at near/middle LOD.
The fixed 27-leaf row retains its full authored surface. Tests check every
geometric leaf edge has exactly one oppositely directed partner and non-zero
outward volume. No runtime backface duplication or mesh repair is added.

Exact
shared body seam vertices are locked against different lower/upper LOD cells.
The OBJ loader therefore uses `force_uv_projection=false` and
`repair_degenerate_uvs=false`; UVs are never clamped from an out-of-range window.

Both static body and moving head use the same Fabric `spriteBake` operation on
the canonical `daedalon:block/bronze` sprite. The head's UVs remain attached to
the rigid sculpture as it turns. The generated asset checks verify normalized
UV bounds and matching lower/upper join phase through all four placed facings.

The native bronze resources are `bronze.png` and `bronze_s.png`. Glaux has no
stone finish, and its fixed metal surface does not use the stone-family
Synapheia repeat/overlay selection. The inspected current Collection 32x/64x
source packs do not replace `assets/daedalon/textures/block/bronze.png`; their
separate Diota bronze-handle textures are unrelated. No pack edits or
Continuity requirement are introduced.

`GlauxRenderChecks.inspect(client)` is an opt-in check against the actual loaded
body models, current sprite/provider, cached head buffers, normals and atlas UVs.
It also compares the emitted body seam in all four facings and verifies that
the Glaux item has a populated shared GUI silhouette template. The item uses
the ordinary bounded icon atlas in Creative/REI, instead of permanently falling
back to the square bronze particle sprite. Compilation and
source tests alone do not prove actual native/active-pack or Iris/PBR appearance;
those live render checks and a visual game check remain separately reported.

The current exact counts, hashes and source bounds are authoritative in
`glaux-source.json`. The offline budget is below 20,000 near head triangles,
3,500 middle triangles, 600 far triangles and 7,000 item triangles. These are
geometry budgets, not a claim of measured Minecraft frame time.
