# Daedalon for Fabric 1.20.1

Daedalon is ERYDON's standalone collection of detailed OBJ decor for
Minecraft 1.20.1.

Version 1.0.1 uses native 16x textures. The unified ERYDON Collection 32x and
64x resource packs support Daedalon, ERYDON, and ERYDON Themelios together.
Daedalon does not require or bundle Continuity: its detailed OBJ models already
use a built-in world-aligned repeat-phase renderer.

This repository is source-available under Oliver's restricted licence. It is
not open-source software; see `LICENSE`. Third-party asset credits are retained
under `src/main/resources/META-INF/THIRD_PARTY_NOTICES.md`.

## Dome particles

For denser dome-breaking particles, run `/daedalon domeParticles high` in game
(64 particles instead of 16). Use `/daedalon domeParticles normal` to restore
the default, or `/daedalon domeParticles` to check the setting. The option is
saved for this computer and takes effect immediately. It adds no server traffic
and does not change other players' settings. Normal mode creates no extra
particles; Minecraft's particle settings and distance limits still apply.

## Development validation

Daedalon requires Java 17 and Python 3.13. Run the complete compilation, test,
and source-asset safety suite without producing a release JAR:

```text
python -m pip install -r requirements.txt
./gradlew --no-daemon check
```

For fountain and Exedra interaction regressions, run `./gradlew --no-daemon runGameTest`.
This loads the real server and mixins in an isolated `build/gametest` test world,
checks repeated debug-stick and bucket edits, and writes
`build/reports/gametest/fountains.xml`. It does not open player saves or package
a JAR; the test mod is separate from production sources.

## Exedra curved bench

Exedra is available in all 27 standard and aged stone finishes. Place it at
the default 3m width; the debug stick selects `width=2`, `width=3` or `width=4`,
then `facing`. All widths stay 1m high; depth scales with width while preserving
the original footprint proportions (about 1.07m, 1.60m and 2.13m deep). Search for Exedra,
bench, seat or furniture.

Each width uses a pre-sized Blender mesh shared across all materials, with
consistent projected texture density. This is ordinary single-block decor:
12 states, cached curved selection/collision shapes, no proxy blocks, no
block entity and no ticking. As with other oversized single-block decor,
placement and editing belong to its centre block rather than every outer cell.

Source provenance and exact measurements are recorded in
`docs/evidence/exedra-source.json`.

## Fountain particle prototype

Waterlogged fountains with at least one bowl now show a fuller upward jet and
gravity-driven drops from four rim outlets per bowl, ending in brief splashes
at the receiving water level. Changing the assembly, emptying the fountain or
unloading it retires the previous particles. Restart the development client to
test this Java change; no new block state or saved-world data is required.
At full rate each outlet emits every tick, with four drops per tick in the top
jet (8/12/16 total for one/two/three tiers). Droplets vary in size and jet height;
wider streams and size-matched splashes make the flow easier to see.
Dedicated transparent 64x64 neutral sprites keep drops and splashes colour-matched.
Circular landing ripples lie flat on the receiving water surface, independent of
camera or fountain facing; only airborne drops remain camera-facing.

This first pass uses ordinary lit droplet/ripple particles, not
refractive shader-water geometry or Physics Mod. The drops follow predetermined
vertical paths to the pools; they do not yet collide with arbitrary intervening
blocks or players, or form a continuous sheet across the rim. The stone models,
pooled water, collision and debug-stick properties remain unchanged.

The effect is client-only, limited to 32 blocks, 768 live particles globally,
320 per fountain and 48 new particles per tick. Decreased particles halves the
per-fountain/global spawn budgets and cadence; Minimal disables the prototype.
Fountain loading events supply the sources, with at most 64 source checks per
tick, instead of world/chunk scans or a server-side ticker.
