# Exedra and statue diagonal positions

The debug stick exposes one **Orientation** control. Right-click to turn
clockwise by 45°, through North, North-east, East, South-east, South, South-west,
West and North-west. Crouch to reverse the cycle; there is no separate Facing
or Straight/Diagonal control. Exedra keeps its 2m, 3m and 4m widths and
its existing one-metre height. Statues and busts keep their existing sizes;
offset statues move along the chosen diagonal direction by the same distance
as their straight counterparts.

Existing saved poses are retained. No block IDs, versions, mesh geometry, item
models or texture density change. All materials share the same geometry and
cached transforms. Collision and selection use cached conservative rotations
of the existing authored boxes. Axiom previews use those same state bounds.

Copy rotation preserves the half step. Reflection reverses the diagonal facing
and offset direction, including East-authored statue profiles. The automatic
checks cover all mesh profiles, widths, sizes, cardinal facings and both mirror
axes. Oliver approved the diagonal Exedra and statue controls in the
24 October 2026 pre-publication review with REI, Complementary Unbound and the
Collection 64x pack. Axiom copy previews remain a separate optional integration
check; the approval does not claim that wider rendering matrix.
