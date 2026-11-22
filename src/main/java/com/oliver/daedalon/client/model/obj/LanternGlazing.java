package com.oliver.daedalon.client.model.obj;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

/** Fixed lantern panes share ERYDON's glass identity without classifying their stone or metal. */
public final class LanternGlazing {
    public static final Identifier TEXTURE = new Identifier("daedalon", "block/lantern_glazing");
    public static final Identifier LEAD_TEXTURE = new Identifier("daedalon", "block/lantern_lead");
    private static final Identifier ERYDON_GLASS = new Identifier("erydon", "glazing_crystal_block");
    private static volatile CachedProxy cachedProxy;

    private LanternGlazing() { }

    public static boolean isGlazingTexture(Identifier texture) {
        return TEXTURE.equals(texture);
    }

    /** Shader-load state maps already contain the optional ERYDON settings/profile decision. */
    public static int shaderBlockId(Object2IntMap<BlockState> ids, int originalBlockId) {
        if (ids == null) return originalBlockId;
        CachedProxy cached = cachedProxy;
        if (cached != null && cached.ids() == ids) {
            return cached.id() >= 0 ? cached.id() : originalBlockId;
        }
        int selected = selectProxyId(ids, ProxyStates.ERYDON, ProxyStates.VANILLA, -1);
        cachedProxy = new CachedProxy(ids, selected);
        return selected >= 0 ? selected : originalBlockId;
    }

    static <T> int selectProxyId(Object2IntMap<T> ids, T erydonGlass, T vanillaGlass, int originalBlockId) {
        if (ids == null) return originalBlockId;
        int preferred = erydonGlass == null ? -1 : ids.getOrDefault(erydonGlass, -1);
        if (preferred >= 0) return preferred;
        int fallback = ids.getOrDefault(vanillaGlass, -1);
        return fallback >= 0 ? fallback : originalBlockId;
    }

    // Registry lookup is deferred until a real world render, after mod registration.
    private static final class ProxyStates {
        private static final BlockState VANILLA = Blocks.GLASS.getDefaultState();
        private static final BlockState ERYDON = Registries.BLOCK.getOrEmpty(ERYDON_GLASS)
                .map(block -> block.getDefaultState()).orElse(null);
    }

    private record CachedProxy(Object2IntMap<BlockState> ids, int id) { }
}
