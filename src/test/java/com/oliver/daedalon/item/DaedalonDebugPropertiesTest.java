package com.oliver.daedalon.item;

import org.junit.jupiter.api.Test;
import com.oliver.daedalon.block.CapitalBlock;
import com.oliver.daedalon.block.CapitalOrientation;
import com.oliver.daedalon.block.DecorOrientation;
import com.oliver.daedalon.block.FriezeBlock;
import com.oliver.daedalon.block.FountainBowlModel;
import com.oliver.daedalon.block.UrnBlock;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.IntProperty;
import net.minecraft.state.property.Property;
import net.minecraft.util.StringIdentifiable;
import net.minecraft.util.math.Direction;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertNull;

final class DaedalonDebugPropertiesTest {
    private static final DirectionProperty FACING = DirectionProperty.of("facing", Direction.Type.HORIZONTAL);
    private static final BooleanProperty WATERLOGGED = BooleanProperty.of("waterlogged");
    @Test
    void cyclesRememberedTextByValueRatherThanObjectIdentity() {
        List<String> controls = List.of("basin_size", "plinth_size", "tiers");
        String rememberedFromNbt = new String("plinth_size");

        assertEquals(
                "tiers",
                DaedalonDebugProperties.cycle(controls, rememberedFromNbt, false)
        );
        assertEquals(
                "basin_size",
                DaedalonDebugProperties.cycle(controls, rememberedFromNbt, true)
        );
    }

    @Test
    void cyclesAtBothEndsAndRecoversFromAnUnknownValue() {
        List<String> controls = List.of("basin_size", "waterlogged");

        assertEquals(
                "basin_size",
                DaedalonDebugProperties.cycle(controls, "waterlogged", false)
        );
        assertEquals(
                "waterlogged",
                DaedalonDebugProperties.cycle(controls, "basin_size", true)
        );
        assertEquals(
                "basin_size",
                DaedalonDebugProperties.cycle(controls, "old_control", false)
        );
        assertEquals(
                "waterlogged",
                DaedalonDebugProperties.cycle(controls, "old_control", true)
        );
        assertEquals("basin_size", DaedalonDebugProperties.cycle(controls, null, false));
        assertEquals("waterlogged", DaedalonDebugProperties.cycle(controls, null, true));
    }

    @Test
    void rejectsAnEmptyCycle() {
        assertThrows(
                IllegalArgumentException.class,
                () -> DaedalonDebugProperties.cycle(List.of(), "anything", false)
        );
    }

    @Test void primarySettingsComeFirstAndAutomaticStateNeverBecomesAControl() {
        var size = EnumProperty.of("size", UrnBlock.UrnSize.class);
        var properties = DaedalonDebugProperties.ordered(List.of(WATERLOGGED,
                FACING, BooleanProperty.of("ceiling"), EnumProperty.of("join", FriezeBlock.Join.class),
                IntProperty.of("pattern", 0, 2), size, BooleanProperty.of("offset"), BooleanProperty.of("raised_base"),
                EnumProperty.of("diameter", Numbered.class), IntProperty.of("width", 2, 4),
                BooleanProperty.of("manual_corner"), IntProperty.of("offset_x", 0, 1),
                BooleanProperty.of("capital_support"), DecorOrientation.PROPERTY));
        assertEquals(List.of("size", "width", "diameter", "raised_base", "offset", "ceiling", "facing", "pattern", "manual_corner", "join", "waterlogged"),
                properties.stream().map(Property::getName).toList());
        assertEquals(List.of("size"), DaedalonDebugProperties.ordered(List.of(size,
                BooleanProperty.of("finial_support"), FACING)).stream().map(Property::getName).toList());
    }

