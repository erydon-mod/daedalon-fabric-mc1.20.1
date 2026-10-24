package com.oliver.daedalon.client.finish;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.oliver.daedalon.Daedalon;
import com.oliver.daedalon.client.search.DaedalonSearchIndex;
import com.oliver.daedalon.client.search.DaedalonSearchVocabulary;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resource.InputSupplier;
import net.minecraft.resource.ResourcePack;
import net.minecraft.resource.ResourceType;
import net.minecraft.resource.metadata.ResourceMetadataReader;
import net.minecraft.util.Identifier;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Cached language-only overlay. Never inspects packs, textures, worlds or registries. */
public final class DaedalonStoneFinishLabelsPack implements ResourcePack {
    private final Map<Identifier, byte[]> resources;

    private DaedalonStoneFinishLabelsPack(Map<Identifier, byte[]> resources) {
        this.resources = Map.copyOf(resources);
    }

    public static List<ResourcePack> append(ResourceType type, List<ResourcePack> packs) {
        if (type != ResourceType.CLIENT_RESOURCES || !FabricLoader.getInstance().isModLoaded("erydon")) return packs;
        return append(type, packs, Cached.PACK);
    }

    static List<ResourcePack> append(ResourceType type, List<ResourcePack> packs, DaedalonStoneFinishLabelsPack labels) {
        if (type != ResourceType.CLIENT_RESOURCES || labels == null || labels.resources.isEmpty()) return packs;
        List<ResourcePack> result = new ArrayList<>(packs);
        result.add(labels);
        return result;
    }

    static DaedalonStoneFinishLabelsPack create(DaedalonSearchIndex index,
                                                Map<String, Map<String, String>> languages,
                                                Map<String, String> finishes) {
        Map<Identifier, byte[]> resources = new LinkedHashMap<>();
        languages.forEach((language, original) -> {
            JsonObject labels = new JsonObject();
            finishes.forEach((path, finish) -> {
                if (!index.isStandardStone(path)) return;
                String key = translationKey(original, path);
                String name = original.get(key);
                String label = original.get("finish.daedalon." + finish);
                var entry = index.entry(path);
                String material = Character.toUpperCase(entry.material().charAt(0)) + entry.material().substring(1);
                String prefix = material + " ";
                if (name != null && label != null && name.startsWith(prefix)) {
                    labels.addProperty(key, prefix + label + " " + name.substring(prefix.length()));
                }
            });
            if (labels.size() > 0) resources.put(new Identifier("daedalon", "lang/" + language + ".json"),
                    labels.toString().getBytes(StandardCharsets.UTF_8));
        });
        return new DaedalonStoneFinishLabelsPack(resources);
    }

    static String translationKey(Map<String, String> language, String path) {
        String block = "block.daedalon." + path;
        return language.containsKey(block) ? block : "item.daedalon." + path;
    }

    private static DaedalonStoneFinishLabelsPack load() {
        try {
            var index = DaedalonSearchVocabulary.index();
            Map<String, Map<String, String>> languages = readLanguages();
            List<String> paths = languages.get("en_us").keySet().stream()
                    .filter(key -> key.startsWith("block.daedalon.") || key.startsWith("item.daedalon."))
                    .map(key -> key.substring(key.indexOf("daedalon.") + "daedalon.".length()))
                    .filter(index::isStandardStone).distinct().toList();
            var finishes = ErydonStoneFinishBridge.read(DaedalonStoneFinishLabelsPack.class.getClassLoader(), paths);
            var result = create(index, languages, finishes);
            Daedalon.LOGGER.info("[daedalon] Stone finish names follow ERYDON's active preferences ({} eligible items; changes require restart)",
                    finishes.size());
            return result;
        } catch (ReflectiveOperationException | IOException | LinkageError | RuntimeException exception) {
            Daedalon.LOGGER.warn("[daedalon] ERYDON stone finish names unavailable; original names retained", exception);
            return null;
        }
    }

    static Map<String, Map<String, String>> readLanguages() throws IOException {
        Map<String, Map<String, String>> languages = new LinkedHashMap<>();
        for (String language : List.of("en_us", "de_de", "es_es")) {
            String path = "/assets/daedalon/lang/" + language + ".json";
            try (InputStream input = DaedalonStoneFinishLabelsPack.class.getResourceAsStream(path)) {
                if (input == null) throw new IOException("Missing bundled language: " + path);
                JsonObject json = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
                Map<String, String> values = new LinkedHashMap<>();
                json.entrySet().forEach(entry -> values.put(entry.getKey(), entry.getValue().getAsString()));
                languages.put(language, Map.copyOf(values));
            }
        }
        return Map.copyOf(languages);
    }

    @Override public InputSupplier<InputStream> open(ResourceType type, Identifier id) {
        byte[] bytes = type == ResourceType.CLIENT_RESOURCES ? resources.get(id) : null;
        return bytes == null ? null : () -> new ByteArrayInputStream(bytes);
    }
    @Override public InputSupplier<InputStream> openRoot(String... segments) { return null; }
    @Override public Set<String> getNamespaces(ResourceType type) {
        return type == ResourceType.CLIENT_RESOURCES && !resources.isEmpty() ? Set.of("daedalon") : Set.of();
    }
    @Override public void findResources(ResourceType type, String namespace, String prefix, ResultConsumer consumer) {
        if (type != ResourceType.CLIENT_RESOURCES) return;
        resources.forEach((id, bytes) -> {
            if (id.getNamespace().equals(namespace) && (prefix.isEmpty() || id.getPath().startsWith(prefix + "/"))) {
                consumer.accept(id, () -> new ByteArrayInputStream(bytes));
            }
        });
    }
    @Override public <T> T parseMetadata(ResourceMetadataReader<T> reader) { return null; }
    @Override public String getName() { return "daedalon:stone_finish_labels"; }
    @Override public void close() { }

    private static final class Cached {
        private static final DaedalonStoneFinishLabelsPack PACK = load();
    }
}
