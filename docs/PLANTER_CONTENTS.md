# Anthophoros planter contents

Anthophoros keeps its 55 public block IDs, 2m/3m/4m width meshes, placement/facing,
material finishes, and cached hollow collision/selection shapes. Existing placements
load as Empty. One `contents` state selects Empty, Earth or Water; there are no
controller entities, periodic ticks, per-material geometry or neighbour helpers.

Use dirt, coarse dirt or rooted dirt on an empty planter to add earth. A shovel
clears earth; in survival the input costs one item, clearing returns one ordinary
dirt and costs one shovel durability. Creative edits consume/drop nothing.
A water bucket fills an empty planter, and an empty bucket recovers the water.
Earth and water cannot replace one another. The debug stick exposes Contents
after Width and Facing, cycling Empty → Earth → Water (reverse while sneaking).
All five current languages have translated control names.

The actual OBJ section at Y=0.900m sets a fill plane 10cm below the rim. Shared
profiles are in `AnthophorosFillProfile`, with measured hashes, wall intersections,
UV aspects and the pilot in [the fill evidence](evidence/anthophoros-fill.json).
Edges overlap the hidden inner wall by at most 2.1mm to close seams and remain
inside the external rim. Shell meshes and their hashes are unchanged.

Earth uses 3/3/5 tiny opaque strips for the 2m/3m/4m widths, cached once per facing
and shared across all finishes. Each width has its own literal 64×64 seamless
tile, repeated every metre. Geometry splits at each repeat boundary, keeping
every strip's UVs inside 0..1. The soil bypasses stone projection and world phase.
Water uses the fountain renderer's animated `minecraft:block/water_still`,
translucent material, biome tint and contained-water shader identity, fitted to the
same rectangle. Water is contained rather than an escaping world fluid. Inventory
icons remain the existing empty planter; contents belong to placed blocks.

## Original earth art

Built-in ImageGen created all three original earth albedos; no Minecraft or
third-party soil image was copied. The final PNGs are the source art. Generating
art is not part of the build. `tools/prepare_planter_earth.py` performs mechanical
square framing/BOX downsampling and deterministic shader-companion export.
The dark peat mixes cool brown-black, charcoal humus, muted brown organic crumbs
and desaturated decomposed plant fibres as intrinsic material colours. Bright
clay/mineral flecks and the warm orange/red cast are removed. Colour is unlit; relief shading
belongs to the normal/height maps and shader. Sparse small weeds remain surface
details in the colour tile.
There is no additional plant geometry or growth behaviour. **Every production
albedo, normal/height and roughness PNG is exactly 64×64 pixels**, with no padded
canvas. Tiles repeat at 64 texels per metre over the measured rectangular cavity;
the depth crops V to .08..92. These square dimensions retain four atlas mip levels.

| Width | Albedo | Albedo / normal-height / roughness pixels |
| --- | --- | --- |
| 2m pilot | `textures/block/anthophoros_earth_2m.png` | 64×64 each |
| 3m | `textures/block/anthophoros_earth_3m.png` | 64×64 each |
| 4m | `textures/block/anthophoros_earth_4m.png` | 64×64 each |

