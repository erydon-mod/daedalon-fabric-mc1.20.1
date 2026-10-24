package com.oliver.daedalon.item;

import com.oliver.daedalon.block.DecorOrientation;
import com.oliver.daedalon.block.CapitalPartBlock;
import com.oliver.daedalon.block.FountainBasinPartBlock;
import com.oliver.daedalon.block.FriezeBlock;
import com.oliver.daedalon.block.MonopterosPartBlock;
import net.minecraft.block.BlockState;
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

    /** The actual player-visible controls, excluding automatic and helper-only state. */
    public static List<Property<?>> ordered(BlockState state) {
        if (state.getBlock() instanceof CapitalPartBlock || state.getBlock() instanceof FountainBasinPartBlock
                || state.getBlock() instanceof MonopterosPartBlock) return List.of();
        boolean manual = state.getBlock() instanceof FriezeBlock && state.get(FriezeBlock.MANUAL_CORNER);
        boolean repeating = !(state.getBlock() instanceof FriezeBlock frieze) || frieze.style().sections() > 1;
        return ordered(state.getBlock().getStateManager().getProperties().stream()
                .filter(property -> property != FriezeBlock.JOIN || manual)
                .filter(property -> property != FriezeBlock.PATTERN || repeating).toList());
    }

    public static List<String> fountainControls(boolean hasPlinth) {
        return hasPlinth ? List.of("basin_size", "raised_base", "plinth_size", "tiers", "facing", "waterlogged")
                : List.of("basin_size", "raised_base", "waterlogged");
    }

    public static List<Property<?>> ordered(Collection<Property<?>> properties) {
        List<Property<?>> ordered = new ArrayList<>(properties);
        boolean finial=ordered.stream().anyMatch(property -> property.getName().equals("finial_support"));
        boolean eightWay = ordered.stream().anyMatch(property -> property.getName().equals("facing"))
                && ordered.contains(DecorOrientation.PROPERTY);
        ordered.removeIf(property -> property.getName().equals("offset_x")
                || property.getName().equals("offset_y") || property.getName().equals("offset_z")
                || property.getName().equals("capital_support")
                || property.getName().startsWith("finial_") || finial && property.getName().equals("facing")
                || eightWay && property == DecorOrientation.PROPERTY);
        ordered.sort(PROPERTY_ORDER);
        return List.copyOf(ordered);
    }

    public static Property<?> rememberedOrFirst(List<Property<?>> properties, String rememberedName) {
        Property<?> remembered = remembered(properties, rememberedName);
        return remembered != null ? remembered : properties.isEmpty() ? null : properties.get(0);
    }

    public static Property<?> remembered(List<Property<?>> properties, String rememberedName) {
        if ("orientation".equals(rememberedName) && properties.stream().anyMatch(property -> property.getName().equals("facing"))
                && !properties.contains(DecorOrientation.PROPERTY)) rememberedName = "facing";
        for (Property<?> property : properties) {
            if (property.getName().equals(rememberedName)) {
                return property;
            }
        }
        return null;
    }

    /** A new stick selects the primary control first; a remembered one keeps cycling normally. */
    public static <T> T selectControl(List<T> controls, T remembered, boolean update, boolean backwards) {
        if (controls.isEmpty()) return null;
        int index = remembered == null ? -1 : controls.indexOf(remembered);
        return index < 0 ? controls.get(0) : update ? controls.get(index) : cycle(controls, remembered, backwards);
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
        int index = current == null ? -1 : values.indexOf(current);
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
            case "width" -> 1;
            case "diameter" -> 2;
            case "raised_base" -> 3;
            case "offset" -> 4;
            case "ceiling" -> 5;
            case "facing", "orientation", "capital_orientation" -> 6;
            case "pattern" -> 7;
            case "manual_corner" -> 8;
            case "join" -> 9;
            case "waterlogged" -> 100;
            default -> 50;
        };
    }

    private static <T extends Comparable<T>> int valuePriority(Property<T> property, T value) {
        String valueName = property.name(value);
        if (value instanceof Boolean flag) return flag ? 1 : 0;
        return switch (property.getName()) {
            case "orientation", "capital_orientation" -> switch (valueName) {
                case "straight" -> 0;
                case "diagonal" -> 1;
                case "straight_90" -> 2;
                case "diagonal_135" -> 3;
                default -> 100;
            };
            case "size" -> switch (valueName) {
                case "small" -> 0;
                case "standard" -> 0;
                case "medium" -> 1;
                case "large" -> 2;
                case "double" -> 2;
                default -> 100;
            };
            case "width", "diameter", "pattern" -> Integer.parseInt(valueName);
            case "join" -> switch (valueName) {
                case "straight" -> 0;
                case "inner_left" -> 1;
                case "inner_right" -> 2;
                case "outer_left" -> 3;
                case "outer_right" -> 4;
                default -> 100;
            };
            case "tiers" -> switch (valueName) {
                case "none" -> 0;
                case "one" -> 1;
                case "two" -> 2;
                case "three" -> 3;
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
