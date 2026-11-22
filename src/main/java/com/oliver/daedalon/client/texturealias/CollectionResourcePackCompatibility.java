package com.oliver.daedalon.client.texturealias;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.oliver.daedalon.compat.FamilyReleaseCompatibility;
import net.minecraft.resource.InputSupplier;
import net.minecraft.resource.ResourcePack;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Distinguishes compatible ERYDON Collection packs from incompatible releases.
 *
 * <p>Minecraft's normal pack format only describes the Minecraft version, so
 * ERYDON's own asset generation needs a separate marker. Small third-party
 * override packs are deliberately left alone.</p>
 */
public final class CollectionResourcePackCompatibility {
    public static final Identifier COLLECTION_MARKER_ID =
            new Identifier("erydon", "erydon_collection_pack.json");

    private static final Logger LOGGER =
            LoggerFactory.getLogger("ERYDON/ResourcePackCompatibility");
    private static final String OWNER_MOD_ID = "daedalon";
    private static final int CURRENT_FAMILY_ASSET_GENERATION = 2;
    private static final Pattern FULL_SEMANTIC_VERSION =
            Pattern.compile("(?:0|[1-9][0-9]*)\\.(?:0|[1-9][0-9]*)\\.(?:0|[1-9][0-9]*)");
    private static final Pattern OFFICIAL_COLLECTION_NAME = Pattern.compile(
            "erydoncollection(?:32x|64x)(?:compat[0-9]+)?(?:v?[0-9]+\\.[0-9]+\\.[0-9]+)?");
    private static final Pattern OFFICIAL_LEGACY_NAME = Pattern.compile(
            "(?:erydon(?:rp|family)?(?:16xlite|64xpbr)(?:v?[0-9]+\\.[0-9]+\\.[0-9]+)?"
                    + "|erydonv?[0-9]+\\.[0-9]+\\.[0-9]+(?:16xlite|64xpbr|64xresourcepack(?:ctm)?))");

    private CollectionResourcePackCompatibility() {
    }

    public enum Reason {
        NOT_COLLECTION, COMPATIBLE, MISSING_MARKER, UNREADABLE_MARKER, MALFORMED_MARKER,
        UNSUPPORTED_SCHEMA, WRONG_ASSET_GENERATION, WRONG_FAMILY_GENERATION, INVALID_RELEASE, INVALID_RESOLUTION;

        public String translationKey() {
            return "message.erydon_family.pack.reason." + name().toLowerCase(Locale.ROOT);
        }
    }

    /** One immutable marker decision shared by the pack list, diagnostics and asset gate. */
    public record Report(Reason reason, Integer assetGeneration, Integer familyGeneration,
                         String release, Integer resolution) {
        public boolean isCollection() { return reason != Reason.NOT_COLLECTION; }
        public boolean compatible() { return reason == Reason.COMPATIBLE; }
        public boolean rejected() { return isCollection() && !compatible(); }
    }

    public static Report inspect(String profileId, String description, ResourcePack pack) {
        return inspect(profileId, description, pack, currentCompatibilityGeneration());
    }

    static Report inspect(String profileId, String description, ResourcePack pack, int expectedGeneration) {
        boolean namedCollection = looksLikeOfficialCollectionPack(profileId, description);
        boolean identifiedCollection = namedCollection;
        try {
            InputSupplier<InputStream> supplier = pack.open(ResourceType.CLIENT_RESOURCES, COLLECTION_MARKER_ID);
            if (supplier == null) return report(namedCollection ? Reason.MISSING_MARKER : Reason.NOT_COLLECTION);
            identifiedCollection = true;
            // A marker identifies a renamed official pack too; filenames never establish compatibility.
            try (InputStream input = supplier.get();
                 InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
                JsonObject marker = JsonParser.parseReader(reader).getAsJsonObject();
                Integer schema = integer(marker, "schema");
                Integer assets = integer(marker, "family_asset_generation");
                Integer family = integer(marker, "family_compatibility_generation");
                Integer resolution = integer(marker, "resolution");
                String release = marker.has("release") && marker.get("release").isJsonPrimitive()
                        && marker.getAsJsonPrimitive("release").isString() ? marker.get("release").getAsString() : null;
                Reason reason;
                if (schema == null || assets == null || family == null) reason = Reason.MALFORMED_MARKER;
                else if (schema != 1) reason = Reason.UNSUPPORTED_SCHEMA;
                else if (assets != CURRENT_FAMILY_ASSET_GENERATION) reason = Reason.WRONG_ASSET_GENERATION;
                else if (family != expectedGeneration) reason = Reason.WRONG_FAMILY_GENERATION;
                else if (release == null || !FULL_SEMANTIC_VERSION.matcher(release).matches()) reason = Reason.INVALID_RELEASE;
                else if (resolution == null || resolution != 32 && resolution != 64) reason = Reason.INVALID_RESOLUTION;
                else reason = Reason.COMPATIBLE;
                return new Report(reason, assets, family, release, resolution);
            } catch (RuntimeException exception) {
                return report(Reason.MALFORMED_MARKER);
            }
        } catch (IOException | RuntimeException exception) {
            return report(identifiedCollection ? Reason.UNREADABLE_MARKER : Reason.NOT_COLLECTION);
        }
    }

