package com.oliver.daedalon.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Property;
import net.minecraft.util.StringIdentifiable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.util.function.BooleanBiFunction;
import net.minecraft.world.BlockView;
import net.minecraft.world.WorldView;

/** Optional coping/slope fitting; Daedalon remains usable without ERYDON installed. */
public final class FinialSupportPlacement {
    private FinialSupportPlacement() { }

    public static final class Placement extends ItemPlacementContext {
        public final BlockPos supportPos;
        public final boolean offset;
        private final BlockPos target;
        private Placement(ItemPlacementContext original,BlockPos support,BlockPos target,boolean offset) {
            super(original); supportPos=support; this.target=target; this.offset=offset; canReplaceExisting=false;
        }
        @Override public BlockPos getBlockPos() { return target==null ? super.getBlockPos() : target; }
        @Override public Direction getSide() { return Direction.UP; }
    }

    public static ItemPlacementContext redirect(ItemPlacementContext context,Block block) {
        if(!(block instanceof FinialBlock)) return context;
        BlockPos support=context.canReplaceExisting() ? context.getBlockPos()
                : context.getBlockPos().offset(context.getSide().getOpposite());
        BlockState found=context.getWorld().getBlockState(support);
        var fit=matched(found,context.getWorld(),support);
        if(fit==null) return context;
        BlockPos target=support.up();
        boolean offset=false;
        if(fit.profile()==FinialSupport.Profile.STEEP_UPPER && !context.getWorld().getBlockState(target).isReplaceable()) {
            BlockState above=context.getWorld().getBlockState(target);
            if(profile(above)==FinialSupport.Profile.STEEP_LOWER && direction(above)==fit.facing()) {
                target=target.offset(fit.facing()); offset=true;
            }
        }
        return new Placement(context,support,target,offset);
    }

    public static BlockState fitted(BlockState finial,BlockView world,BlockPos support) {
        BlockState found=world.getBlockState(support);
        Matched fit=matched(found,world,support);
        if(fit==null) return finial;
        FinialSupport.Profile profile=fit.profile();
        Direction facing=fit.facing();
        if(profile==FinialSupport.Profile.COPING_STEEP_UPPER && named(found,"offset").equals("true"))
            profile=FinialSupport.Profile.COPING_STEEP_UPPER_OFFSET;
        if(profile==FinialSupport.Profile.STEEP_UPPER && finial.get(FinialBlock.SUPPORT)==FinialSupport.Profile.STEEP_UPPER_OFFSET)
            profile=FinialSupport.Profile.STEEP_UPPER_OFFSET;
        return finial.with(FinialBlock.SUPPORT,profile).with(FinialBlock.FACING,facing);
    }

    public static BlockPos supportPos(BlockState state,BlockPos owner) {
        return state.get(FinialBlock.SUPPORT)==FinialSupport.Profile.STEEP_UPPER_OFFSET
                ? owner.down().offset(state.get(FinialBlock.FACING).getOpposite()) : owner.down();
    }

    private record Matched(FinialSupport.Profile profile,Direction facing) { }
    private static Matched matched(BlockState state,BlockView world,BlockPos pos) {
        FinialSupport.Profile profile=profile(state);
        if(profile!=null) return new Matched(profile,direction(state));
        var id=Registries.BLOCK.getId(state.getBlock());
        // Ask ERYDON's authoritative paired-wall resolver only when that optional family is present.
        if(!id.getNamespace().equals("erydon") || !id.getPath().contains("slope_vertical")) return null;
        return VerticalSupport.read(state,world,pos);
    }

    private static final class VerticalSupport {
        private static final java.lang.reflect.Method SUPPORT,SURFACE,FACING;
        static {
            java.lang.reflect.Method support=null,surface=null,facing=null;
            try {
                var block=Class.forName("com.oliver.erydon.block.CopingBlock");
                support=block.getMethod("support",BlockState.class,BlockView.class,BlockPos.class);
                var result=support.getReturnType();
                surface=result.getMethod("surface"); facing=result.getMethod("facing");
            } catch(ReflectiveOperationException | LinkageError absent) { support=null; }
            SUPPORT=support; SURFACE=surface; FACING=facing;
        }
        private static Matched read(BlockState state,BlockView world,BlockPos pos) {
            if(SUPPORT==null) return null;
            try {
                Object result=SUPPORT.invoke(null,state,world,pos);
                if(result==null) return null;
                Object surface=SURFACE.invoke(result),facing=FACING.invoke(result);
                if(!(surface instanceof StringIdentifiable named) || !(facing instanceof Direction direction)) return null;
                var profile=FinialSupport.Profile.find(named.asString(),false);
                return profile.alignment==FinialSupport.Alignment.AXIS ? null : new Matched(profile,direction);
            } catch(ReflectiveOperationException invalid) { return null; }
        }
    }