Each has `_n.png` and `_s.png` companions. The normal/height is a conservative
artistic approximation derived from the exported 64×64 colour tile, not a captured
mesh. Height detail is contrast-stretched and quantized without Gaussian blur.
Normal derivatives wrap at the repeat edges. DirectX RG slope is capped at .45
per axis, blue255 is unoccluded, and alpha237..255 retains rough organic detail
with lower relief than the earlier rocky iteration. The height field is unblurred.
Matte `_s` is (0,10,64,255): maximum roughness, dielectric, porous and nonemissive.
This follows the [LabPBR material standard](https://shaderlabs.org/wiki/LabPBR_Material_Standard).
Shaders supporting normal/height may show subtle relief; vanilla uses the albedo.
There is no displaced geometry or new shader system.

## 3D material preview

`tools/render_planter_earth_preview.py` reads the unchanged runtime OBJ and the
final colour/height PNGs. The full planter and matched flat/height close-ups are
in [the full preview](previews/planter-earth-64x.png) and
[the height comparison](previews/planter-earth-64x-height.png). The shell uses a
neutral stone preview finish; soil repeats its actual 64×64 colour with nearest
sampling. The render is a larger presentation image; the texture files stay
64×64. No high-resolution source art or extra noise is used in the render.

For this offline preview only, alpha displaces vertices by
`(alpha / 255 - 1) * 0.25 metres`, giving at most 17.65mm depth for alpha237..255,
about 42% shallower than the previous 30.39mm iteration. The original height
samples remain unblurred. Interpolated lighting normals avoid displaying the
preview's triangulation as artificial stone facets, matching the continuous
lighting of the production normal-mapped surface more closely. The flat
comparison uses the same camera, light
and colour map, with displacement disabled. Shader packs choose their own
height/parallax scale, so this explicit reference is not an in-game guarantee.

## Offline verification and remaining visual checks

The 2m measured pilot and texture companion checks passed before applying the
same path to 3m/4m. Geometry tests intersect the actual mesh triangles and probe
132 fill-boundary positions per width, including corners. Tests also cover
all facings, bounded UVs, upward normals/winding, mutually exclusive contents,
repeat phase, complete strip coverage, unchanged collision, debug order and translations. GameTest source is compiled
but Minecraft/GameTests are not launched for this task.

In-game appearance, water animation/refraction, shader relief, item-to-world
interaction targeting and resource reload remain unverified. Check those later
with native resources and the active Collection pack. This task changes no
Collection pack, live install or options.
Atlas filtering at exact repeat edges also needs that later visual check; no
UV inset is applied because it would skip colour/height texels at every join.

## Final ImageGen prompts

### 2m tile

Use case: precise-object-edit
Asset type: one seamless square PEAT SOIL albedo tile for Minecraft, exactly64x64 logical pixels, variant2. Image1 is the existing soil tile to revise; the final exported PNG remains literally64x64 pixels.
Keep: the seamless repeat, coarse pixel scale, natural local colour variation, sparse tiny weed detail and flat unlit material colour.
Change: make this distinctly DARK PEAT—cool deep brown-black organic earth, charcoal humus, matted fine decomposed plant fibres and small crumbly organic flecks. It should feel fertile peat, not rocky gravel, clay, stones or warm reddish mud. Use mostly small1-3pixel broken organic clusters with some irregular larger mottles, no big rock shapes.
Palette anchors: charcoal organic matter#151412 (~40%), deep cool peat-brown#27251f (~35%), muted dark brown organic crumbs#383329 (~15%), a few readable desaturated plant-fibre flecks#4a4335 (~6%), tiny muted olive organic bits#263521. Preserve visible colour variety within this dark restrained palette; avoid featureless black. Retain a single tiny low leaf-green seedling#314923 about4pixels across (<2%area), with irregular placement toward one side.
Remove: orange, pink, red-clay, yellow mineral, blue-grey stone colours. No orange or pinkish overall cast.
CRITICAL: pure FLAT UNLIT diffuse/basecolor. Do not paint lighting, glossy highlights, directional shadows, shaded cracks, lit slopes, ambient occlusion, vignette or gradients. Actual soil composition creates the colour changes; the separate normal/height map and shader supply relief lighting.
Only the square seamless colour tile, no pot, rim, perspective, scene, labels, frame, panels, grid lines or height/normal panels. Fine crumbly fibrous PEAT, clearly dark and organic at64x64.

### 3m tile

Use case: precise-object-edit
Asset type: one seamless square PEAT SOIL albedo tile for Minecraft, exactly64x64 logical pixels, variant3. Image1 is the existing soil tile to revise; the final exported PNG remains literally64x64 pixels.
Keep: the seamless repeat, coarse pixel scale, natural local colour variation, sparse tiny weed detail and flat unlit material colour.
Change: make this distinctly DARK PEAT—cool deep brown-black organic earth, charcoal humus, matted fine decomposed plant fibres and small crumbly organic flecks. It should feel fertile peat, not rocky gravel, clay, stones or warm reddish mud. Use mostly small1-3pixel broken organic clusters with some irregular larger mottles, no big rock shapes.
Palette anchors: charcoal organic matter#151412 (~40%), deep cool peat-brown#27251f (~35%), muted dark brown organic crumbs#383329 (~15%), a few readable desaturated plant-fibre flecks#4a4335 (~6%), tiny muted olive organic bits#263521. Preserve visible colour variety within this dark restrained palette; avoid featureless black. Retain a single tiny low leaf-green seedling#314923 about4pixels across (<2%area), with irregular placement away from the centre.
Remove: orange, pink, red-clay, yellow mineral, blue-grey stone colours. No orange or pinkish overall cast.
CRITICAL: pure FLAT UNLIT diffuse/basecolor. Do not paint lighting, glossy highlights, directional shadows, shaded cracks, lit slopes, ambient occlusion, vignette or gradients. Actual soil composition creates the colour changes; the separate normal/height map and shader supply relief lighting.
Only the square seamless colour tile, no pot, rim, perspective, scene, labels, frame, panels, grid lines or height/normal panels. Fine crumbly fibrous PEAT, clearly dark and organic at64x64.

### 4m tile

Use case: precise-object-edit
Asset type: one seamless square PEAT SOIL albedo tile for Minecraft, exactly64x64 logical pixels, variant4. Image1 is the existing soil tile to revise; the final exported PNG remains literally64x64 pixels.
Keep: the seamless repeat, coarse pixel scale, natural local colour variation, sparse tiny weed detail and flat unlit material colour.
Change: make this distinctly DARK PEAT—cool deep brown-black organic earth, charcoal humus, matted fine decomposed plant fibres and small crumbly organic flecks. It should feel fertile peat, not rocky gravel, clay, stones or warm reddish mud. Use mostly small1-3pixel broken organic clusters with some irregular larger mottles, no big rock shapes.
Palette anchors: charcoal organic matter#151412 (~40%), deep cool peat-brown#27251f (~35%), muted dark brown organic crumbs#383329 (~15%), a few readable desaturated plant-fibre flecks#4a4335 (~6%), tiny muted olive organic bits#263521. Preserve visible colour variety within this dark restrained palette; avoid featureless black. Retain a single tiny low leaf-green seedling#314923 about4pixels across (<2%area), with irregular placement in a different small patch.
Remove: orange, pink, red-clay, yellow mineral, blue-grey stone colours. No orange or pinkish overall cast.
CRITICAL: pure FLAT UNLIT diffuse/basecolor. Do not paint lighting, glossy highlights, directional shadows, shaded cracks, lit slopes, ambient occlusion, vignette or gradients. Actual soil composition creates the colour changes; the separate normal/height map and shader supply relief lighting.
Only the square seamless colour tile, no pot, rim, perspective, scene, labels, frame, panels, grid lines or height/normal panels. Fine crumbly fibrous PEAT, clearly dark and organic at64x64.
