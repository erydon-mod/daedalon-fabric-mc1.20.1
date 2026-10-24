package com.oliver.daedalon.item;

import com.oliver.daedalon.block.StatueBlock;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.world.World;

import java.util.List;

/** A saved per-stack selector which places ordinary, material-matched statue blocks. */
public final class OlympianGodItem extends Item {
    public static final String ID_SUFFIX = "olympian_god_statue";
    public static final String NEXT_OLYMPIAN_NBT = "DaedalonNextOlympian";

    private final BlockItem[] statues;

    public OlympianGodItem(Settings settings, String materialPrefix) {
        super(settings);
        statues = new BlockItem[OlympianGodCycle.SUBJECTS.size()];
        for (int i = 0; i < statues.length; i++) {
            Identifier id = new Identifier("daedalon", materialPrefix + "_"
                    + OlympianGodCycle.SUBJECTS.get(i) + "_statue");
            if (!(Registries.ITEM.get(id) instanceof BlockItem item)
                    || !(item.getBlock() instanceof StatueBlock)) {
                throw new IllegalStateException("Missing Olympian placement target: " + id);
            }
            statues[i] = item;
        }
    }

    /** The tooltip and placement share the same cached target; neither scans the registry. */
    public BlockItem nextStatueItem(ItemStack stack) {
        return statues[nextIndex(stack)];
    }

    public static int nextIndex(ItemStack stack) {
        NbtCompound nbt = stack.getNbt();
        return OlympianGodCycle.index(nbt == null ? 0 : nbt.getInt(NEXT_OLYMPIAN_NBT));
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        ItemStack selector = context.getStack();
        if (selector.isEmpty()) return ActionResult.FAIL;

        int index = nextIndex(selector);
        BlockItem target = statues[index];
        ItemStack placementStack = target.getDefaultStack();
        // Only ordinary statue state belongs to the placed model. The selector's
        // cursor, custom name and any unrelated item data remain on the selector.
        NbtCompound stateTag = selector.getSubNbt(BlockItem.BLOCK_STATE_TAG_KEY);
        if (stateTag != null) placementStack.setSubNbt(BlockItem.BLOCK_STATE_TAG_KEY, stateTag.copy());

        ActionResult result = target.useOnBlock(new StatueUsageContext(context, placementStack));
        if (result.isAccepted() && !context.getWorld().isClient) {
            var player = context.getPlayer();
            if (player == null || !player.getAbilities().creativeMode) selector.decrement(1);
            if (!selector.isEmpty()) {
                selector.getOrCreateNbt().putInt(NEXT_OLYMPIAN_NBT, OlympianGodCycle.next(index));
            }
            if (player != null) player.getInventory().markDirty();
            // Creative keeps the stack count, so explicitly publish its changed
            // NBT too. No client-only cursor can drift after a rejected placement.
            if (player instanceof ServerPlayerEntity serverPlayer) {
                serverPlayer.currentScreenHandler.sendContentUpdates();
            }
        }
        return result;
    }

    @Override
    public void appendTooltip(ItemStack stack, World world, List<Text> tooltip, TooltipContext context) {
        super.appendTooltip(stack, world, tooltip, context);
        tooltip.add(Text.translatable("tooltip.daedalon.olympian_god.cycle").formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("tooltip.daedalon.olympian_god.next", nextStatueItem(stack).getName())
                .formatted(Formatting.GRAY));
    }

    private static final class StatueUsageContext extends ItemUsageContext {
        private StatueUsageContext(ItemUsageContext source, ItemStack stack) {
            super(source.getWorld(), source.getPlayer(), source.getHand(), stack,
                    new BlockHitResult(source.getHitPos(), source.getSide(), source.getBlockPos(),
                            source.hitsInsideBlock()));
        }
    }
}
