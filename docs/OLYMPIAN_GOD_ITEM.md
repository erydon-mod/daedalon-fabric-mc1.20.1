# Olympian God placement item

Each standard or aged stone, Bronze and Silver has one item-only
`<material>[_aged]_olympian_god_statue` selector. A fresh stack starts with Zeus,
then places Hera, Poseidon, Demeter, Athena, Apollo, Artemis, Ares, Aphrodite,
Hephaestus, Hermes and Dionysus, before returning to Zeus. Hestia remains
available individually and is outside this twelve-statue cycle.

The selector places the existing statue blocks in its selected material. Their
normal size, pose, texture, picking, drops and editing behavior remain intact.
It registers no duplicate world block and does not replace any statue's block
item. The shared Creative image uses Oliver's supplied medallion mesh with the
selected stone or metal on its recessed background. Its raised portrait, symbols
and rims use white on darker backgrounds and charcoal-black on lighter ones;
Bronze and Silver retain this contrast too. The GUI view turns 20 degrees around
Y, and all finishes reuse one parsed mesh and projection template.

The deterministic preparation adds material assignments to the existing 9,657
triangles. Every original position, triangle, winding and authored normal stays
byte-identical after those material directives are removed. A local depth-field
estimate and connected face patches separate the raised ornament from the field.
The [material evidence](evidence/olympian-medallion-materials.json) records the
locked recipe and native/64x contrast comparison; the [preparation image](evidence/olympian-medallion-material-comparison.png)
compares white, charcoal and bronze on light and dark stone. The [source depth image](evidence/olympian-medallion-depth.png)
shows why small symbols need a local field estimate. Charcoal retains
visible facial shading, while white or charcoal wins the minimum contrast score
across both official texture sets. The component texture choice is fixed at
model preparation and adds no work to the cached one-quad drawing path. The
white and charcoal sprites are declared by the shared display model, and their
`textures/block` paths use Minecraft's normal block-atlas directory source.
The charcoal sprite is a new uniform native 16px component, with no pack edits.

Reproduce the material preparation with
`python tools/prepare_olympian_medallion_materials.py --write`, or verify its
assignment without writing using `--check`. NumPy, Pillow and SciPy are authoring
dependencies; the locked recipe and dependency versions are recorded in the
evidence. This preparation image is separate from the production native raster
and in-game shader checks.

Right-clicking a side of an existing member of the twelve-statue cycle changes
that statue to the next god. Sneaking reverses the order. The clicked statue
retains its material, ageing, size, offset and eight-way orientation, even when
the held selector uses another finish. This edits one existing block without
consuming the tool or changing its independent placement progress. Adventure
and spectator restrictions and world modification permission still apply.
Top/bottom clicks retain normal placement behaviour; busts, Hestia and other
sculptures remain outside the twelve-statue cycle.

The 35 unreleased statue and bust profiles use the measured front of their runtime mesh.
The original sources face south; the later Hermes, Hephaestus and Hestia
statues and Hestia bust were normalized to north during preparation. The shared
transform accounts for that difference, so the physical front follows the
selected eight-way facing through side-click swaps and debug-stick turns.
Fresh placement points these corrected sculptures back towards the player;
it uses the opposite of the player's horizontal look direction and retains
the existing default size, straight pose and disabled offset. Explicit
`BlockStateTag` facing still overrides normal placement as before.
This pre-publication correction also aligns Heracles, the other heroes, lions
and older busts. Existing saved facing values, sizes and
offset settings stay intact; previously mislabelled models rotate to match
that saved facing. Mesh positions, normals and UVs are unchanged. See
[the source-frame audit](evidence/statue-source-facing.json).

Published Spartan Promachos retains its existing EAST profile convention and
world orientation in every saved pose. Its actual mesh front faces south,
so its physical front keeps the existing 90-degree clockwise offset from the
saved facing label. Its geometry, collision, offsets and public rotation helper
are unchanged, and fresh Spartan placement retains the player's original look
direction as its saved facing. This correction does not migrate published
Spartan worlds.

Progress is stored on the held stack as `DaedalonNextOlympian`. Stacks, hands
and players progress independently, and copying or saving a stack preserves
its current position. Only a successful server placement advances it. An
occupied or otherwise rejected placement leaves its progress and count intact.
Creative keeps the selector count; normal Survival placement consumes one unit.
The tooltip shows the next statue using that statue's normal localized name.

The item caches its twelve real `BlockItem` targets during registration. On
use it delegates through normal block-item placement with a temporary actual
statue stack, carrying only `BlockStateTag`. This retains ordinary placement
validation, sound, advancement hooks and Daedalon's tall-decoration placement
behavior without sending selector data into the placed statue. A successful
server update explicitly synchronizes inventory state, including creative and
offhand cursor changes. There is no ticking, placed block entity, runtime
registry scan or new statue geometry.

The focused GameTests cover two complete sets in all 56 finishes, original
block-item identity, save/reload, independent players and hands, creative
inventory synchronization, blocked placement, normal placement properties,
malformed progress and Survival consumption. Side-edit tests cover another
10,752 forward/reverse swaps across all finishes and eight directions, physical
front and pose retention, independent held progress, permission checks and
non-consuming Survival editing. Debug-stick tests also check the physical front
of all 36 shared statue/bust profiles in all eight directions, with the
published Spartan's existing offset retained. A frozen collision fixture checks
all 2,688 Spartan material/pose combinations against the legacy boxes.
Actual item-placement tests also place all 35 corrected profiles from four
player headings, checking the measured physical front points towards the player,
and retain the published Spartan convention for the other four placements.
Pure tests pin the approved twelve-member order and wrap behavior. Visual approval of the
new contrasting medallion remains separate; the sharper catalogue icons and
their performance changes have received Oliver's approval.
