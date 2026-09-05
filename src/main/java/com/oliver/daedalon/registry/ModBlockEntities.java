package com.oliver.daedalon.registry;

import com.oliver.daedalon.Daedalon;
import com.oliver.daedalon.block.FountainBasinBlock;
import com.oliver.daedalon.block.entity.FountainBasinBlockEntity;
import net.minecraft.block.Block;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public final class ModBlockEntities {
    private static BlockEntityType<FountainBasinBlockEntity> fountainBasin;

    private ModBlockEntities() {
    }

    public static void register() {
        if (fountainBasin != null) {
            return;
        }
        Block[] basinBlocks = ModBlocks.blocks().stream()
                .filter(block -> block instanceof FountainBasinBlock)
                .toArray(Block[]::new);
        fountainBasin = Registry.register(
                Registries.BLOCK_ENTITY_TYPE,
                new Identifier(Daedalon.MOD_ID, "fountain_basin"),
                BlockEntityType.Builder.create(
                        FountainBasinBlockEntity::new,
                        basinBlocks
                ).build(null)
        );
    }

    public static BlockEntityType<FountainBasinBlockEntity> fountainBasin() {
        if (fountainBasin == null) {
            throw new IllegalStateException("ModBlockEntities.register() must run before basin creation");
        }
        return fountainBasin;
    }
}
