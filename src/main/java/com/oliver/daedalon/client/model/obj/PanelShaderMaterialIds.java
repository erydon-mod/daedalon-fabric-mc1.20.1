package com.oliver.daedalon.client.model.obj;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;

/** Complementary's generic non-solid identity prevents a full-cube reflection ghost. */
public final class PanelShaderMaterialIds {
    static final int NON_SOLID = 5000;

    private PanelShaderMaterialIds() {}

    public static <T> Object2IntMap<T> classify(Object2IntMap<T> original,
                                              Iterable<T> panelStates,
                                              T waterState, T ladderState) {
        // Match the actual shader material vocabulary, not the shader pack filename.
        // Other packs and explicit mappings supplied by a pack remain authoritative.
        if (original == null || original.getOrDefault(waterState, -1) != 32000
                || original.getOrDefault(ladderState, -1) != 10721) return original;
        Object2IntOpenHashMap<T> adjusted = null;
        for (T state : panelStates) {
            if (original.getOrDefault(state, -1) > 0) continue;
            if (adjusted == null) {
                adjusted = new Object2IntOpenHashMap<>(original);
                adjusted.defaultReturnValue(original.defaultReturnValue());
            }
            adjusted.put(state, NON_SOLID);
        }
        return adjusted == null ? original : adjusted;
    }
}
