package com.oliver.daedalon.glaux;

import net.fabricmc.fabric.api.object.builder.v1.block.FabricBlockSettings;
import com.oliver.daedalon.registry.ModItems;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;

public final class GlauxRegistry {
    public static final Identifier ID = new Identifier("daedalon", "glaux");
    public static final GlauxBlock BLOCK = new GlauxBlock(FabricBlockSettings.create()
            .strength(3.0F, 6.0F).sounds(BlockSoundGroup.METAL).nonOpaque()
            .pistonBehavior(PistonBehavior.BLOCK));
    public static final BlockItem ITEM = new GlauxItem(BLOCK, new Item.Settings());
    private static BlockEntityType<GlauxBlockEntity> type;
    private GlauxRegistry() { }
    public static synchronized void register() {
        if (type != null) return;
        Registry.register(Registries.BLOCK, ID, BLOCK);
        ModItems.registerBlockItem(ID, ITEM);
        type = Registry.register(Registries.BLOCK_ENTITY_TYPE, ID,
                BlockEntityType.Builder.create(GlauxBlockEntity::new, BLOCK).build(null));
    }
    public static BlockEntityType<GlauxBlockEntity> blockEntityType() {
        if (type == null) throw new IllegalStateException("Glaux has not been registered");
        return type;
    }
    public static boolean isGlaux(BlockState state) { return state != null && state.getBlock() == BLOCK; }
    public static BlockPos basePos(BlockView world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        return isGlaux(state) && state.get(GlauxBlock.HALF) == DoubleBlockHalf.UPPER ? pos.down() : pos.toImmutable();
    }
}
