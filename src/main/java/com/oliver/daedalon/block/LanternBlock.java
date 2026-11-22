package com.oliver.daedalon.block;

import com.oliver.daedalon.registry.ModBlocks;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
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
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import java.util.List;

/** One lantern item chooses its mounting from the clicked support face. */
public final class LanternBlock extends Block {
    public static final EnumProperty<Mount> MOUNT=EnumProperty.of("mount",Mount.class);
    public static final EnumProperty<Size> SIZE=EnumProperty.of("size",Size.class);
    public static final DirectionProperty FACING=Properties.HORIZONTAL_FACING;
    public static final BooleanProperty CANOPY=BooleanProperty.of("canopy");
    public static final BooleanProperty LIT=Properties.LIT;
    public static final EnumProperty<Brightness> BRIGHTNESS=EnumProperty.of("brightness",Brightness.class);
    /** Virtual debug control; saved legacy fields retain world/structure compatibility. */
    public static final EnumProperty<Light> LIGHT=EnumProperty.of("light",Light.class);
    private final Style style;

    public LanternBlock(Settings settings,Style style) {
        super(settings.dynamicBounds().nonOpaque().pistonBehavior(PistonBehavior.BLOCK).noBlockBreakParticles());
        this.style=style;
        setDefaultState(getStateManager().getDefaultState().with(MOUNT,Mount.PIER).with(SIZE,Size.LARGE)
                .with(FACING,Direction.SOUTH).with(CANOPY,true).with(LIT,true).with(BRIGHTNESS,Brightness.FULL));
    }
    public Style style() { return style; }
    @Override public void appendTooltip(ItemStack stack,BlockView world,List<Text> tooltip,TooltipContext context) {
        super.appendTooltip(stack,world,tooltip,context);
        tooltip.add(Text.translatable("tooltip.daedalon.lantern.mount").formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("tooltip.daedalon.lantern.size").formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("tooltip.daedalon.lantern.chain").formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("tooltip.daedalon.lantern.light").formatted(Formatting.GRAY));
    }
    @Override protected void appendProperties(StateManager.Builder<Block,BlockState> builder) { builder.add(MOUNT,SIZE,FACING,CANOPY,LIT,BRIGHTNESS); }
    public static int lightLevel(BlockState state) { return state.get(LIT)?state.get(BRIGHTNESS).lightLevel:0; }
    public static Light light(BlockState state) { return !state.get(LIT)?Light.OFF:state.get(BRIGHTNESS)==Brightness.LOW?Light.LOW:Light.BRIGHT; }
    public static BlockState withLight(BlockState state,Light light) {
        return light==Light.OFF?state.with(LIT,false):state.with(LIT,true).with(BRIGHTNESS,light==Light.LOW?Brightness.LOW:Brightness.FULL);
    }
    /** A manually selected hanging body starts a new suspension at this owner. */
    public static BlockState withMount(BlockState state,Mount mount) {
        BlockState changed=state.with(MOUNT,mount);
        return mount==Mount.HANGING?changed.with(CANOPY,true):changed;
    }
    public static Mount mountForSide(Direction side) {
        return LanternGeometry.mountForSide(side);
    }
    public static Direction facingForSide(Direction side) { return LanternGeometry.facingForSide(side); }
    @Override public BlockState getPlacementState(ItemPlacementContext context) {
        Direction clicked=LanternPlacement.clickedSide(context);
        BlockState state=getDefaultState().with(MOUNT,mountForSide(clicked)).with(FACING,facingForSide(clicked));
        var tag=context.getStack().getSubNbt("BlockStateTag");
        if (tag!=null && "small".equals(tag.getString("size"))) state=state.with(SIZE,Size.SMALL);
        if (tag!=null && "false".equals(tag.getString("lit"))) state=state.with(LIT,false);
        if (tag!=null && "low".equals(tag.getString("brightness"))) state=state.with(BRIGHTNESS,Brightness.LOW);
        if (tag!=null && tag.contains("light")) {
            var selected=LIGHT.parse(tag.getString("light"));
            if(selected.isPresent()) state=withLight(state,selected.get());
        }
        if (context instanceof LanternPlacement.LowerPlacement) state=state.with(MOUNT,Mount.HANGING).with(CANOPY,false);
        return canOccupy(context.getWorld(),context.getBlockPos(),state) ? state : null;
    }
    public static boolean needsLowerPart(BlockState state) { return state.get(MOUNT)==Mount.HANGING || state.get(MOUNT)==Mount.WALL && state.get(SIZE)==Size.LARGE; }
    public static boolean needsUpperPart(BlockState state) { return state.get(MOUNT)!=Mount.HANGING && state.get(SIZE)==Size.LARGE; }
    public static boolean needsPart(BlockState state,int offset) { return offset<0?needsLowerPart(state):needsUpperPart(state); }
    /** Validate every occupied helper cell before any placement or resize writes. */
    public static boolean canOccupy(WorldAccess world,BlockPos pos,BlockState state) {
        if (world.isOutOfHeightLimit(pos) || !world.getWorldBorder().contains(pos)) return false;
        for (int offset:new int[]{-1,1}) if(needsPart(state,offset)) {
            BlockPos part=pos.up(offset);
            if (world.isOutOfHeightLimit(part) || !world.getWorldBorder().contains(part)) return false;
            BlockState found=world.getBlockState(part);
            // A debug mount change can replace this lantern's own suspension
            // with an upper body cell. Foreign chains remain obstructions.
            boolean ownChain=state.get(MOUNT)!=Mount.HANGING
                    && found.getBlock() instanceof LanternChainBlock
                    && pos.equals(LanternPlacement.resolveOwner(world,part));
            if (!found.isAir() && !ownChain && !LanternPartBlock.isOwnedBy(found,part,pos) && !LanternPartBlock.isOrphaned(world,part,found)) return false;
        }
        return true;
    }
    @Override public void onBlockAdded(BlockState state,World world,BlockPos pos,BlockState oldState,boolean notify) {
        super.onBlockAdded(state,world,pos,oldState,notify);
        if (!world.isClient) syncPart(world,pos,state);
    }
    @Override public void onStateReplaced(BlockState state,World world,BlockPos pos,BlockState next,boolean moved) {
        if (!world.isClient) {
            // Palette/material replacement keeps the same assembly when the
            // next lantern retains its hanging mount, even across designs.
            if (next.getBlock() instanceof LanternBlock) {
                if (next.get(MOUNT)!=Mount.HANGING) LanternPlacement.clearChainAbove(world,pos);
                syncPart(world,pos,next);
            }
            else {
                clearParts(world,pos);
                // Extension transfers the owner below a new chain cell.
                if (!(next.getBlock() instanceof LanternChainBlock)) LanternPlacement.clearChainAbove(world,pos);
            }
        }
        super.onStateReplaced(state,world,pos,next,moved);
    }
    private static void syncPart(World world,BlockPos pos,BlockState state) {
        if (!canOccupy(world,pos,state)) return;
        for(int offset:new int[]{-1,1}) {
            BlockPos part=pos.up(offset);
            if(needsPart(state,offset)) world.setBlockState(part,LanternPartBlock.withCellYOffset(ModBlocks.lanternPart().getDefaultState(),offset),Block.NOTIFY_ALL);
            else if(LanternPartBlock.isOwnedBy(world.getBlockState(part),part,pos)) world.removeBlock(part,false);
        }
        if (state.get(MOUNT)==Mount.HANGING) LanternPlacement.syncChainSize(world,pos,state.get(SIZE));
    }
    private static void clearParts(World world,BlockPos pos) {
        for(int offset:new int[]{-1,1}) {
            BlockPos part=pos.up(offset);
            if(LanternPartBlock.isOwnedBy(world.getBlockState(part),part,pos)) world.removeBlock(part,false);
        }
    }
    @Override public VoxelShape getOutlineShape(BlockState state,BlockView world,BlockPos pos,ShapeContext context) {
        return LanternGeometry.placedShape(style,state,LanternSupport.offset(world,pos,state),0);
    }
    @Override public VoxelShape getCollisionShape(BlockState state,BlockView world,BlockPos pos,ShapeContext context) { return getOutlineShape(state,world,pos,context); }
    @Override public VoxelShape getCullingShape(BlockState state,BlockView world,BlockPos pos) { return VoxelShapes.empty(); }
    // Lighting must not derive opacity from the neighbour-aware interaction shape.
    @Override public int getOpacity(BlockState state,BlockView world,BlockPos pos) { return 0; }
    @Override public boolean isTransparent(BlockState state,BlockView world,BlockPos pos) { return true; }
    @Override public BlockState rotate(BlockState state,BlockRotation rotation) { return state.with(FACING,rotation.rotate(state.get(FACING))); }
    @Override public BlockState mirror(BlockState state,BlockMirror mirror) { return rotate(state,mirror.getRotation(state.get(FACING))); }
    @Override public ItemStack getPickStack(BlockView world,BlockPos pos,BlockState state) {
        ItemStack stack=super.getPickStack(world,pos,state);
        stack.getOrCreateSubNbt("BlockStateTag").putString("size",state.get(SIZE).asString());
        stack.getOrCreateSubNbt("BlockStateTag").putString("lit",state.get(LIT).toString());
        stack.getOrCreateSubNbt("BlockStateTag").putString("brightness",state.get(BRIGHTNESS).asString());
        stack.getOrCreateSubNbt("BlockStateTag").putString("light",light(state).asString());
        return stack;
    }

