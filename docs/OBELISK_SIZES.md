# Obeliskos sizes

Obeliskos monuments offer exactly three sizes: Small is two blocks high,
Medium is three blocks high and Large is five blocks high. Medium remains the
default selection. Each choice keeps the approved proportional width; Large
is approximately 1.265 blocks wide. The debug stick cycles Small, Medium and
Large, with no separate Extra Large choice.

The five-block Large uses the same measured geometry and shared cached mesh
as the approved five-block review model. Its scaled triangles are clipped at
whole UV repeat boundaries before wrapping each resulting piece into the
original material window. This retains the established stone texel density
without sampling neighbouring atlas sprites. Positions, supplied normals and
vertex channels interpolate along the original triangles. All materials share
the result. Weak source keys let the old cache expire when its baked resource
models are released. Small and Medium use the established cached transform
path at the previously approved two- and three-block scales.

Only Large uses invisible `obeliskos_part` cells. Forty-five cached local
collision and outline cells cover the existing box profile; the owner occupies
the centre bottom cell and 44 helpers cover the remaining height and small
overhangs. These are not item entries or rendered geometry and have no block
entities or custom ticking. Lighting queries never resolve an owner. Small and
Medium do not create helpers or scan for them during ordinary size changes.

Growth checks the complete footprint and world height before the debug stick
or an NBT-selected item can place Large. Blocked growth preserves nearby
builds. Debugging, picking and breaking any helper act on its owner; normal
top placement reaches the five-block crown, and Axiom assembly selection
includes only owned cells. Material and finish swaps retain the shared cells.
Partially occupied edge cells preserve native waterlogging and restore their
water sources when removed. Full solid cells remain dry.

The unpublished review value `extra_large` is accepted as an input alias for
`large` when reading blockstates or item NBT, then saves canonically as `large`.
It does not add a fourth enum value, debug choice or blockstate model selector.
Existing five-block review objects therefore retain their geometry and helper
cells. Older one-, two- and three-block selections now follow the approved
Small/Medium/Large heights of two, three and five blocks respectively; cycle
an older three-block Large selection away from and back to Large to build its
taller interaction cells.

`ObeliskSizeMeshTest` checks the three choices, smaller cached transforms,
triangle area, bounded repeat UVs, native/64x texel density and interpolated
vertex data. The four `ObeliskSizeGameTests` cover all 55 material variants,
three-choice state and legacy-alias decoding, local collision and selection,
blocked edits, upper-cell targeting, material swaps, water cleanup, NBT
placement and ordinary block placement above the crown. Visual shader
acceptance remains separate from these source and runtime checks.
