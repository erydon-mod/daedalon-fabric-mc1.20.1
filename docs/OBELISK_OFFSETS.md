# Obelisk alcove offsets

Obeliskos keeps its Small, Medium and Large heights of 2m, 3m and 5m.
The debug stick now exposes `size`, `offset` and `facing`, in that order.
Enabling offset moves the monument 0.8003375m in its selected facing direction,
matching the shared statue and plinth distance without changing its height or
scale. The ground contact stays at the owner's Y level. Fresh placement chooses
the same offset direction as a plinth; item `BlockStateTag` values preserve the
selected size, offset and facing. Rotation and mirroring carry that direction.

The five-block Large size uses 45 interaction cells when centred and only its
30 occupied cells when offset. Its visible mesh, local collision, selection,
Axiom preview bounds and assembly selection follow the same displacement.
Debug edits validate every new footprint before writing, including direction
and offset changes. Occupied neighbouring construction blocks the edit.
Successful edits remove only the old cells owned by that monument and restore
water from vacated waterlogged edges. Small and Medium retain their established
owner-only interaction and collision behaviour, including the shared offset
selection target.

Regression coverage lives in `ObeliskSizeGameTests` and `ObeliskSizeMeshTest`:
all sizes and facings, saved properties, transforms, exact collision volume,
local helper cells, blocked debug edits, water restoration and shrink cleanup.
Visual acceptance in an alcove remains a separate in-game check.
