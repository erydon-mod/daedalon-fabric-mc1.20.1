package com.oliver.daedalon.client.texturealias;

import net.minecraft.resource.InputSupplier;
import net.minecraft.resource.ResourcePack;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import static com.oliver.daedalon.client.texturealias.CollectionResourcePackCompatibility.Reason.*;
import static org.junit.jupiter.api.Assertions.*;

class CollectionResourcePackCompatibilityTest {
    private static final String OFFICIAL = "file/ERYDON-Collection-64x-compat2-v2.0.0.zip";
    private static final String CURRENT = "{\"schema\":1,\"family_asset_generation\":2,"
            + "\"family_compatibility_generation\":2,\"release\":\"1.5.17\",\"resolution\":64}";

    @Test void aCompatibilityFilenameCannotExcuseAMissingMarker() {
        var pack = new TextureAliasTestSupport.FakePack("lying-name");
        assertEquals(MISSING_MARKER, inspect(OFFICIAL, pack).reason());
        assertNotSame(pack, gate(OFFICIAL, pack));
    }

    @Test void validMarkerOverridesAnOldOrRenamedFilename() {
        for (String id : List.of("file/ERYDON-Collection-64x-compat1-v1.0.0.zip", "file/my-renamed-pack.zip")) {
            var pack = marker(CURRENT);
            var status = inspect(id, pack);
            assertTrue(status.isCollection());
            assertTrue(status.compatible());
            assertFalse(status.rejected());
            assertEquals(COMPATIBLE, status.reason());
            assertEquals(2, status.assetGeneration());
            assertEquals(2, status.familyGeneration());
            assertEquals("1.5.17", status.release());
            assertEquals(64, status.resolution());
            assertSame(pack, gate(id, pack));
        }
    }

    @Test void legacyOfficialShapesRemainRejectedWithoutAMarker() {
        for (String name : List.of("file/ERYDON-Collection-32x-v1.5.12.zip", "file/Erydon_1.5.2_64xPBR.zip")) {
            var pack = new TextureAliasTestSupport.FakePack("legacy");
            assertTrue(CollectionResourcePackCompatibility.looksLikeOfficialCollectionPack(name, ""));
            assertEquals(MISSING_MARKER, inspect(name, pack).reason());
        }
    }

    @Test void smallThirdPartyOverridesRemainUntouched() {
        var pack = new TextureAliasTestSupport.FakePack("override");
        var status = inspect("file/my-erydon-block-fix.zip", pack);
        assertEquals(NOT_COLLECTION, status.reason());
        assertFalse(status.isCollection());
        assertFalse(status.rejected());
        assertSame(pack, gate("file/my-erydon-block-fix.zip", pack));
    }

    @Test void resolutionAndVersionDoNotTurnSmallOverridesIntoOfficialPacks() {
        for (String description : List.of("", "A fix for ERYDON Collection 32x v1.0.0",
                "Overrides ERYDON blocks for the Collection 64x pack; version 1.0.0")) {
            String id = "file/ERYDON-Stone-Fix-32x-v1.0.0.zip";
            var pack = new TextureAliasTestSupport.FakePack("versioned-third-party-override");
            assertFalse(CollectionResourcePackCompatibility.looksLikeOfficialCollectionPack(id, description));
            assertEquals(NOT_COLLECTION, CollectionResourcePackCompatibility.inspect(id, description, pack, 2).reason());
            assertSame(pack, CollectionResourcePackCompatibility.disableIfLegacy(id, description, pack, 2));
        }
    }

    @Test void assetAndFamilyGenerationsHaveDistinctReasons() {
        var assets = marker(CURRENT.replace("\"family_asset_generation\":2", "\"family_asset_generation\":3"));
        assertEquals(WRONG_ASSET_GENERATION, inspect(OFFICIAL, assets).reason());
        var family = marker(CURRENT.replace("\"family_compatibility_generation\":2", "\"family_compatibility_generation\":3"));
        assertEquals(WRONG_FAMILY_GENERATION, inspect(OFFICIAL, family).reason());
        assertNotSame(family, gate(OFFICIAL, family));
    }

    @Test void unsupportedSchemaHasAUsefulReason() {
        assertEquals(UNSUPPORTED_SCHEMA, inspect(OFFICIAL, marker(CURRENT.replace("\"schema\":1", "\"schema\":2"))).reason());
    }

    @Test void malformedJsonIsRejected() {
        for (String json : List.of("{broken", "null", "[]", "{}")) {
            assertEquals(MALFORMED_MARKER, inspect(OFFICIAL, marker(json)).reason());
        }
    }

