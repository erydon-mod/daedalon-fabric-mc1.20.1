# Clean plinth geometry

The five Oliver-authored Meshy plinths are rebuilt from measured architectural
profiles rather than reducing an irregular triangle surface further. Broad
panels become planes, intended curves become regular mouldings, and stone edges
retain slight softening. The original source files remain unchanged.

Bathron is the validated pilot: its taper, base, neck bead and cornice are retained
in 1,010 editable polygons and 2,044 runtime triangles, replacing the previous
6,000-triangle game mesh. The other designs retain their distinguishing features:
Astragalos bead collars and framed panels; Kion's eighteen capsule flutes;
Stephanos wreath and corner foliage; and Triphyllon pointed trefoil panels and
corner colonnettes. Actual current counts and feature measurements are recorded
in [the rebuild evidence](evidence/plinth-rebuild-source.json).

The ornate designs prioritize the original carving over a fixed triangle target.
Their clean architectural cores can support retained source ornament or measured
curved details without reducing the original sculpt to a generic relief. Compare
runtime triangles consistently: editable Blender polygons can triangulate into
more than one face and are not an equivalent performance count.

| Design | Prior game triangles | Rebuilt game triangles |
| --- | ---: | ---: |
| Astragalos | 6,000 | 5,856 |
| Bathron | 6,000 | 2,044 |
| Kion | 6,000 | 5,736 |
| Stephanos | 6,000 | 15,999 |
| Triphyllon | 6,000 | 23,472 |

The two ornate designs restore carving lost by the earlier 6,000-triangle
exports. Stephanos now has 15,999 editable Blender faces after the requested
16,000-face reduction, retaining the exact architectural core and reducing only
the original ornament. These polygons are triangles; Triphyllon's 16,064
editable polygons export as 23,472 triangles. Stephanos retains cropped native leaf surfaces with bounded reduction;
the surrounding panels, mouldings and support core use measured geometry.

`tools/rebuild_plinth_meshes.py` owns the repeatable authoring path. Its inputs
are the approved source root and a staging output directory; editable metric
Blender files and review renders accompany each neutral OBJ. Validate one pilot
visually and against the existing collision/size contract before activating the
remaining batch. The original UUIDs, source hashes and first 6,000-triangle export
remain recorded in [the historical batch evidence](evidence/plinth-batch-source.json).

One shared OBJ continues to serve every stone, aged, bronze and silver variant.
Each rebuilt mesh is Y-up, centred in X/Z and exactly one metre high, with sealed
flat contact planes at Y=0 and Y=1. The mesh definitions retain `fit_to_block`
and uniform scale 2, so the established runtime scales produce heights of
0.5m, 1m and 2m. Kion no longer needs the height correction used for its short
original mesh. Existing footprint profiles, facing, offset, waterlogging and
fountain attachment behaviour remain in place.

Prepared corner normals preserve crisp planes and smooth intended curves;
`smooth_normals=false` keeps those authored normals in the renderer. The single
`none` material, span 6 and shared repeat sheets preserve one texture tile per
metre at every size. Axis-stabilized projection is used by default; Astragalos
uses normal-based box projection so paired base triangles share one UV basis.
Material-specific meshes or textures are unnecessary.

The physical tests inspect shipped triangles for valid normals, nonzero area,
sealed architectural cores, flat support planes, exact dimensions, retained
moulding landmarks and eighteen actual Kion flutes. Astragalos also retains all
240 individually closed beads. Stephanos preserves native relief topology:
the requested reduction leaves 55 embedded rear seams and retains 35 existing
native tessellation edges. Its 2,208-triangle architectural core and core corner
normals remain exact and fully sealed. The [specific relief exceptions](evidence/plinth-stephanos-relief-topology.json)
are checked against the actual exported edges, rather than requiring artificial
closure behind the stone support.

Run the asset suite, build and server tests after activation. Native,
Collection-pack and shader appearance still need separate in-world visual
acceptance.
