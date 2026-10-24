# Capital and column transitions

Daedalon capitals retain their original carving, height, orientation and standard/double size options. A shallow collar fills the transition from an ERYDON square or Gothic column to the capital's circular lower rim. The collar occupies the bottom 2/16 of the existing capital height; it does not lift or stretch the capital.

The original `square` and `gothic` support values retain their existing full-head profiles. The appended `square_shaft` and `gothic_shaft` values fit the actual bare shafts, including Square's fluting and Gothic's lobes. They are selected when ERYDON's capital is set to None, or when the support is a bare pillar/base rather than a capital part. The existing Gothic Byzantine option remains a placeholder using ERYDON's current Gothic head.

The shaft outlines come from the actual UP-faced polygons after their local Y rotations in `models/block/column/square/column_square_pillar_shared.json` and `authoring_models/block/column/gothic/column_gothic_pillar.json`. The Square shaft spans 11 pixels; Gothic spans approximately 11.8154 by 11.8496 pixels. These measurements deliberately do not use the broader collision approximations. Both collars meet the established 11.5/16 circular join above.

A Large capital requires all four supporting cells to agree on both block and head/shaft profile. Its bare-shaft base joins the four real shaft tops with a shallow solid bridge, retaining the exterior flutes. This is wider than doubling one isolated shaft: Square spans 27 pixels across four cells; Gothic spans approximately 27.8154 by 27.8496. Its upper circle and height still follow the existing Large capital.

The shared profiles are bundled in `assets/daedalon/geometry/capital_shaft_profiles.json`, including source hashes and measurements. The offline `tools/prepare_capital_shaft_profiles.py` authoring tool derives and triangulates them; it requires Shapely 2.1, while the running mod does not. It simplifies only sub-0.001-pixel outline noise, covers small internal flute voids with solid necking, and checks the loft for self-intersections. No existing OBJ geometry or per-material mesh is duplicated or changed.

## Runtime behaviour

The automatic `capital_support` state is hidden from Daedalon's debug-stick controls. Size and Orientation remain the player controls. Existing state names and block IDs are preserved. Known ERYDON block types and their support properties are resolved once and cached; placement and local support changes read one support cell, or four for a double capital. There is no tick, block entity, world scan or render/light-thread support lookup. The shared collar meshes and shapes are cached per support/size across every material variant.

New item, command and structure placements fit automatically. Column side-click controls, support replacement and double-capital helper cells refresh the same local state. Existing saved capitals without `capital_support` retain their previous appearance until a neighbouring support changes, the capital is edited or it is placed again. No world-load migration or chunk scanning is required.

Circular columns keep their existing circular join and automatic alignment to a two-by-two ERYDON circular column. A Large capital over incomplete or mixed four-cell square/Gothic support does not invent a larger support collar.

## Collar textures

The collar uses face-handed, world-aligned UVs matching the native column tile phase, with one material tile per block. V runs upwards correctly on side faces, and North/East faces use Minecraft's matching handedness. The existing capital carving keeps its original OBJ texture projection. The collar uses phase-zero material baking after its own cached world projection, so the carving's separate texture-window phase is not applied a second time.

Large collar faces are split once at cell boundaries before caching. Each cell then projects into its own tile window; a triangle never wraps individual UV vertices across the six-tile atlas edge. Positions and terrain normals remain unchanged during UV projection. All finishes continue through the shared OBJ material and shader-normal pipeline. The separate proposed frieze atlas guard is not involved.

## Validation

`CapitalTransitionMeshTest` checks the authored base outlines, watertight underside triangulation, circular joins, height and cell splitting. `CapitalTransitionUvTransformTest` checks handedness, one-tile density, negative coordinates, joins to the column below, and Large texture-period boundaries. `CapitalGameTests` covers 2,592 combinations across six styles, 54 material finishes, both sizes and all four head/shaft profiles, plus serialization, rotation and hidden controls. With ERYDON installed, additional tests invoke the real column side-click controls and verify that changing any of four Large supports updates the existing capital without re-placement.

These source and geometry checks do not establish visual acceptance. The final in-game check should inspect square and Gothic columns with all six capitals, including diagonal orientations and both sizes, under native materials and the active Collection pack/shader combination. Pay particular attention to the collar/rim intersection on the irregular carved bases and to the outer Gothic lobes.
