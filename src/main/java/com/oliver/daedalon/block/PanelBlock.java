package com.oliver.daedalon.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.StringIdentifiable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;

/** One complete carved panel, anchored to a wall or the underside of a ceiling. */
public final class PanelBlock extends Block {
    public static final EnumProperty<Size> SIZE=EnumProperty.of("size",Size.class);
    public static final DirectionProperty FACING=Properties.HORIZONTAL_FACING;
    public static final BooleanProperty CEILING=BooleanProperty.of("ceiling");
    public static final double DEPTH=0.091;
    private static final Transform[][][] TRANSFORMS=new Transform[2][3][4];
    private static final VoxelShape[][][] SHAPES=new VoxelShape[2][3][4];
    static {
        for (int ceiling=0;ceiling<2;ceiling++) for (Size size:Size.values()) for (Direction facing:Direction.Type.HORIZONTAL) {
            int index=DecorShapeTransforms.horizontalIndex(facing);
            Transform transform=new Transform(size.metres,ceiling==1,BenchBlock.rotationStepsFromSouth(facing));
            TRANSFORMS[ceiling][size.ordinal()][index]=transform;
            double x0=Double.POSITIVE_INFINITY,y0=x0,z0=x0,x1=-x0,y1=-x0,z1=-x0;
            for (float x:new float[]{0,1}) for (float y:new float[]{0,1}) for (float z:new float[]{0,(float)DEPTH}) {
                x0=Math.min(x0,transform.x(x,y,z));x1=Math.max(x1,transform.x(x,y,z));
                y0=Math.min(y0,transform.y(x,y,z));y1=Math.max(y1,transform.y(x,y,z));
                z0=Math.min(z0,transform.z(x,y,z));z1=Math.max(z1,transform.z(x,y,z));
            }
            SHAPES[ceiling][size.ordinal()][index]=VoxelShapes.cuboid(x0,y0,z0,x1,y1,z1);
        }
    }
    public PanelBlock(Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState().with(SIZE,Size.SMALL).with(FACING,Direction.SOUTH).with(CEILING,false));
    }
    @Override protected void appendProperties(StateManager.Builder<Block,BlockState> builder) { builder.add(SIZE,FACING,CEILING); }
    @Override public BlockState getPlacementState(ItemPlacementContext context) {
        Direction facing=context.getSide().getAxis().isHorizontal()
                ? context.getSide() : context.getHorizontalPlayerFacing().getOpposite();
        return getDefaultState().with(FACING,facing).with(CEILING,context.getSide()==Direction.DOWN);
    }
    @Override public BlockState rotate(BlockState state,BlockRotation rotation) { return state.with(FACING,rotation.rotate(state.get(FACING))); }
    @Override public BlockState mirror(BlockState state,BlockMirror mirror) { return rotate(state,mirror.getRotation(state.get(FACING))); }
    public Transform transform(BlockState state) { return TRANSFORMS[state.get(CEILING)?1:0][state.get(SIZE).ordinal()][DecorShapeTransforms.horizontalIndex(state.get(FACING))]; }
    @Override public VoxelShape getOutlineShape(BlockState state,BlockView world,BlockPos pos,ShapeContext context) {
        return SHAPES[state.get(CEILING)?1:0][state.get(SIZE).ordinal()][DecorShapeTransforms.horizontalIndex(state.get(FACING))];
    }
    @Override public VoxelShape getCollisionShape(BlockState state,BlockView world,BlockPos pos,ShapeContext context) { return getOutlineShape(state,world,pos,context); }
    @Override public VoxelShape getCullingShape(BlockState state,BlockView world,BlockPos pos) { return VoxelShapes.empty(); }

    public enum Size implements StringIdentifiable {
        SMALL("small",1),MEDIUM("medium",2),LARGE("large",3);
        private final String id;
        public final int metres;
        Size(String id,int metres) { this.id=id;this.metres=metres; }
        @Override public String asString() { return id; }
    }

    /** Same mapping for cached selection/collision and the shared rendered mesh.
     * The surface centre stays fixed while the backing remains flush with its support. */
    public record Transform(int size,boolean ceiling,int steps) {
        public float x(float x,float y,float z) {
            float u=.5F+(x-.5F)*size,w=ceiling?.5F+(y-.5F)*size:z*size;
            return switch (steps) { case 0 -> u;case 1 -> 1-w;case 2 -> 1-u;default -> w; };
        }
        public float y(float x,float y,float z) { return ceiling?1-z*size:.5F+(y-.5F)*size; }
        public float z(float x,float y,float z) {
            float u=.5F+(x-.5F)*size,w=ceiling?.5F+(y-.5F)*size:z*size;
            return switch (steps) { case 0 -> w;case 1 -> u;case 2 -> 1-w;default -> 1-u; };
        }
        public float normalX(float x,float y,float z) {
            float w=ceiling?y:z;
            return switch (steps) { case 0 -> x;case 1 -> -w;case 2 -> -x;default -> w; };
        }
        public float normalY(float x,float y,float z) { return ceiling?-z:y; }
        public float normalZ(float x,float y,float z) {
            float w=ceiling?y:z;
            return switch (steps) { case 0 -> w;case 1 -> x;case 2 -> -w;default -> -x; };
        }
        public Direction direction(Direction face) {
            return Direction.fromVector((int)normalX(face.getOffsetX(),face.getOffsetY(),face.getOffsetZ()),
                    (int)normalY(face.getOffsetX(),face.getOffsetY(),face.getOffsetZ()),
                    (int)normalZ(face.getOffsetX(),face.getOffsetY(),face.getOffsetZ()));
        }
    }
}
