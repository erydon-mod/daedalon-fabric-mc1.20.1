package com.oliver.daedalon.registry;

import net.minecraft.block.Block;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ModItems {
    private static final List<Item> BLOCK_ITEMS = new ArrayList<>();

    public static final Item EMBLEM = Registry.register(
            Registries.ITEM,
            new Identifier(ModBlocks.MOD_ID, "emblem"),
            new Item(new Item.Settings())
    );

    private ModItems() {
    }

    static Item registerBlockItem(Identifier id, Block block) {
        Item item = Registry.register(
                Registries.ITEM,
                id,
                new BlockItem(block, new Item.Settings())
        );
        BLOCK_ITEMS.add(item);
        return item;
    }

    public static List<Item> blockItems() {
        return Collections.unmodifiableList(BLOCK_ITEMS);
    }

    public static void register() {
        /*
         * Block items are registered together with their blocks. This method is
         * an explicit initializer hook and documents the expected entrypoint
         * order without adding a second registration pass.
         */
    }
}
