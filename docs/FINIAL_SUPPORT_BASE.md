# Fitted finial bases

The five original finial OBJ meshes are unchanged. A finial placed on Georgian
coping reads the saved coping surface, facing and owner offset. Its square
support stub follows that plane, while the ornament remains upright.

Edit the shared Blockbench template at
src/main/resources/assets/daedalon/authoring_models/block/finial/support_base.json.
It is one unrotated square cuboid and accepts the 1.21.11 JSON header. Its
X/Z span scales the measured pedestal width; its Y span sets the minimum
stub thickness. Keep the X and Z spans equal. The fitted lower face is generated
from the support plane and the upper face stays horizontal, so no manually
pitched finial models are required. Restart the development run after changing
the source template so rendering and collision read the same dimensions.

Placement supports flat coping, both halves of shallow and steep inclines,
the regular incline, and the saved flat diagonal-wall fits. Straight ERYDON or
Themelios slopes work without coping as a fallback. Paired ERYDON vertical
slopes also use the shared wall resolver to align a finial directly to the flat
diagonal wall, including the handed shallow wall profiles. Ordinary full blocks retain
the original freestanding finial. No ERYDON classes are required by Daedalon.

Small and Large retain their published state names and the existing one-owner
architecture. Geometry and interaction shapes share cached mounting data;
Axiom preview bounds use those same saved shapes. An unsupported finial keeps
its fitted state rather than breaking.

The existing Axiom billboard remains the item's simplified icon; its position
and extent follow the fitted state, while the actual placed mesh and outline
include the square stub. AIR overhangs and bases below the owner remain selectable
through a sparse occupied-cell index, updated on both client and server state
changes and rebuilt only for chunks whose palette contains finials.
Fitted finials use one box for breaking debris, limiting vanilla particles to 64;
the detailed base outline remains available for selection and collision.

The base follows the pedestal footprint. A Large Sphaira pedestal is wider than
one block, so a base on a half-width steep piece can overhang that individual
coping. Placement rejects an overhang if it intersects an occupied neighbouring
cell; it does not replace that cell or silently shrink the ornament.
