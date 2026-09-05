package com.oliver.daedalon.client.model.obj;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShaderTerrainNormalBridgeTest {
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
}
