# Axiom camera-facing decoration previews

Daedalon decoration items use a camera-facing image in Axiom 5.4.2 region
previews on Minecraft 1.20.1. Oliver accepted the Spartan Promachos pilot;
support now extends to the remaining decoration families and materials.

The image uses the same representative item mesh and active-material sampling
as REI, with a separate 256px raster. Bounds follow the copied blockstate,
including size and offsets. Wide decorations use their largest extent;
monopteros and fountain basins use full structure bounds instead of helper cells.
Translation and selection rotation move the anchor; the picture keeps facing
the camera. Placement still uses the real blockstate and complete world mesh.

Connected friezes and assembled fountains show representative item images,
not an exact picture of every connection or additional fountain tier. Facing
is intentionally not visible in a camera-facing image. Inventory and REI
rendering remain unchanged. No entities or extra world blocks are created.

## Bounded cost

- Region add/remove/clear hooks track decoration anchors, without world scans.
  Invisible helper blocks do not create duplicate pictures.
- At most 128 sharp images are retained (32 MiB of RGBA pixels on each of CPU
  and GPU), alongside at most eight shared geometry projection templates.
- Older unused images are recycled. Images used within the last second remain
  protected, preventing churn in large selections. Pending drawing is submitted
  before recycling a texture slot. Resource reload releases the cache.
- At most one new sharp image is generated per 50ms. While waiting, or when
  the sharp cache is full, previews use the existing 64px inventory atlas.
- Each image is one quad. Up to 1,024 decorations are shown per region render.
- Preview opacity follows Axiom; rendering restores projection, model-view and
  framebuffer bindings. Optional mixins are skipped when Axiom is absent.
- Only Axiom region previews are hooked; tools using other rendering paths
  are outside this support.

## Verification

Tests cover camera orientation, rotated anchors, world-border precision,
separate preview/inventory resolutions, and usable bounds for every registered
decoration blockstate, excluding the two invisible helper blocks.

The broader rollout still needs an in-game spot check: wide benches, wall and
ceiling panels, a monopteros, and an assembled fountain; then a mixed-material
selection and resource reload. Confirm placement, rotation and undo retain
the real geometry and states. The statue pilot has already passed visual review.
