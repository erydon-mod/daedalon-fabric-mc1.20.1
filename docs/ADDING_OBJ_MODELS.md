# Adding Daedalon OBJ models

This is the repeatable path established by the Spartan and Zeus pilots. A new
statue should be data plus one measured profile; shared code supplies all
materials, aged variants, sizes, facings, texture density, and geometry caches.

## Per-model inputs

1. Record the source/provenance, licence, SHA-256, vertex/face counts, bounds,
   source-facing direction, ground contact, and support pivot. Distribution is
   blocked until provenance is known.
2. Put the approved OBJ and its mesh-definition JSON in
   `src/main/resources/assets/daedalon/models/mesh`. Add an MTL only when the
   source genuinely uses one. Add one shared item-display model.
3. For a statue, add one `StatueBlock.Profile` with the measured facing, pivot,
   and three-block collision shape. Never allocate profiles per material.
4. Declare one mesh family, then register its 27 standard and 27 aged material
   IDs. New IDs use `<material>_aged_<descriptors>_<form>` for aged variants.
5. Add the family to `tools/generate_daedalon_assets.py`, then generate with
   `--families all`. The language and tag manifests are global, so never run a
   single-family generation as the final write.
6. Lock the mesh hash/counts/bounds in `tools/tests/daedalon_test_support.py`
   and add one focused safety test for IDs, states, resources, transform profile,
   UV policy, and texture reuse.

## Authored UV workflow

UV discovery and normal generation are deliberately separate:

1. For a new mesh only, run
   `python tools/generate_statue_uvs.py <family> --discover --dry-run`.
   Review the seam, stretch, density, fold, island, and float32 checks.
2. Save the approved base and refinement ranks in
   `tools/statue_uv_recipes.json`.
3. From then on, use `--write` or `--check` without `--discover`. These modes
   replay only the locked recipe and never search alternatives. A missing or
   topology-mismatched recipe fails before Blender starts.
4. For the authored OBJ, set `force_uv_projection` and
   `repair_degenerate_uvs` to `false`, then update its locked UV count and hash.

This makes later builds deterministic: the expensive search happens once, and
its accepted result becomes a short reusable recipe.

## Fast urn preparation

Meshy urns use the established cylindrical runtime projection rather than the
statue unwrap search. Keep the source Blender file unchanged and prepare one
shared OBJ/MTL pair with the locked local Blender tool:

```text
python tools/prepare_urn_mesh.py --input <source.blend> --name urn_<shape> --output-dir <staging> --target-faces 3200 --preview
```

The tool runs Blender 5.2 locally in background mode, applies one Collapse
Decimate pass, exports Y-up triangles with normals and material `none`, and
records source/output hashes and counts. Review the preview once; do not start
the statue UV discovery workflow for an urn unless the cylindrical projection
has a specific visible failure. Register IDs as `<material>_<shape>_urn` and
`<material>_aged_<shape>_urn`, while keeping the resource stem `urn_<shape>`.

All ordinary material-backed OBJ families render from the shared 12x6 repeat
surface. The first half is the established 6x6 ERYDON repeat sheet and the
second half is an exact horizontal copy. This lets Daedalon move each model's
UV window to one of six world-position-derived tile origins without wrapping
outside Minecraft's stitched sprite. Urn projections retain their 4x1 window,
single-tile architectural projections retain a 1x1 window, and detailed models
retain the complete 6x6 window, so texture density does not change. Items and
GUI icons always use origin zero, while dedicated OBJ materials remain fixed.

After changing any native or Collection `statue_spartan_promachos_*` source
sheet, run `python tools/extend_repeat_surface_textures.py` for validation (or
with `--write` to extend a square source). Pass each Collection `textures/block`
root explicitly and refresh that repository's deduplication plans afterward.

## Fast architectural preparation

Meshy corbels and capitals use the same bounded local-Blender route rather than
the statue UV search:

