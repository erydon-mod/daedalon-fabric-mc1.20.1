# Bronze and silver decorations

All 62 urn, statue, bust, finial, corbel and plinth families offer both bronze
and silver, including the Hermes, Hephaestus and Hestia statues and Hestia bust.
The public catalogue contains 4,663 blocks and items.
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

ERYDON's shared metal sprite lookup already accepts the `daedalon` namespace.
When ERYDON and a supported Complementary profile are installed, both metals
receive the shared alloy lighting and reflection treatment. The current
Collection 32x and 64x sources provide no overrides for these four sprites,
so they fall back to the native metal resources without pack edits.

## Verification

Run `python tools/generate_daedalon_assets.py --families all --check` and
`./gradlew --no-daemon check runGameTest` without packaging a JAR. Asset tests
check every requested finish, state schema, language, self-drop, search tag and
the exact copied texture bytes. Real Fabric server tests cover the 116 metal
decorations, ordinary placement controls, waterlogged plinth states and rejection
of metal fountain attachments.

Client visual acceptance remains separate: inspect native textures and both
Collection packs, bronze/silver shading with ERYDON and supported Complementary,
inventory and Axiom previews, all sizes/facings, finial bases, and Diota handles
after a full restart. Server/source checks do not certify those appearances.
