# Shared lantern glazing

Lantern panes use the native ERYDON crystal glazing colour and transparency.
Their fixed sprite is `daedalon:block/lantern_glazing`; stone, lead frames and
bronze fittings remain separate OBJ materials. Panes must use a translucent
Fabric render material, while opaque lantern components retain solid rendering.

The native texture copies are recorded with their source commit and exact
SHA-256 values in [the texture evidence](evidence/lantern-glazing-textures.json):

- `lantern_glazing.png` is ERYDON's 16x `glazing_crystal.png`, RGBA
  `(160,190,210,20)` throughout.
- `lantern_glazing_s.png` is the matching authored smooth dielectric map from
  ERYDON's built-in high-polish glazing pack, RGBA `(255,0,0,255)` throughout.
  Daedalon includes this smoothness map as its native PBR fallback.
- `lantern_lead.png` is ERYDON's 64x `lead_black.png`, retained without resizing
  or recolouring, including its authored alpha values of 254–255. Lead quads
  remain solid. That native source has no normal or specular companions.
- Bronze fittings reuse the existing shared `daedalon:block/bronze` and its
  specular map rather than adding another bronze texture.

## Optional Iris glass identity

Only explicitly tagged glazing OBJ quads borrow a glass shader identity. The
Indium normal transport carries that flag through the existing Sodium/Iris
terrain writer. It uses the actual current shader map entry for
`erydon:glazing_crystal_block` when available, otherwise vanilla glass, and
otherwise preserves the original incoming ID. No whole lantern block, stone
surface, lead frame or bronze fitting is reclassified. Contained fountain water
retains its separate fluid handling.

This optional path does not link ERYDON Java classes or hard-code its shader
material numbers. ERYDON's shader profile checks and high-polish glazing option
still determine whether its enhanced reflection response is active. The normal
glass PBR maps also work independently of that enhancement.
Where ERYDON supplies a polished surface ID only for the non-luminous OFF state,
the corresponding ON state inherits it before neutral classification. An
explicit shader mapping for an ON state always takes priority.

## Gas flame and light controls

Lanterns start on with Bright block light (15). The debug stick exposes one
Light cycle: Low (10), Bright (15), Off. Saved `lit` and `brightness` fields
retain compatibility with existing worlds. Off emits no block light and
no flame. Only the lantern owner
emits light, so extending the chain does not create extra light sources.

Separate flame cards sit directly on each burner nozzle, follow the existing
mount/size/facing transform and use their authored 0..1 UVs. Their warm yellow
sprite is translucent and emissive with no diffuse shading or ambient occlusion.
Only flame quads use the current shader map's vanilla fire identity; glazing
continues to use its own glass identity. Stone, lead and bronze never acquire
flame emission. Full and Low use the same visible flame.

Opaque lantern surfaces explicitly keep ambient occlusion and diffuse shading
when the owner emits block light. Indium otherwise disables its default ambient
occlusion for luminous blocks, making the same metal surface look flatter when
switched on. The cached surface material remains non-emissive and honours the
renderer-wide ambient-occlusion setting; glass and flame keep their own flags.

`tools/generate_lantern_flame_texture.py` deterministically authors the 64x128
colour sprite and a neutral, non-reflective LabPBR emission map. Its active
pixels use alpha 254 for full emission, following the
[LabPBR material standard](https://shaderlabs.org/wiki/LabPBR_Material_Standard#Alpha_Channel).
The colour sprite supplies transparency independently of that emission map.

Cached GUI and Axiom icon templates omit the transparent pane faces so the
single-depth icon rasterizer still shows the burner and rear frame. World and
held-item meshes retain their actual panes.
The icon source loader permits authored alpha for the three fixed lantern roles
(glazing, flame and lead), including material entries unused by a particular
icon. Other source textures retain the ordinary opaque validation.

The supported Iris 1.7.6 Minecraft 1.20.1 runtime requires Sodium 0.5.x; Indium
provides the Fabric renderer used by these OBJ models in that setup. Without
shaders, native Fabric/Indigo translucency remains sufficient and requires no
shader material proxy. This terrain bridge does not add shader overrides to
held-item, cached GUI or Axiom render paths. Those previews and shader-disabled,
supported Complementary, native and Collection-pack appearances require visual
acceptance independently of source and unit checks.
