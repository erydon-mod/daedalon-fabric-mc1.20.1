package com.oliver.daedalon.block;

import com.oliver.daedalon.item.DaedalonDebugProperties;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.IntProperty;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.util.math.Direction;
import net.minecraft.state.property.Property;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class AnthophorosContentsTest {
    @Test
    void fillingRequiresAnEmptyPlanterAndNeverReplacesItsContents() {
        for (AnthophorosBlock.Contents current : AnthophorosBlock.Contents.values()) {
            for (AnthophorosBlock.Contents fill : AnthophorosBlock.Contents.values()) {
                assertEquals(current == AnthophorosBlock.Contents.EMPTY
                                && fill != AnthophorosBlock.Contents.EMPTY,
                        current.canFillWith(fill), current + " filled with " + fill);
            }
        }
    }

    @Test
    void debugControlsKeepWidthAndFacingThenCycleEmptyEarthWater() {
        var contents = EnumProperty.of("contents", AnthophorosBlock.Contents.class);
        assertEquals(List.of("width", "facing", "contents"), DaedalonDebugProperties.ordered(
                List.of(contents, DirectionProperty.of("facing", Direction.Type.HORIZONTAL), IntProperty.of("width", 2, 4)))
                .stream().map(Property::getName).toList());
        var values = DaedalonDebugProperties.orderedValues(contents);
        assertEquals(List.of(AnthophorosBlock.Contents.EMPTY, AnthophorosBlock.Contents.EARTH,
                AnthophorosBlock.Contents.WATER), values);
        assertEquals(AnthophorosBlock.Contents.EMPTY,
                DaedalonDebugProperties.cycle(values, AnthophorosBlock.Contents.WATER, false));
        assertEquals(AnthophorosBlock.Contents.WATER,
                DaedalonDebugProperties.cycle(values, AnthophorosBlock.Contents.EMPTY, true));
    }

    @Test
    void savedContentNamesRemainStableAndDistinct() {
        assertEquals(List.of("empty", "earth", "water"),
                List.of(AnthophorosBlock.Contents.values()).stream()
                        .map(AnthophorosBlock.Contents::asString).toList());
    }
}
