package com.oliver.daedalon.client.model.obj;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Carries Daedalon quad metadata across Indium's compact terrain vertex staging
 * objects into Iris's extended shader vertex encoder. This preserves OBJ normals
 * and gives contained basin water, lantern panes and flames their own shader identity,
 * without changing the surrounding solid block's material or world fluids.
 */
public final class ShaderTerrainNormalBridge {
    private static final int OBJ_TAG_SIGNATURE = 0x0DAE0000;
    private static final int CONTAINED_WATER_TAG = 0x0DAF0000;
    private static final int STATIONARY_WATER_TAG = CONTAINED_WATER_TAG | 1;
    private static final int GLAZING_OBJ_TAG_SIGNATURE = 0x0DB00000;
    private static final int FLAME_OBJ_TAG_SIGNATURE = 0x0DB10000;
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
        int signature = tag & OBJ_TAG_SIGNATURE_MASK;
        return signature == OBJ_TAG_SIGNATURE || signature == GLAZING_OBJ_TAG_SIGNATURE || signature == FLAME_OBJ_TAG_SIGNATURE;
    }

    public static int encodeGlazingObjTag(int materialTag) {
        // Reuse the same bounds check as ordinary OBJ material tags.
        return GLAZING_OBJ_TAG_SIGNATURE | (encodeObjTag(materialTag) & OBJ_MATERIAL_TAG_MASK);
    }

    public static boolean isGlazingObjQuad(int tag) {
        return (tag & OBJ_TAG_SIGNATURE_MASK) == GLAZING_OBJ_TAG_SIGNATURE;
    }

    public static int encodeFlameObjTag(int materialTag) {
        return FLAME_OBJ_TAG_SIGNATURE | (encodeObjTag(materialTag) & OBJ_MATERIAL_TAG_MASK);
    }

    public static boolean isFlameObjQuad(int tag) {
        return (tag & OBJ_TAG_SIGNATURE_MASK) == FLAME_OBJ_TAG_SIGNATURE;
    }

    public static int containedWaterTag() {
        return CONTAINED_WATER_TAG;
    }

    public static int stationaryWaterTag() {
        return STATIONARY_WATER_TAG;
    }

    public static boolean isContainedWaterQuad(int tag) {
        return tag == CONTAINED_WATER_TAG || tag == STATIONARY_WATER_TAG;
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
        publish(0, false, false, vertexArray,
                x0, y0, z0,
                x1, y1, z1,
                x2, y2, z2,
                x3, y3, z3);
    }

    public static void publishContainedWater(Object vertexArray,
                                             float x0, float y0, float z0,
                                             float x1, float y1, float z1,
                                             float x2, float y2, float z2,
                                             float x3, float y3, float z3) {
        publishContainedWater(CONTAINED_WATER_TAG, vertexArray,
                x0, y0, z0, x1, y1, z1, x2, y2, z2, x3, y3, z3);
    }

    public static void publishContainedWater(int waterTag, Object vertexArray,
                                             float x0, float y0, float z0,
                                             float x1, float y1, float z1,
                                             float x2, float y2, float z2,
                                             float x3, float y3, float z3) {
        publish(waterTag, false, false, vertexArray,
                x0, y0, z0,
                x1, y1, z1,
                x2, y2, z2,
                x3, y3, z3);
    }

    public static void publishGlazing(Object vertexArray,
                                      float x0, float y0, float z0,
                                      float x1, float y1, float z1,
                                      float x2, float y2, float z2,
                                      float x3, float y3, float z3) {
        publish(0, true, false, vertexArray,
                x0, y0, z0,
                x1, y1, z1,
                x2, y2, z2,
                x3, y3, z3);
    }

    public static void publishFlame(Object vertexArray,
                                    float x0, float y0, float z0,
                                    float x1, float y1, float z1,
                                    float x2, float y2, float z2,
                                    float x3, float y3, float z3) {
        publish(0, false, true, vertexArray,
                x0, y0, z0, x1, y1, z1, x2, y2, z2, x3, y3, z3);
    }

    private static void publish(int waterTag,
                                boolean glazing,
                                boolean flame,
                                Object vertexArray,
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
        pending.waterTag = waterTag;
        pending.claimedWaterTag = 0;
        pending.glazing = glazing;
        pending.claimedGlazing = false;
        pending.flame = flame;
        pending.claimedFlame = false;
        pending.nextIndex = 0;
    }

    public static void discard() {
        PendingNormals pending = PENDING.get();
        pending.vertexArray = null;
        pending.published = false;
        pending.claimed = false;
        pending.waterTag = 0;
        pending.claimedWaterTag = 0;
        pending.glazing = false;
        pending.claimedGlazing = false;
        pending.flame = false;
        pending.claimedFlame = false;
        pending.nextIndex = 0;
    }

    public static boolean claim(Object vertexArray) {
        PendingNormals pending = PENDING.get();
        boolean matches = pending.published && pending.vertexArray == vertexArray;
        pending.vertexArray = null;
        pending.published = false;
        pending.claimed = matches;
        pending.claimedWaterTag = matches ? pending.waterTag : 0;
        pending.claimedGlazing = matches && pending.glazing;
        pending.claimedFlame = matches && pending.flame;
        pending.waterTag = 0;
        pending.glazing = false;
        pending.flame = false;
        pending.nextIndex = 0;
        if (matches && !LOGGED_ACTIVE.get() && LOGGED_ACTIVE.compareAndSet(false, true)) {
            LOGGER.info("Iris terrain normal bridge active; preserving Fabric per-vertex normals");
        }
        return matches;
    }

    public static boolean isClaimedContainedWater() {
        return isContainedWaterQuad(PENDING.get().claimedWaterTag);
    }

    public static boolean isClaimedStationaryWater() {
        return PENDING.get().claimedWaterTag == STATIONARY_WATER_TAG;
    }

    public static boolean isClaimedGlazing() {
        return PENDING.get().claimedGlazing;
    }

    public static boolean isClaimedFlame() {
        return PENDING.get().claimedFlame;
    }

    public static int nextNormal(int fallback) {
        PendingNormals pending = PENDING.get();
        if (!pending.claimed || pending.nextIndex >= 4) {
            return fallback;
        }
        return pending.packed[pending.nextIndex++];
    }

    public static void release() {
        PendingNormals pending = PENDING.get();
        pending.claimed = false;
        pending.claimedWaterTag = 0;
        pending.claimedGlazing = false;
        pending.claimedFlame = false;
        pending.nextIndex = 0;
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
        private int waterTag;
        private int claimedWaterTag;
        private boolean glazing;
        private boolean claimedGlazing;
        private boolean flame;
        private boolean claimedFlame;
        private int nextIndex;
    }
}