    @Test void actualSizeRotationJoinAndNumericValuesFollowOnePredictableOrder() {
        assertEquals(List.of(UrnBlock.UrnSize.SMALL, UrnBlock.UrnSize.MEDIUM, UrnBlock.UrnSize.LARGE),
                DaedalonDebugProperties.orderedValues(EnumProperty.of("size", UrnBlock.UrnSize.class)));
        assertEquals(List.of(CapitalBlock.Size.STANDARD, CapitalBlock.Size.DOUBLE),
                DaedalonDebugProperties.orderedValues(EnumProperty.of("size", CapitalBlock.Size.class)));
        assertEquals(List.of(CapitalOrientation.STRAIGHT, CapitalOrientation.DIAGONAL,
                        CapitalOrientation.STRAIGHT_90, CapitalOrientation.DIAGONAL_135),
                DaedalonDebugProperties.orderedValues(EnumProperty.of("capital_orientation", CapitalOrientation.class)));
        assertEquals(List.of(FriezeBlock.Join.STRAIGHT, FriezeBlock.Join.INNER_LEFT, FriezeBlock.Join.INNER_RIGHT,
                        FriezeBlock.Join.OUTER_LEFT, FriezeBlock.Join.OUTER_RIGHT),
                DaedalonDebugProperties.orderedValues(EnumProperty.of("join", FriezeBlock.Join.class)));
        assertEquals(List.of(Numbered.FOUR, Numbered.SIX, Numbered.EIGHT, Numbered.TEN),
                DaedalonDebugProperties.orderedValues(EnumProperty.of("diameter", Numbered.class)));
        assertEquals(List.of(FountainBowlModel.TierCount.NONE, FountainBowlModel.TierCount.ONE,
                        FountainBowlModel.TierCount.TWO, FountainBowlModel.TierCount.THREE),
                DaedalonDebugProperties.orderedValues(EnumProperty.of("tiers", FountainBowlModel.TierCount.class)));
        for (String name : List.of("offset", "ceiling", "raised_base", "manual_corner", "waterlogged"))
            assertEquals(List.of(false, true), DaedalonDebugProperties.orderedValues(BooleanProperty.of(name)));
    }

    @Test void aNewControlStartsAtPrimaryAndValidLegacySelectionsRemainRemembered() {
        var size = EnumProperty.of("size", UrnBlock.UrnSize.class);
        var offset = BooleanProperty.of("offset");
        List<Property<?>> properties = DaedalonDebugProperties.ordered(List.of(FACING, offset, size));
        assertNull(DaedalonDebugProperties.remembered(properties, "removed_control"));
        assertSame(size, DaedalonDebugProperties.selectControl(properties, null, false, false));
        assertSame(size, DaedalonDebugProperties.selectControl(properties, null, true, false));
        assertSame(size, DaedalonDebugProperties.selectControl(properties, null, false, true));
        assertSame(size, DaedalonDebugProperties.selectControl(properties, null, true, true));
        assertNull(DaedalonDebugProperties.selectControl(List.of(), null, false, false));
        assertSame(offset, DaedalonDebugProperties.selectControl(properties, size, false, false));
        assertSame(offset, DaedalonDebugProperties.selectControl(properties, offset, true, true));
        assertSame(FACING, DaedalonDebugProperties.remembered(properties, "orientation"));
        assertSame(size, DaedalonDebugProperties.selectControl(properties,
                DaedalonDebugProperties.remembered(properties, "orientation"), false, false));
        assertEquals(List.of("basin_size", "raised_base", "waterlogged"), DaedalonDebugProperties.fountainControls(false));
        assertEquals(List.of("basin_size", "raised_base", "plinth_size", "tiers", "facing", "waterlogged"),
                DaedalonDebugProperties.fountainControls(true));
        assertEquals("basin_size", DaedalonDebugProperties.selectControl(DaedalonDebugProperties.fountainControls(true),
                new String("removed_control"), false, false));
        assertEquals("raised_base", DaedalonDebugProperties.selectControl(DaedalonDebugProperties.fountainControls(true),
                new String("basin_size"), false, false));
    }

    private enum Numbered implements StringIdentifiable {
        TEN(10), EIGHT(8), FOUR(4), SIX(6);
        private final int value;
        Numbered(int value) { this.value = value; }
        @Override public String asString() { return Integer.toString(value); }
    }
}
