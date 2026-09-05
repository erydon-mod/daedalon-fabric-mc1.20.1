package com.oliver.daedalon.item;

import net.minecraft.state.property.Property;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/** Daedalon's stable, player-facing debug-stick control order. */
public final class DaedalonDebugProperties {
    public static final String REMEMBERED_PROPERTY_NBT = "DaedalonDebugProperty";

    private static final Comparator<Property<?>> PROPERTY_ORDER = Comparator
            .comparingInt(DaedalonDebugProperties::priority)
            .thenComparing(Property::getName);

    private DaedalonDebugProperties() {
    }

    public static List<Property<?>> ordered(Collection<Property<?>> properties) {
        List<Property<?>> ordered = new ArrayList<>(properties);
        ordered.sort(PROPERTY_ORDER);
        return List.copyOf(ordered);
    }

    public static Property<?> rememberedOrFirst(List<Property<?>> properties, String rememberedName) {
        for (Property<?> property : properties) {
            if (property.getName().equals(rememberedName)) {
                return property;
            }
        }
        return properties.isEmpty() ? null : properties.get(0);
    }

    /**
     * Cycles player-facing values by equality instead of object identity.
     * Minecraft's Util.next helper never terminates when given an equal but
     * separately allocated value, such as a control name read back from NBT.
     */
    public static <T> T cycle(List<T> values, T current, boolean backwards) {
        if (values.isEmpty()) {
            throw new IllegalArgumentException("Cannot cycle an empty value list");
        }
        int index = values.indexOf(current);
        if (index < 0) {
            return backwards ? values.get(values.size() - 1) : values.get(0);
        }
        int nextIndex = backwards
                ? Math.floorMod(index - 1, values.size())
                : (index + 1) % values.size();
        return values.get(nextIndex);
    }

    /**
     * Gives every Daedalon model the same player-facing value cycle, independent of
     * Minecraft's internal property collection order.
     */
    public static <T extends Comparable<T>> List<T> orderedValues(Property<T> property) {
        List<T> ordered = new ArrayList<>(property.getValues());
        ordered.sort(Comparator
                .comparingInt((T value) -> valuePriority(property, value))
                .thenComparing(property::name));
        return List.copyOf(ordered);
    }

    private static int priority(Property<?> property) {
        return switch (property.getName()) {
            case "size" -> 0;
            case "width" -> 0;
            case "offset" -> 1;
            case "facing" -> 2;
            default -> 3;
        };
    }

    private static <T extends Comparable<T>> int valuePriority(Property<T> property, T value) {
        String valueName = property.name(value);
        return switch (property.getName()) {
            case "size" -> switch (valueName) {
                case "small" -> 0;
                case "medium" -> 1;
                case "large" -> 2;
                default -> 100;
            };
            case "offset" -> switch (valueName) {
                case "false" -> 0;
                case "true" -> 1;
                default -> 100;
            };
            case "facing" -> switch (valueName) {
                case "north" -> 0;
                case "east" -> 1;
                case "south" -> 2;
                case "west" -> 3;
                default -> 100;
            };
            default -> 100;
        };
    }
}
