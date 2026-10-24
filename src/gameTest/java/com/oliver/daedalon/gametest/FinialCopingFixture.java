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

/** Saved public coping state and collision contract, without requiring ERYDON on the test server. */
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
            @Override public VoxelShape getOutlineShape(BlockState state,BlockView world,BlockPos pos,ShapeContext context) {
                return getCollisionShape(state,world,pos,context);
            }
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
        boolean grand=path.endsWith("_grand");
        return Registry.register(Registries.BLOCK,new Identifier("erydon",path),
                new Block(Block.Settings.create().nonOpaque().dynamicBounds()) {
                    private final java.util.Map<BlockState,VoxelShape> shapes=new java.util.HashMap<>();
                    { setDefaultState(getDefaultState().with(POST,false).with(OFFSET,false).with(SURFACE,Surface.FLAT)
                            .with(Properties.HORIZONTAL_FACING,net.minecraft.util.math.Direction.EAST)); }
                    @Override protected void appendProperties(StateManager.Builder<Block,BlockState> builder) {
                        builder.add(SURFACE,OFFSET,POST,Properties.HORIZONTAL_FACING);
                    }
                    @Override public VoxelShape getCollisionShape(BlockState state,BlockView world,BlockPos pos,ShapeContext context) {
                        var shape=shapes.computeIfAbsent(state,key -> copingShape(key,grand));
                        return state.get(POST) ? VoxelShapes.union(shape,VoxelShapes.cuboid(0,.3,0,1,1,1)) : shape;
                    }
                    @Override public VoxelShape getOutlineShape(BlockState state,BlockView world,BlockPos pos,ShapeContext context) {
                        return getCollisionShape(state,world,pos,context);
                    }
                });
    }

    /** Matches ERYDON CopingBlock's conservative 1/32 collision sampling, including its normal extrusion. */
    private static VoxelShape copingShape(BlockState state,boolean grand) {
        var profile=com.oliver.daedalon.block.FinialSupport.Profile.find(state.get(SURFACE).asString(),true,grand);
        var facing=state.get(Properties.HORIZONTAL_FACING);
        double thickness=grand ? .5 : 3.6/16,overhang=grand ? 2.7/16 : .125;
        VoxelShape shape=VoxelShapes.empty();
        if(profile.alignment!=com.oliver.daedalon.block.FinialSupport.Alignment.AXIS) {
            var alignment=profile.alignment;
            double x=.5+alignment.offsetX,z=.5+alignment.offsetZ;
            int turns=switch(facing) { case SOUTH -> 1; case WEST -> 2; case NORTH -> 3; default -> 0; };
            for(int turn=0;turn<turns;turn++) { double next=1-z; z=x; x=next; }
            double angle=alignment.yaw+turns*Math.PI/2,cos=Math.cos(angle),sin=Math.sin(angle);
            double runLength=switch(alignment) {
                case DIAGONAL -> Math.sqrt(2);
                case SHALLOW_BROAD_RIGHT,SHALLOW_BROAD_LEFT,SHALLOW_BROAD_WIDE_RIGHT,SHALLOW_BROAD_WIDE_LEFT -> 3/Math.sqrt(5);
                default -> 2.5/Math.sqrt(5);
            };
            double width=switch(alignment) {
                case DIAGONAL -> Math.sqrt(.5);
                case SHALLOW_BROAD_WIDE_RIGHT,SHALLOW_BROAD_WIDE_LEFT -> 3/Math.sqrt(5);
                case SHALLOW_NARROW_THIN_RIGHT,SHALLOW_NARROW_THIN_LEFT -> 1/Math.sqrt(5);
                default -> 2/Math.sqrt(5);
            };
            double run=runLength/2+overhang,cross=width/2+overhang;
            double radius=Math.abs(sin)*run+Math.abs(cos)*cross,step=1/32.0;
            for(int row=(int)Math.floor((z-radius)/step);row<(int)Math.ceil((z+radius)/step);row++) {
                double localZ=(row+.5)*step-z;
                double a=(-run-sin*localZ)/cos,b=(run-sin*localZ)/cos;
                double c=(-cross-cos*localZ)/-sin,d=(cross-cos*localZ)/-sin;
                double low=Math.max(Math.min(a,b),Math.min(c,d)),high=Math.min(Math.max(a,b),Math.max(c,d));
                if(low>=high) continue;
                double margin=step/2*Math.max(Math.abs(sin/cos),Math.abs(cos/sin));
                shape=VoxelShapes.union(shape,VoxelShapes.cuboid(x+low-margin,0,row*step,x+high+margin,thickness,(row+1)*step));
            }
        } else {
            double gradient=profile.gradient(),length=Math.sqrt(1+gradient*gradient);
            double normalX=gradient/length,normalY=1/length;
            int padding=grand ? (int)Math.ceil(overhang*32/(profile.end-profile.start)) : 4;
            for(int step=0;step<32+2*padding;step++) {
                double a=profile.start+(profile.end-profile.start)*(step-padding)/32;
                double b=profile.start+(profile.end-profile.start)*(step-padding+1)/32;
                double x1=a,x2=b+normalX*thickness,y1=profile.height(b),y2=profile.height(a)+normalY*thickness;
                double z1=-overhang,z2=1+overhang;
                var box=switch(facing) {
                    case SOUTH -> VoxelShapes.cuboid(1-z2,y1,x1,1-z1,y2,x2);
                    case WEST -> VoxelShapes.cuboid(1-x2,y1,1-z2,1-x1,y2,1-z1);
                    case NORTH -> VoxelShapes.cuboid(z1,y1,1-x2,z2,y2,1-x1);
                    default -> VoxelShapes.cuboid(x1,y1,z1,x2,y2,z2);
                };
                shape=VoxelShapes.union(shape,box);
            }
            if(state.get(OFFSET)) shape=shape.offset(-facing.getOffsetX(),0,-facing.getOffsetZ());
        }
        return shape.simplify();
    }
}
