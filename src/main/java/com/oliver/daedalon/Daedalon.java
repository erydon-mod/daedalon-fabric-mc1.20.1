package com.oliver.daedalon;

import com.oliver.daedalon.compat.FamilyReleaseCompatibility;
import com.oliver.daedalon.registry.ModBlocks;
import com.oliver.daedalon.registry.ModItemGroups;
import com.oliver.daedalon.registry.ModItems;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public final class Daedalon implements ModInitializer {
    public static final String MOD_ID = "daedalon";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static final String STARTUP_TEXT_LOGO = loadStartupTextLogo();

    @Override
    public void onInitialize() {
        FamilyReleaseCompatibility.enforce(MOD_ID);
        logStartupTextLogo();
        LOGGER.info("[{}] Initialising", MOD_ID);
        ModBlocks.register();
        ModItems.register();
        ModItemGroups.register();
    }

    private static void logStartupTextLogo() {
        if (STARTUP_TEXT_LOGO.isBlank()) {
            return;
        }
        for (String line : STARTUP_TEXT_LOGO.split("\\R", -1)) {
            LOGGER.info(line);
        }
    }

    private static String loadStartupTextLogo() {
        try (InputStream input = Daedalon.class.getResourceAsStream("/daedalon_text_logo.txt")) {
            if (input == null) {
                LOGGER.warn("[{}] Startup text logo resource missing.", MOD_ID);
                return "";
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            LOGGER.warn("[{}] Failed to load startup text logo.", MOD_ID, exception);
            return "";
        }
    }
}
