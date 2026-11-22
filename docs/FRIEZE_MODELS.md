# Adding another frieze design

Follow [ADDING_OBJ_MODELS.md](ADDING_OBJ_MODELS.md) first. A frieze uses the
shared `FriezeBlock.Style` and component path, rather than a statue profile.
The existing Corinthian, Ionic, Gothic and Byzantine sections in that guide
contain the accepted measurements and preparation commands.

## Source and measured pilot

Oliver's supplied models are his own Meshy creations and are approved for the
public Daedalon repository. Record that provenance in the new evidence JSON;
do not request the same ownership confirmation again. Separately credited
third-party assets retain their existing licence and attribution.

1. Obtain the next source OBJ, its original MTL/textures if actually used, and
   the intended design name. Preserve the supplied files unchanged. Record
   SHA-256, vertices, triangles, bounds, source front/up, backing plane and
   support pivot before transforming anything.
2. Measure one complete natural ornament repeat and matching endpoint
   profiles. Choose the number of one-block sections from those measurements,
   keeping the carved motifs proportional. Record any crop, plain-band fitting
   or deliberate simplification rather than applying another style's constants.
3. Reuse `clip`, `move`, `weld_repeat`, `smooth_relief_normals`,
   `build_components`, `export` and `preview` from
   `tools/prepare_corinthian_frieze.py` in one small preparation script for the
   new style. The Ionic, Gothic and Byzantine scripts are examples. Run the
   locked Blender 5.2.0 with background, factory-startup and disable-autoexec.
   Prepare only this pilot, with one shared OBJ/mesh-definition JSON and an
   MTL only when required. Never create geometry per material.
4. Review straight panels, both exposed ends, inside/outside corners and
   repeat boundaries in the generated previews. Measure the actual exported
   component bounds, seam positions/normals and depth envelope. Use these
   values for one `FriezeBlock.Style` entry and its cached collision shapes.
   Each placed block must emit only its selected component phase and caps.

## Shared registration and resources

- `ModBlocks.registerFriezes()` and the style loop in
  `ObjMeshModelLoadingPlugin` supply the shared block and renderer route.
  Reuse its stone/aged variants, plain White/Black and Bronze/Silver finishes;
  do not copy another style's per-material assets by hand.
- Add the family/name/search terms and finish membership to
  `tools/generate_daedalon_assets.py`, and its one item-display transform to
  `tools/generate_obj_display_models.py`. Run the asset generator with
  `--families all`; language and tag manifests are global. Update every active
  language, including French and Japanese when present.
- Keep existing IDs and permanent aliases intact. Determine publication
  lineage from the approved migration evidence before any rename. Gothic and
  Gothic Ornate remain separate concepts. Byzantine architecture retains
  Guilloche search vocabulary.
- Keep the current lightweight browse route: one design membership per item,
  the shared Friezes form, and accurate material/finish searches. Broad tags
  are intentional. Native REI collapse is an optional player preference;
  adding a design does not require changing that preference or adding more
  overlapping groups.
- Use the established OBJ world-aligned repeat and finish bridge. Check the
  actual placed child/component geometry, native material sheets and both
  Collection source packs. A new Continuity rule or dependency is not implied
  merely by a request for seamless textures. Follow
  [FRIEZE_TEXTURE_SAFETY.md](FRIEZE_TEXTURE_SAFETY.md) for atlas-edge protection.

## Acceptance before the next model

Lock source/output hashes, counts and bounds in `docs/evidence/<style>-frieze-source.json`
and `tools/tests/daedalon_test_support.py`. Extend the shared
`FriezeSafetyChecks` with the measured repeat and depth, and cover registration,
all finishes, translations, resources, search and selected-component routing.
Check the actual world UV phase at joins, all four facings, negative coordinates
and repeat boundaries; retain atlas-safe interpolation for every primitive.

Run the generator check, display-model check, relevant asset/Java tests and the
normal project build. Server placement tests should cover the new style's
straight and corner joins, material changes and saved states. In-game visual
acceptance must still inspect native textures, both Collection resolutions,
all facings/joins, finish selection, resource reload and inventory appearance.
Compilation or a passing texture audit alone does not prove that appearance.

Only activate the next supplied model after the pilot's measured, rendering and
collision checks pass. When in-game checks are explicitly deferred, record
that pending gate rather than claiming visual acceptance or enabling a batch.
Remove temporary staging after final verification.
