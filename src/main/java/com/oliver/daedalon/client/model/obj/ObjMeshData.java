package com.oliver.daedalon.client.model.obj;

import net.minecraft.util.Identifier;

import java.util.List;
import java.util.Locale;
import java.util.Map;

record ObjMeshData(Identifier objId,
                   List<Vec3> positions,
                   List<Vec2> textureCoordinates,
                   List<Vec3> normals,
                   List<Face> faces,
                   List<Identifier> mtlIds,
                   Map<String, Identifier> materialTextures,
                   int sourceTriangleCount,
                   int sourceQuadCount,
                   int triangulatedNgonCount,
                   int triangulatedNgonTriangleCount,
                   int objectCount,
                   int groupCount,
                   Bounds originalBounds,
                   long parseNanos) {
    ObjMeshData {
        positions = List.copyOf(positions);
        textureCoordinates = List.copyOf(textureCoordinates);
        normals = List.copyOf(normals);
        faces = List.copyOf(faces);
        mtlIds = List.copyOf(mtlIds);
        materialTextures = Map.copyOf(materialTextures);
    }

    int emittedTriangleCount() {
        return sourceTriangleCount + triangulatedNgonTriangleCount;
    }

    int finalQuadCount() {
        return emittedTriangleCount() + sourceQuadCount;
    }

    int materialCount() {
        return (int) faces.stream().map(Face::material).distinct().count();
    }

    record Vec2(float u, float v) {
    }

    record Vec3(float x, float y, float z) {
        boolean isFinite() {
            return Float.isFinite(x) && Float.isFinite(y) && Float.isFinite(z);
        }
    }

    record VertexRef(int positionIndex, int textureCoordinateIndex, int normalIndex) {
        static final int MISSING = -1;
    }

    record Face(List<VertexRef> vertices, String material, String objectName, String groupName) {
        Face {
            vertices = List.copyOf(vertices);
        }

        boolean isTriangle() {
            return vertices.size() == 3;
        }
    }

    record Bounds(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        static Bounds of(List<Vec3> positions) {
            float minX = Float.POSITIVE_INFINITY;
            float minY = Float.POSITIVE_INFINITY;
            float minZ = Float.POSITIVE_INFINITY;
            float maxX = Float.NEGATIVE_INFINITY;
            float maxY = Float.NEGATIVE_INFINITY;
            float maxZ = Float.NEGATIVE_INFINITY;

            for (Vec3 position : positions) {
                minX = Math.min(minX, position.x());
                minY = Math.min(minY, position.y());
                minZ = Math.min(minZ, position.z());
                maxX = Math.max(maxX, position.x());
                maxY = Math.max(maxY, position.y());
                maxZ = Math.max(maxZ, position.z());
            }

            return new Bounds(minX, minY, minZ, maxX, maxY, maxZ);
        }

        float spanX() {
            return maxX - minX;
        }

        float spanY() {
            return maxY - minY;
        }

        float spanZ() {
            return maxZ - minZ;
        }

        float largestSpan() {
            return Math.max(spanX(), Math.max(spanY(), spanZ()));
        }

        @Override
        public String toString() {
            return String.format(Locale.ROOT,
                    "[(%.6f, %.6f, %.6f) -> (%.6f, %.6f, %.6f); span=(%.6f, %.6f, %.6f)]",
                    minX, minY, minZ, maxX, maxY, maxZ, spanX(), spanY(), spanZ());
        }
    }
}
