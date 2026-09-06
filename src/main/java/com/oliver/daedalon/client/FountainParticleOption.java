package com.oliver.daedalon.client;

import com.oliver.daedalon.Daedalon;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.text.Text;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

/** Loaded once; disk access only when the player changes the option. */
public final class FountainParticleOption {
    private static volatile boolean high;
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("daedalon-fountain-particles.properties");
    private FountainParticleOption() {}

    public static boolean isHigh() { return high; }

    public static void register() {
        if (Files.exists(FILE)) {
            Properties properties = new Properties();
            try (var reader = Files.newBufferedReader(FILE)) {
                properties.load(reader);
                high = Boolean.parseBoolean(properties.getProperty("high", "false"));
            } catch (IOException | IllegalArgumentException exception) {
                Daedalon.LOGGER.warn("Could not read fountain particle option; using normal", exception);
            }
        }
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) -> dispatcher.register(
                literal("daedalon").then(literal("fountainParticles")
                        .executes(context -> report(context.getSource()))
                        .then(literal("normal").executes(context -> set(context.getSource(), false)))
                        .then(literal("high").executes(context -> set(context.getSource(), true))))));
    }

    private static int set(FabricClientCommandSource source, boolean enabled) {
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, "high=" + enabled + "\n");
            high = enabled;
            return report(source);
        } catch (IOException exception) {
            source.sendError(Text.translatable("message.daedalon.fountain_particles.save_failed"));
            return 0;
        }
    }

    private static int report(FabricClientCommandSource source) {
        source.sendFeedback(Text.translatable(high ? "message.daedalon.fountain_particles.high" : "message.daedalon.fountain_particles.normal"));
        return 1;
    }
}
