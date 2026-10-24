# Daedalon item presentation

Daedalon items are hidden in both first-person hands. OBJ decorations use the
shared material-specific 128x128 icon cache in third person, on the ground,
in item frames and in inventories. Placed geometry is unchanged.

Each family's shared depth-buffered projection uses every quad from its actual
baked OBJ mesh, its GUI transform and its model normals. Colour comes from the
active resource pack, with the same phase-zero material mapping as item models
and the established vanilla two-light shading for stone. Bronze and Silver
combine that model lighting with fixed studio reflections from the actual normals, with
bright softboxes, dark surroundings and a gentle cool fill. Their chosen
texture supplies the metal colour, including bronze handles on stone urns.
The metal picture retains 45 percent of the original form lighting and
55 percent reflection, blended in linear colour space. This keeps faces,
clothing and fine relief readable without losing the polished reflection bands.
These reflections are baked into the cached picture, not live shader effects.
Material variants reuse that projection; cached pictures do not need another
mesh render.

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
The atlas is refreshed when resources reload.

Visible item draws enqueue colour work, then immediately use the cached picture
or a temporary flat material sprite. They never decode PNGs, resolve a material
raster or upload textures. One daemon worker colours requested icons from each
family's shared projection; 512 outstanding requests, including completed
images, bound staged image memory to about 33 MiB. Resource reload discards
unstarted requests and closes both completed and late old-generation images.
Source PNGs are decoded once per logical material per resource generation and
copied into immutable ABGR arrays; their native images close immediately.

The client tick uploads at most 16 completed icons with a soft 2 ms budget.
Adjacent cells merge into a single row-strip upload; each uploaded picture has
a transparent one-pixel gutter and becomes visible only after upload. Uploads
explicitly retain linear filtering without mipmaps, including when vanilla's
entity render phases request nearest filtering. A gamma lookup replaces
per-pixel exponentiation while retaining the previous colour within one
8-bit channel step. Warm icons draw one quad and do no raster work.

Each GPU atlas page is 2048x2048 with 225 cells. Pages are created only when
requested icons fill them: 22 pages cover all 4,734 catalogue items, with a
352 MiB worst-case GPU allocation if every item is visited. There are no
full-size CPU atlas copies and no slot reuse that could invalidate deferred
item vertices. Untouched pages allocate nothing. Axiom's separate 256x256
preview keeps its 128-image LRU and generation throttle.

The projection stores one packed reflection value per sample (64 KiB per
128x128 family, shared by all finishes). Metal colouring runs only when an icon
is prepared in the background; it adds no draw calls, atlas pages or per-frame
lighting. Axiom's existing 256x256 render uses the same metal treatment.

The 24 October 2026 second-pass native comparison covered Zeus, Zeus's bust,
Hestia's previous and corrected bust views, Diota, Gothic panels, Gothic friezes
and the medallion with native and Collection 64x resources: 132 renders,
including metal/diffuse comparisons at 64, 128 and 256 pixels.
Alpha and coverage matched, mixed Diota stone pixels retained their exact
diffuse colours, and all 18 existing stone comparison PNGs were byte-identical.
All ten metal comparison silhouettes and alpha channels also matched the
first pass exactly. Visual comparisons at 32/48 pixels retain faces and clothing
with fewer isolated reflection fragments; the underlying normals are not blurred.
Warm cache checks scheduled no new work or atlas allocations; the actual GPU
upload/filter/gutter/release checks also passed. These generated-picture and
cache checks do not measure live REI frame rate or replace player visual review.

This supersedes the older per-family 3D held/dropped-item preparation notes.
Existing optional item meshes remain compatible assets, but this item-rendering
path does not draw them and new decorations do not need a separate 3D item mesh.
