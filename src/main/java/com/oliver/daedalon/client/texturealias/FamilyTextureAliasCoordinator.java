package com.oliver.daedalon.client.texturealias;

import net.fabricmc.fabric.api.resource.ModResourcePack;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.CustomValue;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.minecraft.resource.ResourcePack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Elects one installed ERYDON-family mod to install the shared alias wrappers.
 *
 * <p>Every family mod carries an independent resolver so it remains
 * standalone. When several are installed together, the metadata capability
 * and fixed priority keep their identical mixins from wrapping packs more than
 * once.</p>
 */
public final class FamilyTextureAliasCoordinator {
    public static final String CAPABILITY_KEY = "erydon:texture_alias_resolver";
    public static final int CAPABILITY_VERSION = 1;

    private static final String SELF_MOD_ID = "daedalon";
    private static final Logger LOGGER =
            LoggerFactory.getLogger("Daedalon/TextureAliasCoordinator");
    private static final AtomicBoolean LEADER_LOGGED = new AtomicBoolean();

    private FamilyTextureAliasCoordinator() {
    }

    public static ResourcePack wrapFamilyNamespaces(ResourcePack pack) {
        return isLeader()
                ? TextureAliasResourcePack.wrapFamilyNamespaces(pack)
                : pack;
    }

    public static List<ModResourcePack> wrapAllFamilyModPacks(
            List<ModResourcePack> packs
    ) {
        return isLeader()
                ? TextureAliasResourcePack.wrapAllFamilyModPacks(packs)
                : packs;
    }

    public static boolean isLeader() {
        String leader = selectLeader(FabricLoader.getInstance().getAllMods().stream()
                .map(ModContainer::getMetadata)
                .filter(FamilyTextureAliasCoordinator::hasResolverCapability)
                .map(ModMetadata::getId)
                .toList());
        if (LEADER_LOGGED.compareAndSet(false, true)) {
            LOGGER.info("Texture alias resolver leader: {}", leader);
        }
        return SELF_MOD_ID.equals(leader);
    }

    static String selectLeader(List<String> capableModIds) {
        return capableModIds.stream()
                .filter(FamilyTextureAliasCoordinator::isFamilyResolver)
                .min(Comparator
                        .comparingInt(FamilyTextureAliasCoordinator::priority)
                        .thenComparing(id -> id))
                .orElse(SELF_MOD_ID);
    }

    private static boolean hasResolverCapability(ModMetadata metadata) {
        CustomValue value = metadata.getCustomValue(CAPABILITY_KEY);
        return value != null
                && value.getType() == CustomValue.CvType.NUMBER
                && value.getAsNumber().intValue() >= CAPABILITY_VERSION;
    }

    private static boolean isFamilyResolver(String modId) {
        return priority(modId) < Integer.MAX_VALUE;
    }

    private static int priority(String modId) {
        return switch (modId) {
            case "erydon" -> 0;
            case "erydon_themelios", "themelios" -> 1;
            case "daedalon" -> 2;
            default -> Integer.MAX_VALUE;
        };
    }
}