    public enum Mount implements StringIdentifiable {
        PIER("pier"),WALL("wall"),HANGING("hanging");
        private final String id;
        Mount(String id) { this.id=id; }
        @Override public String asString() { return id; }
    }
    public enum Size implements StringIdentifiable {
        SMALL("small",.75F),LARGE("large",1.25F);
        private final String id;
        public final float metres;
        Size(String id,float metres) { this.id=id;this.metres=metres; }
        @Override public String asString() { return id; }
    }
    public enum Brightness implements StringIdentifiable {
        FULL("full",15),LOW("low",10);
        private final String id;
        public final int lightLevel;
        Brightness(String id,int lightLevel) { this.id=id;this.lightLevel=lightLevel; }
        @Override public String asString() { return id; }
    }
    public enum Light implements StringIdentifiable {
        LOW("low"),BRIGHT("bright"),OFF("off");
        private final String id;
        Light(String id) { this.id=id; }
        @Override public String asString() { return id; }
    }
    /** Bounds are shared by all stone and aged finishes of a design. */
    public enum Style {
        GEORGIAN(.642481626,.556405426,.575383722,.319776624,.575383722,.498296976),
        GOTHIC(.385001302,.444090605,.359805853,.320600510,.359805853,.415060193),
        BYZANTINE(.524109424,.454412908,.431406439,.386550040,.497530371,.431406439);
        public final double width,depth,wallWidth,wallDepth,hangingWidth,hangingDepth;
        Style(double width,double depth,double wallWidth,double wallDepth,double hangingWidth,double hangingDepth) {
            this.width=width;this.depth=depth;this.wallWidth=wallWidth;this.wallDepth=wallDepth;this.hangingWidth=hangingWidth;this.hangingDepth=hangingDepth;
        }
        public String id() { return name().toLowerCase(java.util.Locale.ROOT); }
    }
}
