# Axiom camera-facing preview pilot

Spartan Promachos statues use a single 256x256 image in Axiom 5.4.2 region
previews on Minecraft 1.20.1. The image uses the same complete-mesh projection
and active-material sampling as the 64x64 REI icons, at a higher resolution.
The image faces the current Axiom camera, including its pitch, while region
translation and rotation move its anchor. It intentionally does not depict the
statue facing away from the viewer; placement still uses the actual blockstate.

This replaces the rejected collision-box preview. All OBJ models again return
empty vanilla quads. Placed world meshes and existing inventory/item rendering
are unchanged. No display entities or extra world blocks are created.

## Scope and cost

- Pilot only: Spartan Promachos stone, aged and bronze variants.
- Region add/remove/clear hooks maintain a statue-only list; no world scans.
- One geometry projection is shared per mesh and resource reload. Each requested
  material creates one image lazily, with at most 64 images (16 MiB of RGBA pixels
  each on CPU and GPU, plus shared projection data). Reload frees images and templates.
- Each image is one quad, with at most 1,024 statues drawn per region render.
- Full-bright image rendering preserves the baked icon lighting. Preview opacity
  follows Axiom; projection, model-view and framebuffer bindings are restored.
- Only Axiom region previews are hooked; editor tools using other rendering
  paths remain outside this pilot. Without Axiom these optional mixins are skipped.

## Verification

Automated tests cover camera-facing orientation across yaw, pitch and selection
rotation, translated/rotated anchors, precision near the world border, and the
separate 256px preview / 64px inventory raster resolutions.

Oliver will check visual quality in game:

1. Preview a Spartan Promachos statue using Axiom move/copy/paste.
2. Orbit and look from above: the picture must keep facing the camera.
3. Repeat for all sizes, facings and offset states; place, rotate and undo.
4. Check stone, aged and bronze finishes, then reload resources and preview again.
5. Check mixed selections with vanilla blocks and existing palette/REI icons.
6. Confirm placed statues retain full detail and normal world lighting/shaders.

In-game visual acceptance is pending. Do not expand to other families or claim
full Axiom compatibility until the pilot passes.
