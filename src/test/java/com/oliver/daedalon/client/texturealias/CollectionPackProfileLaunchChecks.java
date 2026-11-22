package com.oliver.daedalon.client.texturealias;

import net.minecraft.resource.ResourcePack;
import net.minecraft.resource.ResourcePackCompatibility;
import net.minecraft.resource.ResourcePackProfile;
import net.minecraft.resource.ResourcePackSource;
import net.minecraft.resource.ResourceType;
import net.minecraft.resource.featuretoggle.FeatureSet;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Real profile mixins: marker decisions and descriptions agree, without recursive/full pack loading. */
public final class CollectionPackProfileLaunchChecks {
    private static final String VALID = "{\"schema\":1,\"family_asset_generation\":2,"
            + "\"family_compatibility_generation\":2,\"release\":\"1.5.17\",\"resolution\":64}";
    private CollectionPackProfileLaunchChecks() { }

    public static void run() {
        verify("file/ERYDON-Collection-64x-compat2-v2.0.0.zip", null,
                CollectionResourcePackCompatibility.Reason.MISSING_MARKER);
        verify("file/ERYDON-Collection-64x-compat1-v1.0.0.zip", VALID,
                CollectionResourcePackCompatibility.Reason.COMPATIBLE);
        verify("file/renamed.zip", VALID, CollectionResourcePackCompatibility.Reason.COMPATIBLE);
        verify("file/ERYDON-Collection-64x-compat2-v2.0.0.zip",
                VALID.replace("\"family_asset_generation\":2", "\"family_asset_generation\":3"),
                CollectionResourcePackCompatibility.Reason.WRONG_ASSET_GENERATION);
        verify("file/my-erydon-block-fix.zip", null, CollectionResourcePackCompatibility.Reason.NOT_COLLECTION);
        verify("file/ERYDON-Stone-Fix-32x-v1.0.0.zip", null, CollectionResourcePackCompatibility.Reason.NOT_COLLECTION);
        refreshOnAssetReload();
        System.out.println("ERYDON_COLLECTION_PROFILE_OK cachedRawReads=1 decisions=6 descriptions=consistent thirdParty=untouched reloadCache=refreshed");
    }

    private static void verify(String id, String marker, CollectionResourcePackCompatibility.Reason expected) {
        AtomicInteger opens = new AtomicInteger();
        AtomicReference<TextureAliasTestSupport.FakePack> inspected = new AtomicReference<>();
        ResourcePackProfile profile = profile(id, () -> {
            opens.incrementAndGet();
            var raw = new TextureAliasTestSupport.FakePack(id);
            if (marker != null) raw.put(CollectionResourcePackCompatibility.COLLECTION_MARKER_ID, marker);
            raw.put(TextureAliasTestSupport.ALIAS, "must-not-load-textures");
            inspected.compareAndSet(null, raw);
            return raw;
        });
        require((Object) profile instanceof CollectionPackProfile, "Collection profile mixin was not applied");
        var diagnostic = (CollectionPackProfile) (Object) profile;
        var first = diagnostic.erydon$collectionStatus();
        require(first.reason() == expected, "Raw marker classification disagreed: " + id);
        for (int repeat = 0; repeat < 4; repeat++) {
            require(diagnostic.erydon$collectionStatus() == first, "Profile rebuilt its immutable report");
            require(profile.getCompatibility() == (first.rejected() ? ResourcePackCompatibility.TOO_OLD
                    : ResourcePackCompatibility.COMPATIBLE), "UI compatibility disagreed with marker: " + id);
            Text description = profile.getDescription();
            require(hasReason(description, expected.translationKey()) == first.rejected(),
                    "Pack description omitted/duplicated marker rejection: " + id);
        }
        require(opens.get() == 1, "Repeated UI queries reopened the pack: " + id);
        require(inspected.get().supplierReads(TextureAliasTestSupport.ALIAS) == 0,
                "UI diagnostics eagerly read a texture");
        require(inspected.get().closeCount() == 1, "Raw marker inspection leaked its pack handle");
        try (ResourcePack loaded = profile.createResourcePack()) {
            require(loaded.getNamespaces(ResourceType.CLIENT_RESOURCES).isEmpty() == first.rejected(),
                    "Asset gate disagreed with cached UI decision: " + id);
        }
    }

    private static void refreshOnAssetReload() {
        String id = "file/ERYDON-Collection-64x-compat2-v2.0.0.zip";
        AtomicInteger opens = new AtomicInteger();
        AtomicReference<String> marker = new AtomicReference<>(VALID);
        var profile = profile(id, () -> {
            opens.incrementAndGet();
            var raw = new TextureAliasTestSupport.FakePack(id);
            raw.put(CollectionResourcePackCompatibility.COLLECTION_MARKER_ID, marker.get());
            return raw;
        });
        var diagnostic = (CollectionPackProfile) (Object) profile;
        require(diagnostic.erydon$collectionStatus().compatible(), "Initial marker was not compatible");
        marker.set(VALID.replace("\"family_compatibility_generation\":2", "\"family_compatibility_generation\":3"));
        try (ResourcePack loaded = profile.createResourcePack()) {
            require(loaded.getNamespaces(ResourceType.CLIENT_RESOURCES).isEmpty(), "Reload accepted changed incompatible assets");
        }
        require(diagnostic.erydon$collectionStatus().reason() == CollectionResourcePackCompatibility.Reason.WRONG_FAMILY_GENERATION,
                "Profile retained stale compatible status after asset reload");
        require(profile.getCompatibility() == ResourcePackCompatibility.TOO_OLD
                        && hasReason(profile.getDescription(), CollectionResourcePackCompatibility.Reason.WRONG_FAMILY_GENERATION.translationKey()),
                "Reload status and pack list disagreed");
        marker.set(VALID);
        try (ResourcePack loaded = profile.createResourcePack()) {
            require(!loaded.getNamespaces(ResourceType.CLIENT_RESOURCES).isEmpty(), "Reload rejected restored compatible assets");
        }
        require(diagnostic.erydon$collectionStatus().compatible()
                        && profile.getCompatibility() == ResourcePackCompatibility.COMPATIBLE,
                "Profile retained stale rejection after asset reload");
        require(opens.get() == 3, "Asset reload diagnostics opened an extra raw pack");
    }

    private static boolean hasReason(Text text, String key) {
        if (text.getContent() instanceof TranslatableTextContent translated && translated.getKey().equals(key)) return true;
        for (Text child : text.getSiblings()) if (hasReason(child, key)) return true;
        return false;
    }

    private static ResourcePackProfile profile(String id, java.util.function.Supplier<ResourcePack> factory) {
        return ResourcePackProfile.of(id, Text.literal(id), false, ignored -> factory.get(),
                new ResourcePackProfile.Metadata(Text.literal("Fixture description"), 15, FeatureSet.empty()),
                ResourceType.CLIENT_RESOURCES, ResourcePackProfile.InsertionPosition.TOP, false, ResourcePackSource.NONE);
    }

    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
