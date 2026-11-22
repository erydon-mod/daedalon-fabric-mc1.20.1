package com.oliver.daedalon.client.model.obj;

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class LanternFlameTest {
    @Test void fixedFlameTextureCannotBeConfusedWithPanesStoneOrHardware() {
        assertTrue(LanternFlame.isFlameTexture(new Identifier("daedalon","block/lantern_flame")));
        assertFalse(LanternFlame.isFlameTexture(LanternGlazing.TEXTURE));
        assertFalse(LanternFlame.isFlameTexture(LanternGlazing.LEAD_TEXTURE));
        assertFalse(LanternFlame.isFlameTexture(new Identifier("daedalon","block/bronze")));
        assertFalse(LanternFlame.isFlameTexture(new Identifier("daedalon","block/latmion_block")));
        assertFalse(LanternFlame.isFlameTexture(null));
        assertFalse(LanternGlazing.isGlazingTexture(LanternFlame.TEXTURE));
    }

    @Test void flameBorrowsOnlyTheCurrentFireMappingAndLeavesAllOtherMaterialsUntouched() {
        var ids=new Object2IntOpenHashMap<String>();ids.defaultReturnValue(-1);
        ids.put("fire",10072);ids.put("glass",12057);ids.put("stone",12041);ids.put("lead",5000);ids.put("bronze",10721);
        var before=new Object2IntOpenHashMap<>(ids);
        assertEquals(10072,LanternFlame.selectProxyId(ids,"fire",12041));
        assertEquals(before,ids);
        ids.put("fire",23456);
        assertEquals(23456,LanternFlame.selectProxyId(ids,"fire",12041));
        ids.put("fire",0);
        assertEquals(0,LanternFlame.selectProxyId(ids,"fire",12041));
        ids.removeInt("fire");
        assertEquals(12041,LanternFlame.selectProxyId(ids,"fire",12041));
        assertEquals(17,LanternFlame.selectProxyId(null,"fire",17));
    }
}
