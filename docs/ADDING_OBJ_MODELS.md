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
`<material>_obeliskos_monument`. Add only meaningful states: Krene has four
facings, Obeliskos has sizes 1/2/3, while radial one-block decor has no state
properties. Record concept images without a matching mesh as unavailable rather
than inventing geometry from the preview.

## Verification

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
  failures fall back to the complete model; world, held, fixed, and dropped
  views always use the complete source mesh.
- Shared item-display JSONs are generated by
  `python tools/generate_obj_display_models.py`. Run its `--check` mode in the
  verification pass so GUI, held, dropped, and fixed transforms cannot drift.
- Generated blockstates, items, loot, languages, tags, and search terms stay in
  sync across every material and aged variant.
- Add models incrementally; do not broaden a successful pilot into the full
  batch until its visual and collision checks pass.
