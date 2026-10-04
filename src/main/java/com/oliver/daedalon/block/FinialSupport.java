package com.oliver.daedalon.block;

import com.google.gson.JsonParser;
import net.minecraft.block.BlockState;
import net.minecraft.util.StringIdentifiable;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** Saved mounting data keeps the model, interaction shape and copied preview in agreement. */
public final class FinialSupport {
    private FinialSupport() { }
    public static final String TEMPLATE = "/assets/daedalon/authoring_models/block/finial/support_base.json";
    public static final double COPING_THICKNESS = 3.6 / 16.0;
    private static final double[] BASE_DIMENSIONS = baseDimensions();
    public static final double BASE_THICKNESS = BASE_DIMENSIONS[1];
    public static final double BASE_WIDTH = BASE_DIMENSIONS[0];

    public enum Profile implements StringIdentifiable {
        NONE("none",0,1,0,0,false),
        SLOPE("slope",0,1,0,-1,false),
        SHALLOW_LOWER("shallow_lower",0,1,-.5,-1,false),
        SHALLOW_UPPER("shallow_upper",0,1,0,-.5,false),
        STEEP_LOWER("steep_lower",0,.5,0,-1,false),
        STEEP_UPPER("steep_upper",.5,1,0,-1,false),
        STEEP_UPPER_OFFSET("steep_upper_offset",.5,1,0,-1,false,true),
        DIAGONAL("diagonal",Alignment.DIAGONAL,false),
        SHALLOW_BROAD_RIGHT("shallow_broad_right",Alignment.SHALLOW_BROAD_RIGHT,false),
        SHALLOW_BROAD_LEFT("shallow_broad_left",Alignment.SHALLOW_BROAD_LEFT,false),
        SHALLOW_BROAD_WIDE_RIGHT("shallow_broad_wide_right",Alignment.SHALLOW_BROAD_WIDE_RIGHT,false),
        SHALLOW_BROAD_WIDE_LEFT("shallow_broad_wide_left",Alignment.SHALLOW_BROAD_WIDE_LEFT,false),
        SHALLOW_NARROW_RIGHT("shallow_narrow_right",Alignment.SHALLOW_NARROW_RIGHT,false),
        SHALLOW_NARROW_LEFT("shallow_narrow_left",Alignment.SHALLOW_NARROW_LEFT,false),
        SHALLOW_NARROW_THIN_RIGHT("shallow_narrow_thin_right",Alignment.SHALLOW_NARROW_THIN_RIGHT,false),
        SHALLOW_NARROW_THIN_LEFT("shallow_narrow_thin_left",Alignment.SHALLOW_NARROW_THIN_LEFT,false),
        COPING_FLAT("coping_flat",0,1,0,0,true),
        COPING_SLOPE("coping_slope",0,1,0,-1,true),
        COPING_SHALLOW_LOWER("coping_shallow_lower",0,1,-.5,-1,true),
        COPING_SHALLOW_UPPER("coping_shallow_upper",0,1,0,-.5,true),
        COPING_STEEP_LOWER("coping_steep_lower",0,.5,0,-1,true),
        COPING_STEEP_UPPER("coping_steep_upper",.5,1,0,-1,true),
        COPING_STEEP_UPPER_OFFSET("coping_steep_upper_offset",.5,1,0,-1,true,true),
        COPING_DIAGONAL("coping_diagonal",Alignment.DIAGONAL),
        COPING_SHALLOW_BROAD_RIGHT("coping_shallow_broad_right",Alignment.SHALLOW_BROAD_RIGHT),
        COPING_SHALLOW_BROAD_LEFT("coping_shallow_broad_left",Alignment.SHALLOW_BROAD_LEFT),
        COPING_SHALLOW_BROAD_WIDE_RIGHT("coping_shallow_broad_wide_right",Alignment.SHALLOW_BROAD_WIDE_RIGHT),
        COPING_SHALLOW_BROAD_WIDE_LEFT("coping_shallow_broad_wide_left",Alignment.SHALLOW_BROAD_WIDE_LEFT),
        COPING_SHALLOW_NARROW_RIGHT("coping_shallow_narrow_right",Alignment.SHALLOW_NARROW_RIGHT),
        COPING_SHALLOW_NARROW_LEFT("coping_shallow_narrow_left",Alignment.SHALLOW_NARROW_LEFT),
        COPING_SHALLOW_NARROW_THIN_RIGHT("coping_shallow_narrow_thin_right",Alignment.SHALLOW_NARROW_THIN_RIGHT),
        COPING_SHALLOW_NARROW_THIN_LEFT("coping_shallow_narrow_thin_left",Alignment.SHALLOW_NARROW_THIN_LEFT);

