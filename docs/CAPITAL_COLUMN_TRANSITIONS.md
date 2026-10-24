# Capital and column transitions

Daedalon capitals retain their original carving, height, orientation and standard/double size options. A shallow collar now fills the transition from an ERYDON square or Gothic column to the capital's circular lower rim. The collar occupies the bottom 2/16 of the existing capital height; it does not lift or stretch the capital.

The square profile starts at a full block-wide square, matching the square column capital's 16-pixel top plate. The Gothic profile forms a small 14/16-block square necking plate over the clustered head, matching its outer extents while deliberately overhanging the gaps between the lobes. Both taper to the existing 11.5/16 circular capital join. The collar stays aligned with the column while the carving rotates above it. A double capital uses the same profile at twice the scale when all four support cells contain the same square or Gothic column block.

The source measurements were checked against the active ERYDON 1.20.1 repository: `models/block/column/square/column_square_capital_shared.json`, `authoring_models/block/column/gothic/column_gothic_capital.json`, and `ColumnBlock`'s Gothic interaction footprint. The six fitted capital OBJ definitions keep a lower rim around 0.72 to 0.75 block wide within the bottom 1/8 of their height. No OBJ geometry or per-material mesh has been duplicated or changed.

## Runtime behaviour

The automatic `capital_support` state is hidden from Daedalon's debug-stick controls. Size and Orientation remain the player controls. Known ERYDON block types are classified once and cached; placement and local support changes read one support cell, or four for a double capital. There is no tick, block entity, world scan or render/light-thread support lookup. The shared collar mesh has four cached combinations: square/Gothic at standard/double size. Shapes are cached and shared across every material variant.

New item, command and structure placements fit automatically. Support replacement and double-capital helper cells refresh the same local state. Existing saved capitals without `capital_support` retain their previous appearance until a neighbouring support changes, the capital is edited or it is placed again. No world-load migration or chunk scanning is required.

Circular columns keep their existing circular join. The previous automatic alignment to a two-by-two ERYDON circular column remains unchanged. A double capital over an incomplete or mixed four-cell square/Gothic support does not invent a larger support collar.

## Validation

`CapitalTransitionMeshTest` checks the bottom footprint, circular join, centre, height and scaling of both collar profiles. `CapitalGameTests` covers the six styles, 54 material finishes, both sizes, serialization, rotation, hidden controls, every double support cell, removal/restoration and unchanged circular-column handling. The material phase, texture scale, OBJ material tag and shader terrain-normal path follow the existing fitted finial base pipeline.

These source and geometry checks do not establish visual acceptance. The final in-game check should inspect square and Gothic columns with all six capitals, including diagonal orientations and both sizes, under native materials and the active Collection pack/shader combination. Pay particular attention to the collar/rim intersection on the irregular carved bases and to the outer Gothic lobes.
