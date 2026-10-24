# Missing Olympian model references

Prepared 24 October 2026 after checking the current Daedalon registrations.
These are authoring references; the four missing models have not been imported
or registered by this task.

| Missing model | Reference image | Status |
| --- | --- | --- |
| Hermes statue | [hermes_statue_front.png](hermes_statue_front.png) | Existing statue reference, reused unchanged |
| Hephaestus statue | [hephaestus_statue_front.png](hephaestus_statue_front.png) | Existing statue reference, reused unchanged |
| Hestia statue | [hestia_statue_front.png](hestia_statue_front.png) | Existing statue reference, reused unchanged |
| Hestia bust | [hestia_bust.png](hestia_bust.png) | New image generated with the built-in image-generation tool |

## Existing statue sources

The three statues already have OBJ and Blender source assets in Oliver's
external Daedalon Working Files tree. Check these before regenerating them in
Meshy:

- `statues/Olymians/Hermes/hermes 15k.obj`
- `statues/Olymians/Hephaestus/Hephaestus 15k.obj`
- `statues/Olymians/Hestia/Hestia 15k.obj`

Each source directory also contains the original Meshy output and front, back,
left and right reference PNGs. The three statue PNGs collected here are exact
copies of their original front images. Their existing 384 x 1024 resolution is
preserved; no image enlargement or reconstruction was performed.

## Hestia bust design

The new bust uses the existing Hestia statue as its face, hair and veil reference,
and the existing Hera and Demeter busts for the stone finish, shoulder proportions,
drapery and flat truncated base. It has no separate pedestal or loose props.
The small clasp carries a carved hearth-flame motif.

Use `hestia_bust.png` as the single-object input for Meshy's Image to 3D workflow.
The generated image is a concept reference, not an existing 3D mesh or in-game
validation. Follow [ADDING_OBJ_MODELS.md](../../ADDING_OBJ_MODELS.md) for a later
model import, with one pilot model before a wider batch.

Meshy's official [Image to 3D guidance](https://docs.meshy.ai/en/webapp/image-to-3d)
supports a clear complete subject, plain background and even lighting. Supply each
reference separately; do not use a montage as a modelling input.

## Hestia bust generation prompt

Input images, in order: Hestia's original statue front; `bust_hera.png`;
`bust_demeter.png`. Generated using the built-in tool, with an opaque grey studio
background.

```text
Use case: style-transfer.
Asset type: one separate reference image for Meshy image-to-3D, completing Oliver's Daedalon classical bust collection.
Primary request: Derive a new HESTIA BUST from the Hestia full-length statue in reference image 1. Preserve Hestia's recognisable facial identity, serene expression, centre-parted softly waved hair and modest head veil from that statue. Reference images 2 and 3 are STYLE AND BUST-SILHOUETTE references only, not replacement faces.
Input images: 1 Hestia statue = character identity and veil; 2 Hera bust = stone carving finish, full shoulder/upper-chest format and studio presentation; 3 Demeter bust = matching bust proportions, broad shoulder cut and flat lower edge.
Subject: a mature dignified classical Greek woman with a calm gentle face, softly carved eyes, closed lips and a close head veil over waved hair; the veil flows behind and over both shoulders. Layered modest chiton and himation folds across the upper chest. A small simple round clasp with an understated carved hearth-flame motif may fasten the garment. The bust includes the entire head, neck, both complete shoulders, upper chest and a flat self-supporting truncated bottom, as in the existing busts.
Style/medium: convincing photographed classical marble sculpture, idealised natural human proportions, finely modelled carved face and hair, continuous solid sculptural forms, deep readable drapery, the same warm ivory stone and faint mottled patina as the Hera and Demeter bust references. All details are carved from the SAME stone.
Composition: single isolated complete bust centred, very gentle three-quarter frontal angle approximately 10 degrees, nearly facing camera, eye-level portrait product photography with minimal perspective. The entire bust and its flat bottom are visible with comfortable clear margins. Portrait canvas at least 1024 pixels wide.
Scene/backdrop: plain light neutral grey studio background and very subtle contact shadow.
Lighting: soft even neutral studio illumination, details visible in both sides of the face and veil.
Constraints: one sculpture only, no separate pedestal or socle, no hands, no bowl, no full body, no tools or loose props. No crown, diadem, wheat wreath or Hera/Demeter identifiers. No floating objects, no actual fire, no coloured inlays, no heavy marble veins, no dramatic shadows. No collage, no additional views, no labels, no text, no frame, no watermark.
Keep her Hestia identity and the original collection's restrained classical carved aesthetic.
```