        public final double start,end,high,low;
        public final boolean coping;
        public final boolean offset;
        public final Alignment alignment;
        private final String name;
        Profile(String name,double start,double end,double high,double low,boolean coping) {
            this.name=name; this.start=start; this.end=end; this.high=high; this.low=low; this.coping=coping;
            this.alignment=Alignment.AXIS; this.offset=false;
        }
        Profile(String name,double start,double end,double high,double low,boolean coping,boolean offset) {
            this.name=name; this.start=start; this.end=end; this.high=high; this.low=low; this.coping=coping;
            this.alignment=Alignment.AXIS; this.offset=offset;
        }
        Profile(String name,Alignment alignment) {
            this(name,alignment,true);
        }
        Profile(String name,Alignment alignment,boolean coping) {
            this.name=name; this.start=0; this.end=1; this.high=0; this.low=0; this.coping=coping; this.alignment=alignment; this.offset=false;
        }
        @Override public String asString() { return name; }
        public double gradient() { return (high-low)/(end-start); }
        public double height(double x) { return high-(x-start)*gradient(); }
        public static Profile find(String surface,boolean coping) {
            String name=(coping ? "coping_" : "")+surface;
            for(Profile value:values()) if(value.name.equals(name)) return value;
            return NONE;
        }
        public Profile reflected() {
            return alignment==Alignment.AXIS || alignment==Alignment.DIAGONAL ? this
                    : find(alignment.name.replace("right","temporary").replace("left","right").replace("temporary","left"),coping);
        }
    }

    public enum Alignment implements StringIdentifiable {
        AXIS("axis",0,0,0),
        DIAGONAL("diagonal",-Math.PI/4,-.25,-.25),
        SHALLOW_BROAD_RIGHT("shallow_broad_right",-Math.atan(2),-.2,-.1),
        SHALLOW_BROAD_LEFT("shallow_broad_left",Math.atan(2),-.2,.1),
        SHALLOW_BROAD_WIDE_RIGHT("shallow_broad_wide_right",-Math.atan(2),-.4,-.2),
        SHALLOW_BROAD_WIDE_LEFT("shallow_broad_wide_left",Math.atan(2),-.4,.2),
        SHALLOW_NARROW_RIGHT("shallow_narrow_right",-Math.atan(2),-.65,-.2),
        SHALLOW_NARROW_LEFT("shallow_narrow_left",Math.atan(2),-.65,.2),
        SHALLOW_NARROW_THIN_RIGHT("shallow_narrow_thin_right",-Math.atan(2),-.45,-.1),
        SHALLOW_NARROW_THIN_LEFT("shallow_narrow_thin_left",Math.atan(2),-.45,.1);
        private final String name;
        public final double yaw,offsetX,offsetZ;
        Alignment(String name,double yaw,double offsetX,double offsetZ) {
            this.name=name; this.yaw=yaw; this.offsetX=offsetX; this.offsetZ=offsetZ;
        }
        @Override public String asString() { return name; }
        public static Alignment find(String name) {
            for(Alignment value:values()) if(value.name.equals(name)) return value;
            return AXIS;
        }
    }

    public record Fit(double centreX,double centreZ,double planeY,double gradientX,double gradientZ,
                      double yaw,double baseWidth,double bodyY,boolean mounted) {
        public double bottom(double x,double z) {
            return planeY+gradientX*(x-centreX)+gradientZ*(z-centreZ);
        }
        public double x(double localX,double localZ) {
            return centreX+localX*Math.cos(yaw)-localZ*Math.sin(yaw);
        }
        public double z(double localX,double localZ) {
            return centreZ+localX*Math.sin(yaw)+localZ*Math.cos(yaw);
        }
    }

    private record Key(FixedDecorBlock.Style style,TwoSizeDecorBlock.Size size,Profile profile,Direction facing) { }
    private static final java.util.Map<Key,Fit> FITS=new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Map<Key,VoxelShape> SHAPES=new java.util.concurrent.ConcurrentHashMap<>();
    private static Key key(BlockState state,FixedDecorBlock.Style style) {
        Profile profile=state.get(FinialBlock.SUPPORT);
        return new Key(style,state.get(TwoSizeDecorBlock.SIZE),profile,
                profile==Profile.NONE ? Direction.EAST : state.get(FinialBlock.FACING));
    }
    public static Fit fit(BlockState state,FixedDecorBlock.Style style) {
        return FITS.computeIfAbsent(key(state,style),ignored -> createFit(state,style));
    }
    private static Fit createFit(BlockState state,FixedDecorBlock.Style style) {
        Profile profile=state.get(FinialBlock.SUPPORT);
        double scale=state.get(TwoSizeDecorBlock.SIZE)==TwoSizeDecorBlock.Size.SMALL ? 1 : 2;
        if(profile==Profile.NONE) return new Fit(.5,.5,0,0,0,0,0,0,false);
        Direction facing=state.get(FinialBlock.FACING);
        double angle=quarterAngle(facing),cos=Math.cos(angle),sin=Math.sin(angle);
        Alignment alignment=profile.alignment;
        double normalLength=Math.sqrt(1+profile.gradient()*profile.gradient());
        double thickness=profile.coping ? COPING_THICKNESS : 0;
        double nativeX=(profile.start+profile.end)*.5+profile.gradient()*thickness/normalLength;
        double nativeZ=.5;
        if(alignment!=Alignment.AXIS) { nativeX=.5+alignment.offsetX; nativeZ=.5+alignment.offsetZ; }
        double x=.5+(nativeX-.5)*cos-(nativeZ-.5)*sin;
        double z=.5+(nativeX-.5)*sin+(nativeZ-.5)*cos;
        if(profile.offset) {
            x-=facing.getOffsetX(); z-=facing.getOffsetZ();
        }
        double planeY=profile.height((profile.start+profile.end)*.5)+thickness/normalLength-(profile.coping ? 1 : 0);
        double gradientX=-profile.gradient()*cos,gradientZ=-profile.gradient()*sin;
        double yaw=angle+alignment.yaw;
        double width=(style.finialFootWidth()+1.0/16.0)*scale*BASE_WIDTH;
        double projectedGradient=Math.abs(gradientX*Math.cos(yaw)+gradientZ*Math.sin(yaw))
                +Math.abs(-gradientX*Math.sin(yaw)+gradientZ*Math.cos(yaw));
        return new Fit(x,z,planeY,gradientX,gradientZ,yaw,width,
                planeY+projectedGradient*width*.5+BASE_THICKNESS*scale,true);
    }

