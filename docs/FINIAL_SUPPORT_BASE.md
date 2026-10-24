# Fitted finial bases

The five original finial OBJ meshes are unchanged. A finial placed on Georgian
coping reads the saved coping surface, facing and owner offset. Its square
support stub follows that plane, while the ornament remains upright.

On full-width pitched coping, the finial stays over the centre of the owning
block rather than following the cap's downhill extrusion. Its base is evaluated
on the same authored top plane at that centre, so it rises with the slope rather
than sinking into it. Both Georgian coping designs and both finial sizes use
this anchor. Flat and diagonal-wall fits retain their established centres.

Steep half-width coping retains the centre of its finite top deck. In particular,
Grand steep coping shifts that deck far enough that forcing the ornament over
the supporting half-cell would leave some small pedestals beyond the real top
face. Upper-half owner offsets remain intact.

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

Both Georgian and Grand Georgian coping retain their authored thickness.
Their existing support fitting and collision clearance are verified against
the actual coping collision geometry, including inclined and half-height fits.
Occupied neighbouring cells still block an intersecting ornament.

A genuine coping overhang hit may lie farther from its owning block centre
than vanilla's packet allowance. While the packet's hand holds a finial,
Daedalon validates that hit against the coping's actual outline before
normalising only that owner-distance check. Ordinary player reach, build height,
world permissions, other held items and off-outline hits keep their existing
checks. Main-hand and off-hand placement use the same path.

Small and Large retain their published state names and the existing one-owner
architecture. Geometry and interaction shapes share cached mounting data;
Axiom preview bounds use those same saved shapes. An unsupported finial keeps
its fitted state rather than breaking.

The existing Axiom billboard remains the item's simplified icon; its position
and extent follow the fitted state, while the actual placed mesh and outline
include the square stub. AIR overhangs and bases below the owner remain selectable
through a sparse occupied-cell index, updated on both client and server state
changes and rebuilt only for chunks whose palette contains finials.
The shared Daedalon break-particle hook emits at most 16 debris particles for the
whole outline, including fitted bases. Detailed selection and collision remain
available without multiplying debris by the number of outline boxes.

The base follows the pedestal footprint. A Large Sphaira pedestal is wider than
one block, so a base on a half-width steep piece can overhang that individual
coping. Placement rejects an overhang if it intersects an occupied neighbouring
cell; it does not replace that cell or silently shrink the ornament.

The standalone GameTests use the real coping collision sampling rather than
an empty support shape. An optional ERYDON runtime additionally checks 1,632
first-use placements against the installed CopingBlock's actual outline and
all saved surfaces, facings, valid steep offsets and both finial sizes. It emits
`DAEDALON_ERYDON_FINIAL_INTEGRATION` with its result; set
`-Ddaedalon.test.requireErydon=true` to reject an accidentally standalone run.
The matrix also checks the horizontal anchor and every fitted base corner
against ERYDON's actual attachment plane. A separate centre regression covers
all saved profiles, five styles, both sizes and all four facings, including the
unchanged finite half-deck and diagonal-wall anchors.
The packet regression also covers valid overhangs in both hands and rejects
off-outline packets and unrelated items. These server checks do not replace
in-game visual and interaction review.
