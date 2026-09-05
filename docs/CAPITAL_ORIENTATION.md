# Capital orientation

All six Daedalon capital styles expose `capital_orientation` through the debug
stick. The names match ERYDON's column ends: Straight (`straight`, the existing
appearance) and Diagonal (`diagonal`, 45 degrees clockwise around Y).

Greek Ionic additionally has Straight (90 degrees, `straight_90`) and Diagonal
(135 degrees, `diagonal_135`) because its volutes distinguish the two horizontal
axes. Right-click cycles clockwise in 45-degree steps; sneaking reverses the
cycle. No placement-facing rule changes. Existing saves without the property
load as Straight.

Five styles have two states per material and Ionic has four: 756 states over
the existing 324 block IDs. Their unconditional blockstate model mappings are
unchanged. Every orientation reuses its existing baked model and OBJ; four
immutable transforms rotate positions and vertex normals during chunk meshing.
Straight takes the existing path without an additional transform. UVs, shader
tags, geometry detail and flat item icons are unchanged. No block entities,
tickers, scans, additional IDs or rotated mesh assets are introduced.

Selection and collision use small, precomputed bounding boxes that contain
the rotated model. Rotated quads remain unculled against neighbouring blocks.
Structure rotations and mirrors preserve Ionic's axis choices and round trip.
The one-block height and centred mounting point remain fixed.

`CapitalGameTests` exercises real debug-stick forward/reverse cycles, legacy
and current save serialization, state counts, cached shapes, rotations and
mirrors for every style and finish. `CapitalOrientationTransformTest` verifies
the three nonzero turns, normals, culling and preservation of unrelated vertex
attributes. The isolated client fixture also checks model sharing and emitted
geometry for all six styles and every orientation with shaders enabled.