```text
python tools/prepare_architectural_mesh.py --input <source.blend> --name corbel_<style> --output-dir <staging> --target-faces 6000 --preview
python tools/prepare_architectural_mesh.py --input <source.blend> --name capital_<style> --output-dir <staging> --target-faces 6000 --preview
```

The tool runs the locked Blender 5.2 build in background mode, leaves the source
file unchanged, applies bounded Collapse Decimate passes, and records exact
source/output hashes. Use the runtime axis-stabilized box projection after one
preview check; do not start a long authored-UV search for these families.

When a broad capital deck becomes uneven during decimation, use the bounded
`--flatten-capital-top` option. It detects the source deck by projected
upward-facing area and flattens only its thin upper band after decimation.
Higher-detail capitals may use an explicitly recorded 12000-face target (or
18000 for exceptionally dense openwork) without changing this one-pass policy.

Corbels use `<material>_<style>_corbel` and
`<material>_aged_<style>_corbel`. Their shared source is normalized to three
blocks high. The runtime transform supplies four facings and three sizes; every
large corbel is uniformly scaled to exactly one block (16 units) wide, so its
height follows the source style's proportions. Every size keeps its top surface
exactly at Y=16, grows downward, and anchors its rear plane to the supporting
wall boundary. Capitals use
`<material>_<style>_capital` and `<material>_aged_<style>_capital`, have no state
properties, and preserve their proportions while fitting the measured lower rim
over the ERYDON circular-column join. Lock the source mapping, placement metrics,
mesh counts, and hashes in a batch evidence file before committing.

## Fast finial and decor preparation

Finials, fountains, basins, and monuments use the same bounded local-Blender
policy. Prepare one `.blend` or `.obj` source at a time and review its single
preview:

```text
python tools/prepare_decor_mesh.py --input <source.blend> --name <resource_stem> --output-dir <staging> --target-faces 15000 --preview
```

The locked Blender 5.2 tool triangulates, exports smooth Y-up OBJ normals with
material `none`, and records source/runtime hashes. Complex models normally use
the roughly 15,000-face source geometry; the tool only applies Collapse
Decimate when the triangulated source exceeds the requested budget. Pass the
source face count when its supplied geometry should remain completely intact.
Prefer cylindrical runtime projection for rotational forms and axis-stabilized
box projection for directional or rectilinear forms. Do not start statue UV
discovery unless one reviewed projection has a specific visible failure.
The cylindrical forms use the same 4x1 window inside the shared repeat surface
as urns; do not change that projection window merely to repack the texture.

Register IDs with material first and form last: for example
`<material>_balanos_finial`, `<material>_aged_balanos_finial`, and
`<material>_obeliskos_monument`. All five finials also register a bronze finish using the existing shared mesh,
texture and size transforms. Add only meaningful states: Krene has four
facings, Obeliskos has sizes 1/2/3, while radial one-block decor has no state
properties. Record concept images without a matching mesh as unavailable rather
than inventing geometry from the preview.

## Verification

### Corinthian Frieze pilot

The Corinthian Frieze is a one-block wall relief, exactly 1m high and at most
0.23m deep. Its three prepared sections repeat over 3m; short runs are valid.
All 27 stone finishes and their aged variants share one component OBJ. Search
terms include frieze, entablature, wall relief and acanthus.

Run Blender 5.2.0 with `--background --factory-startup --disable-autoexec
--python tools/prepare_corinthian_frieze.py -- --input <source.obj>
--output-dir src/main/resources/assets/daedalon/models/mesh
--evidence docs/evidence/corinthian-frieze-source.json
--preview-dir <preview-directory>`. The supplied OBJ remains unchanged. No
decimation is applied: only the finished source returns are cropped, the height
and width normalized, and the last 25mm at each repeat end lofted to a common
profile. The three straight sections contain 6,851 / 4,975 / 6,947 triangles.
Each exposed end is closed on its exact section plane, following that section's
cut silhouette without adding width or projection. There is no generic border.
Corner components are clipped and individually closed on matching mitre planes;
the full component file is not emitted for each placed block.