    @Test void generationsMustBeActualIntegralNumbersWithoutTruncationOrOverflow() {
        for (String key : List.of("schema", "family_asset_generation", "family_compatibility_generation")) {
            String original = key.equals("schema") ? "1" : "2";
            for (String invalid : List.of("2.5", "\"2\"", "true", "null", "[]", "4294967298")) {
                var pack = marker(CURRENT.replace("\"" + key + "\":" + original, "\"" + key + "\":" + invalid));
                assertEquals(MALFORMED_MARKER, inspect(OFFICIAL, pack).reason(), key + "=" + invalid);
                assertNotSame(pack, gate(OFFICIAL, pack));
            }
        }
    }

    @Test void releaseIsIndependentButMustBeAFullSemanticVersionString() {
        for (String invalid : List.of("\"1.5\"", "\"v1.5.17\"", "\"01.5.17\"", "15", "null")) {
            assertEquals(INVALID_RELEASE, inspect(OFFICIAL,
                    marker(CURRENT.replace("\"1.5.17\"", invalid))).reason());
        }
        assertTrue(inspect(OFFICIAL, marker(CURRENT.replace("1.5.17", "9.8.7"))).compatible());
    }

    @Test void onlyOfficialIntegralResolutionsAreAccepted() {
        assertTrue(inspect(OFFICIAL, marker(CURRENT.replace("\"resolution\":64", "\"resolution\":32"))).compatible());
        for (String invalid : List.of("16", "64.5", "\"64\"", "null", "4294967360")) {
            assertEquals(INVALID_RESOLUTION, inspect(OFFICIAL,
                    marker(CURRENT.replace("\"resolution\":64", "\"resolution\":" + invalid))).reason());
        }
    }

    @Test void theAssetGateUsesTheSameExplicitExpectedGeneration() {
        var pack = marker(CURRENT);
        assertEquals(WRONG_FAMILY_GENERATION,
                CollectionResourcePackCompatibility.inspect(OFFICIAL, "", pack, 3).reason());
        assertNotSame(pack, CollectionResourcePackCompatibility.disableIfLegacy(OFFICIAL, "", pack, 3));
    }

    @Test void inspectionReadsOnlyTheSmallMarkerAndDoesNotCloseTheOwnersPack() {
        var pack = marker(CURRENT);
        pack.put(TextureAliasTestSupport.ALIAS, "unread-texture");
        inspect(OFFICIAL, pack);
        assertEquals(1, pack.supplierReads(CollectionResourcePackCompatibility.COLLECTION_MARKER_ID));
        assertEquals(0, pack.supplierReads(TextureAliasTestSupport.ALIAS));
        assertEquals(0, pack.closeCount());
    }

    @Test void unreadableMarkedPacksHaveTheSameUiAndAssetDecision() {
        var pack = new TextureAliasTestSupport.FakePack("unreadable") {
            @Override public InputSupplier<InputStream> open(ResourceType type, Identifier id) {
                return () -> { throw new IOException("fixture read failure"); };
            }
        };
        assertEquals(UNREADABLE_MARKER, inspect(OFFICIAL, pack).reason());
        assertEquals(UNREADABLE_MARKER, inspect("file/renamed.zip", pack).reason());
        var disabled = gate(OFFICIAL, pack);
        assertNotSame(pack, disabled);
        assertTrue(disabled.getNamespaces(ResourceType.CLIENT_RESOURCES).isEmpty());
        disabled.close();
        assertEquals(1, pack.closeCount());
    }

    @Test void applyingAnInspectedDecisionDoesNotReadTheMarkerAgain() {
        var pack = marker(CURRENT);
        var status = inspect(OFFICIAL, pack);
        assertSame(pack, CollectionResourcePackCompatibility.disableIfLegacy(OFFICIAL, pack, status));
        assertEquals(1, pack.supplierReads(CollectionResourcePackCompatibility.COLLECTION_MARKER_ID));
    }

    @Test void reasonKeysAreStableAndPlayerFacing() {
        assertEquals("message.erydon_family.pack.reason.wrong_family_generation", WRONG_FAMILY_GENERATION.translationKey());
        assertEquals(UNREADABLE_MARKER, CollectionResourcePackCompatibility.unavailable(OFFICIAL, "").reason());
        assertEquals(NOT_COLLECTION, CollectionResourcePackCompatibility.unavailable("file/small-override.zip", "").reason());
    }

    private static TextureAliasTestSupport.FakePack marker(String json) {
        var pack = new TextureAliasTestSupport.FakePack("fixture");
        pack.put(CollectionResourcePackCompatibility.COLLECTION_MARKER_ID, json);
        return pack;
    }
    private static CollectionResourcePackCompatibility.Report inspect(String id, ResourcePack pack) {
        return CollectionResourcePackCompatibility.inspect(id, "", pack, 2);
    }
    private static ResourcePack gate(String id, ResourcePack pack) {
        return CollectionResourcePackCompatibility.disableIfLegacy(id, "", pack, 2);
    }
}
