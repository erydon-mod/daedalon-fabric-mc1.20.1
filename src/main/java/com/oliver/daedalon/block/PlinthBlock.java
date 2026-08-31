package com.oliver.daedalon.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.state.StateManager;

/** One- and two-block-high plinths prepared from Oliver's supplied source set. */
public final class PlinthBlock extends TwoSizeDecorBlock {
    private final Style style;

    public PlinthBlock(Settings settings, Style style) {
        super(settings, style.profile, true);
        this.style = style;
    }

    public Style style() {
        return style;
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        super.appendProperties(builder);
        builder.add(OFFSET, FACING);
    }

    public enum Style {
        ASTRAGALOS("astragalos", "plinth_astragalos", "Astragalos", 0.603108658, 1.0, 0.602949901),
        BATHRON("bathron", "plinth_bathron", "Bathron", 0.811787311, 1.0, 0.812034223),
        KION("kion", "plinth_kion", "Kion", 0.998954339, 1.0, 1.0),
        STEPHANOS("stephanos", "plinth_stephanos", "Stephanos", 0.831943020, 1.0, 0.831934729),
        TRIPHYLLON("triphyllon", "plinth_triphyllon", "Triphyllon", 0.957103561, 1.0, 0.973928089);

        private final String styleId;
        private final String resourceStem;
        private final String displayName;
        private final Profile profile;

        Style(String styleId, String resourceStem, String displayName,
              double width, double height, double depth) {
            this.styleId = styleId;
            this.resourceStem = resourceStem;
            this.displayName = displayName;
            this.profile = createProfile(width, height, depth);
        }

        public String styleId() { return styleId; }
        public String resourceStem() { return resourceStem; }
        public String displayName() { return displayName; }
    }
}
