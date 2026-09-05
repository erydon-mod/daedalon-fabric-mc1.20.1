# Daedalon item presentation

Daedalon items are hidden in both first-person hands. OBJ decorations use the
existing material-specific 64x64 icon cache in third person, on the ground,
in item frames and in inventories. Placed geometry is unchanged.

`ObjGuiIconCache.tryRender` intercepts the normal item renderer before its
model transformation or mesh emission. First-person cancellation happens
before any icon lookup. The namespace guard leaves other mods and vanilla
items alone; the emblem already uses a vanilla generated flat item.

Normal GUI icons remain one quad with their established baked lighting. World
icons have two oppositely wound faces, mirrored back UVs, world light and
optional enchantment glint. Their size is independent of the original OBJ:
0.55 in third person and 0.5 on the ground, using vanilla flat-item offsets.
Held icons tilt 45 degrees to remain readable from the front. Matrix changes
are scoped to each draw.

If an OBJ icon cannot be cached, its material sprite is drawn flat instead.
Neither cache failures nor enchanted items restore the expensive item mesh.
The existing atlas remains bounded and is refreshed when resources reload.

This supersedes the older per-family 3D held/dropped-item preparation notes.
Existing optional item meshes remain compatible assets, but this item-rendering
path does not draw them and new decorations do not need a separate 3D item mesh.
