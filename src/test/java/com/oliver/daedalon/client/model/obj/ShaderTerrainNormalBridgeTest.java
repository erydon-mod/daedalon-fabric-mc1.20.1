package com.oliver.daedalon.client.model.obj;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShaderTerrainNormalBridgeTest {
    @AfterEach
    void clearPendingQuad() {
        ShaderTerrainNormalBridge.discard();
    }

    @Test
    void objTagsAreExplicitAndReversible() {
        assertFalse(ShaderTerrainNormalBridge.isObjQuad(0));
        assertFalse(ShaderTerrainNormalBridge.isObjQuad(-1));

        int first = ShaderTerrainNormalBridge.encodeObjTag(0);
        int last = ShaderTerrainNormalBridge.encodeObjTag(65535);
        assertTrue(ShaderTerrainNormalBridge.isObjQuad(first));
        assertTrue(ShaderTerrainNormalBridge.isObjQuad(last));
        assertEquals(0, ShaderTerrainNormalBridge.decodeObjMaterialTag(first));
        assertEquals(65535, ShaderTerrainNormalBridge.decodeObjMaterialTag(last));
    }

    @Test
    void invalidAndForeignTagsFailClosed() {
        assertThrows(IllegalArgumentException.class,
                () -> ShaderTerrainNormalBridge.encodeObjTag(-1));
        assertThrows(IllegalArgumentException.class,
                () -> ShaderTerrainNormalBridge.encodeObjTag(65536));
        assertThrows(IllegalArgumentException.class,
                () -> ShaderTerrainNormalBridge.decodeObjMaterialTag(0));
    }

    @Test
    void containedWaterTagIsSeparateFromObjMaterials() {
        int tag = ShaderTerrainNormalBridge.containedWaterTag();

        assertTrue(ShaderTerrainNormalBridge.isContainedWaterQuad(tag));
        assertFalse(ShaderTerrainNormalBridge.isObjQuad(tag));
        assertFalse(ShaderTerrainNormalBridge.isContainedWaterQuad(0));
        assertFalse(ShaderTerrainNormalBridge.isContainedWaterQuad(
                ShaderTerrainNormalBridge.encodeObjTag(0)
        ));
    }

    @Test
    void containedWaterClassificationOnlySurvivesItsMatchingVertexWrite() {
        Object vertices = new Object();
        ShaderTerrainNormalBridge.publishContainedWater(
                vertices,
                0.0F, 1.0F, 0.0F,
                0.0F, 1.0F, 0.0F,
                0.0F, 1.0F, 0.0F,
                0.0F, 1.0F, 0.0F
        );

        assertTrue(ShaderTerrainNormalBridge.claim(vertices));
        assertTrue(ShaderTerrainNormalBridge.isClaimedContainedWater());

        ShaderTerrainNormalBridge.release();
        assertFalse(ShaderTerrainNormalBridge.isClaimedContainedWater());

        ShaderTerrainNormalBridge.publish(
                vertices,
                0.0F, 1.0F, 0.0F,
                0.0F, 1.0F, 0.0F,
                0.0F, 1.0F, 0.0F,
                0.0F, 1.0F, 0.0F
        );
        assertTrue(ShaderTerrainNormalBridge.claim(vertices));
        assertFalse(ShaderTerrainNormalBridge.isClaimedContainedWater());
        ShaderTerrainNormalBridge.release();
    }

    @Test
    void stationaryWaterKeepsItsStationaryMaterialScopedToMatchingShaderAndQuad() {
        Object vertices = new Object();
        int tag = ShaderTerrainNormalBridge.stationaryWaterTag();
        assertTrue(ShaderTerrainNormalBridge.isContainedWaterQuad(tag));
        assertFalse(ShaderTerrainNormalBridge.isObjQuad(tag));
        assertEquals(32001, ContainedWaterShaderIds.stationaryWater(32000, 10049));
        assertEquals(32000, ContainedWaterShaderIds.stationaryWater(32000, -1));
        assertEquals(41, ContainedWaterShaderIds.stationaryWater(41, 10049));

        ShaderTerrainNormalBridge.publishContainedWater(tag, vertices,
                0, 1, 0, 0, 1, 0, 0, 1, 0, 0, 1, 0);
        assertTrue(ShaderTerrainNormalBridge.claim(vertices));
        assertTrue(ShaderTerrainNormalBridge.isClaimedContainedWater());
        assertTrue(ShaderTerrainNormalBridge.isClaimedStationaryWater());
        ShaderTerrainNormalBridge.release();
        assertFalse(ShaderTerrainNormalBridge.isClaimedStationaryWater());

        ShaderTerrainNormalBridge.publishContainedWater(vertices,
                0, 1, 0, 0, 1, 0, 0, 1, 0, 0, 1, 0);
        assertTrue(ShaderTerrainNormalBridge.claim(vertices));
        assertTrue(ShaderTerrainNormalBridge.isClaimedContainedWater());
        assertFalse(ShaderTerrainNormalBridge.isClaimedStationaryWater());
    }

    @Test
    void glazingObjTagsRetainMaterialIndicesWithoutTaggingOrdinaryObjectsOrWater() {
        for (int material : new int[]{0, 17, 65535}) {
            int tag = ShaderTerrainNormalBridge.encodeGlazingObjTag(material);
            assertTrue(ShaderTerrainNormalBridge.isObjQuad(tag));
            assertTrue(ShaderTerrainNormalBridge.isGlazingObjQuad(tag));
            assertFalse(ShaderTerrainNormalBridge.isContainedWaterQuad(tag));
            assertEquals(material, ShaderTerrainNormalBridge.decodeObjMaterialTag(tag));
        }
        assertFalse(ShaderTerrainNormalBridge.isGlazingObjQuad(ShaderTerrainNormalBridge.encodeObjTag(0)));
        assertFalse(ShaderTerrainNormalBridge.isGlazingObjQuad(ShaderTerrainNormalBridge.containedWaterTag()));
        assertFalse(ShaderTerrainNormalBridge.isGlazingObjQuad(-1));
        assertThrows(IllegalArgumentException.class, () -> ShaderTerrainNormalBridge.encodeGlazingObjTag(-1));
        assertThrows(IllegalArgumentException.class, () -> ShaderTerrainNormalBridge.encodeGlazingObjTag(65536));
    }

    @Test
    void glazingClassificationCannotLeakIntoWaterOrOrdinaryNormals() {
        Object vertices = new Object();
        publishGlazing(vertices);
        assertTrue(ShaderTerrainNormalBridge.claim(vertices));
        assertTrue(ShaderTerrainNormalBridge.isClaimedGlazing());
        assertFalse(ShaderTerrainNormalBridge.isClaimedContainedWater());
        assertEquals(0x007F00, ShaderTerrainNormalBridge.nextNormal(-1));
        ShaderTerrainNormalBridge.release();
        assertFalse(ShaderTerrainNormalBridge.isClaimedGlazing());

        ShaderTerrainNormalBridge.publishContainedWater(vertices,
                0, 1, 0, 0, 1, 0, 0, 1, 0, 0, 1, 0);
        assertTrue(ShaderTerrainNormalBridge.claim(vertices));
        assertTrue(ShaderTerrainNormalBridge.isClaimedContainedWater());
        assertFalse(ShaderTerrainNormalBridge.isClaimedGlazing());
        ShaderTerrainNormalBridge.release();

        ShaderTerrainNormalBridge.publish(vertices,
                0, 1, 0, 0, 1, 0, 0, 1, 0, 0, 1, 0);
        assertTrue(ShaderTerrainNormalBridge.claim(vertices));
        assertFalse(ShaderTerrainNormalBridge.isClaimedContainedWater());
        assertFalse(ShaderTerrainNormalBridge.isClaimedGlazing());
    }

    @Test
    void mismatchedOrDiscardedGlazingWriteCannotAffectTheNextQuad() {
        Object vertices = new Object();
        publishGlazing(vertices);
        assertFalse(ShaderTerrainNormalBridge.claim(new Object()));
        assertFalse(ShaderTerrainNormalBridge.isClaimedGlazing());
        assertFalse(ShaderTerrainNormalBridge.claim(vertices));
        publishGlazing(vertices);
        ShaderTerrainNormalBridge.discard();
        assertFalse(ShaderTerrainNormalBridge.claim(vertices));
        assertFalse(ShaderTerrainNormalBridge.isClaimedGlazing());
        assertEquals(42, ShaderTerrainNormalBridge.nextNormal(42));
    }

    private static void publishGlazing(Object vertices) {
        ShaderTerrainNormalBridge.publishGlazing(vertices,
                0, 1, 0, 0, 1, 0, 0, 1, 0, 0, 1, 0);
    }

    @Test void flameTagsAreReversibleAndCannotBeMistakenForGlassWaterOrOrdinaryObjects() {
        for(int material:new int[]{0,4,65535}) {
            int tag=ShaderTerrainNormalBridge.encodeFlameObjTag(material);
            assertTrue(ShaderTerrainNormalBridge.isObjQuad(tag));
            assertTrue(ShaderTerrainNormalBridge.isFlameObjQuad(tag));
            assertFalse(ShaderTerrainNormalBridge.isGlazingObjQuad(tag));
            assertFalse(ShaderTerrainNormalBridge.isContainedWaterQuad(tag));
            assertEquals(material,ShaderTerrainNormalBridge.decodeObjMaterialTag(tag));
        }
        assertFalse(ShaderTerrainNormalBridge.isFlameObjQuad(ShaderTerrainNormalBridge.encodeGlazingObjTag(4)));
        assertFalse(ShaderTerrainNormalBridge.isFlameObjQuad(ShaderTerrainNormalBridge.encodeObjTag(4)));
        assertFalse(ShaderTerrainNormalBridge.isFlameObjQuad(ShaderTerrainNormalBridge.containedWaterTag()));
        assertThrows(IllegalArgumentException.class,()->ShaderTerrainNormalBridge.encodeFlameObjTag(-1));
        assertThrows(IllegalArgumentException.class,()->ShaderTerrainNormalBridge.encodeFlameObjTag(65536));
    }

    @Test void flameProxyAppliesOnlyToItsOwnMatchingWrite() {
        Object vertices=new Object();
        ShaderTerrainNormalBridge.publishFlame(vertices,0,1,0,0,1,0,0,1,0,0,1,0);
        assertTrue(ShaderTerrainNormalBridge.claim(vertices));
        assertTrue(ShaderTerrainNormalBridge.isClaimedFlame());
        assertFalse(ShaderTerrainNormalBridge.isClaimedGlazing());
        assertFalse(ShaderTerrainNormalBridge.isClaimedContainedWater());
        assertEquals(0x007F00,ShaderTerrainNormalBridge.nextNormal(-1));
        ShaderTerrainNormalBridge.release();
        assertFalse(ShaderTerrainNormalBridge.isClaimedFlame());
        publishGlazing(vertices);
        assertTrue(ShaderTerrainNormalBridge.claim(vertices));
        assertTrue(ShaderTerrainNormalBridge.isClaimedGlazing());
        assertFalse(ShaderTerrainNormalBridge.isClaimedFlame());
        ShaderTerrainNormalBridge.release();
        ShaderTerrainNormalBridge.publishContainedWater(vertices,0,1,0,0,1,0,0,1,0,0,1,0);
        assertTrue(ShaderTerrainNormalBridge.claim(vertices));
        assertTrue(ShaderTerrainNormalBridge.isClaimedContainedWater());
        assertFalse(ShaderTerrainNormalBridge.isClaimedFlame());
        ShaderTerrainNormalBridge.release();
        ShaderTerrainNormalBridge.publish(vertices,0,1,0,0,1,0,0,1,0,0,1,0);
        assertTrue(ShaderTerrainNormalBridge.claim(vertices));
        assertFalse(ShaderTerrainNormalBridge.isClaimedFlame());
        ShaderTerrainNormalBridge.publishFlame(vertices,0,1,0,0,1,0,0,1,0,0,1,0);
        assertFalse(ShaderTerrainNormalBridge.claim(new Object()));
        assertFalse(ShaderTerrainNormalBridge.isClaimedFlame());
        ShaderTerrainNormalBridge.publishFlame(vertices,0,1,0,0,1,0,0,1,0,0,1,0);
        ShaderTerrainNormalBridge.discard();
        assertFalse(ShaderTerrainNormalBridge.claim(vertices));
        assertFalse(ShaderTerrainNormalBridge.isClaimedFlame());
    }
}
