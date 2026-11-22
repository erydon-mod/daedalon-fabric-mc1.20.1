package com.oliver.daedalon.glaux;

import net.fabricmc.api.ModInitializer;

/** Separate entrypoint keeps Glaux independent of the existing decor families. */
public final class GlauxMod implements ModInitializer {
    @Override public void onInitialize() { GlauxRegistry.register(); }
}
