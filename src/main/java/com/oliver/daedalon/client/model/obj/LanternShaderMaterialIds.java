package com.oliver.daedalon.client.model.obj;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;

import java.util.function.UnaryOperator;

/** Light emission must not remove an otherwise explicitly mapped lantern surface finish. */
public final class LanternShaderMaterialIds {
    private LanternShaderMaterialIds() { }

    public static <T> Object2IntMap<T> inheritOffSurfaceIds(Object2IntMap<T> original,
                                                          Iterable<T> litStates,
                                                          UnaryOperator<T> correspondingOffState) {
        if (original == null) return null;
        Object2IntOpenHashMap<T> adjusted = null;
        for (T lit : litStates) {
            // A shader pack's explicit ON mapping is always authoritative.
            if (original.getOrDefault(lit, -1) > 0) continue;
            int surface = original.getOrDefault(correspondingOffState.apply(lit), -1);
            if (surface <= 0) continue;
            if (adjusted == null) {
                adjusted = new Object2IntOpenHashMap<>(original);
                adjusted.defaultReturnValue(original.defaultReturnValue());
            }
            adjusted.put(lit, surface);
        }
        return adjusted == null ? original : adjusted;
    }
}
