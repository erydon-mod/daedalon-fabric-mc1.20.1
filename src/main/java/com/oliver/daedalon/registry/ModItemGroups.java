package com.oliver.daedalon.registry;

import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;

public final class ModItemGroups {
    private static ItemGroup daedalon;

    private ModItemGroups() {
    }

    public static void register() {
        if (daedalon != null) {
            return;
        }

        daedalon = Registry.register(
                Registries.ITEM_GROUP,
                ModBlocks.id("daedalon"),
                FabricItemGroup.builder()
                        .icon(() -> new ItemStack(ModItems.EMBLEM))
                        .displayName(Text.translatable("itemGroup.daedalon"))
                        .entries((context, entries) ->
                                ModItems.blockItems().forEach(entries::add))
                        .build()
        );
    }

    public static ItemGroup daedalon() {
        if (daedalon == null) {
            throw new IllegalStateException(
                    "ModItemGroups.register() has not run"
            );
        }
        return daedalon;
    }
}
