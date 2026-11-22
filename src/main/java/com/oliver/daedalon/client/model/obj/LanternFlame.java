package com.oliver.daedalon.client.model.obj;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.fabricmc.fabric.api.renderer.v1.material.BlendMode;
import net.fabricmc.fabric.api.renderer.v1.material.MaterialFinder;
import net.fabricmc.fabric.api.renderer.v1.material.RenderMaterial;
import net.fabricmc.fabric.api.util.TriState;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.Identifier;

/** Flame-only render and shader identity; the surrounding stone, metal and panes stay separate. */
public final class LanternFlame {
    public static final Identifier TEXTURE = new Identifier("daedalon", "block/lantern_flame");
    private static volatile CachedProxy cachedProxy;

    private LanternFlame() { }

    public static boolean isFlameTexture(Identifier texture) { return TEXTURE.equals(texture); }

    static RenderMaterial material(MaterialFinder finder) {
        return finder.blendMode(BlendMode.TRANSLUCENT).emissive(true).disableDiffuse(true)
                .ambientOcclusion(TriState.FALSE).find();
    }

    public static int shaderBlockId(Object2IntMap<BlockState> ids, int originalBlockId) {
        if (ids == null) return originalBlockId;
        CachedProxy cached = cachedProxy;
        if (cached != null && cached.ids() == ids) return cached.id() >= 0 ? cached.id() : originalBlockId;
        int selected = selectProxyId(ids, FireState.STATE, -1);
        cachedProxy = new CachedProxy(ids, selected);
        return selected >= 0 ? selected : originalBlockId;
    }

    static <T> int selectProxyId(Object2IntMap<T> ids, T fire, int originalBlockId) {
        if (ids == null) return originalBlockId;
        int proxy = ids.getOrDefault(fire, -1);
        return proxy >= 0 ? proxy : originalBlockId;
    }

    private static final class FireState {
        private static final BlockState STATE = Blocks.FIRE.getDefaultState();
    }
    private record CachedProxy(Object2IntMap<BlockState> ids, int id) { }
}
