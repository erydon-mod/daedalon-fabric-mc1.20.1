# Rebuilt finials

The existing Balanos, Kynara, Phlox, Sphaira and Strobilos finials use a measured
reconstruction path in `tools/rebuild_finial_meshes.py`. Clean square feet,
turned collars and mouldings replace the irregular Meshy surfaces, while each
design keeps its distinctive ornament: the carved Balanos acorn cup, overlapping
Kynara petals, six spiral Phlox flame lobes, Sphaira sphere and Strobilos diamond
scales.

## Face budget and evidence

Oliver's requested limit is **6,000 editable Blender polygon faces per finial**.
Exported runtime triangles are recorded separately: an editable quad normally
exports as two triangles, so these are different counts. The budget applies to
the complete authored finial, including its foot, mouldings and ornament.

| Finial | Editable Blender polygons | Runtime triangles |
| --- | ---: | ---: |
| Balanos | 5,598 | 8,044 |
| Kynara | 5,982 | 12,132 |
| Phlox | 5,032 | 9,928 |
| Sphaira | 2,924 | 5,848 |
| Strobilos | 5,320 | 9,944 |

The previous runtime exports contained approximately 6,000 triangles each.
Four reconstructed designs use more runtime triangles to retain their ornament;
all five satisfy the requested editable-polygon budget.

[Current reconstruction evidence](evidence/finial-rebuild-source.json) records
the active models, their editable polygon and runtime triangle counts, exact
export hashes, dimensions, source identity and retained features.
[Historical batch evidence](evidence/finial-decor-batch-source.json) preserves
the original preparation and its earlier 6,000-triangle runtime exports.
Oliver's original Meshy Blender files remain unchanged and hash-checked.

## Authoring and activation

Prepare one model as a separate pilot and inspect its front and angled previews
before activating the next model:

```text
python tools/rebuild_finial_meshes.py --style sphaira --source-root <folder-containing-source-UUID-directories> --output-dir <pilot-output-folder>
```

The tool launches the locked Blender 5.2.0 build `fbe6228777e7` in background.
It writes the editable Blender model, a neutral Y-up OBJ, two rendered views and
a JSON report outside the original source directory. Style-specific profile
measurements live in the tool; the source identity comes from the historical
batch record.

Architectural parts use analytic geometry. Measured turned profiles retain
their beads, necks and curved mouldings through bounded curve approximation;
regular ornament is built with controlled rings, lobes, petals or scales. Keep
the native carved motif where a regular replacement would lose a design's
character. The editable polygon budget is checked after assembling the whole
model.

Balanos retains 3,288 triangles of its native carved acanthus cup, including the
eight-leaf motif, around a clean measured acorn. Two hidden caps close the cropped
cup; 142 near-coincident inner-skin vertices are buried beneath the rebuilt acorn
to avoid face fighting. The evidence records this bounded reduction and the
measured deviation of the exposed leaves. The original source file is unchanged.

The exported OBJ keeps authored corner normals for crisp planes and rounded
curves. Its definition sets `smooth_normals=false`, avoiding a second smoothing
pass over those prepared normals. The shared material remains `none`, including
the fitted base's owner material slot zero.

All five designs share Sphaira's three-step square plinth, concave turned neck
and rounded upper moulding. The common base helper proportions that detailing
to each existing foot and ornament junction; Sphaira's established geometry is
unchanged. Foot widths, support contacts and overall dimensions remain exact.

Kynara's redesigned leaves have thick exposed lips, deep overlapping shoulders,
folded faces and outward-curled tips, following Oliver's report that the earlier
leaves disappeared in-game. Balanos's carved leaf cup remains the depth reference.
The lowest eight leaves finish on a continuous horizontal cut above the collar,
and the upper leaves converge onto a filled crown. The actual supporting body
rises into narrow gaps between the rows. Hidden root plates stay close to that
backing while the visible upper halves project forward, preserving a substantial
step between neighbouring leaves. Filling the gaps must not bury the dressed
leaf edges or smooth away their definition; authored bevel and fold normals
retain the visible changes of plane.
The rear surfaces remain embedded continuously in that shaped body, including
the curled ends. Only the terminal 10mm of the closed pointed crown may taper
below the normal 10mm leaf wall, with a minimum thickness of 3mm there.
Shared base geometry and the full placement envelope remain
unchanged. Strobilos diamonds follow equal upper and lower distances along the
body curve and shrink toward the crown without stretching into the tip.