`FriezeBlock` uses facing, pattern offset 0/1/2, join and `manual_corner`.
Look towards a wall for ordinary wall-facing placement. Look predominantly along
it (more than 45 degrees from the wall normal) when placing to choose a short
outside corner return, even on a continuing flat wall. That corner is retained
through neighbour edits, saving, rotation and mirroring. Extending a frieze from
its side inherits its facing and pattern offset. For automatic placements,
full wall faces take precedence: a backing wall
and one side wall form an inside return; an exposed diagonal wall corner forms
an outside return even without another frieze. Fences and partial faces do not
act as full walls. Two side walls keep a narrow recess flat. Unsupported builds
retain the perpendicular-frieze fallback, with parallel continuations taking
precedence over incidental T junctions. Connections work across material
changes. Each block owns its one-cell collision and selection, without entities,
ticks or whole-run scans. The block occupies the air cell against the wall;
an outside turn needs a frieze placed in the corner cell. Rendering and dynamic
collision/selection resolve the current local wall faces, including diagonal
wall edits that do not send a direct blockstate neighbour notification.

The debug stick exposes facing, pattern offset and `manual_corner`; join is
editable when manual mode is enabled and otherwise calculated from neighbours.
Offset changes apply to the selected block, while newly extended
pieces inherit it. The pattern is world-aligned, so moving or rotating a copied
run can change which motif occupies a particular block. Breaking any section
exposes finished ends immediately. Local moulding coordinates are projected
into the shared material sheet after facing rotation so textures join across
all four directions and remain inside the atlas.

Validation: `./gradlew --no-daemon check` plus `runGameTest`. The frieze tests
cover forward/reverse placement, every corner/facing, wall-only corners and
diagonal support removal, angled corner placement against straight walls,
manual corner persistence and transforms, all 54 finishes,
height/collision, negative coordinates, chunk boundaries, and side-click
inheritance. Geometry tests check all three seam profiles within 0.01mm, source
hashes, flush end closures and collision containment. Client visual acceptance remains required
for native textures, both Collection packs, shaders, reload, and inventory.

### Ionic Frieze

The supplied Ionic panel contains roughly one and a half repeats, not three
identical horses. Its full repeat includes the chariot group and mounted group
with both intervening palm motifs. Matching shallow gaps after the mounted
groups occur at source X -0.357 and +0.724 (period 1.081); crop there, keeping
both designs, before dividing the resulting 3m run into three 1m sections.

Run `tools/prepare_ionic_frieze.py` in the same locked Blender background mode,
with `--input <source.obj> --output-dir src/main/resources/assets/daedalon/models/mesh
--evidence docs/evidence/ionic-frieze-source.json --preview-dir <directory>`.
It reuses the Corinthian clipping, exact end closures, corner components and
25mm repeat-edge loft. No decimation is applied. Sub-millimetre source noise on
the intended top/bottom planes is flattened within a 1mm band. The three sections
contain 5,200 / 3,220 / 4,867 triangles before any exposed end closures.

Height is exactly 1m. Depth scales with height to retain the Ionic section
proportions, giving a 0.34m collision envelope rather than Corinthian's 0.23m.
Each style caches its own shapes and shares one mesh across all 54 finishes.
The established automatic and angled/manual placement behaviour is unchanged.
Material changes within a design connect; different designs keep their finished
ends because the moulding profiles do not match. Inventory icons use the same
approved rotation and compact framing as Corinthian. Search terms include
Ionic, frieze, entablature, horse, chariot, palm, palmette and procession.

Both designs run the same server placement tests, including manual corner
retention, all facings, negative coordinates, wall edits and material changes.
The component tests check seams, exact end silhouettes, bounds and source hashes.

### One-block Gothic Frieze and conservative smoothing

