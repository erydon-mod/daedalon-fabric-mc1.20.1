# Frieze atlas-edge sampling

The Silver Byzantine corner report showed a coloured, pearlescent strip even
though its active native/64x albedo is uniformly silver and its specular map
is uniform. The main mitre closures have opposite winding as intended.
Corner projection can instead put a complete primitive on the outside U edge
of a sprite, where filtered albedo or PBR sampling can include its neighbour.

Placed friezes select the identical horizontal repeat copy with greater
clearance from the outside atlas edge. `FriezeAtlasRepeatGuard` applies one
constant half-width translation to the entire quad after world-phase mapping
and before `spriteBake`. It never moves vertices independently, changes V,
edits positions/normals/tags, clamps texture phase, or changes texture density.
Quads spanning both copies retain their original mapping. Items, capitals and
other OBJ families do not enable this guard.

The material sheet has two identical horizontal halves; uniform White, Bronze
and Silver finishes are also unchanged by that translation. The repeat audit
checks every RGBA channel of albedo, normal and specular images, including the
actual installed Collection ZIP's aliases and native fallback resources.
No texture pixels or source OBJ geometry were changed for this correction.

Validation includes actual OBJ parsing/baking and world model emission for all
four styles, five joins, four facings and six repeat phases: 480 cases. All
emitted primitives preserve their geometry, normals and interpolation, with no
remaining outside-U-edge primitives. A private invisible OpenGL check also
reproduces a neighbouring tile contaminating Silver albedo, flat normals and
specular data at the old edge, then verifies the guarded samples retain their
source values. Oliver approved the live frieze corners on 24 October 2026 in
the combined Daedalon/ERYDON review with Complementary Unbound and the 64x pack.

## Gothic Frieze investigation, 15 November 2026

The live Gothic definition and all three other frieze definitions use one
shared component OBJ per design. `ObjMeshModelLoadingPlugin` supplies each
stone/aged material's `statue_spartan_promachos_*` sheet. Their `none` material
does not select a dedicated MTL texture. `FriezeMeshParts` selects the actual
straight/return/end components, and `FriezeWorldTransform` projects their placed
coordinates after facing rotation. `WorldTexturePhase` then maps the six-column
window into the shared 12x6 sprite before the atlas guard runs. This is
Daedalon's own seamless repeat path; it does not require a Continuity CTM rule.

The new `FriezeSurfaceProjectionTest` parses the actual shipped definitions,
OBJs and MTLs. It checks every nondegenerate triangle in all four facings,
distributing all components over all 216 XYZ repeat origins, negative
coordinates, chunk boundaries and world-limit coordinates. It runs the
production projection, shared sheet mapping and atlas guard, and checks bounded
UVs, unchanged texture density/interpolation and world phase. The existing
`FriezeWorldTransformTest` separately checks coincident joins across horizontal
and vertical repeat boundaries for every facing and surface direction; the
asset seam tests check the real section boundary profiles.

The material audit now also compares every albedo cell against its owning
ERYDON repeat tile. On this date all 54 native stone/aged sheets and both
Collection source tiers matched all 36 tiles: 1,944 comparisons per tier,
5,832 total, with no pixel differences. Every present albedo/PBR map also
passed the identical-horizontal-halves or uniform-finish proof. The selected
`run-dev` 64x `compat2-v1.5.17` ZIP passed the alias/native-fallback audit too;
its SHA-256 was
`81e5df376621f65240958dc06f0210b6b53ca87827acb9e89e5be88f84a97e72`.
Current Collection source `VERSION` was `1.6.1`. No pack pixels, CTM rules,
OBJ geometry, UV renderer code or saved settings were changed by this audit.

Reproduce the source/pack comparison with:

```text
python -B tools/audit_frieze_repeat_surfaces.py --erydon-root "path/to/erydon" --collection-root "path/to/collection-source" --collection-pack "path/to/Collection-64x.zip"
```

### Finish control and the development environment

The owning live ERYDON `client/PolishedStoneMaterials.java` includes fresh
named-stone `daedalon:<material>_<design>_frieze` IDs in the ordinary PLAIN
preference. Its exclusions intentionally leave aged stone, plain White/Black
and metals on their authored response. `ErydonHighPolish` captures the active
settings once at startup. ERYDON's
`mixin/client/compat/iris/HighPolishBlockMaterialMixin.java` assigns that finish
to the actual registered block states after the supported shader adapter is
ready. Daedalon's optional `ErydonStoneFinishBridge` reads that same captured
classifier/settings for language labels; it does not rewrite textures or
control the shader independently. Finish preference changes require a restart.

The inspected IntelliJ `run-dev` had Sodium/Indium/Iris on Gradle's runtime
classpath, but neither Gradle nor `run-dev/mods` supplied ERYDON or Continuity,
and no ERYDON config was present. Iris selected
`ComplementaryUnbound_r5.9_dev2.zip`. Therefore this configuration supplies no
ERYDON finish controller; the authored native/Collection PBR response can
remain fixed. This is an environment limitation, not proof of the cause of
Oliver's earlier view. Continuity's absence does not disable Daedalon's own
frieze repeat. Nothing was installed or enabled during this task.

Current source and offline checks establish the intended mapping and material
route. They do not establish the appearance of a running client. Visual
acceptance still requires native, 32x and 64x runs, straight/inside/outside
joins, all four facings and top/underside phase, both sides of the six-tile
boundary, resource reload, and supported shader finish changes with ERYDON
present. The reported Gothic appearance remains unverified until that review.
