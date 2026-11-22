package com.oliver.daedalon.client.config;

import com.oliver.daedalon.Daedalon;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.fabricmc.loader.api.FabricLoader;

/** Optional navigation only: related mods never load or duplicate ERYDON's setting schema. */
final class ErydonSettingsBridge {
    private ErydonSettingsBridge() { }

    static ConfigScreenFactory<?> find() {
        if (!FabricLoader.getInstance().isModLoaded("erydon")) return null;
        try {
            Object integration = Class.forName("com.oliver.erydon.client.config.ErydonModMenuIntegration")
                    .getConstructor().newInstance();
            return integration instanceof ModMenuApi api ? api.getModConfigScreenFactory() : null;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException failure) {
            Daedalon.LOGGER.warn("Compatible ERYDON settings screen is unavailable", failure);
            return null;
        }
    }
}
