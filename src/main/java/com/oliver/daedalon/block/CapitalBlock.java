package com.oliver.daedalon.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;

/** A fixed one-block column capital fitted over ERYDON's circular shaft. */
public final class CapitalBlock extends Block {
    private final Style style;

    public CapitalBlock(Settings settings, Style style) {
        super(settings);
        this.style = style;
    }

    @Override
    public VoxelShape getOutlineShape(
            BlockState state,
            BlockView world,
            BlockPos pos,
            ShapeContext context
    ) {
        return style.shape();
    }

    @Override
    public VoxelShape getCollisionShape(
            BlockState state,
            BlockView world,
            BlockPos pos,
            ShapeContext context
    ) {
        return style.shape();
    }

    @Override
    public VoxelShape getCullingShape(BlockState state, BlockView world, BlockPos pos) {
        return VoxelShapes.empty();
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }

    public Style style() {
        return style;
    }

    public enum Style {
        BYZANTINE("byzantine", "Byzantine", 0.970513107, 0.970429544),
        CORINTHIAN("corinthian", "Corinthian", 0.903620366, 0.902639683),
        GOTHIC("gothic", "Gothic", 0.979547970, 0.978666574),
        GREEK_IONIC("greek_ionic", "Greek Ionic", 1.317061573, 0.941511460),
        ROMAN_COMPOSITE("roman_composite", "Roman Composite", 0.912417446, 0.914758735),
        TUSCAN("tuscan", "Tuscan", 0.992720929, 1.000345088);

        private final String styleId;
        private final String displayName;
        private final String resourceStem;
        private final VoxelShape shape;

        Style(String styleId, String displayName, double width, double depth) {
            this.styleId = styleId;
            this.displayName = displayName;
            this.resourceStem = "capital_" + styleId;
            this.shape = VoxelShapes.cuboid(
                    0.5 - width * 0.5,
                    0.0,
                    0.5 - depth * 0.5,
                    0.5 + width * 0.5,
                    1.0,
                    0.5 + depth * 0.5
            );
        }

        public String styleId() {
            return styleId;
        }

        public String displayName() {
            return displayName;
        }

        public String resourceStem() {
            return resourceStem;
        }

        private VoxelShape shape() {
            return shape;
        }
    }
}
