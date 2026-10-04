package com.oliver.daedalon.gametest;

import net.fabricmc.api.ModInitializer;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.Identifier;
import net.minecraft.util.StringIdentifiable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;

/** Saved public coping state contract, without requiring ERYDON on the test server. */
public final class FinialCopingFixture implements ModInitializer {
    public enum Surface implements StringIdentifiable {
        FLAT,SLOPE,SHALLOW_LOWER,SHALLOW_UPPER,STEEP_LOWER,STEEP_UPPER,DIAGONAL,
        SHALLOW_BROAD_RIGHT,SHALLOW_BROAD_LEFT,SHALLOW_BROAD_WIDE_RIGHT,SHALLOW_BROAD_WIDE_LEFT,
        SHALLOW_NARROW_RIGHT,SHALLOW_NARROW_LEFT,SHALLOW_NARROW_THIN_RIGHT,SHALLOW_NARROW_THIN_LEFT;
        @Override public String asString() { return name().toLowerCase(java.util.Locale.ROOT); }
    }
    public static final EnumProperty<Surface> SURFACE=EnumProperty.of("surface",Surface.class);
    public static final BooleanProperty OFFSET=BooleanProperty.of("offset");
    public static final BooleanProperty POST=BooleanProperty.of("post");
    private enum Shape implements StringIdentifiable {
        STRAIGHT,CORNER;
        @Override public String asString() { return name().toLowerCase(java.util.Locale.ROOT); }
    }
    private static final EnumProperty<Shape> SHAPE=EnumProperty.of("shape",Shape.class);
    public static final java.util.Map<com.oliver.daedalon.block.FinialSupport.Profile,Block> SLOPES=new java.util.EnumMap<>(com.oliver.daedalon.block.FinialSupport.Profile.class);
    public static Block BLOCK;
    public static BlockState corner(Block block) {
        return block.getDefaultState().with(SHAPE,Shape.CORNER).with(Properties.BLOCK_HALF,net.minecraft.block.enums.BlockHalf.BOTTOM);
    }
    @Override public void onInitialize() {
        BLOCK=Registry.register(Registries.BLOCK,new Identifier("erydon","test_coping_georgian"),
                new Block(Block.Settings.create().nonOpaque()) {
                    { setDefaultState(getDefaultState().with(POST,false).with(OFFSET,false).with(SURFACE,Surface.FLAT)
                            .with(Properties.HORIZONTAL_FACING,net.minecraft.util.math.Direction.EAST)); }
                    @Override protected void appendProperties(StateManager.Builder<Block,BlockState> builder) {
                        builder.add(SURFACE,OFFSET,POST,Properties.HORIZONTAL_FACING);
                    }
                    @Override public VoxelShape getCollisionShape(BlockState state,BlockView world,BlockPos pos,ShapeContext context) {
                        return state.get(POST) ? VoxelShapes.cuboid(0,.3,0,1,1,1) : VoxelShapes.empty();
                    }
                });
        for(var profile:new com.oliver.daedalon.block.FinialSupport.Profile[]{com.oliver.daedalon.block.FinialSupport.Profile.SLOPE,
                com.oliver.daedalon.block.FinialSupport.Profile.SHALLOW_LOWER,com.oliver.daedalon.block.FinialSupport.Profile.SHALLOW_UPPER,
                com.oliver.daedalon.block.FinialSupport.Profile.STEEP_LOWER,com.oliver.daedalon.block.FinialSupport.Profile.STEEP_UPPER}) {
            String suffix=profile==com.oliver.daedalon.block.FinialSupport.Profile.SLOPE ? "slope" : "slope_"+profile.asString();
            SLOPES.put(profile,Registry.register(Registries.BLOCK,new Identifier("erydon","test_"+suffix),new Block(Block.Settings.create().nonOpaque()) {
                @Override protected void appendProperties(StateManager.Builder<Block,BlockState> builder) {
                    builder.add(Properties.BLOCK_HALF,SHAPE,Properties.HORIZONTAL_FACING);
                }
                @Override public VoxelShape getCollisionShape(BlockState state,BlockView world,BlockPos pos,ShapeContext context) {
                    return VoxelShapes.empty();
                }
            }));
        }
    }
}
