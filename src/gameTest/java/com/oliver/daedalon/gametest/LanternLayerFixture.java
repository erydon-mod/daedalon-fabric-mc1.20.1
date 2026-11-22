package com.oliver.daedalon.gametest;

import net.fabricmc.api.ModInitializer;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.Properties;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import java.util.List;
import java.util.Objects;

/** Optional ERYDON layer collision contracts, registered before registry freeze. */
public final class LanternLayerFixture implements ModInitializer {
    public static Block SOLID,TRANSLUCENT;
    @Override public void onInitialize() {
        SOLID=Registry.register(Registries.BLOCK,new Identifier("daedalon_gametest","vertical_layer"),new LayerVerticalBlock());
        TRANSLUCENT=Registry.register(Registries.BLOCK,new Identifier("daedalon_gametest","vertical_layer_translucent"),new LayerVerticalTranslucentBlock());
    }
    public static List<Block> blocks() { return List.of(SOLID,TRANSLUCENT); }
    private static class LayerVerticalBlock extends Block {
        LayerVerticalBlock() { super(Settings.copy(Blocks.STONE).dynamicBounds()); }
        @Override protected void appendProperties(StateManager.Builder<Block,BlockState> builder) { builder.add(Properties.HORIZONTAL_FACING,Properties.LAYERS); }
        @Override public VoxelShape getCollisionShape(BlockState state,BlockView world,BlockPos pos,ShapeContext context) {
            // A collision hook such as Conquest's can look up the block at this position.
            Objects.requireNonNull(world, "Vertical layer collision requires a block view");
            double thickness=state.get(Properties.LAYERS)/8.;
            return switch(state.get(Properties.HORIZONTAL_FACING)) {
                case NORTH->VoxelShapes.cuboid(0,0,0,1,1,thickness);
                case SOUTH->VoxelShapes.cuboid(0,0,1-thickness,1,1,1);
                case WEST->VoxelShapes.cuboid(0,0,0,thickness,1,1);
                default->VoxelShapes.cuboid(1-thickness,0,0,1,1,1);
            };
        }
        @Override public VoxelShape getOutlineShape(BlockState state,BlockView world,BlockPos pos,ShapeContext context) { return getCollisionShape(state,world,pos,context); }
    }
    private static final class LayerVerticalTranslucentBlock extends LayerVerticalBlock {}
}
