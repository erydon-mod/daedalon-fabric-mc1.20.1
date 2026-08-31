package com.oliver.daedalon.client.model.obj;

/**
 * Maps an OBJ's established texture window into a horizontally repeated material sheet.
 * Placed blocks choose one of the six ERYDON repeat columns from their world position;
 * item and GUI rendering use phase zero.
 */
final class WorldTexturePhase {
    static final int REPEAT_PERIOD = 6;
    static final int SHEET_COLUMNS = REPEAT_PERIOD * 2;
    static final int SHEET_ROWS = REPEAT_PERIOD;

    private WorldTexturePhase() {
    }

    static Layout detailedSurface() {
        return Layout.create(REPEAT_PERIOD, REPEAT_PERIOD);
    }

    static Layout urnSurface() {
        return Layout.create(4, 1);
    }

    static Layout blockSurface() {
        return Layout.create(1, 1);
    }

    record Layout(int sourceColumns, int sourceRows, int phaseCount) {
        private static Layout create(int sourceColumns, int sourceRows) {
            int availableHorizontalOrigins = SHEET_COLUMNS - sourceColumns + 1;
            return new Layout(
                    sourceColumns,
                    sourceRows,
                    Math.min(REPEAT_PERIOD, availableHorizontalOrigins)
            );
        }

        Layout {
            if (sourceColumns <= 0 || sourceColumns > SHEET_COLUMNS) {
                throw new IllegalArgumentException("OBJ source texture columns must fit the repeat sheet");
            }
            if (sourceRows <= 0 || sourceRows > SHEET_ROWS) {
                throw new IllegalArgumentException("OBJ source texture rows must fit the repeat sheet");
            }
            if (phaseCount <= 0
                    || phaseCount > REPEAT_PERIOD
                    || sourceColumns + phaseCount - 1 > SHEET_COLUMNS) {
                throw new IllegalArgumentException("OBJ world texture phases must fit the repeat sheet");
            }
        }

        int index(int x, int y, int z) {
            // Each one-block move advances the repeat origin by one tile. The
            // six-column period then matches ERYDON's method=repeat sheets.
            return Math.floorMod(x - y + z, phaseCount);
        }

        float mapU(float u, int phaseIndex) {
            if (phaseIndex < 0 || phaseIndex >= phaseCount) {
                throw new IllegalArgumentException("World texture phase index is outside the layout");
            }
            return u * uScale() + phaseIndex * uStep();
        }

        float mapV(float v) {
            return v * vScale();
        }

        float uScale() {
            return sourceColumns / (float) SHEET_COLUMNS;
        }

        float vScale() {
            return sourceRows / (float) SHEET_ROWS;
        }

        float uStep() {
            return 1.0F / SHEET_COLUMNS;
        }

        float maximumUOffset() {
            return (phaseCount - 1) * uStep();
        }
    }
}
