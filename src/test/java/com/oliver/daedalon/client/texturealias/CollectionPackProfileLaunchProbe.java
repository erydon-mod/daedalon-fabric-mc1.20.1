package com.oliver.daedalon.client.texturealias;

import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;

/** No Minecraft window, world save or packaged test mod is needed. */
public final class CollectionPackProfileLaunchProbe implements PreLaunchEntrypoint {
    @Override public void onPreLaunch() {
        try {
            SharedConstants.createGameVersion();
            Bootstrap.initialize();
            CollectionPackProfileLaunchChecks.run();
            System.exit(0);
        } catch (Throwable failure) {
            failure.printStackTrace();
            System.exit(1);
        }
    }
}