Prepare `entablature_gothic.obj` with `tools/prepare_gothic_frieze.py` in the
same locked Blender background mode. Use `--input <source.obj> --output-dir
src/main/resources/assets/daedalon/models/mesh --evidence
docs/evidence/gothic-frieze-source.json --preview-dir <directory>`.
The source has a roughly 0.674-wide arch/quatrefoil repeat. Two complete repeats
between X=-0.674 and X=0.674 fit a 1m-wide block with uniform scale 1/1.348.
Only the plain top fascia at source Y=0.620..0.650 gains the missing 19.535mm
of height. Carved motifs retain their proportions, with no decimation.
The 12.5mm repeat-edge loft uses a 0.01mm profile tolerance to avoid redundant
triangles. The straight panel has 14,043 triangles, plus 978 per exposed end.
Height and width are exactly 1m; its cached depth envelope is 0.256m.

Gothic uses one component phase, including its inventory mesh, shared across
all 54 finishes. It reuses the approved wall/angled corner placement and compact
GUI transform. The debug stick omits the irrelevant pattern offset for this
one-block repeat; facing and manual corners remain available. Search terms
include Gothic, frieze, entablature, quatrefoil, tracery and lancet.

All frieze preparation tools bake 30-degree, angle-weighted corner
normals before splitting the straight mesh into render components. Repeat ends
share their normals, while subsequently generated cut caps remain flat. Runtime
`smooth_normals` stays false deliberately: Minecraft imports the prepared normals
instead of averaging overlapping straight/corner variants together. This changes
shading only; the accepted Corinthian and Ionic vertex positions, faces, end
closures and corner geometry remain identical. Tests verify repeat seams,
normal lengths and sharp caps, all finishes and placement behaviour.

### Two-block Byzantine Frieze

Use `tools/prepare_byzantine_frieze.py` with the same locked Blender invocation
and `--input <source.obj> --output-dir src/main/resources/assets/daedalon/models/mesh
--evidence docs/evidence/byzantine-frieze-source.json --preview-dir <directory>`.
The complete main panel repeats between the large outer pillar centres at
source X=-0.737 and X=0.737, with both palms, both smaller pillars, both
interlace panels and the cross medallion retained. The large half-pillars join
into a complete pillar at each two-metre repeat boundary. The earlier crop at
the palms omitted those larger pillars and is superseded.

The main panel is uniformly scaled by 2/1.474 and remains undecimated. Its
ornament occupies 0.488467m vertically. Foliage, dentils, braid and rope retain
uniform XYZ proportions within their own independently tiled bands; the plain
mouldings share the remaining height. Smaller trim uses a 0.5 decimation ratio
before seam fitting; its original topology is never simplified across the main
panel. Every tier is closed against its neighbours with fitted horizontal
ledges. Endpoint lofts share profiles, with a 0.1mm profile tolerance.

The complete design is 2m wide and 1m high, divided into two 1m blocks with a
0.235m depth envelope (measured depth 0.232186m). Straight sections contain
14,225 and 14,270 triangles, plus fitted caps on exposed ends. The shared
30-degree smoothing, corner placement and compact GUI transform apply to all
54 finishes. World-coordinate phase selection uses the style's repeat length,
including negative coordinates and arbitrary partial runs; saved pattern values
remain compatible. Search includes Byzantine, Guilloche, frieze, entablature,
interlace, cross, medallion, palm and palmette. Asset and server tests cover the
complete repeat, both pillar sizes, dimensions, seams, normals, ends, corners,
materials and source provenance.

### Width-selectable Exedra

The Exedra pilot uses the decor preparation tool with
`--name exedra_2m --width-meters 2 --height-meters 1 --proportional-depth --target-faces 14842`.
Repeat for `exedra_3m` and `exedra_4m`; `--save-blend --preview` produces
editable metric Blender files and a preview. This preserves the supplied
topology, scales width/depth together, keeps height independently at 1m,
and transforms the imported custom normals with the inverse transpose.

