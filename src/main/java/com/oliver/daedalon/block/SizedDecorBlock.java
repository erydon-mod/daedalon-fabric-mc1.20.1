package com.oliver.daedalon.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.util.StringIdentifiable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;

/** A ground-anchored obelisk available at one-, two-, and three-block heights. */
public final class SizedDecorBlock extends Block {
    public static final EnumProperty<Size> SIZE = EnumProperty.of("size", Size.class);
    private static final VoxelShape[] SHAPES = createShapes();

    public SizedDecorBlock(Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState().with(SIZE, Size.MEDIUM));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) { builder.add(SIZE); }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return SHAPES[state.get(SIZE).ordinal()];
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return getOutlineShape(state, world, pos, context);
    }

    @Override
    public VoxelShape getCullingShape(BlockState state, BlockView world, BlockPos pos) { return VoxelShapes.empty(); }

    @Override
    public BlockRenderType getRenderType(BlockState state) { return BlockRenderType.MODEL; }

    private static VoxelShape[] createShapes() {
        VoxelShape[] result = new VoxelShape[Size.values().length];
        for (Size size : Size.values()) {
            double width = 0.758984466 * size.scale();
            result[size.ordinal()] = VoxelShapes.cuboid(
                    0.5 - width * 0.5, 0.0, 0.5 - width * 0.5,
                    0.5 + width * 0.5, 3.0 * size.scale(), 0.5 + width * 0.5
            );
        }
        return result;
    }

    public enum Size implements StringIdentifiable {
        SMALL("small", 1.0F / 3.0F),
        MEDIUM("medium", 2.0F / 3.0F),
        LARGE("large", 1.0F);
        private final String name;
        private final float scale;
        Size(String name, float scale) { this.name = name; this.scale = scale; }
        public float scale() { return scale; }
        @Override public String asString() { return name; }
        @Override public String toString() { return name; }
    }
}
