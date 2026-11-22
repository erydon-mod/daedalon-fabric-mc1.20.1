package com.oliver.daedalon.client.glaux.interaction;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.oliver.daedalon.client.glaux.dialogue.GlauxDialogueService;
import com.oliver.daedalon.compat.FamilyReleaseCompatibility;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.resource.InputSupplier;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourcePack;
import net.minecraft.resource.ResourcePackProfile;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Observations only: loaded on this client does not imply server/public support. */
final class GlauxContextSampler {
    private static final Identifier MARKER = new Identifier("erydon", "erydon_collection_pack.json");
    private final Map<String,String> mods;
    private final String minecraft;
    private volatile List<GlauxDialogueService.Pack> packs = List.of();
    private volatile boolean packsKnown;

    GlauxContextSampler() {
        Map<String,String> observed = new LinkedHashMap<>();
        FabricLoader.getInstance().getAllMods().forEach(container -> observed.put(
                container.getMetadata().getId(), container.getMetadata().getVersion().getFriendlyString()));
        mods = Map.copyOf(observed);
        minecraft = SharedConstants.getGameVersion().getName();
    }

    void registerReload() {
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            @Override public Identifier getFabricId() { return new Identifier("daedalon", "glaux_context"); }
            @Override public void reload(ResourceManager manager) {
                MinecraftClient.getInstance().execute(() -> {
                    scanPacks(MinecraftClient.getInstance());
                    GlauxInteraction.invalidateLayout();
                });
            }
        });
    }

    void scanPacks(MinecraftClient client) {
        if (client.getResourcePackManager() == null) { packsKnown = false; return; }
        List<GlauxDialogueService.Pack> observed = new ArrayList<>();
        Set<String> enabled = Set.copyOf(client.getResourcePackManager().getEnabledNames());
        boolean complete = true;
        int expected = FamilyReleaseCompatibility.currentGeneration("daedalon");
        for (ResourcePackProfile profile : client.getResourcePackManager().getProfiles()) {
            try (ResourcePack pack = profile.createResourcePack()) {
                // openRoot lets us read a marker even when the compatibility wrapper
                // has intentionally disabled that pack's actual render resources.
                InputSupplier<InputStream> supplier = pack.openRoot("assets", "erydon", "erydon_collection_pack.json");
                if (supplier == null) supplier = pack.open(ResourceType.CLIENT_RESOURCES, MARKER);
                if (supplier == null) continue;
                try (InputStream input = supplier.get()) {
                    byte[] bytes = input.readNBytes(8193);
                    if (bytes.length > 8192) { complete = false; continue; }
                    JsonObject marker = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
                    if (!marker.has("schema") || marker.get("schema").getAsInt() != 1
                            || !marker.has("resolution") || !marker.has("release")
                            || !marker.get("release").getAsString().matches("[0-9]+\\.[0-9]+\\.[0-9]+")) {
                        complete = false; continue;
                    }
                    int resolution = marker.get("resolution").getAsInt();
                    if (resolution != 32 && resolution != 64) { complete = false; continue; }
                    Boolean compatible = marker.has("family_asset_generation") && marker.has("family_compatibility_generation")
                            ? marker.get("family_asset_generation").getAsInt() == expected
                            && marker.get("family_compatibility_generation").getAsInt() == expected : null;
                    observed.add(new GlauxDialogueService.Pack(resolution, true, enabled.contains(profile.getName()), compatible));
                }
            } catch (Exception ignored) { complete = false; }
        }
        packs = List.copyOf(observed);
        packsKnown = complete;
    }

    GlauxDialogueService.Context sample(MinecraftClient client) {
        GlauxDialogueService.HeldItem held = new GlauxDialogueService.HeldItem(false, false, "", "", "", "");
        if (client.player != null) {
            ItemStack stack = client.player.getMainHandStack();
            if (stack.isEmpty()) held = new GlauxDialogueService.HeldItem(true, true, "", "", "", "");
            else {
                Identifier id = Registries.ITEM.getId(stack.getItem());
                held = new GlauxDialogueService.HeldItem(true, false, id.toString(), stack.getName().getString(), id.getNamespace(), family(id.getPath()));
            }
        }
        return new GlauxDialogueService.Context(minecraft, "fabric", mods, true, packs, packsKnown, held);
    }

    private static String family(String path) {
        String id = path.toLowerCase(Locale.ROOT);
        if (id.equals("glaux")) return "glaux";
        if (id.contains("lantern")) return "lantern";
        if (id.contains("bench") || id.contains("hedra") || id.contains("exedra")) return "bench";
        if (id.contains("panel") || id.contains("coffered")) return "panel";
        if (id.contains("statue") || id.contains("bust")) return "statue";
        if (id.contains("column")) return "column";
        if (id.contains("cornice")) return "cornice";
        if (id.contains("urn") || id.contains("vase")) return "urn";
        if (id.endsWith("book")) return "book";
        return "";
    }
}