The three prepared meshes use `fit_to_block=false`, unit scale, and
`translate=[0.5,0,0.5]`. All use `box_projection_span=4` to keep one material
density across widths without escaping the repeat sprite. Its default value
of zero preserves existing families; the renderer always bounds the selected
span below by the actual model extent for atlas safety.

One mesh family per prepared width shares the geometry across all materials.
Only 3m owns the inventory model and public block aliases. The other widths
resolve internal model IDs; only a cached facing rotation runs during chunk
meshing. `python tools/generate_exedra_shape.py --check` verifies the shared
curved shape against triangle-clipped width strips and height bands.

Exedra also opts into one shared `exedra_item` mesh for 3D item rendering.
Prepare it from the same source with `--name exedra_item --width-meters 3
--height-meters 1 --proportional-depth --target-faces 2000`. Its 2,000 faces
avoid resubmitting the full 14,842-face bench through the shader item pipeline
every frame. Only the default-width family owns this optional item definition;
all material variants reuse the same mesh. World geometry, collision, debug
widths, item display transforms, and the full-detail cached GUI icon are
unchanged. Families without an item definition retain their existing rendering.
The item source and bounds are locked in `docs/evidence/exedra-item.json`.

### Width-selectable Hedra

Hedra shares `BenchBlock` width/facing controls, cached shapes, mesh families,
projection, and the optional item-mesh path with Exedra. Prepare each width with
`--name hedra_2m --width-meters 2 --height-meters 1
--stretch-middle-meters 1 --target-faces 13012 --save-blend --preview`,
then repeat for 3m and 4m. The source is uniformly sized to one block high;
only the central metre of its straight seat changes length. Both carved ends
translate intact, and depth stays approximately 0.862m. Imported custom normals
use the local inverse transpose of this piecewise stretch.

Prepare `hedra_item` at 3m with the same dimensions/stretch and
`--target-faces 2000`. The placed meshes retain all 13,012 source triangles;
the 2,000-triangle 3D item is shared across all 54 materials. Run
`python tools/generate_hedra_shape.py --check` to verify the five measured boxes
per width, including the open space beneath the seat. Selection uses a separate
one-block-high/deep bounding box for each width, making the complete bench easy
to target without filling its collision gap. Source provenance,
preparation, geometry hashes and bounds are in `docs/evidence/hedra-source.json`.

### Width-selectable Anthophoros planter

Use Blender 5.2.0 LTS with `tools/prepare_anthophoros_mesh.py -- --input
<source.obj> --output-dir <output>` to reproduce the approved repeated-panel
design. Widths are 2m, 3m and 4m, all 1m high and approximately 1.188486m deep.
The narrow and wide end caps translate intact. Only plain panel margins change
width; garlands and rosettes repeat before the explicitly approved Blender
Collapse decimation to at most 15,000 triangles per placed model.

The script transfers reference normals from triangle centres, avoiding shading
borrowed from an adjacent wall at a moulding vertex. Runtime smoothing is off
for this family only. Generated definitions keep a fixed 4m projection span,
so world texture density does not stretch with width. All 54 stone/aged finishes
and bronze reuse three world meshes and one approximately 2,000-triangle held
item; inventory icons retain the established full-model raster cache.

The default 3m width owns the public item aliases. `AnthophorosBlock` reuses
the static furniture width/facing controls and five cached floor/wall boxes
per width, preserving its hollow interior without block entities or ticking.
This is a decorative planter, not a new crop or fluid system. Provenance,
Blender version and output hashes are in `docs/evidence/anthophoros-source.json`.

### Diameter-selectable Monopteros dome

