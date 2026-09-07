package com.oliver.daedalon.client.fountain;

import net.minecraft.client.render.VertexConsumer;
import org.joml.Quaternionf;

/** One camera-facing quad, stretched along projected motion; no extra geometry or allocations. */
final class FountainStreakGeometry {
    private FountainStreakGeometry() {}
    static void emit(VertexConsumer out, Quaternionf q, float x, float y, float z, float width,
                     float vx, float vy, float vz, float u0, float u1, float v0, float v1,
                     float r, float g, float b, float a, int light) {
        float rx = 1 - 2 * (q.y*q.y + q.z*q.z), ry = 2*(q.x*q.y + q.w*q.z), rz = 2*(q.x*q.z - q.w*q.y);
        float ux = 2*(q.x*q.y - q.w*q.z), uy = 1 - 2*(q.x*q.x + q.z*q.z), uz = 2*(q.y*q.z + q.w*q.x);
        float rightMotion = vx*rx + vy*ry + vz*rz, upMotion = vx*ux + vy*uy + vz*uz;
        float speed = (float)Math.sqrt(rightMotion*rightMotion + upMotion*upMotion);
        float alongR = speed > .001F ? rightMotion/speed : 0;
        float alongU = speed > .001F ? upMotion/speed : 1;
        float length = width + Math.min(.22F, speed * .6F);
        float ax = (rx*alongR + ux*alongU)*length, ay = (ry*alongR + uy*alongU)*length, az = (rz*alongR + uz*alongU)*length;
        float bx = (rx*alongU - ux*alongR)*width, by = (ry*alongU - uy*alongR)*width, bz = (rz*alongU - uz*alongR)*width;
        vertex(out,x-bx-ax,y-by-ay,z-bz-az,u1,v1,r,g,b,a,light);
        vertex(out,x-bx+ax,y-by+ay,z-bz+az,u1,v0,r,g,b,a,light);
        vertex(out,x+bx+ax,y+by+ay,z+bz+az,u0,v0,r,g,b,a,light);
        vertex(out,x+bx-ax,y+by-ay,z+bz-az,u0,v1,r,g,b,a,light);
    }
    private static void vertex(VertexConsumer out,float x,float y,float z,float u,float v,float r,float g,float b,float a,int light) {
        out.vertex(x,y,z).texture(u,v).color(r,g,b,a).light(light).next();
    }
}
