package com.oliver.daedalon.client.model.obj;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Carries Fabric Renderer API per-vertex normals across Indium's compact terrain
 * vertex staging objects into Iris's extended shader vertex encoder.
 */
public final class ShaderTerrainNormalBridge {
    private static final int OBJ_TAG_SIGNATURE = 0x0DAE0000;
    private static final int OBJ_TAG_SIGNATURE_MASK = 0xFFFF0000;
    private static final int OBJ_MATERIAL_TAG_MASK = 0x0000FFFF;
    private static final Logger LOGGER = LoggerFactory.getLogger("Daedalon/ObjShaderNormals");
    private static final AtomicBoolean LOGGED_ACTIVE = new AtomicBoolean();
    private static final ThreadLocal<PendingNormals> PENDING = ThreadLocal.withInitial(PendingNormals::new);

    private ShaderTerrainNormalBridge() {
    }

    public static int encodeObjTag(int materialTag) {
        if (materialTag < 0 || materialTag > OBJ_MATERIAL_TAG_MASK) {
            throw new IllegalArgumentException("OBJ material tag is outside 0..65535: " + materialTag);
        }
        return OBJ_TAG_SIGNATURE | materialTag;
    }

    public static boolean isObjQuad(int tag) {
        return (tag & OBJ_TAG_SIGNATURE_MASK) == OBJ_TAG_SIGNATURE;
    }

    public static int decodeObjMaterialTag(int tag) {
        if (!isObjQuad(tag)) {
            throw new IllegalArgumentException("Quad is not owned by Daedalon OBJ rendering: " + tag);
        }
        return tag & OBJ_MATERIAL_TAG_MASK;
    }

    public static void publish(Object vertexArray,
                               float x0, float y0, float z0,
                               float x1, float y1, float z1,
                               float x2, float y2, float z2,
                               float x3, float y3, float z3) {
        PendingNormals pending = PENDING.get();
        pending.vertexArray = vertexArray;
        pending.packed[0] = pack(x0, y0, z0);
        pending.packed[1] = pack(x1, y1, z1);
        pending.packed[2] = pack(x2, y2, z2);
        pending.packed[3] = pack(x3, y3, z3);
        pending.published = true;
        pending.claimed = false;
        pending.nextIndex = 0;
    }

    public static void discard() {
        PendingNormals pending = PENDING.get();
        pending.vertexArray = null;
        pending.published = false;
        pending.claimed = false;
        pending.nextIndex = 0;
    }

    public static boolean claim(Object vertexArray) {
        PendingNormals pending = PENDING.get();
        boolean matches = pending.published && pending.vertexArray == vertexArray;
        pending.vertexArray = null;
        pending.published = false;
        pending.claimed = matches;
        pending.nextIndex = 0;
        if (matches && LOGGED_ACTIVE.compareAndSet(false, true)) {
            LOGGER.info("Iris terrain normal bridge active; preserving Fabric per-vertex normals");
        }
        return matches;
    }

    public static int nextNormal(int fallback) {
        PendingNormals pending = PENDING.get();
        if (!pending.claimed || pending.nextIndex >= 4) {
            return fallback;
        }
        return pending.packed[pending.nextIndex++];
    }

    public static void release() {
        PENDING.get().claimed = false;
    }

    private static int pack(float x, float y, float z) {
        return ((int) (x * 127.0F) & 0xFF)
                | (((int) (y * 127.0F) & 0xFF) << 8)
                | (((int) (z * 127.0F) & 0xFF) << 16);
    }

    private static final class PendingNormals {
        private final int[] packed = new int[4];
        private Object vertexArray;
        private boolean published;
        private boolean claimed;
        private int nextIndex;
    }
}