Prepare the supplied Monopteros OBJ with the same local Blender tool, using
`--name monopteros_4m --width-meters 4 --height-meters 3
--proportional-depth --monopteros-flat-crown --target-faces 15386 --save-blend --preview`.
Repeat at 6m/4m and 8m/5m diameter/height. The Blender cut at source Y=0.5
removes the built-in ornament and seals the crown. Width/depth scale together;
height aligns independently to a whole block. Preserve the hollow underside,
the lowest rim at Y=0, and the
horizontal centre. The 6m default owns the public block/item aliases, while
4m and 8m use internal mesh variants. Each mesh is shared by 54 stone/aged
finishes and bronze. Keep the box projection span at 8m for constant density.
Prepare `monopteros_item` at the default dimensions with a 2,000-face budget.

`python tools/generate_monopteros_shape.py --check` verifies the measured
hollow shell. `MonopterosGeometry` caches quarter-block collision cells once
for each diameter. Invisible `monopteros_part` blocks let vanilla raycasting
and collision reach the complete 8m rim. They have no items, rendered meshes,
block entities or ticks; their offsets refer directly to the central owner.
Only placement, resizing and removal update these cells. Existing roofs can
refresh their shell by cycling the debug diameter; empty cells saved from the
old ornament allow replacement by a finial and are cleared on edits/removal. The debug stick and
pick/break interactions on a rim cell act on the owner. Resizing checks the
new shell before writing and preserves obstructing builds. Rotation/mirroring
of copied structures also rotates/mirrors the saved part offsets.

Visual geometry is emitted from the owner's chunk. `MonopterosRenderBounds`
records only sections which build a dome mesh and conservatively expands their
frustum bounds to contain an 8m roof. Vanilla/Indigo and optional Sodium hooks
read the same cached section membership, without frame-time world queries or
allocations. Entries clear on chunk unload and world change; removal may retain
the conservative bounds until unload. Check the largest size from underneath
as well as outside at chunk/section boundaries in-game.
Source evidence and hashes are in `docs/evidence/monopteros-source.json`.

### Axiom builder selection for oversized assemblies

With Axiom installed, left-clicking or middle-clicking a Monopteros dome or
fountain rim selects the complete object using Axiom's own sparse selection.
`DecorAssemblySelection` includes the owner and only its existing owned cells;
floors, air and nearby blocks are excluded. The bounded lookup runs on clicks,
not per frame. Axiom handles preview, movement and undo, including the fountain
block entity's plinth, bowls and water. Starting a normal box selection clears
the previous automatic object selection. Selection compatibility is optional
and must also be tested with Axiom absent.

### Build and runtime checks

Run, in order:

```text
python tools/generate_daedalon_assets.py --families all --check
python tools/generate_statue_uvs.py <family> --check
python -m unittest discover -s tools/tests -p "test_*.py" -v
.\gradlew.bat clean build --no-daemon
```

Then check the pilot in-game at all four facings and all three heights, including
ground contact, selection/collision, item view, self-drop, native textures, both
current Collection packs, shaders, and a resource reload. Confirm one geometry
bake per family rather than one per material variant.

## Shared behaviour to preserve

- One immutable statue profile and one geometry bake per family.
- Texture density remains constant across the one-, two-, and three-block sizes.
- Every statue inherits the urn-style horizontal offset and remains flush with
  the block floor. Offset does not change the selected one-, two-, or
  three-block size.
- High-detail families share one full-geometry GUI raster template. Each
  material icon is filled lazily from the active resource-pack texture into a
  bounded 160-pixel atlas cell, so Creative-inventory and recipe-browser views submit one quad
  without deleting any source triangles. Unsupported textures, glint, or cache
  failures fall back to the complete model. Placed models retain the complete
  world mesh; families may explicitly supply one lighter shared mesh for held,
  fixed and dropped views, as the benches do. Families without that opt-in
  retain their complete mesh in every view.
- Shared item-display JSONs are generated by
  `python tools/generate_obj_display_models.py`. Run its `--check` mode in the
  verification pass so GUI, held, dropped, and fixed transforms cannot drift.
- Generated blockstates, items, loot, languages, tags, and search terms stay in
  sync across every material and aged variant.
- Add models incrementally; do not broaden a successful pilot into the full
  batch until its visual and collision checks pass.
