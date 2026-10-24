package com.oliver.daedalon.gametest;

import net.fabricmc.api.ModInitializer;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.IntProperty;
import net.minecraft.util.Identifier;
import net.minecraft.util.StringIdentifiable;

/** Test-only ERYDON state shape, allowing the optional integration to run without ERYDON. */
public final class LargeColumnFixture implements ModInitializer {
    public static final IntProperty X = IntProperty.of("part_x", 0, 1);
    public static final IntProperty Z = IntProperty.of("part_z", 0, 1);
    public static final EnumProperty<Section> SECTION = EnumProperty.of("section", Section.class);
    public static final EnumProperty<Part> PART = EnumProperty.of("part", Part.class);
    public static final EnumProperty<Capital> CAPITAL = EnumProperty.of("capital", Capital.class);
    public static Block BLOCK;
    public static Block SQUARE;
    public static Block GOTHIC;

    @Override public void onInitialize() {
        BLOCK = Registry.register(Registries.BLOCK,
                new Identifier("erydon", "test_column_circular_double"),
                new Block(AbstractBlock.Settings.create().strength(1.5F)) {
                    @Override protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
                        builder.add(X, Z, SECTION);
                    }
                });
        SQUARE = Registry.register(Registries.BLOCK, new Identifier("erydon", "test_column_square"),
                new FixtureColumn());
        GOTHIC = Registry.register(Registries.BLOCK, new Identifier("erydon", "test_column_gothic"),
                new FixtureColumn());
    }

    private static final class FixtureColumn extends Block {
        private FixtureColumn() {
            super(AbstractBlock.Settings.create().strength(1.5F));
            setDefaultState(getDefaultState().with(PART, Part.CAPITAL).with(CAPITAL, Capital.GEORGIAN));
        }
        @Override protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
            builder.add(PART, CAPITAL);
        }
    }

    public enum Part implements StringIdentifiable {
        CAPITAL, PILLAR;
        @Override public String asString() { return name().toLowerCase(java.util.Locale.ROOT); }
    }

    public enum Capital implements StringIdentifiable {
        GEORGIAN, NONE;
        @Override public String asString() { return name().toLowerCase(java.util.Locale.ROOT); }
    }

    public enum Section implements StringIdentifiable {
        CAPITAL_UPPER("capital_upper"), SHAFT("shaft");
        private final String id;
        Section(String id) { this.id = id; }
        @Override public String asString() { return id; }
    }
}