The first seven Strobilos rows split the authored corner normals along each
diamond's raised centre ridge. The shared sharp-angle pass had blended the
normals across these shallower ridges. Splitting just those edges preserves
their definition while retaining the original scale geometry, upper shading
and face budget. As with Kynara, the model exports the authored normals for
the in-game renderer to use.

## Placement, materials and textures

Authored meshes are centred in X/Z, have a flat foot at Y=0 and reach Y=1. Their
existing normalized width, depth and foot width stay aligned with
`FixedDecorBlock.Style`. `fit_to_block=true` and scale `[2,2,2]` bake the model at
the existing Large height; `FinialMeshTransform` supplies **1m Small and 2m Large**
states. Sloped supports change the mounting base and body position while the
ornament stays upright.

Keep the softened foot's full width at its vertical band. Its bottom bevel is
slightly narrower than that band, so the lowest contact plane must not be used
to shrink the cached foot width. Existing coping, slopes, half-height support,
selection, collision and saved fitting metadata retain their established paths.

Each style still has **56 finishes**: 27 stone materials, their 27 aged finishes,
bronze and silver. All finishes reuse the same geometry. Registry IDs, item
models, display transforms and search vocabulary remain intact.

The existing cylindrical projection retains `cylindrical_u_repeats=2`,
`texture_u_tiles=4` and face-oriented UVs inside the shared detailed repeat
surface. Size and fitting transforms keep their established material-density
behaviour. Native textures and optional Collection packs require no new
per-material geometry or texture copies for the reconstruction.

## Verification

`tools/tests/test_finial_rebuild_safety.py` measures shipped triangles rather
than accepting authoring flags alone. It checks hashes and source lineage,
separate face counts, valid unit normals, nonzero triangle areas, closed outward
solid components, flat capped feet, cached dimensions and actual foot/neck
sections. Turned-profile section widths allow the recorded curve approximation;
the foot-width fit remains exact to the export tolerance.
`tools/tests/test_finial_refinement_safety.py` also samples leaf rear interiors
against the actual core, checks leaf thickness and preserves the three stepped
base profiles and Sphaira's original base geometry.
`tools/tests/test_kynara_leaf_depth_safety.py` measures exposed relief against
actual core sections in the shipped OBJ, compares it to Balanos's carved cup,
and checks every leaf's projection so deep tips cannot conceal a shallow body.
`tools/tests/test_kynara_leaf_rim_safety.py` measures the visible rim against
the outermost neighbouring leaf or supporting body, excluding the leaf being
measured. Its length-weighted shoulder check protects the bold overlap step;
wall thickness buried behind another leaf cannot satisfy it.
`tools/tests/test_kynara_leaf_coverage_safety.py` checks the lowest tier's flat
hem and full circumference, then samples the supporting surface in 360 directions
at the row joins and checks the filled crown. It rejects a deep exposed-body
window while allowing thick leaf-to-leaf shoulders. A blanket contour-depth
limit cannot distinguish those cases and must not force the ornament flat.
Closed individual components alone also do not prove that the gaps between
neighbouring leaves are covered.
`tools/tests/test_strobilos_crown_safety.py` independently measures crown diamond
proportions and staggering, outward bevels and embedded rear patches.
`tools/tests/test_strobilos_ridge_normals_safety.py` checks the two halves of
each lower ridge in the exported OBJ and verifies that the correction leaves
geometry and the normals outside the lower scale rows intact.

The existing finial Java and server tests cover both sizes, all materials,
automatic support profiles, saved fits, collision, selection and the fitted
base's material transport. Run the asset suite and relevant runtime checks after
activation. The opt-in client probe checks actual loaded mesh positions, prepared
normals, atlas UVs, footprints and 1m/2m heights across all five styles in stone,
bronze and silver, then runs the actual inventory-icon worker for 15 icons:

```text
gradlew.bat runClient -PdaedalonClientDirectory=build/finial-rebuild-client -PdaedalonFinialChecks=true --no-daemon
```

It writes its icon comparison under `build/reports/finial-rebuild/client` and
reports `DAEDALON_FINIAL_REBUILD_CHECKS_OK` on success. Inspect the Blender previews
and loaded in-game models separately; automated geometry checks do not establish
shader appearance or visual quality.
