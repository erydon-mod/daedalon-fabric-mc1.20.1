package com.oliver.daedalon;

import com.oliver.daedalon.client.CollectionResourcePackNotice;
import com.oliver.daedalon.client.fountain.FountainParticles;
import com.oliver.daedalon.client.model.obj.ObjGuiIconCache;
import com.oliver.daedalon.client.model.obj.ObjMeshModelLoadingPlugin;
import com.oliver.daedalon.client.model.obj.MonopterosRenderBounds;
import com.oliver.daedalon.block.FinialRaycast;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.api.ClientModInitializer;

public final class DaedalonClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        com.oliver.daedalon.client.FountainParticleOption.register();
        CollectionResourcePackNotice.register();
        ObjGuiIconCache.register();
        ObjMeshModelLoadingPlugin.register();
        MonopterosRenderBounds.register();
        ClientChunkEvents.CHUNK_LOAD.register(FinialRaycast::loadChunk);
        ClientChunkEvents.CHUNK_UNLOAD.register((world,chunk) -> FinialRaycast.forgetChunk(world,chunk.getPos().x,chunk.getPos().z));
        ClientPlayConnectionEvents.DISCONNECT.register((handler,client) -> FinialRaycast.clearWorld(client.world));
        FountainParticles.register();
    }
}
