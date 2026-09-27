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
    public static Block BLOCK;

    @Override public void onInitialize() {
        BLOCK = Registry.register(Registries.BLOCK,
                new Identifier("erydon", "test_column_circular_double"),
                new Block(AbstractBlock.Settings.create().strength(1.5F)) {
                    @Override protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
                        builder.add(X, Z, SECTION);
                    }
                });
    }

    public enum Section implements StringIdentifiable {
        CAPITAL_UPPER("capital_upper"), SHAFT("shaft");
        private final String id;
        Section(String id) { this.id = id; }
        @Override public String asString() { return id; }
    }
}
