package com.oliver.daedalon.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.EmptyBlockView;
import net.minecraft.world.BlockView;
import net.minecraft.world.WorldView;
import net.minecraft.world.WorldAccess;

/** A two-height finial retaining its exact supplied style profile. */
public final class FinialBlock extends TwoSizeDecorBlock {
    public static final EnumProperty<FinialSupport.Profile> SUPPORT=EnumProperty.of("finial_support",FinialSupport.Profile.class);
    public static final DirectionProperty FACING=Properties.HORIZONTAL_FACING;
    private final FixedDecorBlock.Style style;
    private final java.util.Map<BlockState,VoxelShape> shapes=new java.util.concurrent.ConcurrentHashMap<>();

    public FinialBlock(Settings settings, FixedDecorBlock.Style style) {
        super(settings.dynamicBounds(), style.twoSizeProfile());
        if (!style.isFinial()) {
            throw new IllegalArgumentException(style.idSuffix() + " is not a finial style");
        }
        this.style = style;
        setDefaultState(getDefaultState().with(SUPPORT,FinialSupport.Profile.NONE)
                .with(FACING,Direction.EAST));
    }

    public FixedDecorBlock.Style style() {
        return style;
    }

    @Override protected void appendProperties(StateManager.Builder<Block,BlockState> builder) {
        super.appendProperties(builder); builder.add(SUPPORT,FACING);
    }
    @Override public BlockState getPlacementState(ItemPlacementContext context) {
        BlockPos support=context instanceof FinialSupportPlacement.Placement placement ? placement.supportPos : context.getBlockPos().down();
        BlockState state=FinialSupportPlacement.fitted(getDefaultState(),context.getWorld(),support);
        if(context instanceof FinialSupportPlacement.Placement placement && placement.offset)
            state=state.with(SUPPORT,FinialSupport.Profile.STEEP_UPPER_OFFSET);
        var supplied=context.getStack().getSubNbt("BlockStateTag");
        if(supplied!=null) {
            var size=SIZE.parse(supplied.getString("size"));
            if(size.isPresent()) state=state.with(SIZE,size.get());
        }
        return state;
    }
    @Override public boolean canPlaceAt(BlockState state,WorldView world,BlockPos pos) {
        return FinialSupportPlacement.isClear(state,world,pos);
    }
    @Override public BlockState getStateForNeighborUpdate(BlockState state,Direction direction,BlockState neighbour,
            WorldAccess world,BlockPos pos,BlockPos neighbourPos) {
        return direction==Direction.DOWN || state.get(SUPPORT)==FinialSupport.Profile.STEEP_UPPER_OFFSET
                ? FinialSupportPlacement.fitted(state,world,FinialSupportPlacement.supportPos(state,pos)) : state;
    }
    @Override public VoxelShape getOutlineShape(BlockState state,BlockView world,BlockPos pos,ShapeContext context) {
        return shapes.computeIfAbsent(state,key -> FinialSupport.shape(key,style));
    }
    @Override public VoxelShape getCollisionShape(BlockState state,BlockView world,BlockPos pos,ShapeContext context) {
        return getOutlineShape(state,world,pos,context);
    }
    /** One particle box caps vanilla debris at64 instead of multiplying it by base slices. */
    public VoxelShape getBreakParticleShape(BlockState state) {
        VoxelShape outline=getOutlineShape(state,EmptyBlockView.INSTANCE,BlockPos.ORIGIN,ShapeContext.absent());
        return state.get(SUPPORT)==FinialSupport.Profile.NONE ? outline : VoxelShapes.cuboid(outline.getBoundingBox());
    }
    @Override public BlockState rotate(BlockState state,BlockRotation rotation) {
        return state.with(FACING,rotation.rotate(state.get(FACING)));
    }
    @Override public BlockState mirror(BlockState state,BlockMirror mirror) {
        if(mirror==BlockMirror.NONE) return state;
        if(state.get(SUPPORT).alignment==FinialSupport.Alignment.DIAGONAL) {
            int quarter=Math.floorMod((int)Math.round(FinialSupport.quarterAngle(state.get(FACING))/(Math.PI/2)),4);
            int reflected=Math.floorMod((mirror==BlockMirror.LEFT_RIGHT ? -1 : 1)-quarter,4);
            Direction facing=new Direction[]{Direction.EAST,Direction.SOUTH,Direction.WEST,Direction.NORTH}[reflected];
            return state.with(FACING,facing);
        }
        return rotate(state.with(SUPPORT,state.get(SUPPORT).reflected()),mirror.getRotation(state.get(FACING)));
    }
}
