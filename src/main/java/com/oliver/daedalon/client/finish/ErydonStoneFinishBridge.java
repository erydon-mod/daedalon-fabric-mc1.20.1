package com.oliver.daedalon.client.finish;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Reads ERYDON's captured renderer preference once, without a hard dependency. */
final class ErydonStoneFinishBridge {
    private ErydonStoneFinishBridge() { }

    static Map<String, String> read(ClassLoader loader, List<String> paths) throws ReflectiveOperationException {
        return read(loader.loadClass("com.oliver.erydon.client.ErydonHighPolish"),
                loader.loadClass("com.oliver.erydon.client.PolishedStoneMaterials"), paths);
    }

    static Map<String, String> read(Class<?> preferences, Class<?> materials, List<String> paths)
            throws ReflectiveOperationException {
        Object active = preferences.getMethod("activeSettings").invoke(null);
        Method includes = materials.getMethod("includes", String.class, String.class);
        Method level = materials.getMethod("level", active.getClass(), String.class);
        Map<String, String> result = new LinkedHashMap<>();
        for (String path : paths) {
            // Ask the actual renderer classifier, including its exclusions and compatibility rules.
            if (!Boolean.TRUE.equals(includes.invoke(null, "daedalon", path))) continue;
            Object value = level.invoke(null, active, path);
            String key = value instanceof Enum<?> finish ? finish.name().toLowerCase(Locale.ROOT) : "";
            if (!List.of("honed", "polished", "mirror").contains(key)) {
                throw new IllegalArgumentException("Unknown ERYDON stone finish: " + value);
            }
            result.put(path, key);
        }
        return Map.copyOf(result);
    }
}
