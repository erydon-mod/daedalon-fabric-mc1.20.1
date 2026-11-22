package com.oliver.daedalon.client.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import static org.junit.jupiter.api.Assertions.*;

class FamilyClientOptionsTest {
    @TempDir Path directory;

    @Test
    void missingAndInvalidPreferencesKeepDefaultAndSavingPreservesOtherFamilyKeys() throws Exception {
        Path file = directory.resolve("family.properties");
        assertTrue(FamilyClientOptions.noticeEnabled(FamilyClientOptions.readProperties(file)));
        Files.writeString(file, "other_family_option=keep\nshow_resource_pack_notice=invalid\n");
        assertTrue(FamilyClientOptions.noticeEnabled(FamilyClientOptions.readProperties(file)));
        FamilyClientOptions.saveNotice(file, false);
        Properties saved = FamilyClientOptions.readProperties(file);
        assertFalse(FamilyClientOptions.noticeEnabled(saved));
        assertEquals("keep", saved.getProperty("other_family_option"));
        FamilyClientOptions.saveNotice(file, true);
        assertTrue(FamilyClientOptions.noticeEnabled(FamilyClientOptions.readProperties(file)));
    }

    @Test
    void failedSaveDoesNotReplaceUnreadablePreferencesOrLeaveStaging() throws Exception {
        Path file = directory.resolve("family.properties");
        Files.writeString(file, "broken=\\uNOPE\n");
        String original = Files.readString(file);
        assertThrows(java.io.IOException.class, () -> FamilyClientOptions.saveNotice(file, false));
        assertEquals(original, Files.readString(file));
        try (var files = Files.list(directory)) {
            assertEquals(1, files.count());
        }
    }
}
