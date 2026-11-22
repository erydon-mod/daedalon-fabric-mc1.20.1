# Coffered panels

## Approved scope and current status

Oliver selected supplied references 2–5; reference 1 is excluded.

| Reference | Design | Family suffix | Shared mesh name |
| --- | --- | --- | --- |
| 2 | Corinthian Panel | `corinthian_wall_panel` | `corinthian_panel` |
| 3 | Byzantine Panel | `byzantine_wall_panel` | `byzantine_panel` |
| 4 | Georgian Panel | `georgian_wall_panel` | `georgian_panel` |
| 5 | Renaissance Panel | `renaissance_wall_panel` | `renaissance_panel` |

Oliver approved the final saved four-panel review on **21 November 2026**.
The selected triangle counts are Corinthian **5,996**, Byzantine **6,000**,
Georgian **5,984** and Renaissance **5,988**. All four approved meshes are active
in source and passed offline checks. Live visual checks remain pending.

After reduced source versions lost too much detail, Oliver chose a simpler
ornament rebuild within the hard limit of **6,000 triangles per complete new
shared mesh**, including its back and borders. Each design was reviewed before
activation; the existing Gothic Panel and its geometry remain unchanged.

The supplied Blender/Meshy references are Oliver's own creations, approved for
public Daedalon inclusion. Corinthian, Byzantine and Georgian use numeric
geometry authored with Codex assistance at Oliver's direction. Renaissance uses
local sampling and simplification of Oliver's original centre, with a rebuilt
mitred frame and dentils. These are approved replacement meshes, not untouched
Meshy exports. Preserve the original source hashes, files and existing
third-party licences and notices.

## Approved review import — 21 November 2026

`tools/export_approved_panel_review.py` exports the exact chosen meshes and
prepared corner normals from `Coffered Panels - Final Corinthian Review.blend`.
The approved file's SHA-256 is
`114e0b03892ee07b07779cbab566192f19e79a5135fccc058afb169cf6396f9d`.
Scene layout translations are ignored; local review axes become runtime
`(x, z, -y)`, retaining the established +Y up and +Z front. Import cleanup
reorients four Corinthian folded lower-curl faces and sixteen Byzantine braid
end-cap faces, repairing their 12 and 48 affected corner normals respectively.
No coordinates are moved or resampled by that cleanup; unaffected reviewed
normals are retained. Georgian and Renaissance need no winding/normal cleanup.

Collision depths remain 0.088m, 0.075m, 0.055m and 0.089m respectively.
Renaissance retains its 0.99010658m authored height, centred on Y=0.5 within
the existing one-metre square mounting envelope. The import does not stretch
its ornament to fill that envelope. Gothic geometry, registered IDs, aliases,
finishes, sizes and mounting controls remain unchanged by this revision.

## Shared implementation

Each design has one OBJ, mesh definition and measured `PanelBlock.Style`
profile. All materials reuse that geometry and the same cached transforms;
collision/selection envelopes are cached once per style. `registerPanels()`
and `ObjMeshModelLoadingPlugin` iterate the styles. `PanelMeshTransform` already
handles their placed geometry, normals and texture windows.

Every panel now offers 58 finishes: 27 named stones, their 27 aged variants,
and plain White, Black, Bronze and Silver. Both plain colours reuse the existing
native sprites and every style's shared geometry, sizes and mounting controls.
Use `<material>_<style>_wall_panel` and
`<material>_aged_<style>_wall_panel`; preserve all existing Gothic IDs and aliases.
The same block supports wall or ceiling mounting, three square size envelopes
(1m, 2m and 3m), four facings and the existing debug-stick controls. Scale the
carving uniformly, including depth; do not stretch ornament to force a square.
Keep the support plane flush and set a conservative depth from the actual export.

Use the existing shared material sheets, panel UV window and six-column world
phase. No per-material geometry, extra rendering dependency or new Continuity
rule is required. Generate global resources with `--families all`, including all
five current languages, tags, search vocabulary, items and loot. Keep one design
membership per item; Byzantine retains Guilloche search vocabulary.

## Evidence and acceptance

The evidence file is `docs/evidence/coffered-panels-source.json`.
Its `models` array records `source_index`, `style`, `family`, `mesh_name`,
`bounds` as `[minimumXYZ, maximumXYZ]`, `collision_depth`, `triangles` and
`outputs` as filename-to-SHA-256 mappings. Also record source names/hashes,
reference versus replacement provenance, front/up, backing plane, normalization
and the preparation tool/settings. Tie each collision depth to its style profile.
The current review identity and export hashes distinguish the 21 November
revision from compact historical counts, bounds and output hashes retained in
the manifest. Earlier per-component lists are not duplicated as current data.

