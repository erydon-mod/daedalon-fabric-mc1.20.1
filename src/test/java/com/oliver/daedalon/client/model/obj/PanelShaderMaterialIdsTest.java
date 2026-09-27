package com.oliver.daedalon.client.model.obj;

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class PanelShaderMaterialIdsTest {
    private Object2IntOpenHashMap<String> complementary() {
        var ids = new Object2IntOpenHashMap<String>();
        ids.defaultReturnValue(-1);
        ids.put("water", 32000); ids.put("ladder", 10721);
        return ids;
    }

    @Test void missingPanelsAvoidFullCubeReflectionVoxelWithoutChangingOtherMaterials() {
        var source = complementary();
        source.put("custom_panel", 12345); source.put("stone", 10080);
        source.put("zero_panel", 0);
        var result = PanelShaderMaterialIds.classify(source,
                List.of("small", "medium", "large", "custom_panel", "zero_panel"), "water", "ladder");
        for (var panel : List.of("small", "medium", "large", "zero_panel")) {
            int id = result.getInt(panel);
            assertEquals(5000, id);
            // Exact non-solid exclusions in Complementary's reflection and light voxelizers.
            assertTrue(Math.abs(id - 5000) <= 4999);
            assertTrue(id < 10000);
        }
        assertEquals(12345, result.getInt("custom_panel"));
        assertEquals(10080, result.getInt("stone"));
        assertEquals(-1, result.getInt("unrelated"));
        assertEquals(-1, source.getInt("small"));
        assertEquals(0, source.getInt("zero_panel"));
        assertSame(result, PanelShaderMaterialIds.classify(result,
                List.of("small", "medium", "large"), "water", "ladder"));
    }

    @Test void otherShaderVocabulariesAndDisabledShadersRemainUntouched() {
        assertNull(PanelShaderMaterialIds.classify(null, List.of("panel"), "water", "ladder"));
        var ids = complementary(); ids.put("ladder", 42);
        assertSame(ids, PanelShaderMaterialIds.classify(ids, List.of("panel"), "water", "ladder"));
        ids.put("ladder", 10721); ids.put("water", 42);
        assertSame(ids, PanelShaderMaterialIds.classify(ids, List.of("panel"), "water", "ladder"));
    }
}
