package com.oliver.daedalon.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.IntProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.StringIdentifiable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;

/** One metre of wall relief, fitted to local wall surfaces and facing. */
public final class FriezeBlock extends Block {
    public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
    public static final IntProperty PATTERN = IntProperty.of("pattern", 0, 2);
    public static final EnumProperty<Join> JOIN = EnumProperty.of("join", Join.class);
    public static final BooleanProperty MANUAL_CORNER = BooleanProperty.of("manual_corner");
    private final Style style;

    public FriezeBlock(Settings settings) {
        this(settings,Style.CORINTHIAN);
    }

    public FriezeBlock(Settings settings, Style style) {
        super(settings.dynamicBounds());
        this.style=style;
        setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.SOUTH)
                .with(PATTERN, 0).with(JOIN, Join.STRAIGHT).with(MANUAL_CORNER, false));
    }

    public Style style() { return style; }

    /** Geometry components are shared per design, never mixed between motifs. */
    public static boolean isMeshPath(String path) {
        return meshStyle(path)!=null;
    }

    public static Style meshStyle(String path) {
        for (Style style:Style.values()) if (path.equals("models/mesh/"+style.id+"_frieze.obj")) return style;
        return null;
    }

    private static boolean sameStyle(BlockState other, BlockState state) {
        return other.getBlock() instanceof FriezeBlock adjacent
                && state.getBlock() instanceof FriezeBlock current && adjacent.style==current.style;
    }

    @Override protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING, PATTERN, JOIN, MANUAL_CORNER);
    }

    @Override public BlockState getPlacementState(ItemPlacementContext context) {
        Direction side = context.getSide();
        Direction facing = side.getAxis().isHorizontal() ? side : context.getHorizontalPlayerFacing().getOpposite();
        BlockState clicked = context.getWorld().getBlockState(context.getBlockPos().offset(side.getOpposite()));
        if (sameStyle(clicked,getDefaultState()) && side.getAxis().isHorizontal()
                && side.getAxis() != clicked.get(FACING).getAxis()) facing = clicked.get(FACING);
        Direction wallFacing=facing;
        Direction aimedFacing=context.getHorizontalPlayerFacing().getOpposite();
        boolean angled=side.getAxis().isHorizontal() && aimedFacing.getAxis()!=wallFacing.getAxis()
                && wallFace(context.getWorld(),context.getBlockPos().offset(wallFacing.getOpposite()),wallFacing);
        // Preserve the main wall run as the corner's secondary arm. This makes a
        // short external return, rather than a full metre projecting off the wall.
        if (angled) facing=aimedFacing;
        BlockState state = getDefaultState().with(FACING, facing);
        if (angled) state=state.with(MANUAL_CORNER,true).with(JOIN,
                facing.rotateYCounterclockwise()==wallFacing ? Join.OUTER_LEFT : Join.OUTER_RIGHT);
        // Extending a manually shifted run inherits its offset, without a run scan.
        for (Direction edge : new Direction[]{wallFacing.rotateYClockwise(), wallFacing.rotateYCounterclockwise()}) {
            BlockState adjacent = context.getWorld().getBlockState(context.getBlockPos().offset(edge));
            if (sameStyle(adjacent,state) && adjacent.get(FACING) == wallFacing) {
                state = state.with(PATTERN, adjacent.get(PATTERN));
                break;
            }
        }
        return refresh(context.getWorld(), context.getBlockPos(), state);
    }

    public static BlockState refresh(BlockView world, BlockPos pos, BlockState state) {
        if (state.get(MANUAL_CORNER)) return state;
        // Lighting workers also request dynamic outline/collision shapes. A
        // neighbour lookup here can wait for the very chunk being lit, leaving
        // both lighting and the server stuck. Use the persisted join and cached
        // shape off the server thread; gameplay and client views still refresh.
        // Exit before wallFace, whose third-party solidity hooks can also load chunks.
        if (world instanceof ServerWorld serverWorld && !serverWorld.getServer().isOnThread()) {
            return state;
        }
        Direction facing = state.get(FACING);
        Direction left=facing.rotateYClockwise(), right=facing.rotateYCounterclockwise();
        BlockPos backPos=pos.offset(facing.getOpposite());
        boolean supported=wallFace(world,backPos,facing);
        boolean leftWall=wallFace(world,pos.offset(left),right);
        boolean rightWall=wallFace(world,pos.offset(right),left);
        // A wall meeting the backing wall is sufficient for an inside return.
        // With walls on both sides there is no unique turn: keep the panel flat.
        if (supported) {
            if (leftWall != rightWall) return state.with(JOIN,leftWall ? Join.INNER_LEFT : Join.INNER_RIGHT);
            return state.with(JOIN,Join.STRAIGHT);
        }
        // The outside corner occupies the empty cell diagonally beyond the wall.
        // Both exposed faces must be full surfaces; plants/fences do not qualify.
        boolean outerLeft=!leftWall && wallFace(world,backPos.offset(left),facing)
                && wallFace(world,backPos.offset(left),right);
        boolean outerRight=!rightWall && wallFace(world,backPos.offset(right),facing)
                && wallFace(world,backPos.offset(right),left);
        if (outerLeft != outerRight) return state.with(JOIN,outerLeft ? Join.OUTER_LEFT : Join.OUTER_RIGHT);
        // Front = an inside turn; behind = an outside turn. A continuing parallel
        // run on the affected side takes precedence over an incidental T junction.
        BlockState front = world.getBlockState(pos.offset(facing));
        if (perpendicular(front, state)) {
            boolean turnsLeft = front.get(FACING) == facing.rotateYCounterclockwise();
            Direction occupiedSide = turnsLeft ? facing.rotateYClockwise() : facing.rotateYCounterclockwise();
            if (!sameFacing(world.getBlockState(pos.offset(occupiedSide)), state)) {
                return state.with(JOIN, turnsLeft ? Join.INNER_LEFT : Join.INNER_RIGHT);
            }
        }
        BlockState back = world.getBlockState(pos.offset(facing.getOpposite()));
        if (perpendicular(back, state)) {
            boolean turnsLeft = back.get(FACING) == facing.rotateYCounterclockwise();
            Direction occupiedSide = turnsLeft ? facing.rotateYCounterclockwise() : facing.rotateYClockwise();
            if (!sameFacing(world.getBlockState(pos.offset(occupiedSide)), state)) {
                return state.with(JOIN, turnsLeft ? Join.OUTER_LEFT : Join.OUTER_RIGHT);
            }
        }
        return state.with(JOIN, Join.STRAIGHT);
    }

    private static boolean wallFace(BlockView world, BlockPos pos, Direction face) {
        BlockState block=world.getBlockState(pos);
        return !(block.getBlock() instanceof FriezeBlock) && block.isSideSolidFullSquare(world,pos,face);
    }

    private static boolean sameFacing(BlockState other, BlockState state) {
        return sameStyle(other,state) && other.get(FACING) == state.get(FACING);
    }

    private static boolean perpendicular(BlockState other, BlockState state) {
        return sameStyle(other,state) && other.get(FACING).getAxis() != state.get(FACING).getAxis();
    }

    @Override public BlockState getStateForNeighborUpdate(BlockState state, Direction direction,
            BlockState neighborState, WorldAccess world, BlockPos pos, BlockPos neighborPos) {
        return direction.getAxis().isHorizontal() ? refresh(world, pos, state) : state;
    }

    @Override public void onBlockAdded(BlockState state, World world, BlockPos pos, BlockState oldState, boolean notify) {
        if (!world.isClient) {
            BlockState updated = refresh(world, pos, state);
            if (updated != state) world.setBlockState(pos, updated, Block.NOTIFY_ALL);
        }
    }

    /** Source X increases to the east for a south-facing panel. */
    public static int section(BlockPos pos, Direction facing, int offset) {
        return section(pos,facing,offset,3);
    }

    private static int section(BlockPos pos, Direction facing, int offset, int sections) {
        int coordinate = switch (facing) {
            case SOUTH -> pos.getX();
            case NORTH -> -pos.getX() - 1;
            case EAST -> -pos.getZ() - 1;
            case WEST -> pos.getZ();
            default -> throw new IllegalArgumentException("Friezes face horizontally");
        };
        return Math.floorMod(coordinate + offset, sections);
    }

    public static Direction secondaryFacing(BlockState state) {
        return state.get(JOIN).left ? state.get(FACING).rotateYCounterclockwise() : state.get(FACING).rotateYClockwise();
    }

    public static Direction armEdge(BlockState state, int arm) {
        Join join = state.get(JOIN);
        Direction local = arm == 0 ? join.firstEdge : join.secondEdge;
        for (int i = 0; i < rotationSteps(state.get(FACING)); i++) local = local.rotateYClockwise();
        return local;
    }

    public static Direction armFacing(BlockState state, int arm) {
        return arm == 0 || state.get(JOIN) == Join.STRAIGHT ? state.get(FACING) : secondaryFacing(state);
    }

    /** Ends connect across material changes, but never across incompatible orientations. */
    public static boolean connected(BlockView world, BlockPos pos, BlockState state, int arm) {
        state=refresh(world,pos,state);
        Direction edge = armEdge(state, arm);
        BlockState neighbor = world.getBlockState(pos.offset(edge));
        if (!sameStyle(neighbor,state)) return false;
        neighbor=refresh(world,pos.offset(edge),neighbor);
        for (int other = 0; other < 2; other++) {
            if (armEdge(neighbor, other) == edge.getOpposite()
                    && armFacing(neighbor, other) == armFacing(state, arm)) return true;
        }
        return false;
    }

    public static int rotationSteps(Direction facing) {
        return Math.floorMod(DecorShapeTransforms.horizontalIndex(facing) - 2, 4);
    }

    @Override public BlockState rotate(BlockState state, BlockRotation rotation) {
        return state.with(FACING, rotation.rotate(state.get(FACING)));
    }

    @Override public BlockState mirror(BlockState state, BlockMirror mirror) {
        if (mirror == BlockMirror.NONE) return state;
        return state.with(FACING, mirror.apply(state.get(FACING))).with(JOIN, state.get(JOIN).mirrored());
    }

    @Override public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        // Diagonal support edits need not notify this cell. Resolve from the
        // current view for both rendering and dynamic collision/selection shapes.
        state=refresh(world,pos,state);
        return style.shapes[state.get(JOIN).ordinal()][DecorShapeTransforms.horizontalIndex(state.get(FACING))];
    }

    @Override public VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return getOutlineShape(state, world, pos, context);
    }

    @Override public VoxelShape getCullingShape(BlockState state, BlockView world, BlockPos pos) {
        return VoxelShapes.empty();
    }

    private static VoxelShape[][] createShapes(double depth) {
        VoxelShape[][] result = new VoxelShape[Join.values().length][4];
        VoxelShape front = VoxelShapes.cuboid(0,0,0,1,1,depth);
        for (Join join : Join.values()) {
            VoxelShape side = join.left ? VoxelShapes.cuboid(0,0,0,depth,1,1)
                    : VoxelShapes.cuboid(1-depth,0,0,1,1,1);
            VoxelShape shape = switch (join) {
                case STRAIGHT -> front;
                case INNER_LEFT, INNER_RIGHT -> VoxelShapes.union(front, side).simplify();
                case OUTER_LEFT -> VoxelShapes.cuboid(0,0,0,depth,1,depth);
                case OUTER_RIGHT -> VoxelShapes.cuboid(1-depth,0,0,1,1,depth);
            };
            for (Direction facing : new Direction[]{Direction.SOUTH,Direction.WEST,Direction.NORTH,Direction.EAST}) {
                result[join.ordinal()][DecorShapeTransforms.horizontalIndex(facing)] = shape;
                final VoxelShape[] rotated = {VoxelShapes.empty()};
                shape.forEachBox((x0,y0,z0,x1,y1,z1) -> rotated[0] = VoxelShapes.union(rotated[0],
                        VoxelShapes.cuboid(1-z1,y0,x0,1-z0,y1,x1)));
                shape = rotated[0].simplify();
            }
        }
        return result;
    }

    public enum Style {
        CORINTHIAN("corinthian",0.23,3), IONIC("ionic",0.34,3), GOTHIC("gothic",0.256,1),
        BYZANTINE("byzantine",0.235,2);
        private final String id;
        private final double depth;
        private final int sections;
        private final VoxelShape[][] shapes;
        Style(String id,double depth,int sections) { this.id=id; this.depth=depth; this.sections=sections; this.shapes=createShapes(depth); }
        public String id() { return id; }
        public double depth() { return depth; }
        public int sections() { return sections; }
        public int section(BlockPos pos,Direction facing,int offset) {
            return FriezeBlock.section(pos,facing,offset,sections);
        }
        public int displaySection() { return sections==1 ? 0 : 1; }
    }

    public enum Join implements StringIdentifiable {
        STRAIGHT("straight", false, Direction.WEST, Direction.EAST),
        INNER_LEFT("inner_left", true, Direction.EAST, Direction.SOUTH),
        INNER_RIGHT("inner_right", false, Direction.WEST, Direction.SOUTH),
        OUTER_LEFT("outer_left", true, Direction.WEST, Direction.NORTH),
        OUTER_RIGHT("outer_right", false, Direction.EAST, Direction.NORTH);

        private final String name;
        public final boolean left;
        private final Direction firstEdge, secondEdge;
        Join(String name, boolean left, Direction firstEdge, Direction secondEdge) {
            this.name=name; this.left=left; this.firstEdge=firstEdge; this.secondEdge=secondEdge;
        }
        public Join mirrored() { return switch (this) {
            case STRAIGHT -> STRAIGHT;
            case INNER_LEFT -> INNER_RIGHT; case INNER_RIGHT -> INNER_LEFT;
            case OUTER_LEFT -> OUTER_RIGHT; case OUTER_RIGHT -> OUTER_LEFT;
        }; }
        @Override public String asString() { return name; }
    }
}
