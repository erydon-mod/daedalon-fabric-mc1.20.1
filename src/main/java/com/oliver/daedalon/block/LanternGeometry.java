package com.oliver.daedalon.block;

import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;

/** Cached conservative glazing/body envelopes and thin chain selection shapes. */
public final class LanternGeometry {
    private record Shifted(VoxelShape whole,net.minecraft.util.math.Vec3d offset,int cellY) {}
    private static final java.util.concurrent.ConcurrentHashMap<Shifted,VoxelShape> PLACED=new java.util.concurrent.ConcurrentHashMap<>();
    private static final VoxelShape[][][][][][] SHAPES=new VoxelShape[3][3][2][4][2][3];
    private static final VoxelShape[][][][][] WHOLE=new VoxelShape[3][3][2][4][2];
    private static final VoxelShape[][] CHAIN=new VoxelShape[2][2];
    static {
        for (LanternBlock.Size size:LanternBlock.Size.values()) for (int canopy=0;canopy<2;canopy++) {
            double r=.018*size.metres;
            VoxelShape chain=VoxelShapes.cuboid(.5-r,0,.5-r,.5+r,1,.5+r);
            if (canopy==1) {
                double cr=.075*size.metres;
                chain=VoxelShapes.union(chain,VoxelShapes.cuboid(.5-cr,.95,.5-cr,.5+cr,1,.5+cr));
            }
            CHAIN[size.ordinal()][canopy]=chain.simplify();
        }
        for (LanternBlock.Style style:LanternBlock.Style.values()) for (LanternBlock.Mount mount:LanternBlock.Mount.values())
            for (LanternBlock.Size size:LanternBlock.Size.values()) for (Direction facing:Direction.Type.HORIZONTAL) for (int canopy=0;canopy<2;canopy++) {
                double scale=size.metres,w=(mount==LanternBlock.Mount.WALL?style.wallWidth:mount==LanternBlock.Mount.HANGING?style.hangingWidth:style.width)*scale;
                double d=(mount==LanternBlock.Mount.WALL?style.wallDepth:mount==LanternBlock.Mount.HANGING?style.hangingDepth:style.depth)*scale;
                double y0=mount==LanternBlock.Mount.PIER?0:mount==LanternBlock.Mount.WALL?.5-scale/2:.5-scale;
                double z0=mount==LanternBlock.Mount.WALL?0:.5-d/2;
                double z1=mount==LanternBlock.Mount.WALL?d:.5+d/2;
                VoxelShape whole=rotatedBox(.5-w/2,y0,z0,.5+w/2,y0+scale,z1,facing);
                if (mount==LanternBlock.Mount.HANGING) {
                    double r=.018*scale;
                    whole=VoxelShapes.union(whole,VoxelShapes.cuboid(.5-r,.5,.5-r,.5+r,1,.5+r));
                    if (canopy==1) {
                        double cr=.075*scale;
                        whole=VoxelShapes.union(whole,VoxelShapes.cuboid(.5-cr,.95,.5-cr,.5+cr,1,.5+cr));
                    }
                }
                int f=horizontalIndex(facing);
                WHOLE[style.ordinal()][mount.ordinal()][size.ordinal()][f][canopy]=whole.simplify();
                for(int offset=-1;offset<=1;offset++) SHAPES[style.ordinal()][mount.ordinal()][size.ordinal()][f][canopy][offset+1]=VoxelShapes.combineAndSimplify(whole.offset(0,-offset,0),VoxelShapes.fullCube(),net.minecraft.util.function.BooleanBiFunction.AND);
            }
    }
    private LanternGeometry() {}
    public static LanternBlock.Mount mountForSide(Direction side) { return side==Direction.UP?LanternBlock.Mount.PIER:side==Direction.DOWN?LanternBlock.Mount.HANGING:LanternBlock.Mount.WALL; }
    public static Direction facingForSide(Direction side) { return side.getAxis().isHorizontal()?side:Direction.SOUTH; }
    private static int horizontalIndex(Direction facing) { return switch (facing) { case NORTH -> 0;case EAST -> 1;case SOUTH -> 2;case WEST -> 3;default -> throw new IllegalArgumentException("Lantern facing must be horizontal"); }; }
    private static VoxelShape rotatedBox(double x0,double y0,double z0,double x1,double y1,double z1,Direction facing) {
        return switch (facing) {
            case SOUTH -> VoxelShapes.cuboid(x0,y0,z0,x1,y1,z1);
            case WEST -> VoxelShapes.cuboid(1-z1,y0,x0,1-z0,y1,x1);
            case NORTH -> VoxelShapes.cuboid(1-x1,y0,1-z1,1-x0,y1,1-z0);
            default -> VoxelShapes.cuboid(z0,y0,1-x1,z1,y1,1-x0);
        };
    }
    public static VoxelShape shape(LanternBlock.Style style,LanternBlock.Mount mount,LanternBlock.Size size,Direction facing,boolean canopy,boolean lower) {
        return partShape(style,mount,size,facing,canopy,lower?-1:0);
    }
    public static VoxelShape partShape(LanternBlock.Style style,LanternBlock.Mount mount,LanternBlock.Size size,Direction facing,boolean canopy,int offset) {
        return SHAPES[style.ordinal()][mount.ordinal()][size.ordinal()][horizontalIndex(facing)][canopy?1:0][offset+1];
    }
    public static VoxelShape fullShape(LanternBlock.Style style,LanternBlock.Mount mount,LanternBlock.Size size,Direction facing,boolean canopy) {
        return WHOLE[style.ordinal()][mount.ordinal()][size.ordinal()][horizontalIndex(facing)][canopy?1:0];
    }
    public static VoxelShape placedShape(LanternBlock.Style style,net.minecraft.block.BlockState state,net.minecraft.util.math.Vec3d offset,int cellY) {
        var whole=fullShape(style,state.get(LanternBlock.MOUNT),state.get(LanternBlock.SIZE),state.get(LanternBlock.FACING),state.get(LanternBlock.CANOPY));
        return PLACED.computeIfAbsent(new Shifted(whole,offset,cellY),key->VoxelShapes.combineAndSimplify(
                whole.offset(offset.x,offset.y-cellY,offset.z),VoxelShapes.cuboid(-1,0,-1,2,1,2),net.minecraft.util.function.BooleanBiFunction.AND));
    }
    public static VoxelShape chain(LanternBlock.Size size,boolean canopy) { return CHAIN[size.ordinal()][canopy?1:0]; }
}
