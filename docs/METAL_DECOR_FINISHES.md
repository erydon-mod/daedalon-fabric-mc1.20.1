# Bronze and silver decorations

All 62 urn, statue, bust, finial, corbel and plinth families offer both bronze
and silver, including the Hermes, Hephaestus and Hestia statues and Hestia bust.
The public catalogue contains 4,678 blocks and items.
Existing block and item IDs are retained. Metal variants have no aged finish.

`DecorMetal` supplies the two shared finishes. Registrations, generated
blockstates, item models, self-drops, all three languages and material/search
tags follow the existing family paths. Each metal variant reuses its stone
family's parsed OBJ, immutable mesh, size/facing controls and texture layout;
there are no additional geometry files or per-material geometry bakes.

The native 16x textures are exact copies of ERYDON's
`textures/block/cover_bronze_gloss.png`, `cover_silver_gloss.png` and their
`_s.png` specular maps, under the Daedalon names `bronze` and `silver`.
Their opaque colours are bronze `(229,160,29)` and silver `(206,206,206)`.
Both specular maps are `(255,255,0,255)`, preserving authored metal smoothness.
The existing shared bronze sprite now uses that canonical ERYDON colour.
Full metal variants also override named OBJ components, so a silver Diota
urn has silver handles. Stone Diota urns retain their authored bronze handles.

Metal plinths are decorative. They retain their size, facing, offset and
waterlogging controls but cannot attach to a fountain. Fountain search tags,
aliases and assembly tooltips apply only to the existing stone/aged plinths.
Stone fountain behaviour and its internal bowl models are unchanged.
Existing bronze-only monuments, the Monopteros dome and the Anthophoros planter
retain their existing finishes; other families are outside this addition.

## White, bronze and gold carved architecture

The Corinthian, Ionic, Gothic and Byzantine friezes and the Gothic panel also
offer plain white, bronze and gold finishes. These 15 additional blocks reuse
their existing shared geometry, placement controls, sizes, corner sections and
world texture phase. `ArchitecturalFinish` limits this finish set to the five
carved families; the existing 62-family `DecorMetal` bronze/silver set is unchanged.
All original stone and aged IDs remain registered. Plain finishes have no aged
variants.

Native `white.png` is an exact copy of ERYDON's 16x `cover_white_matte.png`,
with opaque white `(255,255,255)` and no metal specular map. Bronze uses the
existing shared bronze sprite. Gold is a new authored plain colour
`(255,220,96)`, distinct from bronze, with an exact copy of the existing
`bronze_s.png` metal mask under `gold_s.png`. The three finish sprites are shared
across all five carved families. No per-material OBJ, runtime detector or
Collection-pack source changes are needed. Optional packs fall back to these
native sprites unless they explicitly override them.

White is a nonmetal stone finish; bronze and gold enter the metal material tags.
Both block and item tags and all three translations include the new finishes.
ERYDON's namespace-based sprite lookup reads gold's authored metal mask and
preserves its albedo without applying the bronze or silver alloy override.
White has zero metal coverage. The exact shader reflections still require client
visual acceptance; source classification does not establish the rendered result.

ERYDON's shared metal sprite lookup already accepts the `daedalon` namespace.
When ERYDON and a supported Complementary profile are installed, both metals
receive the shared alloy lighting and reflection treatment. The current
Collection 32x and 64x sources provide no overrides for these four sprites,
so they fall back to the native metal resources without pack edits.

## Verification

Run `python tools/generate_daedalon_assets.py --families all --check` and
`./gradlew --no-daemon check runGameTest` without packaging a JAR. Asset tests
check every requested finish, state schema, language, self-drop, search tag and
the exact copied texture bytes. Real Fabric server tests cover the 124 metal
decorations, ordinary placement controls, waterlogged plinth states and rejection
of metal fountain attachments, plus the 15 carved finishes and their material tags.

Client visual acceptance remains separate: inspect native textures and both
Collection packs, bronze/silver shading with ERYDON and supported Complementary,
inventory and Axiom previews, all sizes/facings, finial bases, and Diota handles
after a full restart. Server/source checks do not certify those appearances.
