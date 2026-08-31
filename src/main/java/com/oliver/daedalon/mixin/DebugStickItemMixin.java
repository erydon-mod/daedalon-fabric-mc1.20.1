package com.oliver.daedalon.mixin;

import com.oliver.daedalon.Daedalon;
import com.oliver.daedalon.item.DaedalonDebugProperties;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.DebugStickItem;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.state.property.Property;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/** Makes Daedalon model controls consistent without changing vanilla blocks. */
@Mixin(DebugStickItem.class)
abstract class DebugStickItemMixin {
    @Inject(method = "use", at = @At("HEAD"), cancellable = true)
    private void daedalon$useOrderedRememberedProperty(
            PlayerEntity player,
            BlockState state,
            WorldAccess world,
            BlockPos pos,
            boolean update,
            ItemStack stack,
            CallbackInfoReturnable<Boolean> callback
    ) {
        if (!Daedalon.MOD_ID.equals(Registries.BLOCK.getId(state.getBlock()).getNamespace())) {
            return;
        }
        if (!player.isCreativeLevelTwoOp()) {
            callback.setReturnValue(false);
            return;
        }

        List<Property<?>> properties = DaedalonDebugProperties.ordered(
                state.getBlock().getStateManager().getProperties()
        );
        String rememberedName = stack.getOrCreateNbt().getString(
                DaedalonDebugProperties.REMEMBERED_PROPERTY_NBT
        );
        Property<?> selected = DaedalonDebugProperties.rememberedOrFirst(properties, rememberedName);
        if (selected == null) {
            sendMessage(player, Text.translatable(
                    "item.minecraft.debug_stick.empty",
                    Registries.BLOCK.getId(state.getBlock()).toString()
            ));
            callback.setReturnValue(false);
            return;
        }

        if (update) {
            BlockState updated = cycleState(state, selected, player.shouldCancelInteraction());
            world.setBlockState(pos, updated, 18);
            sendMessage(player, Text.translatable(
                    "item.minecraft.debug_stick.update",
                    selected.getName(),
                    valueName(updated, selected)
            ));
        } else {
            selected = cycle(properties, selected, player.shouldCancelInteraction());
            stack.getOrCreateNbt().putString(
                    DaedalonDebugProperties.REMEMBERED_PROPERTY_NBT,
                    selected.getName()
            );
            sendMessage(player, Text.translatable(
                    "item.minecraft.debug_stick.select",
                    selected.getName(),
                    valueName(state, selected)
            ));
        }
        callback.setReturnValue(true);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static BlockState cycleState(BlockState state, Property property, boolean backwards) {
        Comparable current = state.get(property);
        Comparable next = (Comparable) cycle(
                DaedalonDebugProperties.orderedValues(property),
                current,
                backwards
        );
        return state.with(property, next);
    }

    private static <T> T cycle(Iterable<T> values, T current, boolean backwards) {
        return backwards ? Util.previous(values, current) : Util.next(values, current);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static String valueName(BlockState state, Property property) {
        return property.name(state.get(property));
    }

    private static void sendMessage(PlayerEntity player, Text message) {
        ((ServerPlayerEntity) player).sendMessageToClient(message, true);
    }
}
