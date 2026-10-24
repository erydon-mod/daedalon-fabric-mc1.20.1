# Obeliskos sizes

Obeliskos monuments keep Small, Medium and Large at their original one-, two-
and three-block heights, with Medium as the default. Extra Large adds a five-
block height and scales width proportionately to approximately 1.265 blocks.
The serialized size is `extra_large`; existing IDs and saved size values are
unchanged. The debug stick cycles Small, Medium, Large and Extra Large.

The new size reuses the supplied three-block mesh. A single lazily cached world
mesh clips its scaled triangles at whole UV repeat boundaries before wrapping
each resulting piece into the original material window. This retains the old
stone texel density without sampling neighbouring atlas sprites. Positions,
supplied normals and vertex channels interpolate along the original triangles.
All materials share the result. Weak source keys allow the old cache to expire
when its baked resource models are released. Small, Medium, Large and item
rendering retain their established transform paths.

Only Extra Large uses invisible `obeliskos_part` cells. Forty-five cached local
collision cells cover the existing box profile; the owner occupies the centre
bottom cell and 44 helpers cover the remaining height and small overhangs.
These are not item entries or rendered geometry and have no block entities or
custom ticking. Lighting queries never resolve an owner. Existing sizes do not
create helpers or scan for them during ordinary size changes.

Growth checks the complete footprint and world height before the debug stick
or an NBT-selected item can place the larger assembly. Blocked growth preserves
nearby builds. Debugging, picking and breaking any helper act on its owner;
normal top placement reaches the five-block crown, and Axiom assembly selection
includes only owned cells. Material and finish swaps retain the shared cells.
Partially occupied edge cells preserve native waterlogging and restore their
water sources when removed. Full solid cells remain dry.

`ObeliskSizeMeshTest` checks the old transforms, triangle area, bounded repeat
UVs, native/64x texel density and interpolated vertex data. The four
`ObeliskSizeGameTests` cover all 55 material variants, local collision and
selection, blocked edits, upper-cell targeting, material swaps, water cleanup,
NBT placement and ordinary block placement above the crown. Visual shader
acceptance remains separate from these source and runtime checks.
