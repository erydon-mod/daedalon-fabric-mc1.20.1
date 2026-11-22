package com.oliver.daedalon.block;

/** Measured cavity section at 0.90m; shared by earth and contained water. */
public record AnthophorosFillProfile(int width, float halfWidth, float halfDepth, float surfaceY) {
    public static final int TILE_PIXELS = 64;
    public static final int ATLAS_MIP_LEVELS = 4;
    private static final AnthophorosFillProfile[] PROFILES = {
            new AnthophorosFillProfile(2, .826F, .420F, .900F),
            new AnthophorosFillProfile(3, 1.326F, .420F, .900F),
            new AnthophorosFillProfile(4, 1.826F, .420F, .900F)
    };

    public static AnthophorosFillProfile forWidth(int width) {
        if (width < 2 || width > 4) throw new IllegalArgumentException("Planter width must be 2, 3 or 4");
        return PROFILES[width - 2];
    }

    public int firstTileIndex() {
        return (int) Math.floor(.5F - halfWidth);
    }

    public int lastTileIndex() {
        return (int) Math.ceil(.5F + halfWidth) - 1;
    }

    public int tileCount() {
        return lastTileIndex() - firstTileIndex() + 1;
    }

    public float tileMinX(int tileIndex) {
        return Math.max(-halfWidth, tileIndex - .5F);
    }

    public float tileMaxX(int tileIndex) {
        return Math.min(halfWidth, tileIndex + .5F);
    }

    /** Each strip remains in 0..1; resetting at its boundary never crosses the atlas. */
    public float textureU(float localX, int tileIndex) {
        return .5F + localX - tileIndex;
    }

    public float textureV(float localZ) {
        return .5F + localZ;
    }
}
