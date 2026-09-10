# Axiom preview pilot

The Spartan Promachos statue supplies a cheap vanilla baked-model fallback for
Axiom 5.4.2 on Minecraft 1.20.1. This pilot covers its stone, aged and bronze
variants. Other OBJ families still return no vanilla quads.

The fallback reuses the statue's cached collision silhouette, including body,
shield and spear. The existing profile handles all sizes, facings and offsets;
collision is used instead of the outline so the invisible offset targeting post
does not become visible. This is an approximate blocky preview, not the full mesh.

Quads are generated lazily and cached by collision shape within each baked model.
Resource reload discards the cache with the model and its atlas sprite. Only the
unculled query emits faces. Geometry is limited to 128 boxes / 768 quads, with one
bounding box used if a future profile exceeds that limit. Normal Fabric world
rendering continues to emit the original mesh; item rendering and the existing
REI/Axiom palette icon route are unchanged. Other vanilla model consumers may
also see the fallback.

## Validation

Automated tests check outward winding, atlas-safe UVs, geometry outside the anchor
cell, immutable output, the complexity cap and empty/item/directional requests.
The development client starts with Axiom 5.4.2, Sodium and Indium.

In-game visual verification is pending Oliver's check:

1. Place a Spartan Promachos statue in a development test world.
2. Check its Axiom move and paste previews from all sides.
3. Repeat for Small, Medium and Large, all four facings, and offset on/off.
4. Check a stone, aged and bronze finish; reload resources and preview again.
5. Rotate and paste, then undo; confirm the placed statue remains fully detailed.
6. Confirm a normal vanilla block and the existing palette icons still render.

The fallback changes rendering only. It does not alter selection targeting,
placement, blockstate rotation or undo logic. Do not claim full Axiom compatibility
or expand the pilot until these visual checks pass.
