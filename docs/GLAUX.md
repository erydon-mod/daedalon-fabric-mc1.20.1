# Glaux, the Collection oracle

Glaux is one bronze ornament, two blocks (2 m) high, registered as
`daedalon:glaux`. There are no size or finish variants. Oliver supplied the
original Meshy owl and confirmed public distribution; the source hash and
measured rig are recorded in `evidence/glaux-source.json`.

## Speaking to Glaux

Look at a nearby owl to reveal a small hint. Right-click or press the remappable
**Speak to Glaux** key (G by default) to focus the floating conversation. The
world stays visible and mouse-look remains available. Enter submits a question;
numbers or ordinary text answer clarification choices. Escape leaves the
conversation. Looking away also releases typing after a short grace period,
unless gaze-free typing is enabled. Held keys and mouse buttons stay suppressed
until released, preventing a typed W or Space from becoming movement.

Ctrl+H shows recent conversation; Page Up / Page Down scroll it. Ctrl+P toggles
gaze-free typing and Ctrl+M toggles reduced motion. Both preferences also have
remappable controls. Conversations and stillness are client-session state,
scoped to the current world; they are not written to the block or server.
Sneak-use and the debug stick keep their building/tool actions; the explicit
Speak key is still available. The debug stick offers Facing only and edits
either half as the same complete owl.

Ctrl+Enter opens a separate review of the unresolved enquiry. The player may
edit it, copy it or explicitly open the website. The link carries a bounded
question and labelled prior exchange in its URL fragment. Neither arriving on
the website nor opening the review sends an AI request or activates a microphone.
The website receiver was published as Collection site version 46 on 18 November
2026. It restores an editable question and labelled Minecraft context without
submitting it: see the versioned sender fixtures in
`src/test/resources/glaux/handoff` and `GLAUX_LOCAL_BRAIN.md`.

## Rendering and movement

The fixed body is split at the block boundary and uses the shared OBJ parser,
material/texture pipeline and chunk-baked Fabric meshes. Only the rigid head
and overlapping feather leaves use a block-entity renderer. The beak belongs
to the head; it is never stretched across the neck joint. A data-driven recipe
supplies 29 moving feather hinges above 27 fixed under-feathers. The generated leaves have closed, outward-facing
shells at both moving LODs; projection/normal seams share their geometric
positions so they cannot tear open as the head turns. Creative/REI use the
shared cached silhouette icon.

All six mesh variants share one authored 2 m rest-space UV projection, including
interpolated vertices at the lower/upper body seam. Their hashes, counts and
bounds are pinned in the shared asset audit. Bronze reuses the native Daedalon
bronze texture/specular map; the inspected Collection 32x and 64x packs do not
replace it or apply a bronze CTM rule. Stone Synapheia rules are not applicable
to this finish. Shader appearance remains a separate visual check.

Nearby attention is limited to three blocks from the head pivot and is client-only,
with restrained acceleration and shortest-path
head rotation. Occlusion is checked at bounded intervals; there is no server
ticker, world scan or animation packet. Still/reduced-motion settings gate the
gestures. Unquoted profanity or rude directed banter may trigger a brief head/neck-feather shiver with
a cooldown; the body remains chunk-baked. Mid/far head meshes, cached poses and
frustum culling reduce work when Glaux occupies fewer screen pixels. Head meshes
and the body survive resource reloads through their normal resource lifecycle.

Collision and selection use a measured layered silhouette, cached in all four
facings, instead of two oversized boxes. Its head envelope allows every yaw
without changing physics as the client animates. Regenerate it after authoring
changes with `python tools/generate_glaux_shape.py`.

The entry field uses flat dark text, a clear caret and a clipped selection on
the pale background; vanilla text editing and shortcuts are retained.
Language inference and model loading run on one daemon worker, outside rendering
and client ticks. HUD line layout is cached by reply, GUI width and font-resource
generation. See `GLAUX_LOCAL_BRAIN.md` for model provenance, byte budget and the
limits of local question recognition. A file-size budget is not a JVM heap or
frame-rate measurement.

## Verification

Run the normal `build` task for Java tests, generated assets, shared mesh safety
tests and the standalone JAR audit. Dedicated server regressions can be run with:

```powershell
.\gradlew.bat runGameTest -PdaedalonGlauxGameTests=true --offline
```

The opt-in `-PdaedalonGlauxChecks=true` client profile is for an owned development
fixture only:

```powershell
.\gradlew.bat runClient -PdaedalonGlauxChecks=true -PdaedalonClientDirectory=build/glaux-client --offline
```

Read `passed` in `build/reports/glaux-client/glaux-native-report.json`; a normal
client shutdown alone does not establish that the checks passed.
Native input injection is not proof of physical keyboard delivery;
headless model assertions are not shader or visual approval. Keep the resulting
reports and any remaining live verification limits with the release review.
