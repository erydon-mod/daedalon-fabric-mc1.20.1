package com.oliver.daedalon.client.config;

import com.oliver.daedalon.Daedalon;
import net.fabricmc.loader.api.FabricLoader;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

/** Shared family preferences; disk access occurs only on menu open, save or connection. */
public final class FamilyClientOptions {
    private FamilyClientOptions() { }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("erydon-family-client.properties");
    }

    public static boolean readNotice() throws IOException {
        return noticeEnabled(readProperties(file()));
    }

    public static boolean showResourcePackNotice() {
        try {
            return readNotice();
        } catch (IOException failure) {
            Daedalon.LOGGER.warn("Could not read ERYDON family client preferences; using defaults", failure);
            return true;
        }
    }

    public static void saveNotice(boolean enabled) throws IOException {
        saveNotice(file(), enabled);
    }

    static boolean noticeEnabled(Properties properties) {
        // Invalid values retain the default rather than silently suppressing notices.
        return !"false".equalsIgnoreCase(properties.getProperty("show_resource_pack_notice", "true").trim());
    }

    static void saveNotice(Path target, boolean enabled) throws IOException {
        Properties properties = readProperties(target);
        properties.setProperty("show_resource_pack_notice", Boolean.toString(enabled));
        writeProperties(target, properties);
    }

    public static Properties readProperties(Path target) throws IOException {
        Properties properties = new Properties();
        if (Files.exists(target)) {
            try (var reader = Files.newBufferedReader(target)) {
                properties.load(reader);
            } catch (IllegalArgumentException failure) {
                throw new IOException("Invalid client preferences", failure);
            }
        }
        return properties;
    }

    /** Replace only after the complete new file has been written; remove active staging on failure. */
    public static void writeProperties(Path target, Properties properties) throws IOException {
        Path directory = target.toAbsolutePath().getParent();
        Files.createDirectories(directory);
        Path staged = Files.createTempFile(directory, "erydon-client-settings-", ".tmp");
        try {
            try (var writer = Files.newBufferedWriter(staged)) {
                properties.store(writer, "ERYDON client preferences");
            }
            try {
                Files.move(staged, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(staged, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(staged);
        }
    }
}
