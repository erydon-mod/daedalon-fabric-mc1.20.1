package com.oliver.daedalon;

import com.oliver.daedalon.client.CollectionResourcePackNotice;
import com.oliver.daedalon.client.fountain.FountainParticles;
import com.oliver.daedalon.client.model.obj.ObjGuiIconCache;
import com.oliver.daedalon.client.model.obj.ObjMeshModelLoadingPlugin;
import com.oliver.daedalon.client.model.obj.MonopterosRenderBounds;
import net.fabricmc.api.ClientModInitializer;

public final class DaedalonClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        com.oliver.daedalon.client.FountainParticleOption.register();
        CollectionResourcePackNotice.register();
        ObjGuiIconCache.register();
        ObjMeshModelLoadingPlugin.register();
        MonopterosRenderBounds.register();
        FountainParticles.register();
    }
}
