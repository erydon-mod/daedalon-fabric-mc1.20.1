# Raised fountain bases

Gothic, Georgian and Greek fountain basins have a `raised_base` boolean state.
It defaults to `false`, so existing fountains retain their original height.
The Daedalon Debug Stick lists Raised Base immediately after Basin Size and
keeps Waterlogged last. A change is rejected when the resulting fountain would
overwrite a solid neighbouring block or exceed the supported world height.

When enabled, the existing basin, attached plinth, all attached bowls and their
contained water move up exactly one block. The anchor block stays in place.
A new foundation continues the basin's authored underside footprint down to
the anchor's original bottom plane. Each size retains its current full width;
the foundation is one block tall rather than scaling its height with Basin Size.

Waterlogged raised foundations expose ordinary water in their partially open
edge cells. This water can flow around the foundation normally. Full foundation
cells and upper interaction cells do not acquire an ordinary fluid state; the
basin and bowl water above remain contained. Removing a wet edge helper restores
its water source. Filling or draining the fountain still works through its
visible basin or interaction cells.

## Shared geometry and source measurements

`tools/derive_fountain_base_profiles.py` derives the three shared profiles in
`src/main/resources/data/daedalon/fountain_base/` from the existing basin OBJs.
Running this authoring tool requires Shapely 2.1 or newer. Runtime and the normal
asset audits do not require Shapely.

The tool replays the existing float fit transform and projects the triangles
within one four-hundredth of the source's largest span above its lowest point.
Their polygon union supplies the foundation outline and constrained cap
triangulation. The saved source hash makes an outdated profile detectable.
The original OBJ files are not rewritten.

The supplied underside triangles are slightly uneven. Each new upper perimeter
vertex uses the lowest actual source-triangle height at the same horizontal
position, after the existing basin has moved up by one block. This closes the
join without altering the basin. The concealed upper cap follows those measured
heights. Collision uses a cached horizontal footprint from Y=0 to Y=1; the
existing basin collision is reused one cell higher.

There are nine foundation collision shapes and at most nine lazily created
render meshes: three styles multiplied by three sizes. Materials and world
positions share them. Raised interaction geometry reuses the existing local
basin shapes shifted by one cell, and assembly layout caches include the
Raised Base state. No per-material OBJ or additional placed block entity is
created. Basin blockstate JSON keeps its existing six size/water combinations;
the shared wrapper handles Raised Base at runtime.

## Texture continuation

Foundation UVs continue the actual basin box projection and size crop. The
basin uses a five-block source span and the existing `detailedSurface` layout,
which gives 6/5 repeat tiles per world block. Reusing the capital's tile-unit
projector would give a different texture density.

The continuation treats foundation Y as the original basin-local Y minus one.
Side faces are split once at a source V wrap when required, then shifted by a
whole repeat period. This preserves the original texture scale and phase at
the raised underside while keeping emitted UVs inside the existing sprite.
No new material textures or Collection pack overrides are required.

## Verification boundary

`FountainRaisedBaseMeshTest` checks the exact one-block render translation,
unchanged original UVs, repeat density, underside UV agreement and wrap bounds.
The profile safety audits compare source hashes, footprints, triangulation and
measured heights. Raised fountain GameTests exercise collision, layout, state
changes, water and helper behaviour in the server. These checks do not replace
an in-game shader or visual inspection.
