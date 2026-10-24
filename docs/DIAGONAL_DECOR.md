# Exedra and statue diagonal positions

The debug stick exposes an **Orientation** control after Facing. Choose
**Straight** for the existing four cardinal positions or **Diagonal (45°)**
for the four positions between them. Exedra keeps its 2m, 3m and 4m widths and
its existing one-metre height. Statues and busts keep their existing sizes;
offset statues move along the chosen diagonal direction by the same distance
as their straight counterparts.

Old states default to Straight. No block IDs, versions, mesh geometry, item
models or texture density change. All materials share the same geometry and
cached transforms. Collision and selection use cached conservative rotations
of the existing authored boxes. Axiom previews use those same state bounds.

Copy rotation preserves the half step. Reflection reverses the diagonal facing
and offset direction, including East-authored statue profiles. The automatic
checks cover all mesh profiles, widths, sizes, cardinal facings and both mirror
axes. Client inspection still needs to confirm the diagonal Exedra opening,
selection feel, statues, offset positions, shaders and Axiom copy previews.