Offline acceptance must check the actual exported pilot and previews, its
triangle cap, bounds, normals, support plane and recognisable ornament before
the next model. `PanelGeometryTest` always exercises the real Gothic mesh and
adds prepared models when the manifest exists: all 24 size/facing/mount poses,
collision containment, normal orientation, texture density, atlas bounds and
world phases. `PanelGameTests` checks registered finishes, mounted states,
cached shapes and saved transforms. Resource/registration tests must require
every activated style and keep the manifest, profiles and generated assets aligned.

`gradlew --offline --no-daemon check compileGameTestJava` passed for this
revision, including 394 Java tests (one skipped), 287 asset checks and
placement-test compilation. The asset tests verify that open decorative
components meet their local backing surface along the entire edge. The Java
geometry check covers collision and texture sampling through all panel poses.
Shader and in-game visual checks remain pending.
Inspect native textures and both Collection packs, every size/facing/mount,
top and underside texture phase,
neighbouring panels, shader finishes, reload and inventory/Axiom appearance.
Offline geometry tests and compilation do not prove the running appearance.

## Finish and Collection verification — 15 November 2026

This historical source audit predates the Black panel addition and the approved
21 November geometry revision.

A disposable Java probe compiled the actual live ERYDON
`HighPolishSettings`/`PolishedStoneMaterials` sources and Daedalon's actual
`ErydonStoneFinishBridge`. All 108 new fresh-stone IDs (27 stones × four
designs) were eligible. All 324 `HONED`/`POLISHED`/`MIRROR` renderer-and-bridge
level checks matched (`MIRROR` is shown to players as Ganosis); the disabled
controller resolved to Honed. All 124 aged,
plain White/Black and metal probe IDs remained excluded. Black was tested as
an exclusion, not added to panel registrations. ERYDON's Iris classifier uses
the same generic classifier for every registered block state, with no
Gothic-only condition. No bridge or classifier correction was needed.

The inspected live sources were ERYDON `2.0.2` and Collection `1.6.1`, family
generation 2. Native textures and both active Collection source tiers cover
all 57 panel finishes through shared sheets:

| Effective resources | Albedo | Normal | Specular |
| --- | ---: | ---: | ---: |
| Native | 57 | 27 | 29 |
| Collection 32x | 57 | 27 | 56 |
| Collection 64x | 57 | 27 | 56 |

Each pack inherits five native maps: `white.png`, `bronze.png`, `bronze_s.png`,
`silver.png` and `silver_s.png`. Neither source pack overrides the new
panel meshes, definitions, blockstates or item models, or references their IDs
in CTM rules. No pack-source edits were needed; probe staging was removed.

The inspected IntelliJ `run-dev` runtime supplied Sodium/Indium/Iris but no
ERYDON controller or ERYDON config. Its selected shader was
`ComplementaryUnbound_r5.9_dev2.zip`. Authored PBR can therefore retain a fixed
response there. The optional bridge supplies labels from ERYDON's captured
settings; the supported ERYDON shader route controls the rendered finish.
Changes require a restart. These source/probe results do not establish that
finish control works in the current running client; nothing was installed or enabled.

## Completed offline validation — initial 15 November geometry

`gradlew --no-daemon check compileGameTestJava build` passed, including 275 Java
unit tests (one existing skip), 259 asset checks, branding/deduplication checks
and the final standalone JAR audit. The four model caps, shared resources,
all active languages and catalogue memberships are covered. Server placement
tests were compiled; neither a Minecraft client nor a game-test server was
launched. Live native/32x/64x texture joins, shader finishes and Axiom appearances
still require the requested later visual test. These results describe the
initial meshes; they do not establish validation of the 21 November import.

## Plain panel finishes — 18 November 2026

Added the five missing `black_<style>_wall_panel` block/item registrations.
White was already present for every style. Both colours are nonmetal and keep
their plain surface response rather than following named-stone finish settings.
No OBJ, model definition, material file, display transform, placement profile or
texture was changed; Oliver deferred further panel geometry work.

Read-only inspection of the actual `erydon-public/run-collection-test` setup
found Daedalon 1.0.3 and the selected Collection 64x compat2 pack
`ERYDON-Collection-64x-compat2-v1.6.1.1.zip` (marker release 1.6.1), with
Complementary Unbound r5.10 dev6. Its direct resources and alias manifest do not
override either plain sprite: both resolve to the installed native 16x opaque
White/Black images, with no normal or specular maps. At that inspection the JAR
contained White panels and both sprites but no Black panel IDs; a later
authorized JAR update was required before the new entries could appear. Resource
reload alone cannot add registrations. No game interaction or installation was
performed, and live appearance remains to be retested.