    public static VoxelShape shape(BlockState state,FixedDecorBlock.Style style) {
        return SHAPES.computeIfAbsent(key(state,style),ignored -> createShape(state,style));
    }
    private static VoxelShape createShape(BlockState state,FixedDecorBlock.Style style) {
        Fit fit=fit(state,style);
        double scale=state.get(TwoSizeDecorBlock.SIZE)==TwoSizeDecorBlock.Size.SMALL ? 1 : 2;
        double width=style.width()*scale,depth=style.depth()*scale;
        double halfX=(Math.abs(Math.cos(fit.yaw))*width+Math.abs(Math.sin(fit.yaw))*depth)*.5;
        double halfZ=(Math.abs(Math.sin(fit.yaw))*width+Math.abs(Math.cos(fit.yaw))*depth)*.5;
        VoxelShape result=VoxelShapes.cuboid(fit.centreX-halfX,fit.bodyY,fit.centreZ-halfZ,
                fit.centreX+halfX,fit.bodyY+scale,fit.centreZ+halfZ);
        if(!fit.mounted) return result;
        double half=fit.baseWidth*.5;
        // Finial yaw follows the incline axis; a flat diagonal base has no gradient.
        int slices=fit.gradientX==0 && fit.gradientZ==0 ? 1 : 32;
        for(int i=0;i<slices;i++) {
            double a=-half+fit.baseWidth*i/slices,b=-half+fit.baseWidth*(i+1)/slices;
            double[] xs={fit.x(a,-half),fit.x(a,half),fit.x(b,-half),fit.x(b,half)};
            double[] zs={fit.z(a,-half),fit.z(a,half),fit.z(b,-half),fit.z(b,half)};
            double minX=java.util.Arrays.stream(xs).min().orElseThrow(),maxX=java.util.Arrays.stream(xs).max().orElseThrow();
            double minZ=java.util.Arrays.stream(zs).min().orElseThrow(),maxZ=java.util.Arrays.stream(zs).max().orElseThrow();
            double bottom=Double.POSITIVE_INFINITY;
            for(int corner=0;corner<4;corner++) bottom=Math.min(bottom,fit.bottom(xs[corner],zs[corner]));
            result=VoxelShapes.union(result,VoxelShapes.cuboid(minX,bottom,minZ,maxX,fit.bodyY,maxZ));
        }
        return result.simplify();
    }

    public static double quarterAngle(Direction facing) {
        return switch(facing) {
            case EAST -> 0; case SOUTH -> Math.PI/2; case WEST -> Math.PI; case NORTH -> -Math.PI/2;
            default -> throw new IllegalArgumentException("Finial facing must be horizontal");
        };
    }

    private static double[] baseDimensions() {
        try(var input=FinialSupport.class.getResourceAsStream(TEMPLATE)) {
            if(input==null) throw new IllegalStateException("Missing editable finial base "+TEMPLATE);
            var json=JsonParser.parseReader(new InputStreamReader(input,StandardCharsets.UTF_8)).getAsJsonObject();
            if(json.getAsJsonArray("elements").size()!=1) throw new IllegalStateException("Finial support template must contain one square cuboid");
            var element=json.getAsJsonArray("elements").get(0).getAsJsonObject();
            double width=(element.getAsJsonArray("to").get(0).getAsDouble()-element.getAsJsonArray("from").get(0).getAsDouble())/16;
            double depth=(element.getAsJsonArray("to").get(2).getAsDouble()-element.getAsJsonArray("from").get(2).getAsDouble())/16;
            double height=(element.getAsJsonArray("to").get(1).getAsDouble()-element.getAsJsonArray("from").get(1).getAsDouble())/16;
            if(!Double.isFinite(height) || height<=0 || height>.25) throw new IllegalStateException("Finial base height must be 0..4 model units");
            if(!Double.isFinite(width) || width<=0 || width>2 || Math.abs(width-depth)>.00001)
                throw new IllegalStateException("Finial base must have a square footprint of 0..32 model units");
            return new double[]{width,height,depth};
        } catch(java.io.IOException exception) { throw new IllegalStateException("Cannot read editable finial base",exception); }
    }
}
