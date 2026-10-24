package com.oliver.daedalon.client.finish;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.oliver.daedalon.client.search.DaedalonSearchVocabulary;
import net.minecraft.resource.ResourcePack;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DaedalonStoneFinishLabelsPackTest {
    @Test
    void everyFreshNamedStoneUsesTheCapturedFinishAcrossAllThreeLocales() throws Exception {
        var index = DaedalonSearchVocabulary.index();
        var languages = DaedalonStoneFinishLabelsPack.readLanguages();
        Map<String, String> finishes = new LinkedHashMap<>();
        for (String key : languages.get("en_us").keySet()) {
            if (key.startsWith("block.daedalon.")) {
                String path = key.substring("block.daedalon.".length());
                if (index.isStandardStone(path)) finishes.put(path, "mirror");
            }
        }
        assertEquals(2268, finishes.size());
        finishes.put("glacium_hedra", "honed");
        finishes.put("latmion_zeus_statue", "polished");
        // A malformed/overbroad optional provider must not rename unrelated finishes.
        for (String path : List.of("glacium_aged_hedra", "white_gothic_wall_panel",
                "bronze_zeus_statue", "silver_gothic_frieze", "emblem", "glacium_capital_part")) {
            finishes.put(path, "mirror");
        }
        var pack = DaedalonStoneFinishLabelsPack.create(index, languages, finishes);
        for (String language : List.of("en_us", "de_de", "es_es")) {
            JsonObject labels = labels(pack, language);
            assertEquals(2268, labels.size());
            var original = languages.get(language);
            for (var pair : Map.of("glacium_hedra", "honed", "latmion_zeus_statue", "polished",
                    "statue_spartan_promachos_glacium", "mirror").entrySet()) {
                String key = "block.daedalon." + pair.getKey();
                String name = original.get(key);
                int boundary = name.indexOf(' ') + 1;
                String expected = name.substring(0, boundary) + original.get("finish.daedalon." + pair.getValue())
                        + " " + name.substring(boundary);
                assertEquals(expected, labels.get(key).getAsString(), language + ": " + pair.getKey());
            }
            for (String path : List.of("glacium_aged_hedra", "white_gothic_wall_panel",
                    "bronze_zeus_statue", "silver_gothic_frieze", "emblem", "glacium_capital_part")) {
                assertFalse(labels.has("block.daedalon." + path), path);
            }
        }
    }

    @Test
    void onlyRendererEligibleNamesAreOverriddenAndNoPackTexturesAreRead() throws Exception {
        var languages = DaedalonStoneFinishLabelsPack.readLanguages();
        var pack = DaedalonStoneFinishLabelsPack.create(DaedalonSearchVocabulary.index(), languages,
                Map.of("glacium_hedra", "honed"));
        ResourcePack original = (ResourcePack) Proxy.newProxyInstance(ResourcePack.class.getClassLoader(),
                new Class[]{ResourcePack.class}, (proxy, method, args) -> {
                    throw new AssertionError("Finish names must not inspect another pack");
                });
        var packs = List.of(original);
        assertSame(packs, DaedalonStoneFinishLabelsPack.append(ResourceType.SERVER_DATA, packs, pack));
        assertSame(packs, DaedalonStoneFinishLabelsPack.append(ResourceType.CLIENT_RESOURCES, packs, null));
        var result = DaedalonStoneFinishLabelsPack.append(ResourceType.CLIENT_RESOURCES, packs, pack);
        assertSame(original, result.get(0));
        assertSame(pack, result.get(1));
        assertEquals(1, labels(pack, "en_us").size());
        assertFalse(labels(pack, "en_us").has("block.daedalon.statue_spartan_promachos_glacium"),
                "A finish must not be claimed when the renderer excluded that path");
        var textures = new HashSet<Identifier>();
        pack.findResources(ResourceType.CLIENT_RESOURCES, "daedalon", "textures", (id, input) -> textures.add(id));
        assertTrue(textures.isEmpty());
        for (String namespace : List.of("erydon", "daedalon", "minecraft")) {
            for (String suffix : List.of("", "_s", "_n")) {
                assertNull(pack.open(ResourceType.CLIENT_RESOURCES, new Identifier(namespace,
                        "textures/block/glacium_block" + suffix + ".png")));
            }
        }
        assertNull(pack.open(ResourceType.SERVER_DATA, new Identifier("daedalon", "lang/en_us.json")));
        assertNull(pack.open(ResourceType.CLIENT_RESOURCES, new Identifier("erydon", "lang/en_us.json")));
    }

    private static JsonObject labels(ResourcePack pack, String language) throws Exception {
        try (var stream = pack.open(ResourceType.CLIENT_RESOURCES,
                new Identifier("daedalon", "lang/" + language + ".json")).get()) {
            return JsonParser.parseString(new String(stream.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
}
