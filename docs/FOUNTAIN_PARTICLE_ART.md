# Fountain particle artwork

The fountain uses its own 64x64 RGBA albedo sprites, not replacements for vanilla
water particles or Collection-pack textures. Both are neutrally coloured and
receive the same biome-water tint in the particle renderer. Airborne drops are
billboards; landing ripples are horizontal quads on the receiving water plane.
These are ordinary lit particles, not refractive shader-water geometry.

## Assets and preparation

- `src/main/resources/assets/daedalon/textures/particle/fountain_drop.png`
- `src/main/resources/assets/daedalon/textures/particle/fountain_ripple.png`

Original artwork was generated with the built-in image-generation tool. The
approved outputs were mechanically downsampled to 64x64 with Lanczos filtering
using `tools/prepare_fountain_particles.py`; original RGB and alpha were retained.
No Minecraft or third-party texture was copied or repainted. The checked-in PNGs
are the final source assets; generating art is not part of the build.

## Final prompt set

### Drop

Use case: stylized-concept. Asset type: single production game particle sprite for Minecraft fountain water, destined to be downscaled to 64x64. Generate a square RGBA image with a genuinely transparent background. One small round slightly vertically oval water bead centered exactly in the canvas, occupying about half the canvas width and height, generous empty transparent margins. Neutral white and very pale grey ONLY, no blue, no black contour, no colored pixels. Soft clean antialiased silhouette, subtle white highlight and translucent body conveyed with alpha, readable when tiny. This is an isolated tintable albedo sprite, NOT a photograph of water, no environment, no ground, no shadow, no checkerboard, no text, no border, no additional droplets, no refraction scene. Keep the complete canvas transparent outside the bead.

### Ripple

Use case: stylized-concept. Asset type: single production top-down circular water impact ripple game particle, destined to be downscaled to 64x64. Square RGBA with a genuinely transparent background. One perfectly centered circular thin soft-edged white water ripple ring viewed directly from above, diameter about 75 percent of canvas width; equal circular dimensions, no perspective or ellipse. Neutral white ONLY, no blue, no dark edges. Transparent hole in the middle, softly feathered alpha across the ring and fully transparent generous outer margins. A delicate slightly irregular watery ripple, not a thick doughnut, flat albedo mask intended to be tinted by the game. No surface or environment, no ground, no reflections of a scene, no shadow, no checkerboard, no text or border, no extra separate drops.
