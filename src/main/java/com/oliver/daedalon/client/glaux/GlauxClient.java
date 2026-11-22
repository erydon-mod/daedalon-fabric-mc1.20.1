package com.oliver.daedalon.client.glaux;

import com.oliver.daedalon.client.glaux.interaction.GlauxInteraction;
import net.fabricmc.api.ClientModInitializer;

/** Client entrypoint kept separate from the other decoration families. */
public final class GlauxClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        GlauxVisuals.register();
        GlauxInteraction.register();
    }
}