    private static FinialSupport.Profile profile(BlockState state) {
        var id=Registries.BLOCK.getId(state.getBlock());
        if(id.getNamespace().equals("erydon") && id.getPath().endsWith("_coping_georgian")) {
            String surface=named(state,"surface");
            FinialSupport.Profile profile=FinialSupport.Profile.find(surface,true);
            return profile==FinialSupport.Profile.NONE ? null : profile;
        }
        if(!id.getNamespace().equals("erydon") && !id.getNamespace().equals("themelios")) return null;
        if(!named(state,"half").equals("bottom") || !named(state,"shape").equals("straight")) return null;
        String path=id.getPath();
        for(String surface:new String[]{"shallow_lower","shallow_upper","steep_lower","steep_upper"})
            if(path.endsWith("_slope_"+surface) || path.endsWith("_"+surface+"_slope"))
                return FinialSupport.Profile.find(surface,false);
        return path.endsWith("_slope") ? FinialSupport.Profile.SLOPE : null;
    }

    private static Direction direction(BlockState state) {
        Direction result=Direction.byName(named(state,"facing"));
        return result!=null && result.getAxis().isHorizontal() ? result : Direction.EAST;
    }
    public static String named(BlockState state,String name) {
        Property<?> property=state.getBlock().getStateManager().getProperty(name);
        Object value=property==null ? null : state.getEntries().get(property);
        if(value instanceof StringIdentifiable named) return named.asString();
        return value==null ? "" : value.toString();
    }

    public static boolean isClear(BlockState state,WorldView world,BlockPos owner) {
        if(!(state.getBlock() instanceof FinialBlock finial)) return true;
        var shape=finial.getCollisionShape(state,world,owner,ShapeContext.absent());
        var fit=FinialSupport.fit(state,finial.style());
        var bounds=shape.getBoundingBox().offset(owner);
        BlockPos support=supportPos(state,owner);
        for(int y=(int)Math.floor(bounds.minY);y<(int)Math.ceil(bounds.maxY);y++)
            for(int x=(int)Math.floor(bounds.minX);x<(int)Math.ceil(bounds.maxX);x++)
                for(int z=(int)Math.floor(bounds.minZ);z<(int)Math.ceil(bounds.maxZ);z++) {
                    BlockPos cell=new BlockPos(x,y,z);
                    if(world.isOutOfHeightLimit(cell) || !world.getWorldBorder().contains(cell)) return false;
                    BlockState existing=world.getBlockState(cell);
                    if(existing.isReplaceable() || cell.equals(owner) && existing.isOf(finial)) continue;
                    var obstruction=existing.getCollisionShape(world,cell,ShapeContext.absent());
                    if(obstruction.isEmpty()) continue;
                    // Shapes can extend outside their registered cell; test the actual geometry.
                    var localObstruction=obstruction.offset(x-owner.getX(),y-owner.getY(),z-owner.getZ());
                    if(!VoxelShapes.matchesAnywhere(shape,localObstruction,BooleanBiFunction.AND)) continue;
                    if(fit.mounted() && (cell.equals(support) || coplanarCoping(state,world,owner,cell,fit))
                            && onlyTouchesMountingPlane(shape,localObstruction,fit)) continue;
                    return false;
                }
        return true;
    }

    private static boolean coplanarCoping(BlockState finial,BlockView world,BlockPos owner,BlockPos cell,FinialSupport.Fit fit) {
        BlockState found=world.getBlockState(cell);
        var id=Registries.BLOCK.getId(found.getBlock());
        if(!id.getNamespace().equals("erydon") || !id.getPath().endsWith("_coping_georgian")) return false;
        var state=fitted(finial,world,cell);
        var other=FinialSupport.fit(state,((FinialBlock)finial.getBlock()).style());
        if(!other.mounted() || Math.abs(other.gradientX()-fit.gradientX())>1e-6
                || Math.abs(other.gradientZ()-fit.gradientZ())>1e-6) return false;
        double px=other.centreX()+cell.getX()-owner.getX(),pz=other.centreZ()+cell.getZ()-owner.getZ();
        double atCentre=other.planeY()+cell.getY()+1-owner.getY()
                +other.gradientX()*(fit.centreX()-px)+other.gradientZ()*(fit.centreZ()-pz);
        return Math.abs(atCentre-fit.planeY())<1e-6;
    }

    private static boolean onlyTouchesMountingPlane(net.minecraft.util.shape.VoxelShape shape,
            net.minecraft.util.shape.VoxelShape obstruction,FinialSupport.Fit fit) {
        // Both existing support slopes and the fitted stub use 1/32-cell stair approximations.
        // Permit their conservative contact overlap, but reject posts above the actual plane.
        double tolerance=(Math.abs(fit.gradientX())+Math.abs(fit.gradientZ()))*(1+fit.baseWidth())/32+1e-6;
        var contact=VoxelShapes.combineAndSimplify(shape,obstruction,BooleanBiFunction.AND);
        for(var box:contact.getBoundingBoxes()) {
            double minimum=Math.min(Math.min(fit.bottom(box.minX,box.minZ),fit.bottom(box.minX,box.maxZ)),
                    Math.min(fit.bottom(box.maxX,box.minZ),fit.bottom(box.maxX,box.maxZ)));
            if(box.maxY>minimum+tolerance) return false;
        }
        return true;
    }
}
