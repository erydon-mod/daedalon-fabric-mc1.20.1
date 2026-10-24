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
source values. These checks do not replace the next live shader inspection.
