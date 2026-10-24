package com.oliver.daedalon.client.search;

import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.Language;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** One vocabulary for Creative search and optional item browsers, without changing hover tooltips. */
public final class DaedalonSearchVocabulary {
    private static volatile LocalizedTerms localized;

    private DaedalonSearchVocabulary() { }

    public static DaedalonSearchIndex index() { return IndexHolder.INDEX; }

    public static List<String> terms(ItemStack stack) {
        if (!(stack.getItem() instanceof BlockItem)) return List.of();
        Identifier id = Registries.ITEM.getId(stack.getItem());
        if (id == null || !"daedalon".equals(id.getNamespace())) return List.of();

        LocalizedTerms cache = localizedTerms(Language.getInstance());
        return cache.byPath.computeIfAbsent(id.getPath(), path -> {
            List<String> keys = IndexHolder.INDEX.keys(path);
            if (keys.isEmpty()) return List.of();
            LinkedHashSet<String> result = new LinkedHashSet<>();
            for (String key : keys) {
                result.add(DaedalonSearchIndex.words(key));
                String translationKey = "search.daedalon.tag." + key;
                if (cache.language.hasTranslation(translationKey)) {
                    result.add(cache.language.get(translationKey));
                }
            }
            // Metal plinths deliberately do not support fountain attachments.
            result.addAll(PlinthSearchVocabulary.terms(stack));
            return List.copyOf(result);
        });
    }

    public static String appendTerms(String existing, ItemStack stack) {
        List<String> terms = terms(stack);
        if (terms.isEmpty()) return existing;
        return (existing == null ? "" : existing) + '\u0000'
                + String.join("\u0000", terms).toLowerCase(Locale.ROOT);
    }

    private static LocalizedTerms localizedTerms(Language language) {
        LocalizedTerms current = localized;
        if (current == null || current.language != language) {
            synchronized (DaedalonSearchVocabulary.class) {
                current = localized;
                if (current == null || current.language != language) {
                    localized = current = new LocalizedTerms(language);
                }
            }
        }
        return current;
    }

    private static final class LocalizedTerms {
        private final Language language;
        private final ConcurrentMap<String, List<String>> byPath = new ConcurrentHashMap<>();

        private LocalizedTerms(Language language) {
            this.language = language;
        }
    }

    private static final class IndexHolder {
        private static final DaedalonSearchIndex INDEX = readIndex();

        private static DaedalonSearchIndex readIndex() {
            String resource = "/assets/daedalon/search_vocabulary.json";
            try (var input = DaedalonSearchVocabulary.class.getResourceAsStream(resource)) {
                if (input == null) throw new IllegalStateException("Missing bundled search vocabulary: " + resource);
                try (var reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
                    return DaedalonSearchIndex.read(reader);
                }
            } catch (IOException exception) {
                throw new IllegalStateException("Cannot read bundled Daedalon search vocabulary", exception);
            }
        }
    }
}
