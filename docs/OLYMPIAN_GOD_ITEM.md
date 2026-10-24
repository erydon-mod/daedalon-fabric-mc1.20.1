# Olympian God placement item

Each standard or aged stone, Bronze and Silver has one item-only
`<material>[_aged]_olympian_god_statue` selector. A fresh stack starts with Zeus,
then places Hera, Poseidon, Demeter, Athena, Apollo, Artemis, Ares, Aphrodite,
Hephaestus, Hermes and Dionysus, before returning to Zeus. Hestia remains
available individually and is outside this twelve-statue cycle.

The selector places the existing statue blocks in its selected material. Their
normal size, pose, texture, picking, drops and editing behavior remain intact.
It registers no duplicate world block and does not replace any statue's block
item. The initial Creative image is a shared placeholder until the final image
is supplied.

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
malformed progress and Survival consumption. Pure tests pin the approved
twelve-member order and wrap behavior. Visual approval of the final Creative
image remains separate.
