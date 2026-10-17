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
        SHALLOW_NARROW_RIGHT,SHALLOW_NARROW_LEFT,SHALLOW_NARROW_THIN_RIGHT,SHALLOW_NARROW_THIN_LEFT,FLAT_HALF;
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
    public static Block GRAND;
    public static Block LAYER;
    public static Block MULTIFACE_LAYER;
    public static final net.minecraft.state.property.IntProperty LAYERS=net.minecraft.state.property.IntProperty.of("layers",1,8);
    public static final BooleanProperty TOP=BooleanProperty.of("top");
    public static BlockState corner(Block block) {
        return block.getDefaultState().with(SHAPE,Shape.CORNER).with(Properties.BLOCK_HALF,net.minecraft.block.enums.BlockHalf.BOTTOM);
    }
    @Override public void onInitialize() {
        BLOCK=registerCoping("test_coping_georgian");
        GRAND=registerCoping("test_coping_georgian_grand");
        LAYER=Registry.register(Registries.BLOCK,new Identifier("erydon","test_layer"),new Block(Block.Settings.create().nonOpaque()) {
            { setDefaultState(getDefaultState().with(LAYERS,4).with(TOP,false)); }
            @Override protected void appendProperties(StateManager.Builder<Block,BlockState> builder) { builder.add(LAYERS,TOP); }
            @Override public VoxelShape getCollisionShape(BlockState state,BlockView world,BlockPos pos,ShapeContext context) {
                double height=state.get(LAYERS)/8.0;
                return VoxelShapes.cuboid(0,state.get(TOP) ? 1-height : 0,0,1,state.get(TOP) ? 1 : height,1);
            }
        });
        MULTIFACE_LAYER=Registry.register(Registries.BLOCK,new Identifier("erydon","test_layer_multiface"),new Block(Block.Settings.create().nonOpaque()) {
            { setDefaultState(getDefaultState().with(LAYERS,4).with(Properties.DOWN,true).with(Properties.UP,false)
                    .with(Properties.NORTH,false).with(Properties.SOUTH,false).with(Properties.EAST,false).with(Properties.WEST,false)); }
            @Override protected void appendProperties(StateManager.Builder<Block,BlockState> builder) {
                builder.add(LAYERS,Properties.DOWN,Properties.UP,Properties.NORTH,Properties.SOUTH,Properties.EAST,Properties.WEST);
            }
            @Override public VoxelShape getCollisionShape(BlockState state,BlockView world,BlockPos pos,ShapeContext context) {
                return VoxelShapes.cuboid(0,0,0,1,state.get(LAYERS)/8.0,1);
            }
            @Override public VoxelShape getOutlineShape(BlockState state,BlockView world,BlockPos pos,ShapeContext context) {
                return VoxelShapes.union(getCollisionShape(state,world,pos,context),Block.createCuboidShape(6,0,6,10,16,10));
            }
            @Override public VoxelShape getRaycastShape(BlockState state,BlockView world,BlockPos pos) {
                return getOutlineShape(state,world,pos,ShapeContext.absent());
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
    private static Block registerCoping(String path) {
        return Registry.register(Registries.BLOCK,new Identifier("erydon",path),
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
    }
}
