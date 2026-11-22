package com.oliver.daedalon.client.glaux.model;

import java.util.List;

/** Atlas-bound immutable vertex buffers, replaced only during resource reload. */
public record GlauxHeadMeshes(List<Part> near,List<Part> middle,List<Part> far) {
    public static volatile GlauxHeadMeshes current;
    public record Part(String name,float[] vertices,int hingeIndex) {
        public Part(String name,float[] vertices){this(name,vertices,name.startsWith("feather_")?Integer.parseInt(name.substring(8)):-1);}
    }
    public List<Part> atDistance(double squaredDistance) {
        return squaredDistance<36?near:squaredDistance<400?middle:far;
    }
}
