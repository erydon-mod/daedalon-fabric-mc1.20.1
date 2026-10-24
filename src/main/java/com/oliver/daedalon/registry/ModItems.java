package com.oliver.daedalon.registry;

import com.oliver.daedalon.item.OlympianGodItem;
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
    private static final List<Item> CATALOGUE_ITEMS = new ArrayList<>();
    private static final List<Item> OLYMPIAN_GOD_ITEMS = new ArrayList<>();
    private static boolean selectorsRegistered;

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
        CATALOGUE_ITEMS.add(item);
        return item;
    }

    public static List<Item> blockItems() {
        return Collections.unmodifiableList(BLOCK_ITEMS);
    }

    /** Block items plus placement selectors, in their registration order. */
    public static List<Item> catalogueItems() {
        return Collections.unmodifiableList(CATALOGUE_ITEMS);
    }

    public static List<Item> olympianGodItems() {
        return Collections.unmodifiableList(OLYMPIAN_GOD_ITEMS);
    }

    public static void register() {
        if (selectorsRegistered) return;
        // All statue block items exist now; each selector caches its twelve
        // actual targets once instead of looking them up during placement.
        for (DecorMaterial material : DecorMaterial.values()) {
            registerOlympianGod(material.id());
            registerOlympianGod(material.id() + "_aged");
        }
        for (DecorMetal metal : DecorMetal.values()) registerOlympianGod(metal.id());
        selectorsRegistered = true;
    }

    private static void registerOlympianGod(String materialPrefix) {
        Identifier id = new Identifier(ModBlocks.MOD_ID, materialPrefix + "_" + OlympianGodItem.ID_SUFFIX);
        Item item = Registry.register(Registries.ITEM, id, new OlympianGodItem(new Item.Settings(), materialPrefix));
        OLYMPIAN_GOD_ITEMS.add(item);
        CATALOGUE_ITEMS.add(item);
    }
}
