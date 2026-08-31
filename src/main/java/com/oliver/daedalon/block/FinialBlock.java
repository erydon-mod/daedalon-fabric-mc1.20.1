package com.oliver.daedalon.block;

/** A two-height finial retaining its exact supplied style profile. */
public final class FinialBlock extends TwoSizeDecorBlock {
    private final FixedDecorBlock.Style style;

    public FinialBlock(Settings settings, FixedDecorBlock.Style style) {
        super(settings, style.twoSizeProfile());
        if (!style.isFinial()) {
            throw new IllegalArgumentException(style.idSuffix() + " is not a finial style");
        }
        this.style = style;
    }

    public FixedDecorBlock.Style style() {
        return style;
    }
}
