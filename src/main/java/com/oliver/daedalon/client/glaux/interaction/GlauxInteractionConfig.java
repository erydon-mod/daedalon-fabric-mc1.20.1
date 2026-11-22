package com.oliver.daedalon.client.glaux.interaction;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** No transcript, player or world identity is written to this client config. */
final class GlauxInteractionConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    boolean pinnedFocus;
    boolean reducedMotion;
    private static Path path() { return FabricLoader.getInstance().getConfigDir().resolve("daedalon-glaux.json"); }
    static GlauxInteractionConfig load() {
        try {
            if (Files.isRegularFile(path()) && Files.size(path()) < 4096) {
                GlauxInteractionConfig config = GSON.fromJson(Files.readString(path(), StandardCharsets.UTF_8), GlauxInteractionConfig.class);
                if (config != null) return config;
            }
        } catch (IOException | RuntimeException ignored) { /* malformed options fall back, no game crash */ }
        return new GlauxInteractionConfig();
    }
    void save() {
        try { Files.createDirectories(path().getParent()); Files.writeString(path(), GSON.toJson(this), StandardCharsets.UTF_8); }
        catch (IOException ignored) { /* an unwritable options folder must not trap input */ }
    }
}