    /** A raw profile factory can fail before it supplies a resource pack. */
    public static Report unavailable(String profileId, String description) {
        return report(looksLikeOfficialCollectionPack(profileId, description)
                ? Reason.UNREADABLE_MARKER : Reason.NOT_COLLECTION);
    }

    private static Report report(Reason reason) { return new Report(reason, null, null, null, null); }

    private static Integer integer(JsonObject marker, String key) {
        if (!marker.has(key) || !marker.get(key).isJsonPrimitive()
                || !marker.getAsJsonPrimitive(key).isNumber()) return null;
        try {
            return marker.get(key).getAsBigDecimal().toBigIntegerExact().intValueExact();
        } catch (ArithmeticException | NumberFormatException exception) {
            return null;
        }
    }

    public static ResourcePack disableIfLegacy(String profileId, String description, ResourcePack pack) {
        return disableIfLegacy(profileId, description, pack, currentCompatibilityGeneration());
    }

    static ResourcePack disableIfLegacy(String profileId, String description, ResourcePack pack, int expectedGeneration) {
        return disableIfLegacy(profileId, pack, inspect(profileId, description, pack, expectedGeneration));
    }

    /** Apply the already inspected decision so asset loading and profile diagnostics cannot disagree. */
    public static ResourcePack disableIfLegacy(String profileId, ResourcePack pack, Report status) {
        if (!status.rejected()) return pack;
        LOGGER.warn("Ignoring assets from incompatible ERYDON Collection pack '{}': {}.", profileId, status.reason());
        return new DisabledIncompatibleCollectionPack(pack);
    }

    public static boolean looksLikeOfficialCollectionPack(String profileId, String description) {
        // Recognize complete official titles, never arbitrary mentions of ERYDON/resolution/version.
        // In particular, a small "ERYDON-Stone-Fix-32x-v1.0.0" override is not an official pack.
        int separator = Math.max(profileId.lastIndexOf('/'), profileId.lastIndexOf('\\'));
        return isOfficialTitle(profileId.substring(separator + 1)) || isOfficialTitle(description);
    }

    private static boolean isOfficialTitle(String value) {
        String compact = value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9.]", "");
        if (compact.endsWith(".zip")) compact = compact.substring(0, compact.length() - 4);
        return OFFICIAL_COLLECTION_NAME.matcher(compact).matches()
                || OFFICIAL_LEGACY_NAME.matcher(compact).matches();
    }

    private static int currentCompatibilityGeneration() {
        return FamilyReleaseCompatibility.currentGeneration(OWNER_MOD_ID);
    }

    private static final class DisabledIncompatibleCollectionPack implements ResourcePack {
        private final ResourcePack delegate;

        private DisabledIncompatibleCollectionPack(ResourcePack delegate) {
            this.delegate = delegate;
        }

        @Override
        public InputSupplier<InputStream> openRoot(String... segments) {
            return delegate.openRoot(segments);
        }

        @Override
        public InputSupplier<InputStream> open(ResourceType type, Identifier id) {
            return null;
        }

        @Override
        public void findResources(
                ResourceType type,
                String namespace,
                String prefix,
                ResultConsumer consumer
        ) {
        }

        @Override
        public java.util.Set<String> getNamespaces(ResourceType type) {
            return java.util.Set.of();
        }

        @Override
        public <T> T parseMetadata(
                net.minecraft.resource.metadata.ResourceMetadataReader<T> metaReader
        ) throws IOException {
            return delegate.parseMetadata(metaReader);
        }

        @Override
        public String getName() {
            return delegate.getName();
        }

        @Override
        public boolean isAlwaysStable() {
            return delegate.isAlwaysStable();
        }

        @Override
        public void close() {
            delegate.close();
        }
    }
}
