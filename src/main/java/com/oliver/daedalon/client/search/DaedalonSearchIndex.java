package com.oliver.daedalon.client.search;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.Reader;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Collections;

/** Bundled catalogue metadata; independent of a world's tag binding or optional browsers. */
public final class DaedalonSearchIndex {
    private final Map<String, List<String>> families;
    private final Map<String, List<String>> materials;

    private DaedalonSearchIndex(Map<String, List<String>> families, Map<String, List<String>> materials) {
        this.families = families;
        this.materials = materials;
    }

    public static DaedalonSearchIndex read(Reader reader) {
        JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
        return new DaedalonSearchIndex(readSection(root, "families"), readSection(root, "materials"));
    }

    public List<String> terms(String path) {
        return keys(path).stream().map(DaedalonSearchIndex::words).toList();
    }

    public List<String> terms(String namespace, String path) {
        return "daedalon".equals(namespace) ? terms(path) : List.of();
    }

    List<String> keys(String path) {
        Entry entry = entry(path);
        if (entry == null) return List.of();
        String material = entry.material();
        String family = entry.family();
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        keys.add("daedalon");
        keys.add(material);
        keys.addAll(materials.get(material));
        keys.add(family);
        keys.addAll(families.get(family));
        if (entry.aged()) {
            keys.add("aged");
            keys.add("weathered");
            keys.add("worn");
        }
        return List.copyOf(keys);
    }

    /** The same exact family lookup drives aliases and browser grouping. */
    public Entry entry(String path) {
        if (path == null) return null;
        // Published stone statues retain their original form-first IDs.
        String statuePrefix = "statue_spartan_promachos_";
        if (path.startsWith(statuePrefix)) {
            String material = path.substring(statuePrefix.length());
            boolean aged = material.endsWith("_aged");
            if (aged) material = material.substring(0, material.length() - "_aged".length());
            if (!materials.containsKey(material)) return null;
            path = material + (aged ? "_aged" : "") + "_spartan_promachos_statue";
        }
        int separator = path.indexOf('_');
        if (separator < 1) return null;
        String material = path.substring(0, separator);
        List<String> materialKeys = materials.get(material);
        if (materialKeys == null) return null;

        String family = path.substring(separator + 1);
        boolean aged = family.startsWith("aged_");
        if (aged) family = family.substring("aged_".length());
        List<String> familyKeys = families.get(family);
        if (familyKeys == null) return null;
        return new Entry(material, family, aged);
    }

    public List<String> families() { return List.copyOf(families.keySet()); }

    public List<String> materials() { return List.copyOf(materials.keySet()); }

    public record Entry(String material, String family, boolean aged) { }

    static String words(String key) {
        return key.replace('_', ' ');
    }

    private static Map<String, List<String>> readSection(JsonObject root, String section) {
        if (!root.has(section) || !root.get(section).isJsonObject()) {
            throw new IllegalArgumentException("Missing catalogue search section: " + section);
        }
        Map<String, List<String>> result = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject(section).entrySet()) {
            if (!entry.getValue().isJsonArray()) {
                throw new IllegalArgumentException("Catalogue search terms must be an array: " + entry.getKey());
            }
            LinkedHashSet<String> keys = new LinkedHashSet<>();
            for (JsonElement value : entry.getValue().getAsJsonArray()) {
                String key = value.getAsString();
                if (!key.matches("[a-z0-9_]+")) {
                    throw new IllegalArgumentException("Invalid catalogue search key: " + key);
                }
                keys.add(key);
            }
            result.put(entry.getKey(), List.copyOf(keys));
        }
        return Collections.unmodifiableMap(result);
    }
}
