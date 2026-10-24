# Daedalon 1.0.0 pre-publication audit

Date: 24 October 2026. Result: **passed**.

This audit covers Daedalon for Fabric/Minecraft 1.20.1, Java 17 and family
compatibility generation 2. The mod version remains **1.0.0**. The original
full audit checkpoint is commit `3ea55f30`; this document also records the
subsequent requested Spartan icon correction. ERYDON remains a separately
maintained project.

## Approval and final corrections

Oliver approved the pre-publication changes after the combined Daedalon and
ERYDON review using REI only, Complementary Unbound r5.9.4_dev1 and Collection
64x compat2 v1.6.0. This includes benches, finial placement, fitted capitals and
UVs, carved finishes, controls, fountain bases, finish names, the Olympian
selector, obelisk sizes, sculpture facing and the second metallic-icon pass.

The final source audit found the same rear-facing inventory view on the three
north-authored full statues: Hermes, Hephaestus and Hestia. One shared display
turned 180 degrees around Y now supplies all 168 finish variants. Actual OBJ
comparison renders confirm their fronts; placed orientation, collision,
source geometry and UVs are unchanged. Hestia's bust retains its approved
separate corrected view.

Oliver subsequently requested the Spartan icon turn 180 degrees around Y.
Its shared GUI display changes from `[30,45,0]` to `[30,225,0]`, updating every
finish through the existing cache. Only GUI Y changes: the other display
contexts, published placed-state behavior, controls, source mesh and UVs retain
their existing values. The standalone build and real-mesh icon comparisons
were refreshed for this correction.

The release JAR auditor also needed the current exact catalogue: four internal
helpers, 56 item-only Olympian selectors, two shared selector item parents and
the item-only medallion mesh. Its inventories remain strict; additional tests
reject missing selector parents and invalid helper/selector block or loot
resources. Duplicate archive entries and development/probe classes are rejected.

## Verification

| Check | Result |
| --- | --- |
| Standalone `gradlew build`, including final JAR audit | Passed |
| Java tests | 192 passed, no failures, errors or skips |
| Source/model/resource tests | 200 passed |
| Branding tests and approved-artwork audit | 8 passed; hashes, timing and references match |
| Texture-deduplication tool tests | 10 passed |
| Both resource generators with `--check` | Current; zero changes |
| Standalone dedicated-server GameTests | All 95 required tests passed |
| GameTests with the existing compatible ERYDON test companion | All 95 required tests passed |
| Actual OBJ icon comparisons, native and Collection 64x | 264 renders passed at 64/128/256 pixels, including Spartan's previous/corrected views |
| Invisible native OpenGL checks | Upload, filtering, gutters, resource release and frieze edge samples passed |
| Native break-particle and translated-name checks | Passed; own-block bursts remain limited to 16 |
| Live REI catalogue | 4,734 items, 85 design groups, 130 subsets; JEI and EMI absent |
| Publication-content scan | No credential patterns, personal home paths or unintended runtime/build files found |

The server tests cover actual mixed-in placement, editing, water, helper-cell
ownership and removal, statue cycling, saved-state rotation and the published
Spartan conventions. Tests operate on isolated build worlds, not player saves.
The companion tests consume an existing compatible snapshot and do not build
or modify ERYDON source.

Live acceptance covers the requested REI/shader/64x setup. Separate live JEI,
EMI, broader shader and Axiom-region-preview combinations are outside that
accepted matrix; optional integrations retain their guarded source checks.
Native render comparisons establish generated-picture correctness, not a new
measurement of live REI frame rate.

## Verified standalone artifact

`daedalon-fabric-mc1.20.1-compat2-1.0.0.jar`

- Size: **39,029,688 bytes**.
- SHA-256: `811ceae5a8798e8d70a1258ce4ca16790c72a77b5a3d105c1ae041c3ed3c4671`.
- Public blocks: 4,678; blockstate files: 4,682, including four internal helpers.
- Item models: 4,737; loot tables: 4,516.
- OBJ meshes and mesh definitions: 100 each; MTL files: 77; shared block display models: 34.
- Foreign packaged entries, foreign mod dependencies and nested JARs: zero.
- Exact-byte texture aliases: 140, backed by 22 content-addressed blobs.

The restricted project licence and separately credited CC BY model material
retain their respective notices. Documentation branding stays outside runtime
assets; current static/animated runtime emblems match their approved contracts.

## Reproduction

From this repository, install `requirements.txt`, then run:

```text
python -B tools/generate_daedalon_assets.py --check
python -B tools/generate_obj_display_models.py --check
./gradlew build
./gradlew runGameTest
```

`build/reports/daedalon-jar-audit.json`, the JUnit reports and
`build/reports/gametest/fountains.xml` contain the local machine-readable results.
The checked-in Verify workflow runs the source checks on Java 17/Python 3.13
for pushed commits. The Google Sheet's `CL Daedalon` tab remains the player
changelog and is kept in Draft until release.
