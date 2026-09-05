package com.oliver.daedalon.block;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Fixed shell cells, shared by all finishes; no world scans or mesh work at query time. */
final class MonopterosGeometry {
    static final int RADIUS = 4;
    static final int HEIGHT_CELLS = 6;
    private static final VoxelShape[][] PARTS = new VoxelShape[3][9*9*HEIGHT_CELLS];
    private static final List<List<BlockPos>> OFFSETS = new ArrayList<>();

    static {
        for (MonopterosBlock.Diameter size : MonopterosBlock.Diameter.values()) {
            VoxelShape[] parts = PARTS[size.ordinal()];
            Arrays.fill(parts, VoxelShapes.empty());
            double height = MonopterosProfile.HEIGHT * size.metres;
            int halfCells = size.metres * 2;
            for (int y = 0; y < Math.ceil(height*4); y++) {
                double y0=y/4.0, y1=Math.min(height,(y+1)/4.0);
                double inner=Double.POSITIVE_INFINITY, outer=0;
                for (int band=0; band<32; band++) {
                    if ((band+1)*height/32 < y0 || band*height/32 > y1) continue;
                    inner=Math.min(inner, MonopterosProfile.RADII[band][0]*size.metres);
                    outer=Math.max(outer, MonopterosProfile.RADII[band][1]*size.metres);
                }
                for (int z=-halfCells; z<halfCells; z++) {
                    for (int x=-halfCells; x<halfCells; x++) {
                        double x0=x/4.0, x1=(x+1)/4.0, z0=z/4.0, z1=(z+1)/4.0;
                        double nearX=x0>0 ? x0 : Math.max(0,-x1);
                        double nearZ=z0>0 ? z0 : Math.max(0,-z1);
                        double farX=Math.max(Math.abs(x0),Math.abs(x1));
                        double farZ=Math.max(Math.abs(z0),Math.abs(z1));
                        if (Math.hypot(nearX,nearZ)>outer || Math.hypot(farX,farZ)<inner) continue;
                        // Quarter-block cells align with Minecraft cells after centring.
                        double worldX=x0+.5, worldZ=z0+.5;
                        int px=(int)Math.floor(worldX), py=(int)Math.floor(y0), pz=(int)Math.floor(worldZ);
                        int index=index(px,py,pz);
                        parts[index]=VoxelShapes.union(parts[index],VoxelShapes.cuboid(
                                worldX-px,y0-py,worldZ-pz,worldX+.25-px,y1-py,worldZ+.25-pz));
                    }
                }
            }
            List<BlockPos> offsets=new ArrayList<>();
            for (int y=0;y<HEIGHT_CELLS;y++) for (int z=-RADIUS;z<=RADIUS;z++) for (int x=-RADIUS;x<=RADIUS;x++) {
                int index=index(x,y,z);
                parts[index]=parts[index].simplify();
                if (!parts[index].isEmpty() && (x!=0 || y!=0 || z!=0)) offsets.add(new BlockPos(x,y,z));
            }
            OFFSETS.add(List.copyOf(offsets));
        }
    }

    private MonopterosGeometry() {}
    static int index(int x,int y,int z) { return (y*9+z+RADIUS)*9+x+RADIUS; }
    static VoxelShape part(MonopterosBlock.Diameter size,int x,int y,int z) {
        return PARTS[size.ordinal()][index(x,y,z)];
    }
    static List<BlockPos> offsets(MonopterosBlock.Diameter size) { return OFFSETS.get(size.ordinal()); }
}
