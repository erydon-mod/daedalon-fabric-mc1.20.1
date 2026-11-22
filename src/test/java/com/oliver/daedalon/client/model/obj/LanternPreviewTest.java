package com.oliver.daedalon.client.model.obj;

import com.oliver.daedalon.block.LanternBlock;
import com.oliver.daedalon.block.LanternPlacement;
import net.minecraft.util.math.Direction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class LanternPreviewTest {
    @Test void offNeverSelectsAFlameAndLitPreviewKeysStayDistinct() {
        for(var mount:LanternBlock.Mount.values()) {
            assertEquals(java.util.List.of(mount.asString()),LanternMeshParts.bodyComponents(mount,false));
            assertEquals(java.util.List.of(mount.asString(),mount.asString()+"_flame"),LanternMeshParts.bodyComponents(mount,true));
            for(String material:new String[]{"stone","lead","bronze","glass"})
                assertEquals(mount.asString(),LanternMeshParts.componentKey(mount.asString()+"_"+material));
            assertEquals(mount.asString()+"_flame",LanternMeshParts.componentKey(mount.asString()+"_flame"));
            assertEquals(mount.asString()+"_flame",LanternMeshParts.componentKey(mount.asString()+"_flame_front"));
            var off=new LanternPreview(mount,LanternBlock.Size.SMALL,Direction.NORTH,2,true,false);
            var on=new LanternPreview(mount,LanternBlock.Size.SMALL,Direction.NORTH,2,true,true);
            assertNotEquals(off,on);
            assertFalse(off.lit());assertTrue(on.lit());
        }
        assertEquals("full_chain",LanternMeshParts.componentKey("full_chain"));
    }
    @Test void mountSizeFacingAndCanopyRemainDistinctButIrrelevantChainsDoNot() {
        for (var mount : LanternBlock.Mount.values()) for (var size : LanternBlock.Size.values())
            for (var facing : Direction.Type.HORIZONTAL) {
                var preview = new LanternPreview(mount, size, facing, 3, true, true);
                assertEquals(mount, preview.mount());
                assertEquals(size, preview.size());
                assertEquals(facing, preview.facing());
                assertEquals(mount == LanternBlock.Mount.HANGING ? 3 : 0, preview.chainCount());
                assertEquals(mount == LanternBlock.Mount.HANGING, preview.canopy());
            }
        assertEquals(new LanternPreview(LanternBlock.Mount.PIER, LanternBlock.Size.LARGE, Direction.SOUTH, 0, false, true),
                new LanternPreview(LanternBlock.Mount.PIER, LanternBlock.Size.LARGE, Direction.SOUTH, 50, true, true));
    }

    @Test void suspensionUsesOneHalfCellThenWholeCellsAndOnlyOneTopCanopy() {
        for (var size : LanternBlock.Size.values()) for (int count : new int[]{0, 1, 3, LanternPlacement.MAX_CHAIN}) {
            String suffix = size == LanternBlock.Size.SMALL ? "_small" : "";
            var preview = new LanternPreview(LanternBlock.Mount.HANGING, size, Direction.SOUTH, count, true, true);
            var parts = LanternMeshParts.suspensionPreviewComponents(preview);
            assertEquals(count + 2, parts.size());
            assertEquals(new LanternMeshParts.PreviewComponent("half_chain" + suffix, .5F), parts.get(0));
            for (int i = 1; i <= count; i++)
                assertEquals(new LanternMeshParts.PreviewComponent("full_chain" + suffix, i), parts.get(i));
            assertEquals(new LanternMeshParts.PreviewComponent("canopy" + suffix, count + 1), parts.get(count + 1));
            assertEquals(count + 1, LanternMeshParts.suspensionPreviewComponents(
                    new LanternPreview(LanternBlock.Mount.HANGING, size, Direction.SOUTH, count, false, true)).size());
        }
        for (var mount : new LanternBlock.Mount[]{LanternBlock.Mount.PIER, LanternBlock.Mount.WALL})
            assertTrue(LanternMeshParts.suspensionPreviewComponents(
                    new LanternPreview(mount, LanternBlock.Size.LARGE, Direction.SOUTH, 4, true, true)).isEmpty());
    }

    @Test void malformedAssemblyKeysAreBounded() {
        assertEquals(0, new LanternPreview(LanternBlock.Mount.HANGING, LanternBlock.Size.LARGE, Direction.SOUTH, -1, false, true).chainCount());
        assertEquals(LanternPlacement.MAX_CHAIN, new LanternPreview(LanternBlock.Mount.HANGING,
                LanternBlock.Size.LARGE, Direction.SOUTH, Integer.MAX_VALUE, true, true).chainCount());
        assertThrows(IllegalArgumentException.class, () -> new LanternPreview(LanternBlock.Mount.WALL,
                LanternBlock.Size.SMALL, Direction.UP, 0, false, true));
    }
}
