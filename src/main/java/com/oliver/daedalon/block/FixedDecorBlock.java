package com.oliver.daedalon.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;

/** A one-block freestanding decor mesh with no meaningless state properties. */
public final class FixedDecorBlock extends Block {
    private final Style style;

    public FixedDecorBlock(Settings settings, Style style) {
        super(settings);
        this.style = style;
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return style.shape;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return style.shape;
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
        BALANOS_FINIAL("balanos_finial", "finial_balanos", "Balanos Finial", 0.494869746, 1.0, 0.494972038),
        KYNARA_FINIAL("kynara_finial", "finial_kynara", "Kynara Finial", 0.521664875, 1.0, 0.580038780),
        PHLOX_FINIAL("phlox_finial", "finial_phlox", "Phlox Finial", 0.535196576, 1.0, 0.531525611),
        SPHAIRA_FINIAL("sphaira_finial", "finial_sphaira", "Sphaira Finial", 0.661462481, 1.0, 0.662851611),
        STROBILOS_FINIAL("strobilos_finial", "finial_strobilos", "Strobilos Finial", 0.510813963, 1.0, 0.513596362),
        LOUTERION_BASIN("louterion_basin", "basin_louterion", "Louterion Basin", 1.0, 0.958789790, 0.999205574),
        PEGE_FOUNTAIN("pege_fountain", "fountain_pege", "Pege Fountain", 1.0, 0.974990921, 0.994125107);

        private final String idSuffix;
        private final String resourceStem;
        private final String displayName;
        private final VoxelShape shape;
        private final TwoSizeDecorBlock.Profile twoSizeProfile;
        private final double width,depth;

        Style(String idSuffix, String resourceStem, String displayName, double width, double height, double depth) {
            this.idSuffix = idSuffix;
            this.resourceStem = resourceStem;
            this.displayName = displayName;
            this.width=width; this.depth=depth;
            this.shape = VoxelShapes.cuboid(
                    0.5 - width * 0.5, 0.0, 0.5 - depth * 0.5,
                    0.5 + width * 0.5, height, 0.5 + depth * 0.5
            );
            this.twoSizeProfile = idSuffix.endsWith("_finial")
                    ? TwoSizeDecorBlock.createProfile(width, height, depth)
                    : null;
        }

        public String idSuffix() { return idSuffix; }
        public String resourceStem() { return resourceStem; }
        public String displayName() { return displayName; }
        public boolean isFinial() { return twoSizeProfile != null; }
        public double width() { return width; }
        public double depth() { return depth; }
        /** Measured lower pedestal, excluding the wider ornament above it. */
        public double finialFootWidth() {
            return switch(this) {
                case BALANOS_FINIAL -> .368761;
                case KYNARA_FINIAL -> .331942;
                case PHLOX_FINIAL -> .433910;
                case SPHAIRA_FINIAL -> .605648;
                case STROBILOS_FINIAL -> .401336;
                default -> throw new IllegalStateException("Not a finial style");
            };
        }
        public TwoSizeDecorBlock.Profile twoSizeProfile() {
            if (twoSizeProfile == null) {
                throw new IllegalStateException(idSuffix + " is not a two-size finial");
            }
            return twoSizeProfile;
        }
    }
}
